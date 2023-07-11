/*
 * Copyright (c) 1996, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.lang.reflect;

import jdk.internal.access.SharedSecrets;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.FieldAccessor;
import jdk.internal.reflect.Reflection;
import jdk.internal.vm.annotation.ForceInline;
import sun.reflect.generics.repository.FieldRepository;
import sun.reflect.generics.factory.CoreReflectionFactory;
import sun.reflect.generics.factory.GenericsFactory;
import sun.reflect.generics.scope.ClassScope;

import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;

import sun.reflect.annotation.AnnotationParser;
import sun.reflect.annotation.AnnotationSupport;
import sun.reflect.annotation.TypeAnnotation;
import sun.reflect.annotation.TypeAnnotationParser;

/**
 *
 * @date 2023/7/8 14:36
 */
public final class Field extends AccessibleObject implements Member {

    private Class<?> clazz;
    private int slot;
    // This is guaranteed to be interned by the VM in the 1.4
    // reflection implementation
    private String name;
    private Class<?> type;
    private int modifiers;
    // Generics and annotations support
    private transient String signature;
    // generic info repository; lazily initialized
    private transient FieldRepository genericInfo;
    private byte[] annotations;
    // Cached field accessor created without override
    private FieldAccessor fieldAccessor;
    // Cached field accessor created with override
    private FieldAccessor overrideFieldAccessor;
    private Field root;

    // Generics infrastructure

    private String getGenericSignature() {
        return signature;
    }

    // Accessor for factory
    private GenericsFactory getFactory() {
        Class<?> c = getDeclaringClass();
        // create scope and factory
        return CoreReflectionFactory.make(c, ClassScope.make(c));
    }

    // Accessor for generic info repository
    private FieldRepository getGenericInfo() {
        // lazily initialize repository if necessary
        if (genericInfo == null) {
            // create and cache generic info repository
            genericInfo = FieldRepository.make(getGenericSignature(),
                    getFactory());
        }
        return genericInfo; //return cached repository
    }


    Field(Class<?> declaringClass,
          String name,
          Class<?> type,
          int modifiers,
          int slot,
          String signature,
          byte[] annotations) {
        this.clazz = declaringClass;
        this.name = name;
        this.type = type;
        this.modifiers = modifiers;
        this.slot = slot;
        this.signature = signature;
        this.annotations = annotations;
    }

