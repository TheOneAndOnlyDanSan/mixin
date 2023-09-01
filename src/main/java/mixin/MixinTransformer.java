package mixin;

import mixin.annotations.Mixin;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;
import reflection.ClassReflection;
import reflection.MethodReflection;
import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.UnmodifiableClassException;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Arrays;

import static mixin.Agent.mixined;
import static org.objectweb.asm.Opcodes.*;

public class MixinTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {

        if(classBeingRedefined == null) {
            return null;
        }

        if(mixined.contains(classBeingRedefined)) return null;
        mixined.add(classBeingRedefined);


        if(classBeingRedefined.isAnnotationPresent(Mixin.class)) {
            try {
                Agent.agent.retransformClasses(classBeingRedefined.getAnnotation(Mixin.class).value());
            } catch(UnmodifiableClassException e) {
                throw new RuntimeException(e);
            }

            classfileBuffer = ByteManipulator.getMixinMixin(classBeingRedefined, classfileBuffer);
        }
        if(Agent.mixinClasses.containsKey(classBeingRedefined)) {
            classfileBuffer = ByteManipulator.getTargetMixin(classBeingRedefined, classfileBuffer);
        }

        try {
            java.io.File file = new java.io.File("D:/Desktop/mixin/build/libs/" + classBeingRedefined.getSimpleName() + ".class");
            file.createNewFile();
            java.io.FileOutputStream fileOutputStream = new java.io.FileOutputStream(file);
            fileOutputStream.write(classfileBuffer);
            fileOutputStream.close();
        } catch(IOException e) {
            throw new RuntimeException(e);
        }

        return classfileBuffer;
    }

    public static Object callMethod(String className, String methodName, Object instance, String[] types, Object[] args) {
        Class<?>[] typeClasses = Arrays.stream(types).map(ClassReflection::getClassByName).toArray(Class[]::new);

        Method method = MethodReflection.getMethod(ClassReflection.getClassByName(className), methodName, typeClasses);
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
