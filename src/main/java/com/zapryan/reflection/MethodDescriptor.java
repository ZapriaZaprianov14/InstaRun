package com.zapryan.reflection;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class MethodDescriptor {

    private final String declaringClassFqn;
    private final String methodName;
    private final String returnTypeFqn;
    private final boolean isStatic;
    private final List<ParameterDescriptor> parameters;

    public MethodDescriptor(String declaringClassFqn,
                            String methodName,
                            String returnTypeFqn,
                            boolean isStatic,
                            List<ParameterDescriptor> parameters) {
        this.declaringClassFqn = declaringClassFqn;
        this.methodName = methodName;
        this.returnTypeFqn = returnTypeFqn;
        this.isStatic = isStatic;
        this.parameters = Collections.unmodifiableList(new ArrayList<>(parameters));
    }

    public String getDeclaringClassFqn() {
        return declaringClassFqn;
    }

    public String getMethodName() {
        return methodName;
    }

    public String getReturnTypeFqn() {
        return returnTypeFqn;
    }

    public boolean isStatic() {
        return isStatic;
    }

    public List<ParameterDescriptor> getParameters() {
        return parameters;
    }

    public static final class ParameterDescriptor {
        private final String name;
        private final String typeFqn;

        public ParameterDescriptor(String name, String typeFqn) {
            this.name = name;
            this.typeFqn = typeFqn;
        }

        public String getName() {
            return name;
        }

        public String getTypeFqn() {
            return typeFqn;
        }
    }
}
