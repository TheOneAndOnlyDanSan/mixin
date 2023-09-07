package mixin.bytemanipulators;

import mixin.Agent;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;
import reflection.ClassReflection;
import reflection.FieldReflection;
import reflection.MethodReflection;
import reflection.Vars;
import sun.misc.Unsafe;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;

import static org.objectweb.asm.Opcodes.*;
import static reflection.ClassReflection.getClassByName;
import static reflection.FieldReflection.getFieldValue;
import static reflection.FieldReflection.setFieldValue;
import static reflection.MethodReflection.getMethod;

public class AbstractByteManipulator {

    public static String getClassName(String className) {
        if(className.length() == 1) {
            return switch(className) {
                case "I" -> "int";
                case "C" -> "char";
                case "Z" -> "boolean";
                case "D" -> "double";
                case "J" -> "long";
                case "F" -> "float";
                case "B" -> "byte";
                case "S" -> "short";
                default -> throw new RuntimeException();
            };
        }

        return className.replace("/", ".");
    }

    public static void setUpCallMethod(MethodVisitor mv, int offset) {
        mv.visitLdcInsn(Type.getType("Ljava/lang/ClassLoader;"));
        mv.visitLdcInsn("getBuiltinAppClassLoader");
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 1 + offset);
        mv.visitLdcInsn("sun.misc.Unsafe");
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;)Ljava/lang/Class;", false);
        mv.visitVarInsn(Opcodes.ASTORE, 2 + offset);
        mv.visitVarInsn(ALOAD, 2 + offset);
        mv.visitLdcInsn("theUnsafe");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredField", "(Ljava/lang/String;)Ljava/lang/reflect/Field;", false);
        mv.visitInsn(DUP);
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", "setAccessible", "(Z)V", false);
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Field", "get", "(Ljava/lang/Object;)Ljava/lang/Object;", false);
        mv.visitVarInsn(ASTORE, 3 + offset);
        mv.visitVarInsn(ALOAD, 2 + offset);
        mv.visitLdcInsn("putBoolean");
        mv.visitLdcInsn(3);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
        loadIntoArray(0, mv, () -> mv.visitLdcInsn(Type.getType("Ljava/lang/Object;")));
        loadIntoArray(1, mv, () -> mv.visitFieldInsn(GETSTATIC, "java/lang/Long", "TYPE", "Ljava/lang/Class;"));
        loadIntoArray(2, mv, () -> mv.visitFieldInsn(GETSTATIC, "java/lang/Boolean", "TYPE", "Ljava/lang/Class;"));
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
        mv.visitVarInsn(ALOAD, 3 + offset);
        mv.visitLdcInsn(3);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        loadIntoArray(0, mv, () -> mv.visitVarInsn(ALOAD, 1 + offset));
        loadIntoArray(1, mv, () -> {
            mv.visitLdcInsn("12");
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Long", "valueOf", "(Ljava/lang/String;)Ljava/lang/Long;", false);
        });
        loadIntoArray(2, mv, () -> {
            mv.visitLdcInsn("true");
            mv.visitMethodInsn(INVOKESTATIC, "java/lang/Boolean", "valueOf", "(Ljava/lang/String;)Ljava/lang/Boolean;", false);
        });
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
        mv.visitInsn(POP);
        mv.visitVarInsn(Opcodes.ALOAD, 1 + offset);
        mv.visitInsn(ACONST_NULL);
        mv.visitLdcInsn(0);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
        mv.visitTypeInsn(Opcodes.CHECKCAST, "java/lang/ClassLoader");
        mv.visitVarInsn(ASTORE, 5 + offset);
        mv.visitLdcInsn("mixin.bytemanipulators.AbstractByteManipulator");
        mv.visitInsn(Opcodes.ICONST_1);
        mv.visitVarInsn(Opcodes.ALOAD, 5 + offset);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/Class", "forName", "(Ljava/lang/String;ZLjava/lang/ClassLoader;)Ljava/lang/Class;", false);
        mv.visitLdcInsn("callMethod");
        mv.visitInsn(Opcodes.ICONST_5);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
        loadIntoArray(0, mv, () -> mv.visitLdcInsn(Type.getType("Ljava/lang/String;")));
        loadIntoArray(1, mv, () -> mv.visitLdcInsn(Type.getType("Ljava/lang/String;")));
        loadIntoArray(2, mv, () -> mv.visitLdcInsn(Type.getType("Ljava/lang/Object;")));
        loadIntoArray(3, mv, () -> mv.visitLdcInsn(Type.getType("[Ljava/lang/String;")));
        loadIntoArray(4, mv, () -> mv.visitLdcInsn(Type.getType("[Ljava/lang/Object;")));
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class", "getDeclaredMethod", "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;", false);
        mv.visitInsn(ACONST_NULL);
        mv.visitVarInsn(ALOAD, offset);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/reflect/Method", "invoke", "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;", false);
    }

    public static void loadIntoArray(int index, MethodVisitor mv, Runnable value) {
        mv.visitInsn(Opcodes.DUP);
        mv.visitLdcInsn(index);
        value.run();
        mv.visitInsn(Opcodes.AASTORE);
    }

    public static Object callMethod(String className, String methodName, Object instance, String[] types, Object[] args) {
        Class<?>[] typeClasses = Arrays.stream(types).map(ClassReflection::getClassByName).toArray(Class[]::new);

        Class<?> targetClass = Arrays.stream(Agent.getAgent().getAllLoadedClasses()).filter(clazz -> clazz.getName().equals(className)).toList().get(0);

        Method method = getMethod(targetClass, methodName, typeClasses);
        return MethodReflection.useMethod(method, instance, args);
    }

    public static Object getField(String className, String fieldName, Object instance) {
        Class<?> targetClass = Arrays.stream(Agent.getAgent().getAllLoadedClasses()).filter(clazz -> clazz.getName().equals(className)).toList().get(0);

        Field method = FieldReflection.getField(targetClass, fieldName);
        return getFieldValue(method, instance);
    }

    public static void setField(Object value, String className, String fieldName, Object instance) {
        Class<?> targetClass = Arrays.stream(Agent.getAgent().getAllLoadedClasses()).filter(clazz -> clazz.getName().equals(className)).toList().get(0);

        Field method = FieldReflection.getField(targetClass, fieldName);
        setFieldValue(method, instance, value);
    }

    static int getTypeOffset(Type type) {

        if(type.getSort() > 8) return 0;

        return switch (type.getInternalName().charAt(0)) {
            case 'Z', 'B', 'S', 'C', 'I' ->  4;
            case 'J'  -> 3;
            case 'F'  -> 2;
            case 'D' -> 1;
            default -> throw new RuntimeException();
        };
    }

    static void loadArgs(Type[] parameters, int offset, int startIndex, MethodVisitor mv) {
        for(int i = startIndex; i < parameters.length; i++) {
            int finalI = i;
            loadIntoArray(i, mv, () -> {
                mv.visitVarInsn(ALOAD - getTypeOffset(parameters[finalI]) , finalI - offset);
                if(parameters[finalI].getInternalName().length() == 1) {
                    String className = Array.get(Array.newInstance(ClassReflection.getPrimitiveClassByName(parameters[finalI].getClassName()) ,1),0).getClass().getName().replace(".", "/");

                    mv.visitMethodInsn(INVOKESTATIC, className, "valueOf", "(" + parameters[finalI].getInternalName() + ")L" + className + ";", false);
                }
            });
        }
    }

    static void castToPrimitive(Type returnType, MethodVisitor mv) {
        String className = Array.get(Array.newInstance(ClassReflection.getPrimitiveClassByName(returnType.getClassName()) ,1),0).getClass().getName().replace(".", "/");

        mv.visitTypeInsn(CHECKCAST, className);
        mv.visitMethodInsn(INVOKEVIRTUAL, className, returnType.getClassName() + "Value", "()" + returnType.getInternalName(), false);
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
