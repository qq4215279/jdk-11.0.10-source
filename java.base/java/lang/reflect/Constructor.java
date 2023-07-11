/*
 * Copyright (c) 1996, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.lang.reflect;

import jdk.internal.access.SharedSecrets;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.ConstructorAccessor;
import jdk.internal.reflect.Reflection;
import jdk.internal.vm.annotation.ForceInline;
import sun.reflect.annotation.TypeAnnotation;
import sun.reflect.annotation.TypeAnnotationParser;
import sun.reflect.generics.repository.ConstructorRepository;
import sun.reflect.generics.factory.CoreReflectionFactory;
import sun.reflect.generics.factory.GenericsFactory;
import sun.reflect.generics.scope.ConstructorScope;

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationFormatError;
import java.util.StringJoiner;

/**
 *
 * @date 2023/7/8 14:33
 */
public final class Constructor<T> extends Executable {
    private Class<T> clazz;
    private int slot;
    private Class<?>[] parameterTypes;
    private Class<?>[] exceptionTypes;
    private int modifiers;
    // Generics and annotations support
    private transient String signature;
    // generic info repository; lazily initialized
    private transient ConstructorRepository genericInfo;
    private byte[] annotations;
    private byte[] parameterAnnotations;

    private GenericsFactory getFactory() {
        // create scope and factory
        return CoreReflectionFactory.make(this, ConstructorScope.make(this));
    }

    // Accessor for generic info repository
    @Override
    ConstructorRepository getGenericInfo() {
        // lazily initialize repository if necessary
        if (genericInfo == null) {
            // create and cache generic info repository
            genericInfo =
                    ConstructorRepository.make(getSignature(),
                            getFactory());
        }
        return genericInfo; //return cached repository
    }

    private volatile ConstructorAccessor constructorAccessor;
    private Constructor<T> root;

    @Override
    Constructor<T> getRoot() {
        return root;
    }

    Constructor(Class<T> declaringClass,
                Class<?>[] parameterTypes,
                Class<?>[] checkedExceptions,
                int modifiers,
                int slot,
                String signature,
                byte[] annotations,
                byte[] parameterAnnotations) {
        this.clazz = declaringClass;
        this.parameterTypes = parameterTypes;
        this.exceptionTypes = checkedExceptions;
        this.modifiers = modifiers;
        this.slot = slot;
        this.signature = signature;
        this.annotations = annotations;
        this.parameterAnnotations = parameterAnnotations;
    }

    Constructor<T> copy() {
        // This routine enables sharing of ConstructorAccessor objects
        // among Constructor objects which refer to the same underlying
        // method in the VM. (All of this contortion is only necessary
        // because of the "accessibility" bit in AccessibleObject,
        // which implicitly requires that new java.lang.reflect
        // objects be fabricated for each reflective call on Class
        // objects.)
        if (this.root != null)
            throw new IllegalArgumentException("Can not copy a non-root Constructor");

        Constructor<T> res = new Constructor<>(clazz,
                parameterTypes,
                exceptionTypes, modifiers, slot,
                signature,
                annotations,
                parameterAnnotations);
        res.root = this;
        // Might as well eagerly propagate this if already present
        res.constructorAccessor = constructorAccessor;
        return res;
    }

    @Override
    @CallerSensitive
    public void setAccessible(boolean flag) {
        AccessibleObject.checkPermission();
        if (flag) {
            checkCanSetAccessible(Reflection.getCallerClass());
        }
        setAccessible0(flag);
    }

    @Override
    void checkCanSetAccessible(Class<?> caller) {
        checkCanSetAccessible(caller, clazz);
        if (clazz == Class.class) {
            // can we change this to InaccessibleObjectException?
            throw new SecurityException("Cannot make a java.lang.Class"
                    + " constructor accessible");
        }
    }

    @Override
    boolean hasGenericInformation() {
        return (getSignature() != null);
    }

    @Override
    byte[] getAnnotationBytes() {
        return annotations;
    }

    @Override
    public Class<T> getDeclaringClass() {
        return clazz;
    }

