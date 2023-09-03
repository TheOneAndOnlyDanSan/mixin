package mixin;

import mixin.annotations.Method.*;
import mixin.annotations.field.AnnotationsField;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;
import reflection.ClassReflection;
import reflection.ConstructorReflection;
import reflection.FieldReflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.getArgumentTypes;
import static reflection.ClassReflection.getClassByName;
import static reflection.ConstructorReflection.getConstructor;
import static reflection.MethodReflection.*;

public class TargetByteManipulator extends AbstractByteManipulator {

    final Class<?> targetClass;
    final Class<?> mixinClass;
    final byte[] originalByteCode;

    TargetByteManipulator(Class<?> targetClass, byte[] originalByteCode) {
        this.targetClass = targetClass;
        this.originalByteCode = originalByteCode;

        mixinClass = Agent.mixinClasses.get(targetClass);
    }

    private List<Method> filterMethods(List<Method> methods, String name, String descriptor) {
        List<Method> methodList = methods.stream().filter(method -> {
            if(!Modifier.isStatic(method.getModifiers()))
                throw new RuntimeException(mixinClass.getName() + "." + method.getName() + " must be static");

            int isMethod = 0;
            if(method.isAnnotationPresent(OverwriteMethod.class))
                isMethod += method.getAnnotation(OverwriteMethod.class).value().equals(name + descriptor) ? 1 : 0;
            if(method.isAnnotationPresent(ReturnValueMethod.class))
                isMethod += method.getAnnotation(ReturnValueMethod.class).value().equals(name + descriptor) ? 1 : 0;
            if(method.isAnnotationPresent(InjectHead.class)) {
                String injectDescriptor = method.getAnnotation(InjectHead.class).value();
                if(injectDescriptor.split("\\(")[0].equals("<init>")) throw new IllegalArgumentException("cannot InjectHead a constructor");
                isMethod += injectDescriptor.equals(name + descriptor) ? 1 : 0;
            }
            if(method.isAnnotationPresent(InjectTail.class)) {
                String injectDescriptor = method.getAnnotation(InjectTail.class).value();
                if(!injectDescriptor.split("\\)")[1].equals("V")) throw new IllegalArgumentException("InjectTail in only allowed on void methods and constructors");
                isMethod += injectDescriptor.equals(name + descriptor) ? 1 : 0;
            }

            return isMethod != 0;
        }).collect(Collectors.toList());


        return methodList;
    }

    private void addAnnotationsToMethod(MethodVisitor methodVisitor, Method mixinMethod, Executable targetMethod) {
        Annotation[] annotations;

        if(mixinMethod != null) {
            annotations = (Annotation[]) useMethod(mixinMethod, null, new Object[mixinMethod.getParameterCount()]);
        } else {
            annotations = targetMethod.getDeclaredAnnotations();
        }

        for(Annotation annotation : annotations) {
            AnnotationVisitor annotationVisitor = methodVisitor.visitAnnotation("L" + annotation.annotationType().getName().replace(".", "/") + ";", true);

            for(Method m : getMethods(annotation.annotationType(), false)) {
                annotationVisitor.visit(m.getName(), useMethod(m, annotation));
            }

            annotationVisitor.visitEnd();
        }
    }

    private void addAnnotationsToField(FieldVisitor fieldVisitor, Method mixinMethod, Field targetMethod) {
        Annotation[] annotations;

        if(mixinMethod != null) {
            annotations = (Annotation[]) useMethod(mixinMethod, null, new Object[mixinMethod.getParameterCount()]);
        } else {
            annotations = targetMethod.getDeclaredAnnotations();
        }

        for(Annotation annotation : annotations) {
            AnnotationVisitor annotationVisitor = fieldVisitor.visitAnnotation("L" + annotation.annotationType().getName().replace(".", "/") + ";", true);

            for(Method m : getMethods(annotation.annotationType(), false)) {
                annotationVisitor.visit(m.getName(), useMethod(m, annotation));
            }

            annotationVisitor.visitEnd();
        }
    }

    private Method getAnnotationMethodSetter(List<Method> methods, String name, String descriptor) {
        methods = methods.stream().filter(method -> method.isAnnotationPresent(AnnotationsMethod.class) && method.getAnnotation(AnnotationsMethod.class).value().equals(name + descriptor)).toList();

        if(methods.size() > 1) throw new RuntimeException(name + " connot have more than 1 annotation method");

        return methods.size() == 1 ? methods.get(0) : null;
    }

    private Method getAnnotationFieldSetter(List<Method> methods, String name) {
        methods = methods.stream().filter(method -> method.isAnnotationPresent(AnnotationsField.class) && method.getAnnotation(AnnotationsField.class).value().equals(name)).toList();

        if(methods.size() > 1) throw new RuntimeException(name + " connot have more than 1 annotation method");

        return methods.size() == 1 ? methods.get(0) : null;
    }

