package mixin;

import mixin.annotations.Method.ShadowMethod;
import mixin.annotations.field.OverwriteField;
import mixin.annotations.field.ShadowField;
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
