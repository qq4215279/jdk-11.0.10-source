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
import java.util.function.LongBinaryOperator;
import java.util.function.LongUnaryOperator;

import jdk.internal.misc.Unsafe;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

import java.lang.invoke.VarHandle;

public abstract class AtomicLongFieldUpdater<T> {
    @CallerSensitive
    public static <U> AtomicLongFieldUpdater<U> newUpdater(Class<U> tclass, String fieldName) {
        Class<?> caller = Reflection.getCallerClass();
        if (AtomicLong.VM_SUPPORTS_LONG_CAS)
            return new CASUpdater<U>(tclass, fieldName, caller);
        else
            return new LockedUpdater<U>(tclass, fieldName, caller);
    }

    protected AtomicLongFieldUpdater() {
    }

    public abstract boolean compareAndSet(T obj, long expect, long update);

    public abstract boolean weakCompareAndSet(T obj, long expect, long update);

    public abstract void set(T obj, long newValue);

    public abstract void lazySet(T obj, long newValue);

    public abstract long get(T obj);

    public long getAndSet(T obj, long newValue) {
        long prev;
        do {
            prev = get(obj);
        } while (!compareAndSet(obj, prev, newValue));
        return prev;
    }

    public long getAndIncrement(T obj) {
        long prev, next;
        do {
            prev = get(obj);
            next = prev + 1;
        } while (!compareAndSet(obj, prev, next));
        return prev;
    }

    public long getAndDecrement(T obj) {
        long prev, next;
        do {
            prev = get(obj);
            next = prev - 1;
        } while (!compareAndSet(obj, prev, next));
        return prev;
    }

    public long getAndAdd(T obj, long delta) {
        long prev, next;
        do {
            prev = get(obj);
            next = prev + delta;
        } while (!compareAndSet(obj, prev, next));
        return prev;
    }

    public long incrementAndGet(T obj) {
        long prev, next;
        do {
            prev = get(obj);
            next = prev + 1;
        } while (!compareAndSet(obj, prev, next));
        return next;
    }

    public long decrementAndGet(T obj) {
        long prev, next;
        do {
            prev = get(obj);
            next = prev - 1;
        } while (!compareAndSet(obj, prev, next));
        return next;
    }

    public long addAndGet(T obj, long delta) {
        long prev, next;
        do {
            prev = get(obj);
            next = prev + delta;
        } while (!compareAndSet(obj, prev, next));
        return next;
    }

    public final long getAndUpdate(T obj, LongUnaryOperator updateFunction) {
        long prev, next;
        do {
            prev = get(obj);
            next = updateFunction.applyAsLong(prev);
        } while (!compareAndSet(obj, prev, next));
        return prev;
    }

    public final long updateAndGet(T obj, LongUnaryOperator updateFunction) {
        long prev, next;
        do {
            prev = get(obj);
            next = updateFunction.applyAsLong(prev);
        } while (!compareAndSet(obj, prev, next));
        return next;
    }

    public final long getAndAccumulate(T obj, long x, LongBinaryOperator accumulatorFunction) {
        long prev, next;
        do {
            prev = get(obj);
            next = accumulatorFunction.applyAsLong(prev, x);
        } while (!compareAndSet(obj, prev, next));
        return prev;
    }

    public final long accumulateAndGet(T obj, long x, LongBinaryOperator accumulatorFunction) {
        long prev, next;
        do {
            prev = get(obj);
            next = accumulatorFunction.applyAsLong(prev, x);
        } while (!compareAndSet(obj, prev, next));
        return next;
    }

    private static final class CASUpdater<T> extends AtomicLongFieldUpdater<T> {
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

