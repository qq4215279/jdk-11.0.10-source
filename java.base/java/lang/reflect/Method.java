/*
 * Copyright (c) 1996, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.lang.reflect;

import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.access.SharedSecrets;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.MethodAccessor;
import jdk.internal.reflect.Reflection;
import jdk.internal.vm.annotation.ForceInline;
import sun.reflect.annotation.ExceptionProxy;
import sun.reflect.annotation.TypeNotPresentExceptionProxy;
import sun.reflect.generics.repository.MethodRepository;
import sun.reflect.generics.factory.CoreReflectionFactory;
import sun.reflect.generics.factory.GenericsFactory;
import sun.reflect.generics.scope.MethodScope;
import sun.reflect.annotation.AnnotationType;
import sun.reflect.annotation.AnnotationParser;

import java.lang.annotation.Annotation;
import java.lang.annotation.AnnotationFormatError;
import java.nio.ByteBuffer;
import java.util.StringJoiner;

/**
 *
 * @date 2023/7/8 14:36
 */
public final class Method extends Executable {
    private Class<?> clazz;
    private int slot;
    // This is guaranteed to be interned by the VM in the 1.4
    // reflection implementation
    private String name;
    private Class<?> returnType;
    private Class<?>[] parameterTypes;
    private Class<?>[] exceptionTypes;
    private int modifiers;
    // Generics and annotations support
    private transient String signature;
    // generic info repository; lazily initialized
    private transient MethodRepository genericInfo;
    private byte[] annotations;
    private byte[] parameterAnnotations;
    private byte[] annotationDefault;
    private volatile MethodAccessor methodAccessor;
    private Method root;

    // Generics infrastructure
    private String getGenericSignature() {
        return signature;
    }

    // Accessor for factory
    private GenericsFactory getFactory() {
        // create scope and factory
        return CoreReflectionFactory.make(this, MethodScope.make(this));
    }

    // Accessor for generic info repository
    @Override
    MethodRepository getGenericInfo() {
        // lazily initialize repository if necessary
        if (genericInfo == null) {
            // create and cache generic info repository
            genericInfo = MethodRepository.make(getGenericSignature(),
                    getFactory());
        }
        return genericInfo; //return cached repository
    }

    Method(Class<?> declaringClass,
           String name,
           Class<?>[] parameterTypes,
           Class<?> returnType,
           Class<?>[] checkedExceptions,
           int modifiers,
           int slot,
           String signature,
           byte[] annotations,
           byte[] parameterAnnotations,
           byte[] annotationDefault) {
        this.clazz = declaringClass;
        this.name = name;
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
        this.exceptionTypes = checkedExceptions;
        this.modifiers = modifiers;
        this.slot = slot;
        this.signature = signature;
        this.annotations = annotations;
        this.parameterAnnotations = parameterAnnotations;
        this.annotationDefault = annotationDefault;
    }

    Method copy() {
        // This routine enables sharing of MethodAccessor objects
        // among Method objects which refer to the same underlying
        // method in the VM. (All of this contortion is only necessary
        // because of the "accessibility" bit in AccessibleObject,
        // which implicitly requires that new java.lang.reflect
        // objects be fabricated for each reflective call on Class
        // objects.)
        if (this.root != null)
            throw new IllegalArgumentException("Can not copy a non-root Method");

        Method res = new Method(clazz, name, parameterTypes, returnType,
                exceptionTypes, modifiers, slot, signature,
                annotations, parameterAnnotations, annotationDefault);
        res.root = this;
        // Might as well eagerly propagate this if already present
        res.methodAccessor = methodAccessor;
        return res;
    }

    Method leafCopy() {
        if (this.root == null)
            throw new IllegalArgumentException("Can only leafCopy a non-root Method");

        Method res = new Method(clazz, name, parameterTypes, returnType,
                exceptionTypes, modifiers, slot, signature,
                annotations, parameterAnnotations, annotationDefault);
        res.root = root;
        res.methodAccessor = methodAccessor;
        return res;
    }

    @Override
    @CallerSensitive
    public void setAccessible(boolean flag) {
        AccessibleObject.checkPermission();
        if (flag) checkCanSetAccessible(Reflection.getCallerClass());
        setAccessible0(flag);
    }

    @Override
    void checkCanSetAccessible(Class<?> caller) {
        checkCanSetAccessible(caller, clazz);
    }

    @Override
    Method getRoot() {
        return root;
    }

    @Override
    boolean hasGenericInformation() {
        return (getGenericSignature() != null);
    }

    @Override
    byte[] getAnnotationBytes() {
        return annotations;
    }

