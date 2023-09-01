package mixin;

import mixin.annotations.Mixin;
import mixin.annotations.Overwrite;
import mixin.annotations.Shadow.ShadowMethod;
import org.objectweb.asm.*;
import reflection.ClassReflection;
import reflection.MethodReflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static mixin.Agent.mixinClasses;
import static org.objectweb.asm.Opcodes.*;
import static reflection.ClassReflection.getClassByName;
import static reflection.MethodReflection.*;

public class ByteManipulator {

    private static String getShadowDescriptor(Method method) {

        String shadowDescriptor = Type.getMethodDescriptor(method);

        return shadowDescriptor.replace(shadowDescriptor.substring(0, shadowDescriptor.indexOf(";") +1), "(");
    }

    public static byte[] getTargetMixin(Class<?> targetClass, byte[] originalByteCode) {
        return getMixinedClass(targetClass, mixinClasses.get(targetClass), originalByteCode);
    }

    public static byte[] getMixinMixin(Class<?> mixinClass, byte[] originalByteCode) {
        return getMixinedClass(mixinClass.getAnnotation(Mixin.class).value(), mixinClass, originalByteCode);
    }

    private static List<Method> filterMethods(List<Method> methods, String name, String descriptor) {
        return methods.stream().filter(method -> {
            int isMethod = 0;
            if(method.isAnnotationPresent(Overwrite.class))
                isMethod = method.getAnnotation(Overwrite.class).value().equals(name + descriptor) ? 1 : 0;
            if(method.isAnnotationPresent(ShadowMethod.class))
                isMethod = method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor) ? 1 : 0;

            if(isMethod == 0) return false;
            if(isMethod == 1) return true;
            if(isMethod > 1) throw new RuntimeException(method.getName() + " cannot be mixin more than once");
            return false;
        }).toList();
    }

    private static void copyAnnotations(MethodVisitor methodVisitor, Method method) {
        Annotation[] annotations = method.getDeclaredAnnotations();

        for(Annotation annotation : annotations) {
            AnnotationVisitor annotationVisitor = methodVisitor.visitAnnotation("L" + annotation.annotationType().getName().replace(".", "/") + ";", true);

            for(Method m : getMethods(annotation.annotationType(), false)) {
                annotationVisitor.visit(m.getName(), useMethod(m, annotation));
            }
        }
    }

    private static byte[] getMixinedClass(Class<?> targetClass, Class<?> mixinClass, byte[] originalByteCode) {
        try {

            ClassReader classReader = new ClassReader(originalByteCode);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);

            classReader.accept(new ClassVisitor(ASM9, classWriter) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

                    List<Method> methods = filterMethods(Arrays.stream(mixinClass.getDeclaredMethods()).toList(), name, descriptor);

                    if(methods.size() > 1) throw new RuntimeException(name + " cannot be mixined more than once");

                    if(methods.size() == 0) {
                        return super.visitMethod(access, name, descriptor, signature, exceptions);
                    } else {
                        MethodVisitor methodVisitor = classWriter.visitMethod(access, name, descriptor, signature, exceptions);

                        copyAnnotations(methodVisitor, getMethod(getClassByName(classReader.getClassName().replace("/", ".")), name, Arrays.stream(Type.getArgumentTypes(descriptor)).map(type -> getClassByName(type.getClassName())).toArray(Class[]::new)));

                        Method mixinMethod = methods.get(0);
                        if(!Modifier.isStatic(mixinMethod.getModifiers()))
                            throw new RuntimeException(mixinClass.getName() + "." + mixinMethod.getName() + " must be static");

                        if(mixinMethod.isAnnotationPresent(Overwrite.class))
                            OverwriteMethodHandler.generateMethodBytecode(mixinMethod.getName(), Type.getMethodDescriptor(mixinMethod), (access&ACC_STATIC) != 0, mixinClass, methodVisitor);
                        if(mixinMethod.isAnnotationPresent(ShadowMethod.class))
                            ShadowMethodHandler.generateMethodBytecode(mixinMethod.getName(), getShadowDescriptor(mixinMethod), targetClass, (access&ACC_STATIC) != 0, methodVisitor);
                    }

                    return null;
                }
            }, ClassReader.EXPAND_FRAMES);

            return classWriter.toByteArray();
        } catch (Throwable e) {
            e.printStackTrace();
        }

      return null;
    }

    private static class OverwriteMethodHandler {

        public static MethodVisitor generateMethodBytecode(String mixinName, String descriptor, boolean isTargetStatic, Class<?> mixinClass, MethodVisitor mv) {

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

            return mv;
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


    public static Object callMethod(String className, String methodName, Object instance, String[] types, Object[] args) {
        Class<?>[] typeClasses = Arrays.stream(types).map(ClassReflection::getClassByName).toArray(Class[]::new);

        Method method = getMethod(getClassByName(className), methodName, typeClasses);
        return MethodReflection.useMethod(method, instance, args);
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
