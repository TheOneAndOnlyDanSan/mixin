package mixin.bytemanipulators;

import mixin.Agent;
import mixin.annotations.Shadow;
import mixin.annotations.Mixin;
import org.objectweb.asm.*;
import reflection.ClassReflection;
import reflection.FieldReflection;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.getArgumentTypes;
import static reflection.ClassReflection.getClassByName;
import static reflection.FieldReflection.getFieldValue;
import static reflection.FieldReflection.getFields;

public class MixinByteManipulator extends AbstractByteManipulator {

    final Class<?> targetClass;
    final Class<?> mixinClass;
    final byte[] originalByteCode;

    public MixinByteManipulator(Class<?> mixinClass, byte[] originalByteCode) {
        this.mixinClass = mixinClass;
        this.originalByteCode = originalByteCode;

        targetClass = Agent.getMixinedClass(mixinClass);
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

                    return new MethodVisitor(Opcodes.ASM9, methodVisitor) {;

                        @Override
                        public void visitCode() {

                            methodList.forEach(mixinMethod -> {
                                if(mixinMethod.isAnnotationPresent(Shadow.class))
                                    ShadowMethodHandler.generateMethodBytecode(mixinMethod.getName(), removeFirstArgFromDescriptor(mixinMethod), targetClass, Modifier.isStatic(access), this);
                            });
                        }

                        private int getCurrentLocals() {
                            return (int) getFieldValue(FieldReflection.getField(getClassByName("org.objectweb.asm.MethodWriter"), "currentLocals"), mv);
                        }

                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {

                            List<Field> fieldList = filterFields(name);
                            fieldList.forEach(mixinField -> {
                                if(opcode % 2 == 0) { //getField
                                    if(mixinField.isAnnotationPresent(Shadow.class))
                                        ShadowGetFieldHandler.generateMethodBytecode(name, targetClass, descriptor, Modifier.isStatic(FieldReflection.getField(targetClass, name).getModifiers()), this, getCurrentLocals());
                                } else { //set
                                    if(mixinField.isAnnotationPresent(Shadow.class))
                                        ShadowSetFieldHandler.generateMethodBytecode(name, targetClass, descriptor, Modifier.isStatic(FieldReflection.getField(targetClass, name).getModifiers()), this, getCurrentLocals());
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

            Type[] parameters = getArgumentTypes(descriptor);
            int offset = parameters.length;

            mv.visitInsn(ICONST_5);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            mv.visitVarInsn(ASTORE, offset +1);
            mv.visitVarInsn(ALOAD, offset +1);

            loadIntoArray(0, mv, () -> mv.visitLdcInsn(targetClass.getName()));
            loadIntoArray(1, mv, () -> mv.visitLdcInsn(fieldName));
            loadIntoArray(2, mv, () -> {
                if(!isTargetStatic) mv.visitInsn(ACONST_NULL);
                else mv.visitVarInsn(ALOAD, 0);
            });

            mv.visitVarInsn(ALOAD, offset +1);
            mv.visitInsn(ICONST_3);
            mv.visitLdcInsn(parameters.length); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String"); // Creates an empty array of Class references


            for(int i = 0; i < parameters.length; i++) {
                int finalI = i;
                loadIntoArray(i, mv, () -> mv.visitLdcInsn(parameters[finalI].getClassName()));
            }

            mv.visitInsn(AASTORE);


            mv.visitVarInsn(ALOAD, offset +1);
            mv.visitInsn(ICONST_4);
            mv.visitLdcInsn(parameters.length); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object"); // Creates an empty array of Class references

            loadArgs(parameters, -1, 0, mv);
            mv.visitInsn(AASTORE);

            mv.visitVarInsn(ASTORE, offset);
            setUpCallMethod(mv, offset);

            addReturn(descriptor, mv);
        }
    }

    private static class ShadowGetFieldHandler {

        public static void generateMethodBytecode(String name, Class<?> targetClass, String descriptor, boolean isTargetStatic, MethodVisitor mv, int offset) {

            mv.visitInsn(ICONST_5);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            mv.visitVarInsn(ASTORE, offset);
            mv.visitVarInsn(ALOAD, offset);

            loadIntoArray(0, mv, () -> mv.visitLdcInsn("mixin.bytemanipulators.AbstractByteManipulator"));
            loadIntoArray(1, mv, () -> mv.visitLdcInsn("getField"));
            loadIntoArray(2, mv, () -> mv.visitInsn(ACONST_NULL));


            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_3);
            mv.visitLdcInsn(3); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String"); // Creates an empty array of Class references

            loadIntoArray(0, mv, () -> mv.visitLdcInsn(getClassName(String.class.getName())));
            loadIntoArray(1, mv, () -> mv.visitLdcInsn(getClassName(String.class.getName())));
            loadIntoArray(2, mv, () -> mv.visitLdcInsn(getClassName(Object.class.getName())));

            mv.visitInsn(AASTORE);


            mv.visitInsn(ICONST_4);
            mv.visitLdcInsn(3); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object"); // Creates an empty array of Class references

            loadIntoArray(0, mv, () -> mv.visitLdcInsn(targetClass.getName()));
            loadIntoArray(1, mv, () -> mv.visitLdcInsn(name));
            loadIntoArray(2, mv, () -> {
                if(isTargetStatic) mv.visitInsn(ACONST_NULL);
                else mv.visitVarInsn(ALOAD, 0);
            });


            mv.visitInsn(AASTORE);


            setUpCallMethod(mv, offset);

            if(Type.getType(descriptor).getSort() >= 9) mv.visitTypeInsn(CHECKCAST, Type.getType(descriptor).getInternalName());
            else castToPrimitive(Type.getType(descriptor), mv);
        }
    }

    private static class ShadowSetFieldHandler {

        public static void generateMethodBytecode(String name, Class<?> targetClass, String descriptor, boolean isTargetStatic, MethodVisitor mv, int offset) {

            mv.visitVarInsn(ASTORE - getTypeOffset(Type.getType(descriptor)), offset +1);

            mv.visitInsn(ICONST_5);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            mv.visitVarInsn(ASTORE, offset);
            mv.visitVarInsn(ALOAD, offset);

            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_0);
            mv.visitLdcInsn("mixin.bytemanipulators.AbstractByteManipulator");
            mv.visitInsn(AASTORE);

            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_1);
            mv.visitLdcInsn("setField");
            mv.visitInsn(AASTORE);

            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_2);
            mv.visitInsn(ACONST_NULL);
            mv.visitInsn(AASTORE);

            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_3);
            mv.visitLdcInsn(4); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String"); // Creates an empty array of Class references


            Class<?>[] classes = new Class[]{Object.class, String.class, String.class, Object.class};
            for(int i = 0; i < classes.length; i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                mv.visitLdcInsn(getClassName(classes[i].getName()));
                mv.visitInsn(AASTORE);
            }

            mv.visitInsn(AASTORE);

            mv.visitInsn(ICONST_4);
            mv.visitLdcInsn(4); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object"); // Creates an empty array of Class references

            Object[] args = new Object[]{targetClass.getName(), name};

            mv.visitInsn(DUP);
            mv.visitLdcInsn(0);
            mv.visitVarInsn(ALOAD - getTypeOffset(Type.getType(descriptor)), offset +1);
            if(getTypeOffset(Type.getType(descriptor)) != 0) {
                String className = Array.get(Array.newInstance(ClassReflection.getPrimitiveClassByName(Type.getType(descriptor).getClassName()) ,1),0).getClass().getName().replace(".", "/");

                mv.visitMethodInsn(INVOKESTATIC, className, "valueOf", "(" + Type.getType(descriptor).getInternalName() + ")L" + className + ";", false);
            }
            mv.visitInsn(AASTORE);

            for(int i = 0; i < args.length; i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i +1);
                mv.visitLdcInsn(args[i]);
                mv.visitInsn(AASTORE);
            }

            mv.visitInsn(DUP);
            mv.visitLdcInsn(args.length +1);
            if(isTargetStatic) mv.visitInsn(ACONST_NULL);
            else mv.visitVarInsn(ALOAD, 0);
            mv.visitInsn(AASTORE);

            mv.visitInsn(AASTORE);

            setUpCallMethod(mv, offset);
            mv.visitInsn(POP);
        }
    }
}
