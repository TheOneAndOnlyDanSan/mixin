package mixin;

import mixin.annotations.Method.AnnotationsMethod;
import mixin.annotations.Mixin;
import mixin.annotations.Method.OverwriteMethod;
import mixin.annotations.Method.ShadowMethod;
import mixin.annotations.field.AnnotationsField;
import mixin.annotations.field.OverwriteField;
import mixin.annotations.field.ShadowField;
import org.objectweb.asm.*;
import reflection.ClassReflection;
import reflection.FieldReflection;
import reflection.MethodReflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static mixin.Agent.mixinClasses;
import static org.objectweb.asm.Opcodes.*;
import static reflection.ClassReflection.*;
import static reflection.FieldReflection.*;
import static reflection.MethodReflection.*;

public class ByteManipulator {

    private static String getShadowDescriptor(Method method) {

        String shadowDescriptor = Type.getMethodDescriptor(method);

        return shadowDescriptor.replace(shadowDescriptor.substring(0, shadowDescriptor.indexOf(";") +1), "(");
    }

    public static byte[] getTargetMixin(Class<?> targetClass, byte[] originalByteCode) {
        return getMixinedClass(targetClass, mixinClasses.get(targetClass), originalByteCode, true);
    }

    public static byte[] getMixinMixin(Class<?> mixinClass, byte[] originalByteCode) {
        return getMixinedClass(mixinClass.getAnnotation(Mixin.class).value(), mixinClass, originalByteCode, false);
    }

    private static List<Method> filterMethods(List<Method> methods, String name, String descriptor, boolean mixiningTarget) {
        return methods.stream().filter(method -> {
            int isMethod = 0;
            if(mixiningTarget && method.isAnnotationPresent(OverwriteMethod.class))
                isMethod += method.getAnnotation(OverwriteMethod.class).value().equals(name + descriptor) ? 1 : 0;
            if(!mixiningTarget && method.isAnnotationPresent(ShadowMethod.class))
                isMethod += method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor) ? 1 : 0;
            if(!mixiningTarget && method.isAnnotationPresent(ShadowField.class))
                isMethod += method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor) ? 1 : 0;
            if(!mixiningTarget && method.isAnnotationPresent(OverwriteField.class))
                isMethod += method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor) ? 1 : 0;

