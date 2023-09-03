package mixin;

import mixin.annotations.Shadow;
import mixin.annotations.Mixin;
import org.objectweb.asm.*;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;
import static reflection.ClassReflection.getClassByName;
import static reflection.FieldReflection.getFields;

public class MixinByteManipulator extends AbstractByteManipulator {

    final Class<?> targetClass;
    final Class<?> mixinClass;
    final byte[] originalByteCode;

    MixinByteManipulator(Class<?> mixinClass, byte[] originalByteCode) {
        this.mixinClass = mixinClass;
        this.originalByteCode = originalByteCode;

        targetClass = mixinClass.getAnnotation(Mixin.class).value();
    }

    private List<Method> filterMethods(List<Method> methods, String name, String descriptor) {
        List<Method> methodList = methods.stream().filter(method -> {
            if(!Modifier.isStatic(method.getModifiers()))
                throw new RuntimeException(mixinClass.getName() + "." + method.getName() + " must be static");

            int isMethod = 0;
            if(method.isAnnotationPresent(Shadow.class))
                isMethod += method.getName().equals(name) && Type.getMethodDescriptor(method).equals(descriptor) ? 1 : 0;

            return isMethod != 0;
        }).toList();

        return methodList;
    }

    private List<Field> filterFields(String name) {
        List<Field> fieldList = Arrays.stream(getFields(mixinClass, false)).filter(field -> {

            int isField = 0;
            if(field.isAnnotationPresent(Shadow.class))
                isField += field.getName().equals(name) ? 1 : 0;

            if(isField != 0) {
                if(!Modifier.isStatic(field.getModifiers()))
                    throw new RuntimeException(mixinClass.getName() + "." + field.getName() + " must be static");
                return true;
            }

            return false;
        }).toList();

        return fieldList;
    }

    private String removeFirstArgFromDescriptor(Method method) {

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
                    MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

                    List<Method> methodList = filterMethods(Arrays.stream(mixinClass.getDeclaredMethods()).toList(), name, descriptor);

                    return new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                        @Override
                        public void visitCode() {

                            methodList.forEach(mixinMethod -> {
                                if(mixinMethod.isAnnotationPresent(Shadow.class))
                                    ShadowMethodHandler.generateMethodBytecode(mixinMethod.getName(), removeFirstArgFromDescriptor(mixinMethod), targetClass, Modifier.isStatic(access), this);
                            });
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            List<Field> fieldList = filterFields(name);
                            fieldList.forEach(mixinField -> {
                                if(opcode % 2 == 0) { //getField
                                    if(mixinField.isAnnotationPresent(Shadow.class))
                                        ShadowFieldHandler.generateMethodBytecode(name, targetClass, descriptor, opcode <= GETFIELD, this);
                                } else { //set
                                    if(mixinField.isAnnotationPresent(Shadow.class))
                                        OverwriteFieldHandler.generateMethodBytecode(name, targetClass, opcode <= GETFIELD, this);
                                }
                            });
                            if(fieldList.size() == 0) {
                                super.visitFieldInsn(opcode, owner, name, descriptor);
                            }
                        }
                    };
                }

            }, ClassReader.EXPAND_FRAMES);

            return classWriter.toByteArray();
        } catch (Throwable e) {
            e.printStackTrace();
        }

        return null;
    }

    private static class ShadowMethodHandler {

        public static void generateMethodBytecode(String fieldName, String descriptor, Class<?> targetClass, boolean isTargetStatic, MethodVisitor mv) {

            mv.visitLdcInsn(targetClass.getName()); // Pushes the class reference onto the stack
            mv.visitLdcInsn(fieldName); // Pushes the method name onto the stack

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

        public static void generateMethodBytecode(String name, Class<?> targetClas, String descriptor, boolean isTargetStatic, MethodVisitor mv) {
            mv.visitLdcInsn(targetClas.getName()); // Pushes the class reference onto the stack
            mv.visitLdcInsn(name); // Pushes the method name onto the stack

            if(!isTargetStatic) mv.visitInsn(ACONST_NULL);
            else mv.visitVarInsn(ALOAD, 0);

            mv.visitMethodInsn(INVOKESTATIC, "mixin/AbstractByteManipulator", "getField", "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)Ljava/lang/Object;", false);

            //if(descriptor.length() == 1) {
            //    castToPrimitive(Type.getType(descriptor), mv);
            //} else {
            //    mv.visitTypeInsn(CHECKCAST, descriptor);
            //}
        }
    }

    private static class OverwriteFieldHandler {

        public static void generateMethodBytecode(String name, Class<?> targetClass, boolean isTargetStatic, MethodVisitor mv) {

            mv.visitLdcInsn(targetClass.getName()); // Pushes the class reference onto the stack
            mv.visitLdcInsn(name); // Pushes the method name onto the stack

            if(!isTargetStatic) mv.visitInsn(ACONST_NULL);
            else mv.visitVarInsn(ALOAD, 0);

            mv.visitMethodInsn(INVOKESTATIC, "mixin/AbstractByteManipulator", "setField", "(Ljava/lang/Object;Ljava/lang/String;Ljava/lang/String;Ljava/lang/Object;)V", false);
        }
    }
}