    private String getDescriptor(Executable e) {
        if(e.getClass() == Method.class) return Type.getMethodDescriptor((Method) e);
        else return Type.getConstructorDescriptor((Constructor<?>) e);
    }

    public byte[] getMixinedClass() {
        try {
            ClassReader classReader = new ClassReader(originalByteCode);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);

            classReader.accept(new ClassVisitor(ASM9, classWriter) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

                    List<Method> methodList = filterMethods(Arrays.stream(mixinClass.getDeclaredMethods()).toList(), name, descriptor);
                    Method annotationsMethod = getAnnotationMethodSetter(Arrays.stream(mixinClass.getDeclaredMethods()).toList(), name, descriptor);


                    Executable targetMethod;
                    if(name.equals("<init>")) {
                        targetMethod = getConstructor(getClassByName(classReader.getClassName().replace("/", ".")), Arrays.stream(getArgumentTypes(descriptor)).map(type -> getClassByName(type.getClassName())).toArray(Class[]::new));
                    } else {
                        targetMethod = getMethod(getClassByName(classReader.getClassName().replace("/", ".")), name, Arrays.stream(getArgumentTypes(descriptor)).map(type -> getClassByName(type.getClassName())).toArray(Class[]::new));
                    }

                    if(methodList.size() == 0) {
                        if(annotationsMethod != null) {
                            addAnnotationsToMethod(methodVisitor, annotationsMethod, targetMethod);
                        }

                        return methodVisitor;
                    } else {
                        if(annotationsMethod == null) {
                            addAnnotationsToMethod(methodVisitor, null, targetMethod);
                        }


                        return new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                            @Override
                            public void visitCode() {
                                methodList.forEach(mixinMethod -> {

                                    if(mixinMethod.isAnnotationPresent(OverwriteMethod.class)) {
                                        if(targetMethod.getClass() == Constructor.class) {
                                            ConstructorSetupHandler.generateMethodBytecode(methodVisitor);
                                        }

                                        OverwriteMethodHandler.generateMethodBytecode(mixinMethod.getName(), getDescriptor(mixinMethod), (access&ACC_STATIC) != 0, mixinClass, this);
                                    }
                                    if(mixinMethod.isAnnotationPresent(InjectHead.class)) {
                                        InjectHeadMethodHandler.generateMethodBytecode(mixinMethod.getName(), getDescriptor(mixinMethod), (access&ACC_STATIC) != 0, mixinClass, this);
                                       super.visitCode();
                                    }
                                });
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if(opcode <= ARETURN && opcode >= IRETURN) {

                                    methodList.forEach(mixinMethod -> {

                                        if(mixinMethod.isAnnotationPresent(ReturnValueMethod.class)) {
                                            ReturnValueMethodHandler.generateMethodBytecode(mixinMethod.getName(), getDescriptor(mixinMethod), (access&ACC_STATIC) != 0, mixinClass, this, ARETURN - opcode);
                                        }
                                    });
                                } else if(opcode == RETURN) {
                                    methodList.forEach(mixinMethod -> {

                                        if(mixinMethod.isAnnotationPresent(InjectTail.class)) {
                                            InjectHeadMethodHandler.generateMethodBytecode(mixinMethod.getName(), getDescriptor(mixinMethod), (access&ACC_STATIC) != 0, mixinClass, this);
                                        }
                                    });
                                }

                                super.visitInsn(opcode);
                            }
                        };
                    }
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    FieldVisitor fieldVisitor = super.visitField(access, name, descriptor, signature, value);

                    Field targetField = FieldReflection.getField(getClassByName(classReader.getClassName().replace("/", ".")), name);
                    Method annotationsField = getAnnotationFieldSetter(Arrays.stream(mixinClass.getDeclaredMethods()).toList(), name);

                    if(annotationsField != null) {
                        addAnnotationsToField(fieldVisitor, annotationsField, targetField);
                    }

                    return fieldVisitor;
                }

            }, ClassReader.EXPAND_FRAMES);

            return classWriter.toByteArray();
        } catch (Throwable e) {
            e.printStackTrace();
        }

      return null;
    }

    private static class ConstructorSetupHandler {

        public static void generateMethodBytecode(MethodVisitor mv) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL,"java/lang/Object", "<init>", "()V", false);
        }
    }

    private static class OverwriteMethodHandler {

        public static void generateMethodBytecode(String mixinName, String descriptor, boolean isTargetStatic, Class<?> mixinClass, MethodVisitor mv) {

            mv.visitLdcInsn(mixinClass.getName()); // Pushes the class reference onto the stack
            mv.visitLdcInsn(mixinName); // Pushes the method name onto the stack

            mv.visitInsn(ACONST_NULL);

            Type[] parameters = getArgumentTypes(descriptor);
            mv.visitLdcInsn(parameters.length); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String"); // Creates an empty array of Class references

            //load parameters
            for(int i = 0; i < parameters.length; i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                mv.visitLdcInsn(parameters[i].getClassName());
                mv.visitInsn(AASTORE);
            }

            mv.visitLdcInsn(parameters.length); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object"); // Creates an empty array of Class references

            mv.visitInsn(DUP);
            mv.visitLdcInsn(0);
            if(isTargetStatic) mv.visitInsn(ACONST_NULL); else mv.visitVarInsn(ALOAD, 0);
            mv.visitInsn(AASTORE);

            //load parameters
            loadArgs(parameters, 1, mv);

            mv.visitMethodInsn(INVOKESTATIC, "mixin/AbstractByteManipulator", "callMethod", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false);

            addReturn(descriptor, mv);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    private static class InjectHeadMethodHandler {

        public static void generateMethodBytecode(String mixinName, String descriptor, boolean isTargetStatic, Class<?> mixinClass, MethodVisitor mv) {

            mv.visitLdcInsn(mixinClass.getName()); // Pushes the class reference onto the stack
            mv.visitLdcInsn(mixinName); // Pushes the method name onto the stack

            mv.visitInsn(ACONST_NULL);

            Type[] parameters = getArgumentTypes(descriptor);
            mv.visitLdcInsn(parameters.length); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String"); // Creates an empty array of Class references

            //load parameters
            for(int i = 0; i < parameters.length; i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                mv.visitLdcInsn(parameters[i].getClassName());
                mv.visitInsn(AASTORE);
            }

            mv.visitLdcInsn(parameters.length); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object"); // Creates an empty array of Class references

            mv.visitInsn(DUP);
            mv.visitLdcInsn(0);
            if(isTargetStatic) mv.visitInsn(ACONST_NULL); else mv.visitVarInsn(ALOAD, 0);
            mv.visitInsn(AASTORE);

            //load parameters
            loadArgs(parameters, 1, mv);

            mv.visitMethodInsn(INVOKESTATIC, "mixin/AbstractByteManipulator", "callMethod", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false);

            mv.visitInsn(POP);
        }
    }

    private static class ReturnValueMethodHandler {

        public static void generateMethodBytecode(String mixinName, String descriptor, boolean isTargetStatic, Class<?> mixinClass, MethodVisitor mv, int type) {

            Type[] parameters = getArgumentTypes(descriptor);

            mv.visitVarInsn(ASTORE - type, parameters.length);

            mv.visitLdcInsn(mixinClass.getName()); // Pushes the class reference onto the stack
            mv.visitLdcInsn(mixinName); // Pushes the method name onto the stack

            mv.visitInsn(ACONST_NULL);

            mv.visitLdcInsn(parameters.length); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String"); // Creates an empty array of Class references

            //load parameters
            for(int i = 0; i < parameters.length; i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                mv.visitLdcInsn(parameters[i].getClassName());
                mv.visitInsn(AASTORE);
            }

            mv.visitLdcInsn(parameters.length); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object"); // Creates an empty array of Class references

            mv.visitInsn(DUP);
            mv.visitLdcInsn(0);
            if(isTargetStatic) mv.visitInsn(ACONST_NULL);
            else mv.visitVarInsn(ALOAD, 0);
            mv.visitInsn(AASTORE);

            mv.visitInsn(DUP);
            mv.visitLdcInsn(1);
            mv.visitVarInsn(ALOAD - type, parameters.length);
            if(ClassReflection.getPrimitiveClassByName(parameters[1].getClassName()) != null) {
                String className = Array.get(Array.newInstance(ClassReflection.getPrimitiveClassByName(parameters[1].getClassName()) ,1),0).getClass().getName().replace(".", "/");

                mv.visitMethodInsn(INVOKESTATIC, className, "valueOf", "(" + parameters[1].getInternalName() + ")L" + className + ";", false);
            }
            mv.visitInsn(AASTORE);

            loadArgs(parameters, 2, mv);

            mv.visitMethodInsn(INVOKESTATIC, "mixin/AbstractByteManipulator", "callMethod", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false);
            if(Type.getReturnType(descriptor).getSort() >= 9)  mv.visitTypeInsn(CHECKCAST, Type.getReturnType(descriptor).getInternalName());
            else castToPrimitive(Type.getReturnType(descriptor), mv);
        }
    }
}
