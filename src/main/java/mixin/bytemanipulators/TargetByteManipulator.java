package mixin.bytemanipulators;

import mixin.Agent;
import mixin.annotations.Annotations;
import mixin.annotations.method.*;
import org.objectweb.asm.*;
import org.objectweb.asm.Type;
import reflection.ClassReflection;
import reflection.FieldReflection;

import javax.swing.plaf.SeparatorUI;
import java.lang.annotation.Annotation;
import java.lang.reflect.*;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.objectweb.asm.Opcodes.*;
import static org.objectweb.asm.Type.getArgumentTypes;
import static reflection.ClassReflection.getClassByName;
import static reflection.ConstructorReflection.getConstructor;
import static reflection.MethodReflection.*;

public class TargetByteManipulator extends AbstractByteManipulator {

    final Class<?> targetClass;
    final Class<?> mixinClass;
    final byte[] originalByteCode;

    public TargetByteManipulator(Class<?> targetClass, byte[] originalByteCode) {
        this.targetClass = targetClass;
        this.originalByteCode = originalByteCode;

        mixinClass = Agent.getMixinClasses().get(targetClass);
    }

    private List<Method> filterMethods(List<Method> methods, String name, String descriptor) {
        List<Method> methodList = methods.stream().filter(method -> {
            if(!Modifier.isStatic(method.getModifiers()))
                throw new RuntimeException(mixinClass.getName() + "." + method.getName() + " must be static");

            int isMethod = 0;
            if(method.isAnnotationPresent(Overwrite.class))
                isMethod += method.getAnnotation(Overwrite.class).value().equals(name + descriptor) ? 1 : 0;
            if(method.isAnnotationPresent(ChangeReturn.class))
                isMethod += method.getAnnotation(ChangeReturn.class).value().equals(name + descriptor) ? 1 : 0;
            if(method.isAnnotationPresent(InjectHead.class)) {
                String injectDescriptor = method.getAnnotation(InjectHead.class).value();
                if(injectDescriptor.split("\\(")[0].equals("<init>")) throw new IllegalArgumentException("cannot InjectHead a constructor");
                isMethod += injectDescriptor.equals(name + descriptor) ? 1 : 0;
            }
            if(method.isAnnotationPresent(InjectTail.class)) {
                String injectDescriptor = method.getAnnotation(InjectTail.class).value();
                if(!injectDescriptor.split("\\)")[1].equals("V")) throw new IllegalArgumentException("InjectTail in only allowed on void methods and constructors");
                isMethod += injectDescriptor.equals(name + descriptor) ? 1 : 0;
            }

            return isMethod != 0;
        }).collect(Collectors.toList());


        return methodList;
    }

    private void addAnnotationsToMethod(MethodVisitor methodVisitor, Method mixinMethod, Executable targetMethod) {
        Annotation[] annotations;

        if(mixinMethod != null) {
            annotations = (Annotation[]) useMethod(mixinMethod, null, new Object[mixinMethod.getParameterCount()]);
        } else {
            annotations = targetMethod.getDeclaredAnnotations();
        }

        for(Annotation annotation : annotations) {
            AnnotationVisitor annotationVisitor = methodVisitor.visitAnnotation("L" + annotation.annotationType().getName().replace(".", "/") + ";", true);

            for(Method m : getMethods(annotation.annotationType(), false)) {
                annotationVisitor.visit(m.getName(), useMethod(m, annotation));
            }

            annotationVisitor.visitEnd();
        }
    }

    private void addAnnotationsToField(FieldVisitor fieldVisitor, Method mixinMethod, Field targetMethod) {
        Annotation[] annotations = (Annotation[]) useMethod(mixinMethod, null, new Object[mixinMethod.getParameterCount()]);

        for(Annotation annotation : annotations) {
            AnnotationVisitor annotationVisitor = fieldVisitor.visitAnnotation("L" + annotation.annotationType().getName().replace(".", "/") + ";", true);

            for(Method m : getMethods(annotation.annotationType(), false)) {
                annotationVisitor.visit(m.getName(), useMethod(m, annotation));
            }
        }
    }

