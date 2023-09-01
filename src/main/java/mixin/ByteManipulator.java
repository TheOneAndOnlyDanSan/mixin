package mixin;

import mixin.annotations.Mixin;
import mixin.annotations.Overwrite;
import mixin.annotations.Shadow.ShadowMethod;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static mixin.Agent.mixinClasses;
import static org.objectweb.asm.Opcodes.*;

public class ByteManipulator {

    private static String getShadowDescriptor(Method method) {

        String shadowDescriptor = Type.getMethodDescriptor(method);

        return shadowDescriptor.replace(shadowDescriptor.substring(0, shadowDescriptor.indexOf(";") + 1), "(");
    }

    public static byte[] getTargetMixin(Class<?> targetClass, byte[] originalByteCode) {
        return getMixinedClass(targetClass, mixinClasses.get(targetClass), originalByteCode);
    }

    public static byte[] getMixinMixin(Class<?> mixinClass, byte[] originalByteCode) {
        return getMixinedClass(mixinClass.getAnnotation(Mixin.class).value(), mixinClass, originalByteCode);
    }

    private static byte[] getMixinedClass(Class<?> targetClass, Class<?> mixinClass, byte[] originalByteCode) {
        List<Method> mixinMethodsOverwrite = Arrays.stream(mixinClass.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(Overwrite.class)).toList();
        List<Method> mixinMethodsShadow = Arrays.stream(mixinClass.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(ShadowMethod.class)).toList();

        ClassReader classReader = new ClassReader(originalByteCode);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        classReader.accept(new ClassVisitor(ASM9, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

                List<Method> methods = mixinMethodsOverwrite.stream().filter(method -> method.getAnnotation(Overwrite.class).value().equals(name + descriptor)).collect(Collectors.toList());
                methods.addAll(mixinMethodsShadow.stream().filter(method -> method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor)).toList());

                if(methods.size() > 1) throw new RuntimeException(targetClass.getName() + "." + name + " cannot be mixined more than once");

                if (methods.size() == 0) {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                } else {
                    MethodVisitor mv = classWriter.visitMethod(access, name, descriptor, signature, exceptions);

                    Method mixinMethod = methods.get(0);
                    if(!Modifier.isStatic(mixinMethod.getModifiers())) throw new RuntimeException(mixinClass.getName() + "." + mixinMethod.getName() + " must be static");

                    if(mixinMethod.isAnnotationPresent(Overwrite.class))
                        OverwriteMethodHandler.generateMethodBytecode(mixinMethod.getName(), Type.getMethodDescriptor(mixinMethod), (access&ACC_STATIC) != 0, mixinClass, mv);
                    if(mixinMethod.isAnnotationPresent(ShadowMethod.class))
                        ShadowMethodHandler.generateMethodBytecode(mixinMethod.getName(), getShadowDescriptor(mixinMethod), targetClass, (access&ACC_STATIC) != 0, mv);
                }

                return null;
            }
        }, ClassReader.EXPAND_FRAMES);

        return classWriter.toByteArray();
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

            mv.visitMethodInsn(INVOKESTATIC, "mixin/MixinTransformer", "callMethod", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false);

            MixinTransformer.addReturn(descriptor, mv);

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

            mv.visitMethodInsn(INVOKESTATIC, "mixin/MixinTransformer", "callMethod", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;[Ljava/lang/String;[Ljava/lang/Object;)Ljava/lang/Object;", false);

            MixinTransformer.addReturn(descriptor, mv);

            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }
    }
}
