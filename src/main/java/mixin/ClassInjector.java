package mixin;

import mixin.annotations.Mixin;
import java.util.Arrays;

import static mixin.Agent.addMixinClass;

public abstract class ClassInjector {

    public static void addClasses(Class<?>... classes) {
        Arrays.stream(classes).forEach(clazz -> {
            if(!clazz.isAnnotationPresent(Mixin.class)) throw new RuntimeException(clazz.getName() + " is not a mixin class");
            else addMixinClass(clazz);
        });
    }
}
