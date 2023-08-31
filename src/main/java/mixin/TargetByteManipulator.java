package mixin;

import mixin.annotations.Overwrite;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;
import reflection.ClassReflection;

import java.lang.reflect.*;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

import static mixin.Agent.mixinClasses;
import static org.objectweb.asm.Opcodes.*;

public class TargetByteManipulator {

    public static byte[] getMixinedClass(Class<?> targetClass, byte[] originalByteCode) {
        Class<?> mixinClass = mixinClasses.get(targetClass);
        List<Method> mixinMethodsOverwrite = Arrays.stream(mixinClass.getDeclaredMethods()).filter(method -> method.isAnnotationPresent(Overwrite.class)).toList();


        ClassReader classReader = new ClassReader(originalByteCode);
        ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES | ClassWriter.COMPUTE_MAXS);

        classReader.accept(new ClassVisitor(ASM9, classWriter) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {

                List<Method> overwriteMethods = mixinMethodsOverwrite.stream().filter(executable -> executable.getAnnotation(Overwrite.class).value().equals(name + descriptor)).toList();

                if (overwriteMethods.size() == 0) {
                    return super.visitMethod(access, name, descriptor, signature, exceptions);
                } else {
                    MethodVisitor mv = classWriter.visitMethod(access, name, descriptor, signature, exceptions);

                    Method mixinMethod = overwriteMethods.get(0);
                    OverwriteMethodHandler.generateMethodBytecode(mixinMethod.getName(), Type.getMethodDescriptor(mixinMethod), (access&ACC_STATIC) != 0, mixinClass, mv);
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
}
