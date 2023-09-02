package mixin;

import mixin.annotations.Method.AnnotationsMethod;
import mixin.annotations.Method.OverwriteMethod;
import mixin.annotations.field.AnnotationsField;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;
import reflection.ConstructorReflection;
import reflection.FieldReflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.*;
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

    private Method filterMethods(List<Method> methods, String name, String descriptor) {
        List<Method> methodList = methods.stream().filter(method -> {
            int isMethod = 0;
            if(method.isAnnotationPresent(OverwriteMethod.class))
                isMethod += method.getAnnotation(OverwriteMethod.class).value().equals(name + descriptor) ? 1 : 0;

            return isMethod != 0;
        }).toList();


        if(methodList.size() > 1) throw new RuntimeException(name + " cannot be mixined more than once");
        return methodList.size() == 1 ? methodList.get(0) : null;
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

                    Method mixinMethod = filterMethods(Arrays.stream(mixinClass.getDeclaredMethods()).toList(), name, descriptor);
                    Method annotationsMethod = getAnnotationMethodSetter(Arrays.stream(mixinClass.getDeclaredMethods()).toList(), name, descriptor);

                    Executable targetMethod;
                    if(name.equals("<init>")) {
                        targetMethod = getConstructor(getClassByName(classReader.getClassName().replace("/", ".")), Arrays.stream(Type.getArgumentTypes(descriptor)).map(type -> getClassByName(type.getClassName())).toArray(Class[]::new));
                    } else {
                        targetMethod = getMethod(getClassByName(classReader.getClassName().replace("/", ".")), name, Arrays.stream(Type.getArgumentTypes(descriptor)).map(type -> getClassByName(type.getClassName())).toArray(Class[]::new));
                    }

                    if(annotationsMethod != null) {
                        addAnnotationsToMethod(methodVisitor, annotationsMethod, targetMethod);
                    }

                    if(mixinMethod == null) {
                        return methodVisitor;
                    } else {
                        if(annotationsMethod != null) {
                            addAnnotationsToMethod(methodVisitor, annotationsMethod, targetMethod);
                        }

                        if(!Modifier.isStatic(mixinMethod.getModifiers()))
                            throw new RuntimeException(mixinClass.getName() + "." + mixinMethod.getName() + " must be static");

                        if(targetMethod.getClass() == Constructor.class) {
                            ConstructorSetupHandler.generateMethodBytecode(methodVisitor);
                        }

                        if(mixinMethod.isAnnotationPresent(OverwriteMethod.class))
                            OverwriteMethodHandler.generateMethodBytecode(mixinMethod.getName(), getDescriptor(mixinMethod), (access & ACC_STATIC) != 0, mixinClass, methodVisitor);

                        return null;
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

            Type[] parameters = Type.getArgumentTypes(descriptor);
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
            for(int i = 1;i < parameters.length;i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                mv.visitVarInsn(ALOAD, i);
                mv.visitInsn(AASTORE);
            }

            mv.visitMethodInsn(INVOKESTATIC, "mixin/AbstractByteManipulator", "callMethod", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false);

            addReturn(descriptor, mv);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }
}
