/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.atomic;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.Objects;
import java.util.function.BinaryOperator;
import java.util.function.UnaryOperator;

import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

import java.lang.invoke.VarHandle;

public abstract class AtomicReferenceFieldUpdater<T, V> {

    @CallerSensitive
    public static <U, W> AtomicReferenceFieldUpdater<U, W> newUpdater(Class<U> tclass, Class<W> vclass, String fieldName) {
        return new AtomicReferenceFieldUpdaterImpl<U, W>(tclass, vclass, fieldName, Reflection.getCallerClass());
    }

    protected AtomicReferenceFieldUpdater() {
    }

    public abstract boolean compareAndSet(T obj, V expect, V update);

    public abstract boolean weakCompareAndSet(T obj, V expect, V update);

    public abstract void set(T obj, V newValue);

    public abstract void lazySet(T obj, V newValue);

    public abstract V get(T obj);

    public V getAndSet(T obj, V newValue) {
        V prev;
        do {
            prev = get(obj);
        } while (!compareAndSet(obj, prev, newValue));
        return prev;
    }

    public final V getAndUpdate(T obj, UnaryOperator<V> updateFunction) {
        V prev, next;
        do {
            prev = get(obj);
            next = updateFunction.apply(prev);
        } while (!compareAndSet(obj, prev, next));
        return prev;
    }

    public final V updateAndGet(T obj, UnaryOperator<V> updateFunction) {
        V prev, next;
        do {
            prev = get(obj);
            next = updateFunction.apply(prev);
        } while (!compareAndSet(obj, prev, next));
        return next;
    }

    public final V getAndAccumulate(T obj, V x, BinaryOperator<V> accumulatorFunction) {
        V prev, next;
        do {
            prev = get(obj);
            next = accumulatorFunction.apply(prev, x);
        } while (!compareAndSet(obj, prev, next));
        return prev;
    }

    public final V accumulateAndGet(T obj, V x, BinaryOperator<V> accumulatorFunction) {
        V prev, next;
        do {
            prev = get(obj);
            next = accumulatorFunction.apply(prev, x);
        } while (!compareAndSet(obj, prev, next));
        return next;
    }

    private static final class AtomicReferenceFieldUpdaterImpl<T, V> extends AtomicReferenceFieldUpdater<T, V> {
        private static final Unsafe U = Unsafe.getUnsafe();
        private final long offset;
        /**
         * if field is protected, the subclass constructing updater, else
         * the same as tclass
         */
        private final Class<?> cclass;
        /**
         * class holding the field
         */
        private final Class<T> tclass;
        /**
         * field value type
         */
        private final Class<V> vclass;

        /*
         * Internal type checks within all update methods contain
         * internal inlined optimizations checking for the common
         * cases where the class is final (in which case a simple
         * getClass comparison suffices) or is of type Object (in
         * which case no check is needed because all objects are
         * instances of Object). The Object case is handled simply by
         * setting vclass to null in constructor.  The targetCheck and
         * updateCheck methods are invoked when these faster
         * screenings fail.
         */

        AtomicReferenceFieldUpdaterImpl(final Class<T> tclass, final Class<V> vclass, final String fieldName, final Class<?> caller) {
            final Field field;
            final Class<?> fieldClass;
            final int modifiers;
            try {
                field = AccessController.doPrivileged(new PrivilegedExceptionAction<Field>() {
                    public Field run() throws NoSuchFieldException {
                        return tclass.getDeclaredField(fieldName);
                    }
                });
                modifiers = field.getModifiers();
                sun.reflect.misc.ReflectUtil.ensureMemberAccess(caller, tclass, null, modifiers);
                ClassLoader cl = tclass.getClassLoader();
                ClassLoader ccl = caller.getClassLoader();
                if ((ccl != null) && (ccl != cl) && ((cl == null) || !isAncestor(cl, ccl))) {
                    sun.reflect.misc.ReflectUtil.checkPackageAccess(tclass);
                }
                fieldClass = field.getType();
            } catch (PrivilegedActionException pae) {
                throw new RuntimeException(pae.getException());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            if (vclass != fieldClass)
                throw new ClassCastException();
            if (vclass.isPrimitive())
                throw new IllegalArgumentException("Must be reference type");

            if (!Modifier.isVolatile(modifiers))
                throw new IllegalArgumentException("Must be volatile type");

            // Access to protected field members is restricted to receivers only
            // of the accessing class, or one of its subclasses, and the
            // accessing class must in turn be a subclass (or package sibling)
            // of the protected member's defining class.
            // If the updater refers to a protected field of a declaring class
            // outside the current package, the receiver argument will be
            // narrowed to the type of the accessing class.
            this.cclass = (Modifier.isProtected(modifiers) && tclass.isAssignableFrom(caller) && !isSamePackage(tclass, caller)) ? caller : tclass;
            this.tclass = tclass;
            this.vclass = vclass;
            this.offset = U.objectFieldOffset(field);
        }

        /**
         * Returns true if the second classloader can be found in the first
         * classloader's delegation chain.
         * Equivalent to the inaccessible: first.isAncestor(second).
         */
        private static boolean isAncestor(ClassLoader first, ClassLoader second) {
            ClassLoader acl = first;
            do {
                acl = acl.getParent();
                if (second == acl) {
                    return true;
                }
            } while (acl != null);
            return false;
        }

        /**
         * Returns true if the two classes have the same class loader and
         * package qualifier
         */
        private static boolean isSamePackage(Class<?> class1, Class<?> class2) {
            return class1.getClassLoader() == class2.getClassLoader() && Objects.equals(class1.getPackageName(), class2.getPackageName());
        }

        /**
         * Checks that target argument is instance of cclass.  On
         * failure, throws cause.
         */
        private final void accessCheck(T obj) {
            if (!cclass.isInstance(obj))
                throwAccessCheckException(obj);
        }

        /**
         * Throws access exception if accessCheck failed due to
         * protected access, else ClassCastException.
         */
        private final void throwAccessCheckException(T obj) {
            if (cclass == tclass)
                throw new ClassCastException();
            else
                throw new RuntimeException(new IllegalAccessException(
                    "Class " + cclass.getName() + " can not access a protected member of class " + tclass.getName() + " using an instance of " +
                    obj.getClass().getName()));
        }

        private final void valueCheck(V v) {
            if (v != null && !(vclass.isInstance(v)))
                throwCCE();
        }

        static void throwCCE() {
            throw new ClassCastException();
        }

        public final boolean compareAndSet(T obj, V expect, V update) {
            accessCheck(obj);
            valueCheck(update);
            return U.compareAndSetObject(obj, offset, expect, update);
        }

        public final boolean weakCompareAndSet(T obj, V expect, V update) {
            // same implementation as strong form for now
            accessCheck(obj);
            valueCheck(update);
            return U.compareAndSetObject(obj, offset, expect, update);
        }

        public final void set(T obj, V newValue) {
            accessCheck(obj);
            valueCheck(newValue);
            U.putObjectVolatile(obj, offset, newValue);
        }

        public final void lazySet(T obj, V newValue) {
            accessCheck(obj);
            valueCheck(newValue);
            U.putObjectRelease(obj, offset, newValue);
        }

        @SuppressWarnings("unchecked")
        public final V get(T obj) {
            accessCheck(obj);
            return (V)U.getObjectVolatile(obj, offset);
        }

        @SuppressWarnings("unchecked")
        public final V getAndSet(T obj, V newValue) {
            accessCheck(obj);
            valueCheck(newValue);
            return (V)U.getAndSetObject(obj, offset, newValue);
        }
    }
}
