package com.zapryan.codegen;

import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.SmartPsiElementPointer;
import com.zapryan.bootstrap.Outcome;
import com.zapryan.launch.MethodInput;
import com.zapryan.reflection.MethodDescriptor;
import com.zapryan.ui.MessageBundler;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;

/**
 * Generates a unit test from a recorded run of a method and writes it into the module's test sources. A test calls
 * the method the way the run did, so everything it needs must be reachable from the test's package; that is checked
 * before anything runs, so a run that can't become a test isn't made.
 */
public final class TestGenerator {

    private static final Logger LOG = Logger.getInstance(TestGenerator.class);
    private static final String NOTIFICATION_GROUP = "InstaRun";

    private TestGenerator() {
    }

    /**
     * Why no test can be generated for the method, or null if one can as far as can be told before the inputs are
     * known: the test can call the method, and the module has a test folder and JUnit. Needs a read action.
     */
    public static @Nullable String checkMethod(Project project, Module module, PsiMethod method) {
        String name = method.getName();
        if (method.hasModifierProperty(PsiModifier.PRIVATE)) {
            return MessageBundler.message("generate.error.privateMethod", name);
        }
        PsiClass aClass = method.getContainingClass();
        if (aClass == null || !Visibility.isAccessible(aClass, Visibility.packageOf(aClass))) {
            return MessageBundler.message("generate.error.privateClass", name);
        }
        try {
            TestLocation.find(project, module, aClass, name);
            return null;
        } catch (CannotGenerate e) {
            return e.getMessage();
        }
    }

    /** Why the test couldn't build the inputs the user entered, or null if it can. Needs a read action. */
    public static @Nullable String checkInputs(Project project, PsiMethod method, MethodInput input) {
        String inaccessible = TestMembers.scan(project, Visibility.packageOf(method), method, input).getInaccessible();
        return inaccessible != null
                ? MessageBundler.message("generate.error.inaccessible", method.getName(), inaccessible)
                : null;
    }

    /**
     * Writes the test for how the recorded run went, opens it at the new method, or tells the user why there is no
     * test. Call on the event dispatch thread.
     *
     * @param outcome null when the run ended without recording one, e.g. it was stopped
     */
    public static void generate(Project project, Module module, SmartPsiElementPointer<PsiMethod> pointer,
                                MethodDescriptor method, MethodInput input, @Nullable Outcome outcome) {
        String name = method.getMethodName();
        if (outcome == null) {
            notify(project, MessageBundler.message("generate.failed.noOutcome", name));
        } else if (outcome instanceof Outcome.NotRun) {
            notify(project, MessageBundler.message("generate.failed.notRun", name, ((Outcome.NotRun) outcome).getReason()));
        } else if (outcome instanceof Outcome.SetupThrew) {
            Outcome.SetupThrew threw = (Outcome.SetupThrew) outcome;
            notify(project, MessageBundler.message("generate.failed.setupThrew", name,
                    threw.getExceptionClassBinaryName(), String.valueOf(threw.getMessage())));
        } else {
            DumbService.getInstance(project).runWhenSmart(() -> write(project, module, pointer, method, input, outcome));
        }
    }

    /**
     * Writes a test that only makes the call with the given inputs, without running the method first, and opens it
     * at the new method, or tells the user why there is no test. Call on the event dispatch thread.
     */
    public static void generateWithoutRunning(Project project, Module module, SmartPsiElementPointer<PsiMethod> pointer,
                                              MethodDescriptor method, MethodInput input) {
        DumbService.getInstance(project).runWhenSmart(() -> write(project, module, pointer, method, input, null));
    }

    /** Tells the user in a balloon, for problems found after the editor's hint is gone. */
    public static void notify(Project project, String message) {
        NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP)
                .createNotification(StringUtil.escapeXmlEntities(message), NotificationType.WARNING)
                .notify(project);
    }

    /** @param outcome null when the method wasn't run */
    private static void write(Project project, Module module, SmartPsiElementPointer<PsiMethod> pointer,
                              MethodDescriptor method, MethodInput input, @Nullable Outcome outcome) {
        String name = method.getMethodName();
        PsiMethod written;
        try {
            written = WriteCommandAction.writeCommandAction(project)
                    .withName(MessageBundler.message("generate.command", name))
                    .compute(() -> {
                        PsiMethod psiMethod = pointer.getElement();
                        PsiClass classUnderTest = psiMethod != null ? psiMethod.getContainingClass() : null;
                        if (classUnderTest == null) {
                            throw new CannotGenerate(MessageBundler.message("generate.failed.methodGone", name));
                        }
                        TestLocation location = TestLocation.find(project, module, classUnderTest, name);
                        GeneratedTest test = render(project, location, psiMethod, method, input, outcome);
                        try {
                            return TestFileWriter.insert(project, location, test);
                        } catch (IOException e) {
                            throw new CannotGenerate(MessageBundler.message("generate.failed.write", name, e.getMessage()));
                        }
                    });
        } catch (CannotGenerate e) {
            notify(project, e.getMessage());
            return;
        } catch (RuntimeException e) {
            LOG.warn("Could not generate a test for " + name, e);
            notify(project, MessageBundler.message("generate.failed.write", name, e.getMessage()));
            return;
        }
        written.navigate(true);
    }

    private static GeneratedTest render(Project project, TestLocation location, PsiMethod psiMethod,
                                        MethodDescriptor method, MethodInput input, @Nullable Outcome outcome)
            throws CannotGenerate {
        TestMembers members = TestMembers.scan(project, location.packageName(), psiMethod, input);
        if (members.getInaccessible() != null) {
            throw new CannotGenerate(MessageBundler.message("generate.error.inaccessible", method.getMethodName(),
                    members.getInaccessible()));
        }
        TestSubject subject = new TestSubject(method,
                TestFileWriter.freeMethodName(location.existingClass(), testMethodName(method, outcome)),
                method.isStatic() ? null : receiverType(psiMethod),
                members.declaresExceptions());
        return TestMethodWriter.write(subject, input, outcome, location.framework());
    }

    /** {@code placeOrder}, or {@code placeOrderThrowsIllegalStateException} for a call that threw. */
    private static String testMethodName(MethodDescriptor method, @Nullable Outcome outcome) {
        return outcome instanceof Outcome.Threw
                ? method.getMethodName() + "Throws" + StringUtil.getShortName(((Outcome.Threw) outcome).getExceptionClass())
                : method.getMethodName();
    }

    /** The receiver's class, with wildcards for its type parameters, e.g. {@code com.acme.Cache<?, ?>}. */
    private static String receiverType(PsiMethod psiMethod) {
        PsiClass aClass = psiMethod.getContainingClass();
        String name = aClass != null ? aClass.getQualifiedName() : null;
        if (name == null) {
            throw new IllegalStateException("A method of a local or anonymous class can't be tested");
        }
        int typeParameters = aClass.getTypeParameters().length;
        return typeParameters == 0 ? name : name + "<" + String.join(", ", Collections.nCopies(typeParameters, "?")) + ">";
    }
}