            return isMethod != 0;
        }).toList();
    }

    private static void addAnnotationsMethod(MethodVisitor methodVisitor, Method mixinMethod, Method targetMethod) {
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

    private static void addAnnotationsField(FieldVisitor fieldVisitor, Method mixinMethod, Field targetMethod) {
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

    private static List<Method> getAnnotationMethod(List<Method> methods, String name, String descriptor) {
        return methods.stream().filter(method -> method.isAnnotationPresent(AnnotationsMethod.class) && method.getAnnotation(AnnotationsMethod.class).value().equals(name + descriptor)).toList();
    }

    private static List<Method> getAnnotationField(List<Method> methods, String name) {
        return methods.stream().filter(method -> method.isAnnotationPresent(AnnotationsField.class) && method.getAnnotation(AnnotationsField.class).value().equals(name)).toList();
    }

    private static byte[] getMixinedClass(Class<?> targetClass, Class<?> mixinClass, byte[] originalByteCode, boolean mixiningTarget) {
        try {

            ClassReader classReader = new ClassReader(originalByteCode);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);

            classReader.accept(new ClassVisitor(ASM9, classWriter) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

                    if(name.equals("<init>") || name.equals("<clinit>")) return super.visitMethod(access, name, descriptor, signature, exceptions);

                    List<Method> methods = filterMethods(Arrays.stream(mixinClass.getDeclaredMethods()).toList(), name, descriptor, mixiningTarget);
                    Method targetMethod = getMethod(getClassByName(classReader.getClassName().replace("/", ".")), name, Arrays.stream(Type.getArgumentTypes(descriptor)).map(type -> getClassByName(type.getClassName())).toArray(Class[]::new));
                    List<Method> annotationsMethod = getAnnotationMethod(Arrays.stream(mixinClass.getDeclaredMethods()).toList(), name, descriptor);
                    if(annotationsMethod.size() > 1) throw new RuntimeException(name + " connot have more than 1 annotation method");

                    if(methods.size() > 1) throw new RuntimeException(name + " cannot be mixined more than once");

                    MethodVisitor methodVisitor;

                    if(methods.size() == 0) {
                        methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);


                    } else {
                        Method mixinMethod = methods.get(0);

                        methodVisitor = classWriter.visitMethod(access, name, descriptor, signature, exceptions);

                        if(!Modifier.isStatic(mixinMethod.getModifiers()))
                            throw new RuntimeException(mixinClass.getName() + "." + mixinMethod.getName() + " must be static");

                        if(mixiningTarget && mixinMethod.isAnnotationPresent(OverwriteMethod.class))
                            OverwriteMethodHandler.generateMethodBytecode(mixinMethod.getName(), Type.getMethodDescriptor(mixinMethod), (access & ACC_STATIC) != 0, mixinClass, methodVisitor);
                        if(!mixiningTarget && mixinMethod.isAnnotationPresent(ShadowMethod.class))
                            ShadowMethodHandler.generateMethodBytecode(mixinMethod.getName(), getShadowDescriptor(mixinMethod), targetClass, (access & ACC_STATIC) != 0, methodVisitor);
                        if(!mixiningTarget && mixinMethod.isAnnotationPresent(ShadowField.class))
                            ShadowFieldHandler.generateMethodBytecode(mixinMethod.getAnnotation(ShadowField.class).value(), getShadowDescriptor(mixinMethod), targetClass, (access & ACC_STATIC) != 0, methodVisitor);
                        if(!mixiningTarget && mixinMethod.isAnnotationPresent(OverwriteField.class))
                            OverwriteFieldHandler.generateMethodBytecode(mixinMethod.getAnnotation(OverwriteField.class).value(), targetClass, (access & ACC_STATIC) != 0, methodVisitor);
                    }

                    if(annotationsMethod.size() == 1 && mixiningTarget) {
                        addAnnotationsMethod(methodVisitor, annotationsMethod.get(0), targetMethod);
                    }

                    return methodVisitor;
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    FieldVisitor fieldVisitor = super.visitField(access, name, descriptor, signature, value);

                    Field targetField = FieldReflection.getField(getClassByName(classReader.getClassName().replace("/", ".")), name);
                    List<Method> annotationsField = getAnnotationField(Arrays.stream(mixinClass.getDeclaredMethods()).toList(), name);
                    if(annotationsField.size() > 1) throw new RuntimeException(name + " connot have more than 1 annotation method");

                    if(annotationsField.size() == 1) {
                        addAnnotationsField(fieldVisitor, annotationsField.get(0), targetField);
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

            mv.visitMethodInsn(INVOKESTATIC, "mixin/ByteManipulator", "callMethod", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false);

            addReturn(descriptor, mv);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    private static class ShadowMethodHandler {

        public static void generateMethodBytecode(String name, String descriptor, Class<?> targetClass, boolean isTargetStatic, MethodVisitor mv) {

            mv.visitLdcInsn(targetClass.getName()); // Pushes the class reference onto the stack
            mv.visitLdcInsn(name); // Pushes the method name onto the stack

            if(!isTargetStatic) mv.visitInsn(ACONST_NULL);
            else mv.visitVarInsn(ALOAD, 0);

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

            //load parameters
            for(int i = 0;i < parameters.length;i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                mv.visitVarInsn(ALOAD, i +1);
                mv.visitInsn(AASTORE);
            }

            mv.visitMethodInsn(INVOKESTATIC, "mixin/ByteManipulator", "callMethod", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false);

            addReturn(descriptor, mv);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    private static class ShadowFieldHandler {

        public static void generateMethodBytecode(String name, String descriptor, Class<?> targetClass, boolean isTargetStatic, MethodVisitor mv) {

            mv.visitLdcInsn(targetClass.getName()); // Pushes the class reference onto the stack
            mv.visitLdcInsn(name); // Pushes the method name onto the stack

            if(!isTargetStatic) mv.visitInsn(ACONST_NULL);
            else mv.visitVarInsn(ALOAD, 0);

            mv.visitMethodInsn(INVOKESTATIC, "mixin/ByteManipulator", "getField", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;", false);

            addReturn(descriptor, mv);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    private static class OverwriteFieldHandler {

        public static void generateMethodBytecode(String name, Class<?> targetClass, boolean isTargetStatic, MethodVisitor mv) {

            mv.visitLdcInsn(targetClass.getName()); // Pushes the class reference onto the stack
            mv.visitLdcInsn(name); // Pushes the method name onto the stack

            if(!isTargetStatic) mv.visitInsn(ACONST_NULL);
            else mv.visitVarInsn(ALOAD, 0);

            mv.visitVarInsn(ALOAD, 1);

            mv.visitMethodInsn(INVOKESTATIC, "mixin/ByteManipulator", "setField", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", false);

            mv.visitInsn(RETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }

    public static Object callMethod(String className, String methodName, Object instance, String[] types, Object[] args) {
        Class<?>[] typeClasses = Arrays.stream(types).map(ClassReflection::getClassByName).toArray(Class[]::new);

        Method method = getMethod(getClassByName(className), methodName, typeClasses);
        return MethodReflection.useMethod(method, instance, args);
    }

    public static Object getField(String className, String fieldName, Object instance) {
        Field method = FieldReflection.getField(getClassByName(className), fieldName);
        return getFieldValue(method, instance);
    }

    public static void setField(String className, String fieldName, Object instance, Object value) {
        Field method = FieldReflection.getField(getClassByName(className), fieldName);
        setFieldValue(method, instance, value);
    }

    static void addReturn(String descriptor, MethodVisitor mv) {
        Type returnType = Type.getReturnType(descriptor);
        if (returnType.getSort() == 0) {
            mv.visitInsn(RETURN);
        } else if(returnType.getSort() >= 1 && returnType.getSort() <= 8) { //check if the return type is primitive

            char charType = descriptor.split("\\)")[1].charAt(0);
            int returnPrimitive;
            String className = Array.get(Array.newInstance(ClassReflection.getPrimitiveClassByName(returnType.getClassName()) ,1),0).getClass().getName().replace(".", "/");

            switch (charType) {
                case 'Z', 'B', 'S', 'C', 'I' -> returnPrimitive = IRETURN;
                case 'F'  -> returnPrimitive = FRETURN;
                case 'J'  -> returnPrimitive = LRETURN;
                case 'D' -> returnPrimitive = DRETURN;
                default -> throw new RuntimeException();
            }

            mv.visitTypeInsn(CHECKCAST, className);

            mv.visitMethodInsn(INVOKEVIRTUAL, className, returnType.getClassName() + "Value", "()" + charType, false);
            mv.visitInsn(returnPrimitive);
        } else {
            mv.visitTypeInsn(CHECKCAST, returnType.getInternalName());
            mv.visitInsn(ARETURN);
        }
    }
}
