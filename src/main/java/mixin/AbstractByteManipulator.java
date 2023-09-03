package mixin;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import reflection.ClassReflection;
import reflection.FieldReflection;
import reflection.MethodReflection;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import static org.objectweb.asm.Opcodes.*;
import static reflection.ClassReflection.getClassByName;
import static reflection.FieldReflection.getFieldValue;
import static reflection.FieldReflection.setFieldValue;
import static reflection.MethodReflection.getMethod;

public class AbstractByteManipulator {
    public static Object callMethod(String className, String methodName, Object instance, String[] types, Object[] args) {
        Class<?>[] typeClasses = Arrays.stream(types).map(ClassReflection::getClassByName).toArray(Class[]::new);

        Method method = getMethod(getClassByName(className), methodName, typeClasses);
        return MethodReflection.useMethod(method, instance, args);
    }

    public static Object getField(String className, String fieldName, Object instance) {
        Field method = FieldReflection.getField(getClassByName(className), fieldName);
        return getFieldValue(method, instance);
    }

    public static void setField(Object value, String className, String fieldName, Object instance) {
        Field method = FieldReflection.getField(getClassByName(className), fieldName);
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

    static void loadArgs(Type[] parameters, int startIndex, MethodVisitor mv) {
        for(int i = startIndex; i < parameters.length; i++) {
            mv.visitInsn(DUP);
            mv.visitLdcInsn(i);
            mv.visitVarInsn(ALOAD - getTypeOffset(parameters[i]) , i);
            if(ClassReflection.getPrimitiveClassByName(parameters[i].getClassName()) != null) {
                String className = Array.get(Array.newInstance(ClassReflection.getPrimitiveClassByName(parameters[i].getClassName()) ,1),0).getClass().getName().replace(".", "/");

                mv.visitMethodInsn(INVOKESTATIC, className, "valueOf", "(" + parameters[i].getInternalName() + ")L" + className + ";", false);
            }
            mv.visitInsn(AASTORE);
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