    private Method getAnnotationSetter(List<Method> methods, String value) {
        methods = methods.stream().filter(method -> method.isAnnotationPresent(Annotations.class) && method.getAnnotation(Annotations.class).value().equals(value)).toList();

        if(methods.size() > 1) throw new RuntimeException(value + " connot have more than 1 annotation method");

        return methods.size() == 1 ? methods.get(0) : null;
    }

    private String getDescriptor(Executable e) {
        if(e.getClass() == Method.class) return Type.getMethodDescriptor((Method) e);
        else return Type.getConstructorDescriptor((Constructor<?>) e);
    }

    private static final Label start = new Label();
    private static final Label end = new Label();
    private static final Label handler = new Label();

    public byte[] getMixinedClass() {
        try {
            ClassReader classReader = new ClassReader(originalByteCode);
            ClassWriter classWriter = new ClassWriter(ClassWriter.COMPUTE_FRAMES|ClassWriter.COMPUTE_MAXS);

            classReader.accept(new ClassVisitor(ASM9, classWriter) {

                @Override
                public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                    MethodVisitor methodVisitor = super.visitMethod(access, name, descriptor, signature, exceptions);

                    if(name.equals("<clinit>")) return methodVisitor;

                    List<Method> methodList = filterMethods(Arrays.stream(mixinClass.getDeclaredMethods()).toList(), name, descriptor);
                    Method annotationsMethod = getAnnotationSetter(Arrays.stream(mixinClass.getDeclaredMethods()).toList(), name + descriptor);


                    Executable targetMethod;
                    if(name.equals("<init>")) {
                        targetMethod = getConstructor(targetClass, Arrays.stream(getArgumentTypes(descriptor)).map(type -> getClassByName(getClassName(type.getInternalName()))).toArray(Class[]::new));
                    } else {
                        targetMethod = getMethod(targetClass, name, Arrays.stream(getArgumentTypes(descriptor)).map(type -> getClassByName(getClassName(type.getInternalName()))).toArray(Class[]::new));
                    }

                    if(annotationsMethod != null) {
                        addAnnotationsToMethod(methodVisitor, annotationsMethod, targetMethod);
                    }

                    if(methodList.size() == 0) {
                        return methodVisitor;
                    } else {
                        return new MethodVisitor(Opcodes.ASM9, methodVisitor) {

                            @Override
                            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                                return null;
                            }

                            @Override
                            public void visitCode() {

                                methodList.forEach(mixinMethod -> {

                                    if(mixinMethod.isAnnotationPresent(Overwrite.class)) {
                                        if(targetMethod.getClass() == Constructor.class) {
                                            ConstructorSetupHandler.generateMethodBytecode(methodVisitor);
                                        }

                                        if(annotationsMethod == null) {
                                            addAnnotationsToMethod(methodVisitor, null, targetMethod);
                                        }

                                        OverwriteMethodHandler.generateMethodBytecode(mixinMethod.getName(), getDescriptor(mixinMethod), (access&ACC_STATIC) != 0, mixinClass, this);
                                    }

                                    if(mixinMethod.isAnnotationPresent(InjectHead.class)) {
                                        InjectHeadMethodHandler.generateMethodBytecode(mixinMethod.getName(), getDescriptor(mixinMethod), Modifier.isStatic(access), mixinClass, this);
                                        super.visitCode();
                                    }
                                });
                            }

                            @Override
                            public void visitInsn(int opcode) {
                                if(opcode <= ARETURN && opcode >= IRETURN) {

                                    methodList.forEach(mixinMethod -> {

                                        if(mixinMethod.isAnnotationPresent(ChangeReturn.class)) {
                                            ReturnValueMethodHandler.generateMethodBytecode(mixinMethod.getName(), getDescriptor(mixinMethod), Modifier.isStatic(access), mixinClass, this, ARETURN - opcode);
                                        }
                                    });
                                } else if(opcode == RETURN) {
                                    methodList.forEach(mixinMethod -> {

                                        if(mixinMethod.isAnnotationPresent(InjectTail.class)) {
                                            InjectHeadMethodHandler.generateMethodBytecode(mixinMethod.getName(), getDescriptor(mixinMethod), Modifier.isStatic(access), mixinClass, this);
                                        }
                                    });
                                }

                                if(opcode > 500) {
                                    super.visitInsn(opcode -500);
                                } else if(methodList.stream().noneMatch(mixinMethod -> mixinMethod.isAnnotationPresent(ChangeReturn.class) || mixinMethod.isAnnotationPresent(InjectTail.class))) {
                                    super.visitInsn(opcode);
                                } else if(opcode > RETURN || opcode < IRETURN) {
                                    super.visitInsn(opcode);
                                }
                            }
                        };
                    }
                }

                @Override
                public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                    FieldVisitor fieldVisitor = super.visitField(access, name, descriptor, signature, value);

                    Field targetField = FieldReflection.getField(getClassByName(classReader.getClassName().replace("/", ".")), name);
                    Method annotationsField = getAnnotationSetter(Arrays.stream(mixinClass.getDeclaredMethods()).toList(), name);

                    if(annotationsField != null) {
                        addAnnotationsToField(fieldVisitor, annotationsField, targetField);
                    }

                    return new FieldVisitor(Opcodes.ASM7, fieldVisitor) {

                        @Override
                        public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                            return null;
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

    private static class ConstructorSetupHandler {

        public static void generateMethodBytecode(MethodVisitor mv) {
            mv.visitVarInsn(ALOAD, 0);
            mv.visitMethodInsn(INVOKESPECIAL,"java/lang/Object", "<init>", "()V", false);
        }
    }

    private static class OverwriteMethodHandler {

        public static void generateMethodBytecode(String mixinName, String descriptor, boolean isTargetStatic, Class<?> mixinClass, MethodVisitor mv) {

            Type[] parameters = getArgumentTypes(descriptor);
            int offset = parameters.length;

            mv.visitInsn(ICONST_5);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            mv.visitVarInsn(ASTORE, offset);
            mv.visitVarInsn(ALOAD, offset);

            loadIntoArray(0, mv, () -> mv.visitLdcInsn(mixinClass.getName()));
            loadIntoArray(1, mv, () -> mv.visitLdcInsn(mixinName));
            loadIntoArray(2, mv, () -> mv.visitInsn(ACONST_NULL));

            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_3);
            mv.visitLdcInsn(parameters.length); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String"); // Creates an empty array of Class references


            for(int i = 0; i < parameters.length; i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                mv.visitLdcInsn(parameters[i].getClassName());
                mv.visitInsn(AASTORE);
            }

            mv.visitInsn(AASTORE);

            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_4);
            mv.visitLdcInsn(parameters.length); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object"); // Creates an empty array of Class references

            mv.visitInsn(DUP);
            mv.visitLdcInsn(0);
            if(isTargetStatic) mv.visitInsn(ACONST_NULL);
            else mv.visitVarInsn(ALOAD, 0);
            mv.visitInsn(AASTORE);

            loadArgs(parameters, isTargetStatic ? 1 : 0, 1, mv);
            mv.visitInsn(AASTORE);

            mv.visitVarInsn(ASTORE, offset);

            setUpCallMethod(mv, offset);

            addReturn(descriptor, mv);
        }
    }

    private static class InjectHeadMethodHandler {

        public static void generateMethodBytecode(String mixinName, String descriptor, boolean isTargetStatic, Class<?> mixinClass, MethodVisitor mv) {

            Type[] parameters = getArgumentTypes(descriptor);
            int offset = parameters.length;

            mv.visitInsn(ICONST_5);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            mv.visitVarInsn(ASTORE, offset);
            mv.visitVarInsn(ALOAD, offset);

            loadIntoArray(0, mv, () -> mv.visitLdcInsn(mixinClass.getName()));
            loadIntoArray(1, mv, () -> mv.visitLdcInsn(mixinName));
            loadIntoArray(2, mv, () -> mv.visitInsn(ACONST_NULL));

            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_3);
            mv.visitLdcInsn(parameters.length); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String"); // Creates an empty array of Class references


            for(int i = 0; i < parameters.length; i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                mv.visitLdcInsn(parameters[i].getClassName());
                mv.visitInsn(AASTORE);
            }

            mv.visitInsn(AASTORE);

            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_4);
            mv.visitLdcInsn(parameters.length); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object"); // Creates an empty array of Class references

            loadIntoArray(0, mv, () -> {
                if(isTargetStatic) mv.visitInsn(ACONST_NULL);
                else mv.visitVarInsn(ALOAD, 0);
            });

            loadArgs(parameters, isTargetStatic ? 1 : 0, 1, mv);
            mv.visitInsn(AASTORE);

            setUpCallMethod(mv, offset);

            mv.visitInsn(RETURN +500);
        }
    }

    private static class ReturnValueMethodHandler {

        public static void generateMethodBytecode(String mixinName, String descriptor, boolean isTargetStatic, Class<?> mixinClass, MethodVisitor mv, int type) {

            Type[] parameters = getArgumentTypes(descriptor);
            int offset = parameters.length;

            mv.visitVarInsn(ASTORE - type, offset);

            mv.visitInsn(ICONST_5);
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object");
            mv.visitVarInsn(ASTORE, offset +1);
            mv.visitVarInsn(ALOAD, offset +1);

            loadIntoArray(0, mv, () -> mv.visitLdcInsn(mixinClass.getName()));
            loadIntoArray(1, mv, () -> mv.visitLdcInsn(mixinName));
            loadIntoArray(2, mv, () -> mv.visitInsn(ACONST_NULL));


            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_3);
            mv.visitLdcInsn(parameters.length); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/String"); // Creates an empty array of Class references


            //load parameters
            for(int i = 0; i < parameters.length; i++) {
                mv.visitInsn(DUP);
                mv.visitLdcInsn(i);
                mv.visitLdcInsn(parameters[i].getClassName());
                mv.visitInsn(AASTORE);
            }

            mv.visitInsn(AASTORE);


            mv.visitInsn(DUP);
            mv.visitInsn(ICONST_4);
            mv.visitLdcInsn(parameters.length); // Push the number of parameters onto the stack
            mv.visitTypeInsn(ANEWARRAY, "java/lang/Object"); // Creates an empty array of Class references
            mv.visitVarInsn(ASTORE, offset +2);

            loadIntoArray(0, mv, () -> {
                if(isTargetStatic) mv.visitInsn(ACONST_NULL);
                else mv.visitVarInsn(ALOAD, 0);
            });

            mv.visitVarInsn(ALOAD, offset +2);
            mv.visitLdcInsn(1);
            loadIntoArray(0, mv, () -> {
                mv.visitVarInsn(ALOAD - type, offset);
                if(parameters[1].getInternalName().length() == 1) {
                    String className = Array.get(Array.newInstance(ClassReflection.getPrimitiveClassByName(parameters[1].getClassName()), 1), 0).getClass().getName().replace(".", "/");

                    mv.visitMethodInsn(INVOKESTATIC, className, "valueOf", "(" + parameters[1].getInternalName() + ")L" + className + ";", false);
                }
            });
            mv.visitInsn(AASTORE);

            loadArgs(parameters, isTargetStatic ? 2 : 1, 2, mv);

            mv.visitVarInsn(ALOAD, offset +1);
            mv.visitInsn(ICONST_4);
            mv.visitVarInsn(ALOAD, offset +2);
            mv.visitInsn(AASTORE);

            mv.visitVarInsn(ASTORE, offset);

            setUpCallMethod(mv, offset);

            if(Type.getReturnType(descriptor).getSort() >= 9) mv.visitTypeInsn(CHECKCAST, Type.getReturnType(descriptor).getInternalName());
            else castToPrimitive(Type.getReturnType(descriptor), mv);

            mv.visitInsn((ARETURN - type) +500);
        }
    }
}
