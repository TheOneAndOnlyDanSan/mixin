package mixin;

import mixin.annotations.Mixin;
import reflection.Vars;

import java.lang.instrument.Instrumentation;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

class Agent {

    static Instrumentation agent;
    static Hashtable<Class<?>, Class<?>> mixinClasses = new Hashtable<>();
    static List<Class<?>> mixined = new ArrayList<>();

    static {
        Vars.init();
    }

    public static void addMixinClass(Class<?> clazz) {
        Class<?> targetClass = clazz.getAnnotation(Mixin.class).value();

        if(!targetClass.isHidden() && !targetClass.isArray() && !targetClass.isPrimitive() && !targetClass.isAnonymousClass() && !targetClass.getName().equals("jdk.internal.vm.Continuation")) {
            if(mixinClasses.containsKey(targetClass)) throw new RuntimeException();
            if(mixinClasses.containsValue(clazz)) throw new RuntimeException();

            mixinClasses.put(targetClass, clazz);

            try {
                agent.retransformClasses(clazz);
            } catch(Throwable e) {
                System.out.println(clazz);
                e.printStackTrace();
            }
        }
    }

    public static void premain(String agentArgs, Instrumentation instrumentation) {
        agent = instrumentation;
        instrumentation.addTransformer(new MixinTransformer(), true);
    }

    public static void agentmain(String agentArgs, Instrumentation instrumentation) {
        premain(agentArgs, instrumentation);
    }
}