    Field copy() {
        // This routine enables sharing of FieldAccessor objects
        // among Field objects which refer to the same underlying
        // method in the VM. (All of this contortion is only necessary
        // because of the "accessibility" bit in AccessibleObject,
        // which implicitly requires that new java.lang.reflect
        // objects be fabricated for each reflective call on Class
        // objects.)
        if (this.root != null)
            throw new IllegalArgumentException("Can not copy a non-root Field");

        Field res = new Field(clazz, name, type, modifiers, slot, signature, annotations);
        res.root = this;
        // Might as well eagerly propagate this if already present
        res.fieldAccessor = fieldAccessor;
        res.overrideFieldAccessor = overrideFieldAccessor;

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
    public Class<?> getDeclaringClass() {
        return clazz;
    }

    public String getName() {
        return name;
    }

    public int getModifiers() {
        return modifiers;
    }

    public boolean isEnumConstant() {
        return (getModifiers() & Modifier.ENUM) != 0;
    }

    public boolean isSynthetic() {
        return Modifier.isSynthetic(getModifiers());
    }

    public Class<?> getType() {
        return type;
    }

    public Type getGenericType() {
        if (getGenericSignature() != null)
            return getGenericInfo().getGenericType();
        else
            return getType();
    }


    public boolean equals(Object obj) {
        if (obj != null && obj instanceof Field) {
            Field other = (Field) obj;
            return (getDeclaringClass() == other.getDeclaringClass())
                    && (getName() == other.getName())
                    && (getType() == other.getType());
        }
        return false;
    }

    public int hashCode() {
        return getDeclaringClass().getName().hashCode() ^ getName().hashCode();
    }

    public String toString() {
        int mod = getModifiers();
        return (((mod == 0) ? "" : (Modifier.toString(mod) + " "))
                + getType().getTypeName() + " "
                + getDeclaringClass().getTypeName() + "."
                + getName());
    }

    @Override
    String toShortString() {
        return "field " + getDeclaringClass().getTypeName() + "." + getName();
    }

    public String toGenericString() {
        int mod = getModifiers();
        Type fieldType = getGenericType();
        return (((mod == 0) ? "" : (Modifier.toString(mod) + " "))
                + fieldType.getTypeName() + " "
                + getDeclaringClass().getTypeName() + "."
                + getName());
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public Object get(Object obj)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        return getFieldAccessor(obj).get(obj);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public boolean getBoolean(Object obj)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        return getFieldAccessor(obj).getBoolean(obj);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public byte getByte(Object obj)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        return getFieldAccessor(obj).getByte(obj);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public char getChar(Object obj)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        return getFieldAccessor(obj).getChar(obj);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public short getShort(Object obj)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        return getFieldAccessor(obj).getShort(obj);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public int getInt(Object obj)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        return getFieldAccessor(obj).getInt(obj);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public long getLong(Object obj)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        return getFieldAccessor(obj).getLong(obj);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public float getFloat(Object obj)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        return getFieldAccessor(obj).getFloat(obj);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public double getDouble(Object obj)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        return getFieldAccessor(obj).getDouble(obj);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void set(Object obj, Object value)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        getFieldAccessor(obj).set(obj, value);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setBoolean(Object obj, boolean z)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        getFieldAccessor(obj).setBoolean(obj, z);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setByte(Object obj, byte b)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        getFieldAccessor(obj).setByte(obj, b);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setChar(Object obj, char c)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        getFieldAccessor(obj).setChar(obj, c);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setShort(Object obj, short s)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        getFieldAccessor(obj).setShort(obj, s);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setInt(Object obj, int i)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        getFieldAccessor(obj).setInt(obj, i);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setLong(Object obj, long l)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        getFieldAccessor(obj).setLong(obj, l);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setFloat(Object obj, float f)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        getFieldAccessor(obj).setFloat(obj, f);
    }

    @CallerSensitive
    @ForceInline // to ensure Reflection.getCallerClass optimization
    public void setDouble(Object obj, double d)
            throws IllegalArgumentException, IllegalAccessException {
        if (!override) {
            Class<?> caller = Reflection.getCallerClass();
            checkAccess(caller, obj);
        }
        getFieldAccessor(obj).setDouble(obj, d);
    }

    // check access to field
    private void checkAccess(Class<?> caller, Object obj)
            throws IllegalAccessException {
        checkAccess(caller, clazz,
                Modifier.isStatic(modifiers) ? null : obj.getClass(),
                modifiers);
    }

    // security check is done before calling this method
    private FieldAccessor getFieldAccessor(Object obj)
            throws IllegalAccessException {
        boolean ov = override;
        FieldAccessor a = (ov) ? overrideFieldAccessor : fieldAccessor;
        return (a != null) ? a : acquireFieldAccessor(ov);
    }

    private FieldAccessor acquireFieldAccessor(boolean overrideFinalCheck) {
        // First check to see if one has been created yet, and take it
        // if so
        FieldAccessor tmp = null;
        if (root != null) tmp = root.getFieldAccessor(overrideFinalCheck);
        if (tmp != null) {
            if (overrideFinalCheck)
                overrideFieldAccessor = tmp;
            else
                fieldAccessor = tmp;
        } else {
            // Otherwise fabricate one and propagate it up to the root
            tmp = reflectionFactory.newFieldAccessor(this, overrideFinalCheck);
            setFieldAccessor(tmp, overrideFinalCheck);
        }

        return tmp;
    }

    private FieldAccessor getFieldAccessor(boolean overrideFinalCheck) {
        return (overrideFinalCheck) ? overrideFieldAccessor : fieldAccessor;
    }

    private void setFieldAccessor(FieldAccessor accessor, boolean overrideFinalCheck) {
        if (overrideFinalCheck)
            overrideFieldAccessor = accessor;
        else
            fieldAccessor = accessor;
        // Propagate up
        if (root != null) {
            root.setFieldAccessor(accessor, overrideFinalCheck);
        }
    }

    @Override
    Field getRoot() {
        return root;
    }

    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        Objects.requireNonNull(annotationClass);
        return annotationClass.cast(declaredAnnotations().get(annotationClass));
    }

    @Override
    public <T extends Annotation> T[] getAnnotationsByType(Class<T> annotationClass) {
        Objects.requireNonNull(annotationClass);

        return AnnotationSupport.getDirectlyAndIndirectlyPresent(declaredAnnotations(), annotationClass);
    }

    public Annotation[] getDeclaredAnnotations() {
        return AnnotationParser.toArray(declaredAnnotations());
    }

    private transient volatile Map<Class<? extends Annotation>, Annotation> declaredAnnotations;

    private Map<Class<? extends Annotation>, Annotation> declaredAnnotations() {
        Map<Class<? extends Annotation>, Annotation> declAnnos;
        if ((declAnnos = declaredAnnotations) == null) {
            synchronized (this) {
                if ((declAnnos = declaredAnnotations) == null) {
                    Field root = this.root;
                    if (root != null) {
                        declAnnos = root.declaredAnnotations();
                    } else {
                        declAnnos = AnnotationParser.parseAnnotations(
                                annotations,
                                SharedSecrets.getJavaLangAccess()
                                        .getConstantPool(getDeclaringClass()),
                                getDeclaringClass());
                    }
                    declaredAnnotations = declAnnos;
                }
            }
        }
        return declAnnos;
    }

    private native byte[] getTypeAnnotationBytes0();

    public AnnotatedType getAnnotatedType() {
        return TypeAnnotationParser.buildAnnotatedType(getTypeAnnotationBytes0(),
                SharedSecrets.getJavaLangAccess().
                        getConstantPool(getDeclaringClass()),
                this,
                getDeclaringClass(),
                getGenericType(),
                TypeAnnotation.TypeAnnotationTarget.FIELD);
    }
}
