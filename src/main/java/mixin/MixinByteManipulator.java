package mixin;

import mixin.annotations.Method.ShadowMethod;
import mixin.annotations.Mixin;
import mixin.annotations.field.OverwriteField;
import mixin.annotations.field.ShadowField;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;

public class MixinByteManipulator extends AbstractByteManipulator {

    final Class<?> targetClass;
    final Class<?> mixinClass;
    final byte[] originalByteCode;

    MixinByteManipulator(Class<?> mixinClass, byte[] originalByteCode) {
        this.mixinClass = mixinClass;
        this.originalByteCode = originalByteCode;

        targetClass = mixinClass.getAnnotation(Mixin.class).value();
    }

    private static Method filterMethods(List<Method> methods, String name, String descriptor) {
        List<Method> methodList = methods.stream().filter(method -> {
            int isMethod = 0;
            if(method.isAnnotationPresent(ShadowMethod.class))
                isMethod += method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor) ? 1 : 0;
            if(method.isAnnotationPresent(ShadowField.class))
                isMethod += method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor) ? 1 : 0;
            if(method.isAnnotationPresent(OverwriteField.class))
                isMethod += method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor) ? 1 : 0;

            return isMethod != 0;
        }).toList();


        if(methodList.size() > 1) throw new RuntimeException(name + " cannot be mixined more than once");
        return methodList.size() == 1 ? methodList.get(0) : null;
    }

    private static String removeFirstArgFromDescriptor(Method method) {

        String shadowDescriptor = Type.getMethodDescriptor(method);

        return shadowDescriptor.replace(shadowDescriptor.substring(0, shadowDescriptor.indexOf(";") +1), "(");
    }

    public byte[] getMixinedClass() {
        try {
            ClassReader classReader = new ClassReader(originalByteCode);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);

            classReader.accept(new ClassVisitor(ASM9, classWriter) {
                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor;

                    Method mixinMethod = filterMethods(Arrays.stream(mixinClass.getDeclaredMethods()).toList(), name, descriptor);

                    if(mixinMethod == null) {
                        methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);
                    } else {
                        methodVisitor = classWriter.visitMethod(access, name, descriptor, signature, exceptions);

                        if(!Modifier.isStatic(mixinMethod.getModifiers()))
                            throw new RuntimeException(mixinClass.getName() + "." + mixinMethod.getName() + " must be static");

                        if(mixinMethod.isAnnotationPresent(ShadowMethod.class))
                            ShadowMethodHandler.generateMethodBytecode(mixinMethod.getAnnotation(ShadowMethod.class).value().split("\\(")[0], removeFirstArgFromDescriptor(mixinMethod), targetClass, (access & ACC_STATIC) != 0, methodVisitor);
                        if(mixinMethod.isAnnotationPresent(ShadowField.class))
                            ShadowFieldHandler.generateMethodBytecode(mixinMethod.getAnnotation(ShadowField.class).value(), removeFirstArgFromDescriptor(mixinMethod), targetClass, (access & ACC_STATIC) != 0, methodVisitor);
                        if(mixinMethod.isAnnotationPresent(OverwriteField.class))
                            OverwriteFieldHandler.generateMethodBytecode(mixinMethod.getAnnotation(OverwriteField.class).value(), targetClass, (access & ACC_STATIC) != 0, methodVisitor);
                    }

                    return methodVisitor;
                }

            }, ClassReader.EXPAND_FRAMES);

            return classWriter.toByteArray();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
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
            loadArgs(parameters, 0, mv);

            mv.visitMethodInsn(INVOKESTATIC, "mixin/AbstractByteManipulator", "callMethod", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false);

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

            mv.visitMethodInsn(INVOKESTATIC, "mixin/AbstractByteManipulator", "getField", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;", false);

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

            mv.visitMethodInsn(INVOKESTATIC, "mixin/AbstractByteManipulator", "setField", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;Ljava/lang/Object;)V", false);

            mv.visitInsn(RETURN);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }
}
