package mixin;

import mixin.annotations.Mixin;
import mixin.annotations.Shadow.ShadowMethod;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;
import static org.objectweb.asm.Opcodes.*;

public class MixinByteManipulator {

    private static String getShadowDescriptor(Method method) {

        String shadowDescriptor = Type.getMethodDescriptor(method);

        if(shadowDescriptor.startsWith("(L")) {
            shadowDescriptor = shadowDescriptor.replace(shadowDescriptor.substring(0, shadowDescriptor.indexOf(";") + 1), "(");
        } else {
            shadowDescriptor = shadowDescriptor.replace(shadowDescriptor.substring(0, 2), "(");
        }

        return shadowDescriptor;
    }

    public static byte[] getMixinedClass(Class<?> mixinClass, byte[] originalByteCode) {
        Class<?> targetClass = mixinClass.getAnnotation(Mixin.class).value();
        List<Method> mixinMethodsShadow = Arrays.stream(mixinClass.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(ShadowMethod.class)).toList();

        ClassReader classReader = new ClassReader(originalByteCode);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);



        classReader.accept(new ClassVisitor(ASM9, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

                List<Method> shadowMethods = mixinMethodsShadow.stream().filter(executable -> executable.getName().equals(name) && Type.getMethodDescriptor(executable).equals(descriptor)).toList();

                if (shadowMethods.size() == 0) {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                } else {
                    MethodVisitor mv = classWriter.visitMethod(access, name, descriptor, signature, exceptions);

                    Method mixinMethod = mixinMethodsShadow.get(0);
                    ShadowMethodHandler.generateMethodBytecode(mixinMethod.getName(), getShadowDescriptor(mixinMethod), targetClass, (access&ACC_STATIC) != 0, mv);
                }

                return null;
            }
        }, ClassReader.EXPAND_FRAMES);

        return classWriter.toByteArray();
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

            mv.visitMethodInsn(INVOKESTATIC, "mixin/MixinTransformer", "callMethod", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false);

            MixinTransformer.addReturn(descriptor, mv);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }
}