    @Override
    public Class<?> getDeclaringClass() {
        return clazz;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public int getModifiers() {
        return modifiers;
    }

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public TypeVariable<Method>[] getTypeParameters() {
        if (getGenericSignature() != null)
            return (TypeVariable<Method>[]) getGenericInfo().getTypeParameters();
        else
            return (TypeVariable<Method>[]) new TypeVariable[0];
    }

    public Class<?> getReturnType() {
        return returnType;
    }

    public Type getGenericReturnType() {
        if (getGenericSignature() != null) {
            return getGenericInfo().getReturnType();
        } else {
            return getReturnType();
        }
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
        if (obj != null && obj instanceof Method) {
            Method other = (Method) obj;
            if ((getDeclaringClass() == other.getDeclaringClass())
                    && (getName() == other.getName())) {
                if (!returnType.equals(other.getReturnType()))
                    return false;
                return equalParamTypes(parameterTypes, other.parameterTypes);
            }
        }
        return false;
    }

    public int hashCode() {
        return getDeclaringClass().getName().hashCode() ^ getName().hashCode();
    }

    public String toString() {
        return sharedToString(Modifier.methodModifiers(),
                isDefault(),
                parameterTypes,
                exceptionTypes);
    }

    @Override
    void specificToStringHeader(StringBuilder sb) {
        sb.append(getReturnType().getTypeName()).append(' ');
        sb.append(getDeclaringClass().getTypeName()).append('.');
        sb.append(getName());
    }

    @Override
    String toShortString() {
        StringBuilder sb = new StringBuilder("method ");
        sb.append(getDeclaringClass().getTypeName()).append('.');
        sb.append(getName());
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
        return sharedToGenericString(Modifier.methodModifiers(), isDefault());
    }

    @Override
    void specificToGenericStringHeader(StringBuilder sb) {
        Type genRetType = getGenericReturnType();
        sb.append(genRetType.getTypeName()).append(' ');
        sb.append(getDeclaringClass().getTypeName()).append('.');
        sb.append(getName());
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    @HotSpotIntrinsicCandidate
    public Object invoke(Object obj, Object... args)
            throws IllegalAccessException, IllegalArgumentException,
            InvocationTargetException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, clazz,
                    Modifier.isStatic(modifiers) ? null : obj.getClass(),
                    modifiers);
        }
        MethodAccessor ma = methodAccessor;             // read volatile
        if (ma == null) {
            ma = acquireMethodAccessor();
        }
        return ma.invoke(obj, args);
    }

    public boolean isBridge() {
        return (getModifiers() & Modifier.BRIDGE) != 0;
    }

    @Override
    public boolean isVarArgs() {
        return super.isVarArgs();
    }

    @Override
    public boolean isSynthetic() {
        return super.isSynthetic();
    }

    public boolean isDefault() {
        // Default methods are public non-abstract instance methods
        // declared in an interface.
        return ((getModifiers() & (Modifier.ABSTRACT | Modifier.PUBLIC | Modifier.STATIC)) ==
                Modifier.PUBLIC) && getDeclaringClass().isInterface();
    }

    private MethodAccessor acquireMethodAccessor() {
        // First check to see if one has been created yet, and take it
        // if so
        MethodAccessor tmp = null;
        if (root != null) tmp = root.getMethodAccessor();
        if (tmp != null) {
            methodAccessor = tmp;
        } else {
            // Otherwise fabricate one and propagate it up to the root
            tmp = reflectionFactory.newMethodAccessor(this);
            setMethodAccessor(tmp);
        }

        return tmp;
    }

    MethodAccessor getMethodAccessor() {
        return methodAccessor;
    }

    void setMethodAccessor(MethodAccessor accessor) {
        methodAccessor = accessor;
        // Propagate up
        if (root != null) {
            root.setMethodAccessor(accessor);
        }
    }

    public Object getDefaultValue() {
        if (annotationDefault == null)
            return null;
        Class<?> memberType = AnnotationType.invocationHandlerReturnType(
                getReturnType());
        Object result = AnnotationParser.parseMemberValue(
                memberType, ByteBuffer.wrap(annotationDefault),
                SharedSecrets.getJavaLangAccess().
                        getConstantPool(getDeclaringClass()),
                getDeclaringClass());
        if (result instanceof ExceptionProxy) {
            if (result instanceof TypeNotPresentExceptionProxy) {
                TypeNotPresentExceptionProxy proxy = (TypeNotPresentExceptionProxy) result;
                throw new TypeNotPresentException(proxy.typeName(), proxy.getCause());
            }
            throw new AnnotationFormatError("Invalid default: " + this);
        }
        return result;
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
    public AnnotatedType getAnnotatedReturnType() {
        return getAnnotatedReturnType0(getGenericReturnType());
    }

    @Override
    boolean handleParameterNumberMismatch(int resultLength, int numParameters) {
        throw new AnnotationFormatError("Parameter annotations don't match number of parameters");
    }
}
