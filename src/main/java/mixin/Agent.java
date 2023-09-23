package mixin;

import mixin.annotations.Mixin;
import reflection.Vars;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Hashtable;
import java.util.List;

public class Agent {

    static Instrumentation agent;
    static Hashtable<Class<?>, Class<?>> mixinClasses = new Hashtable<>();
    static List<Class<?>> mixined = new ArrayList<>();

    public static Hashtable<Class<?>, Class<?>> getMixinClasses() {
        return mixinClasses;
    }

    public static Instrumentation getAgent() {
        return agent;
    }

    static {
        Vars.init();
    }

    public static Class<?> getMixinedClass(Class<?> mixined) {
        return Arrays.stream(agent.getAllLoadedClasses()).filter(c -> c.getName().equals(mixined.getAnnotation(Mixin.class).value())).toList().get(0);
    }

    public static void addMixinClass(Class<?> clazz) {
        if(agent == null || !agent.isRetransformClassesSupported()) throw new IllegalStateException();

        Class<?> targetClass = getMixinedClass(clazz);

        if(!targetClass.isHidden() && !targetClass.isArray() && !targetClass.isPrimitive() && !targetClass.isAnonymousClass() && !targetClass.getName().equals("jdk.internal.vm.Continuation")) {
            if(mixinClasses.containsKey(targetClass)) throw new RuntimeException();
            if(mixinClasses.containsValue(clazz)) throw new RuntimeException();

            mixinClasses.put(targetClass, clazz);

            try {
                agent.retransformClasses(clazz);
                agent.retransformClasses(targetClass);
            } catch(Throwable e) {
                e.printStackTrace();
            }
        }
    }

    private static void init(Instrumentation instrumentation) {
        agent = instrumentation;
        instrumentation.addTransformer(new MixinTransformer(), true);
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        init(instrumentation);
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        init(instrumentation);
    }
}

