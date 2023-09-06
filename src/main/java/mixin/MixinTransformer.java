package mixin;

import mixin.annotations.Mixin;
import mixin.bytemanipulators.MixinByteManipulator;
import mixin.bytemanipulators.TargetByteManipulator;

import java.io.IOException;
import java.lang.instrument.ClassFileTransformer;
import java.security.ProtectionDomain;

import static mixin.Agent.mixined;
import static reflection.ClassReflection.getClassByName;
import static reflection.MethodReflection.getMethod;

public class MixinTransformer implements ClassFileTransformer {

    @Override
    public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain, byte[] classfileBuffer) {

        if(classBeingRedefined == null) {
            return null;
        }

        if(mixined.contains(classBeingRedefined)) return null;
        mixined.add(classBeingRedefined);

        if(classBeingRedefined.isAnnotationPresent(Mixin.class)) {
            classfileBuffer = new MixinByteManipulator(classBeingRedefined, classfileBuffer).getMixinedClass();
        }
        if(Agent.mixinClasses.containsKey(classBeingRedefined)) {
            classfileBuffer = new TargetByteManipulator(classBeingRedefined, classfileBuffer).getMixinedClass();
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
}
