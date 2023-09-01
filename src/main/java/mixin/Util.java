package mixin;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

import static reflection.ClassReflection.getClassByName;
import static reflection.MethodReflection.*;

public class Util {

    public static <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return getAnnotation(annotationClass, new HashMap<>());
    }

    public static <T extends Annotation> T getAnnotation(Class<T> annotationClass, Map<String, Object> values) {
        return getAnnotation(annotationClass, new HashMap<>(values));
    }

    private static <T extends Annotation> T getAnnotation(Class<T> annotationClass, HashMap<String, Object> values) {
        List<Method> methods = Arrays.stream(getMethods(annotationClass, false)).collect(Collectors.toList());

        if(values.entrySet().size() > methods.size()) throw new IllegalArgumentException();

        values.entrySet().stream().forEach(entry -> {
            List<Method> method = methods.stream().filter(m -> m.getName().equals(entry.getKey()) && m.getReturnType() == entry.getValue().getClass()).toList();

            if(method.size() != 0) {
                methods.remove(method.get(0));
            } else {
                throw new IllegalArgumentException();
            }
        });

        methods.stream().forEach(method -> {
            Object defaultValue = method.getDefaultValue();

            if(defaultValue == null) return;

            values.put(method.getName(), defaultValue);
        });

        return (T) useMethod(getMethod(getClassByName("sun.reflect.annotation.AnnotationParser"), "annotationForMap", Class.class, Map.class), null, annotationClass, values);
    }
}