        CASUpdater(final Class<T> tclass, final String fieldName, final Class<?> caller) {
            final Field field;
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
            } catch (PrivilegedActionException pae) {
                throw new RuntimeException(pae.getException());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            if (field.getType() != long.class)
                throw new IllegalArgumentException("Must be long type");

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
            this.offset = U.objectFieldOffset(field);
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

        public final boolean compareAndSet(T obj, long expect, long update) {
            accessCheck(obj);
            return U.compareAndSetLong(obj, offset, expect, update);
        }

        public final boolean weakCompareAndSet(T obj, long expect, long update) {
            accessCheck(obj);
            return U.compareAndSetLong(obj, offset, expect, update);
        }

        public final void set(T obj, long newValue) {
            accessCheck(obj);
            U.putLongVolatile(obj, offset, newValue);
        }

        public final void lazySet(T obj, long newValue) {
            accessCheck(obj);
            U.putLongRelease(obj, offset, newValue);
        }

        public final long get(T obj) {
            accessCheck(obj);
            return U.getLongVolatile(obj, offset);
        }

        public final long getAndSet(T obj, long newValue) {
            accessCheck(obj);
            return U.getAndSetLong(obj, offset, newValue);
        }

        public final long getAndAdd(T obj, long delta) {
            accessCheck(obj);
            return U.getAndAddLong(obj, offset, delta);
        }

        public final long getAndIncrement(T obj) {
            return getAndAdd(obj, 1);
        }

        public final long getAndDecrement(T obj) {
            return getAndAdd(obj, -1);
        }

        public final long incrementAndGet(T obj) {
            return getAndAdd(obj, 1) + 1;
        }

        public final long decrementAndGet(T obj) {
            return getAndAdd(obj, -1) - 1;
        }

        public final long addAndGet(T obj, long delta) {
            return getAndAdd(obj, delta) + delta;
        }
    }

    private static final class LockedUpdater<T> extends AtomicLongFieldUpdater<T> {
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

        LockedUpdater(final Class<T> tclass, final String fieldName, final Class<?> caller) {
            final Field field;
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
            } catch (PrivilegedActionException pae) {
                throw new RuntimeException(pae.getException());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }

            if (field.getType() != long.class)
                throw new IllegalArgumentException("Must be long type");

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
            this.offset = U.objectFieldOffset(field);
        }

        /**
         * Checks that target argument is instance of cclass.  On
         * failure, throws cause.
         */
        private final void accessCheck(T obj) {
            if (!cclass.isInstance(obj))
                throw accessCheckException(obj);
        }

        /**
         * Returns access exception if accessCheck failed due to
         * protected access, else ClassCastException.
         */
        private final RuntimeException accessCheckException(T obj) {
            if (cclass == tclass)
                return new ClassCastException();
            else
                return new RuntimeException(new IllegalAccessException(
                    "Class " + cclass.getName() + " can not access a protected member of class " + tclass.getName() + " using an instance of " +
                    obj.getClass().getName()));
        }

        public final boolean compareAndSet(T obj, long expect, long update) {
            accessCheck(obj);
            synchronized (this) {
                long v = U.getLong(obj, offset);
                if (v != expect)
                    return false;
                U.putLong(obj, offset, update);
                return true;
            }
        }

        public final boolean weakCompareAndSet(T obj, long expect, long update) {
            return compareAndSet(obj, expect, update);
        }

        public final void set(T obj, long newValue) {
            accessCheck(obj);
            synchronized (this) {
                U.putLong(obj, offset, newValue);
            }
        }

        public final void lazySet(T obj, long newValue) {
            set(obj, newValue);
        }

        public final long get(T obj) {
            accessCheck(obj);
            synchronized (this) {
                return U.getLong(obj, offset);
            }
        }
    }

    static boolean isAncestor(ClassLoader first, ClassLoader second) {
        ClassLoader acl = first;
        do {
            acl = acl.getParent();
            if (second == acl) {
                return true;
            }
        } while (acl != null);
        return false;
    }

    static boolean isSamePackage(Class<?> class1, Class<?> class2) {
        return class1.getClassLoader() == class2.getClassLoader() && Objects.equals(class1.getPackageName(), class2.getPackageName());
    }
}
