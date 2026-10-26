package com.zapryan.codegen;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiMember;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiVariable;
import com.intellij.psi.util.ClassUtil;
import com.intellij.psi.util.JavaPsiRecordUtil;
import com.zapryan.launch.MethodInput;
import com.zapryan.reflection.JvmNames;
import com.zapryan.reflection.SetterCall;
import com.zapryan.reflection.ValueSpec;
import com.zapryan.ui.MessageBundler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The classes, constructors and setters a generated test calls to rebuild a call's inputs, checked against what the
 * test can reach: the run used reflection and could call private members, a test can't. Needs a read action.
 */
final class TestMembers {

    private final PsiManager manager;
    private final String testPackage;
    private @Nullable String inaccessible;
    private boolean declaresExceptions;

    private TestMembers(Project project, String testPackage) {
        this.manager = PsiManager.getInstance(project);
        this.testPackage = testPackage;
    }

    /** Looks at the method itself and every member its inputs are built with. */
    static TestMembers scan(Project project, String testPackage, PsiMethod method, MethodInput input) {
        TestMembers members = new TestMembers(project, testPackage);
        members.check(method);
        if (input.getReceiver() != null) {
            members.check(input.getReceiver());
        }
        input.getArguments().forEach(members::check);
        return members;
    }

    /** What the test couldn't reach, described for a message, e.g. {@code Person.setId(long), which is private}. */
    @Nullable String getInaccessible() {
        return inaccessible;
    }

    /** Whether the method or anything building its inputs declares exceptions the test has to declare too. */
    boolean declaresExceptions() {
        return declaresExceptions;
    }

    private void check(ValueSpec spec) {
        if (spec instanceof ValueSpec.Constructed) {
            ValueSpec.Constructed constructed = (ValueSpec.Constructed) spec;
            PsiClass aClass = checkClass(constructed.getClassBinaryName());
            PsiMethod constructor = aClass != null ? constructor(aClass, constructed.getParameterTypeNames()) : null;
            if (constructor != null) {
                check(constructor);
            }
            constructed.getArguments().forEach(this::check);
            for (SetterCall call : constructed.getSetterCalls()) {
                PsiMethod setter = setter(call);
                if (setter != null) {
                    check(setter);
                }
                check(call.getValue());
            }
        } else if (spec instanceof ValueSpec.ArrayOf) {
            String component = ((ValueSpec.ArrayOf) spec).getComponentTypeName();
            String elementClass = component.replace("[]", "");
            if (elementClass.contains(".")) {
                checkClass(elementClass);
            }
            ((ValueSpec.ArrayOf) spec).getElements().forEach(this::check);
        } else if (spec instanceof ValueSpec.CollectionOf) {
            ((ValueSpec.CollectionOf) spec).getElements().forEach(this::check);
        } else if (spec instanceof ValueSpec.EnumConstant) {
            checkClass(((ValueSpec.EnumConstant) spec).getEnumClassBinaryName());
        }
    }

    private void check(PsiMethod method) {
        if (method.getThrowsList().getReferencedTypes().length > 0) {
            declaresExceptions = true;
        }
        if (inaccessible == null && !Visibility.isAccessible(method, testPackage)) {
            inaccessible = method.hasModifierProperty(PsiModifier.PRIVATE)
                    ? MessageBundler.message("generate.member.private", describe(method))
                    : MessageBundler.message("generate.member.package", describe(method), packageOf(method));
        }
    }

    private @Nullable PsiClass checkClass(String binaryName) {
        PsiClass aClass = ClassUtil.findPsiClass(manager, binaryName);
        if (aClass != null && inaccessible == null && !Visibility.isAccessible(aClass, testPackage)) {
            inaccessible = MessageBundler.message("generate.member.class", aClass.getQualifiedName(), testPackage);
        }
        return aClass;
    }

    private static @Nullable PsiMethod constructor(PsiClass aClass, List<String> parameterTypeNames) {
        for (PsiMethod constructor : aClass.getConstructors()) {
            // A compact record constructor declares no parameters in source but takes the record components.
            PsiVariable[] parameters = JavaPsiRecordUtil.isCompactConstructor(constructor)
                    ? aClass.getRecordComponents()
                    : constructor.getParameterList().getParameters();
            if (erasedNames(parameters).equals(parameterTypeNames)) {
                return constructor;
            }
        }
        // None declared: the implicit constructor is as visible as its class, which is checked already.
        return null;
    }

    private @Nullable PsiMethod setter(SetterCall call) {
        PsiClass declaringClass = checkClass(call.getDeclaringClassBinaryName());
        if (declaringClass == null) {
            return null;
        }
        for (PsiMethod method : declaringClass.findMethodsByName(call.getMethodName(), false)) {
            if (erasedNames(method.getParameterList().getParameters())
                    .equals(List.of(call.getParameterTypeName()))) {
                return method;
            }
        }
        return null;
    }

    private static List<String> erasedNames(PsiVariable[] parameters) {
        List<String> names = new ArrayList<>(parameters.length);
        for (PsiVariable parameter : parameters) {
            names.add(JvmNames.erasedName(parameter.getType()));
        }
        return names;
    }

    private static String describe(PsiMethod method) {
        PsiClass containingClass = method.getContainingClass();
        String className = containingClass != null ? containingClass.getName() : "";
        String parameters = String.join(", ", Arrays.stream(method.getParameterList().getParameters())
                .map(parameter -> parameter.getType().getPresentableText())
                .toList());
        return (method.isConstructor() ? className : className + "." + method.getName()) + "(" + parameters + ")";
    }

    private static String packageOf(PsiMember member) {
        String packageName = Visibility.packageOf(member);
        return packageName.isEmpty() ? MessageBundler.message("generate.package.default") : packageName;
    }
}