    @Override
    public String getName() {
        return getDeclaringClass().getName();
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public TypeVariable<Constructor<T>>[] getTypeParameters() {
        if (getSignature() != null) {
            return (TypeVariable<Constructor<T>>[]) getGenericInfo().getTypeParameters();
        } else
            return (TypeVariable<Constructor<T>>[]) new TypeVariable[0];
    }


    @Override
    Class<?>[] getSharedParameterTypes() {
        return parameterTypes;
    }

    @Override
    Class<?>[] getSharedExceptionTypes() {
        return exceptionTypes;
    }

    @Override
    public Class<?>[] getParameterTypes() {
        return parameterTypes.clone();
    }

    public int getParameterCount() {
        return parameterTypes.length;
    }

    @Override
    public Type[] getGenericParameterTypes() {
        return super.getGenericParameterTypes();
    }

    @Override
    public Class<?>[] getExceptionTypes() {
        return exceptionTypes.clone();
    }

    @Override
    public Type[] getGenericExceptionTypes() {
        return super.getGenericExceptionTypes();
    }

    public boolean equals(Object obj) {
        if (obj != null && obj instanceof Constructor) {
            Constructor<?> other = (Constructor<?>) obj;
            if (getDeclaringClass() == other.getDeclaringClass()) {
                return equalParamTypes(parameterTypes, other.parameterTypes);
            }
        }
        return false;
    }

    public int hashCode() {
        return getDeclaringClass().getName().hashCode();
    }

    public String toString() {
        return sharedToString(Modifier.constructorModifiers(),
                false,
                parameterTypes,
                exceptionTypes);
    }

    @Override
    void specificToStringHeader(StringBuilder sb) {
        sb.append(getDeclaringClass().getTypeName());
    }

    @Override
    String toShortString() {
        StringBuilder sb = new StringBuilder("constructor ");
        sb.append(getDeclaringClass().getTypeName());
        sb.append('(');
        StringJoiner sj = new StringJoiner(",");
        for (Class<?> parameterType : getParameterTypes()) {
            sj.add(parameterType.getTypeName());
        }
        sb.append(sj);
        sb.append(')');
        return sb.toString();
    }

    @Override
    public String toGenericString() {
        return sharedToGenericString(Modifier.constructorModifiers(), false);
    }

    @Override
    void specificToGenericStringHeader(StringBuilder sb) {
        specificToStringHeader(sb);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public T newInstance(Object... initargs)
            throws InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, clazz, clazz, modifiers);
        }
        if ((clazz.getModifiers() & Modifier.ENUM) != 0)
            throw new IllegalArgumentException("Cannot reflectively create enum objects");
        ConstructorAccessor ca = constructorAccessor;   // read volatile
        if (ca == null) {
            ca = acquireConstructorAccessor();
        }
        @SuppressWarnings("unchecked")
        T inst = (T) ca.newInstance(initargs);
        return inst;
    }

    @Override
    public boolean isVarArgs() {
        return super.isVarArgs();
    }

    @Override
    public boolean isSynthetic() {
        return super.isSynthetic();
    }

    private ConstructorAccessor acquireConstructorAccessor() {
        // First check to see if one has been created yet, and take it
        // if so.
        ConstructorAccessor tmp = null;
        if (root != null) tmp = root.getConstructorAccessor();
        if (tmp != null) {
            constructorAccessor = tmp;
        } else {
            // Otherwise fabricate one and propagate it up to the root
            tmp = reflectionFactory.newConstructorAccessor(this);
            setConstructorAccessor(tmp);
        }

        return tmp;
    }

    ConstructorAccessor getConstructorAccessor() {
        return constructorAccessor;
    }

    void setConstructorAccessor(ConstructorAccessor accessor) {
        constructorAccessor = accessor;
        // Propagate up
        if (root != null) {
            root.setConstructorAccessor(accessor);
        }
    }

    int getSlot() {
        return slot;
    }

    String getSignature() {
        return signature;
    }

    byte[] getRawAnnotations() {
        return annotations;
    }

    byte[] getRawParameterAnnotations() {
        return parameterAnnotations;
    }


    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return super.getAnnotation(annotationClass);
    }

    public Annotation[] getDeclaredAnnotations() {
        return super.getDeclaredAnnotations();
    }

    @Override
    public Annotation[][] getParameterAnnotations() {
        return sharedGetParameterAnnotations(parameterTypes, parameterAnnotations);
    }

    @Override
    boolean handleParameterNumberMismatch(int resultLength, int numParameters) {
        Class<?> declaringClass = getDeclaringClass();
        if (declaringClass.isEnum() ||
                declaringClass.isAnonymousClass() ||
                declaringClass.isLocalClass())
            return false; // Can't do reliable parameter counting
        else {
            if (declaringClass.isMemberClass() &&
                    ((declaringClass.getModifiers() & Modifier.STATIC) == 0) &&
                    resultLength + 1 == numParameters) {
                return true;
            } else {
                throw new AnnotationFormatError(
                        "Parameter annotations don't match number of parameters");
            }
        }
    }

    @Override
    public AnnotatedType getAnnotatedReturnType() {
        return getAnnotatedReturnType0(getDeclaringClass());
    }

    @Override
    public AnnotatedType getAnnotatedReceiverType() {
        Class<?> thisDeclClass = getDeclaringClass();
        Class<?> enclosingClass = thisDeclClass.getEnclosingClass();

        if (enclosingClass == null) {
            // A Constructor for a top-level class
            return null;
        }

        Class<?> outerDeclaringClass = thisDeclClass.getDeclaringClass();
        if (outerDeclaringClass == null) {
            // A constructor for a local or anonymous class
            return null;
        }

        // Either static nested or inner class
        if (Modifier.isStatic(thisDeclClass.getModifiers())) {
            // static nested
            return null;
        }

        // A Constructor for an inner class
        return TypeAnnotationParser.buildAnnotatedType(getTypeAnnotationBytes0(),
                SharedSecrets.getJavaLangAccess().
                        getConstantPool(thisDeclClass),
                this,
                thisDeclClass,
                enclosingClass,
                TypeAnnotation.TypeAnnotationTarget.METHOD_RECEIVER);
    }
}
