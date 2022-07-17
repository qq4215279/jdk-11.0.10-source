/*
 * Copyright (c) 2000, 2019, Oracle and/or its affiliates. All rights reserved. ORACLE PROPRIETARY/CONFIDENTIAL. Use is
 * subject to license terms.
 */

package jdk.internal.misc;

import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.ref.Cleaner;
import jdk.internal.vm.annotation.ForceInline;
import sun.nio.ch.DirectBuffer;

import java.lang.reflect.Field;
import java.security.ProtectionDomain;

/**
 * 原语
 * 
 * @date 2022/6/28 7:56
 * @return
 */
public final class Unsafe {

    private static native void registerNatives();

    static {
        registerNatives();
    }

    private Unsafe() {}

    private static final Unsafe theUnsafe = new Unsafe();

    public static Unsafe getUnsafe() {
        return theUnsafe;
    }

    /**
     * 唤醒原语
     * 
     * @date 2022/7/13 21:26
     * @param thread
     * @return void
     */
    @HotSpotIntrinsicCandidate
    public native void unpark(Object thread);

    /**
     * 阻塞原语
     * 
     * @date 2022/7/13 21:26
     * @param isAbsolute
     * @param time
     * @return void
     */
    @HotSpotIntrinsicCandidate
    public native void park(boolean isAbsolute, long time);

    /// peek and poke operations
    /// (compilers should optimize these to memory ops)

    // These work on object fields in the Java heap.
    // They will not work on elements of packed arrays.

    @HotSpotIntrinsicCandidate
    public native int getInt(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putInt(Object o, long offset, int x);

    @HotSpotIntrinsicCandidate
    public native Object getObject(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putObject(Object o, long offset, Object x);

    @HotSpotIntrinsicCandidate
    public native boolean getBoolean(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putBoolean(Object o, long offset, boolean x);

    @HotSpotIntrinsicCandidate
    public native byte getByte(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putByte(Object o, long offset, byte x);

    @HotSpotIntrinsicCandidate
    public native short getShort(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putShort(Object o, long offset, short x);

    @HotSpotIntrinsicCandidate
    public native char getChar(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putChar(Object o, long offset, char x);

    @HotSpotIntrinsicCandidate
    public native long getLong(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putLong(Object o, long offset, long x);

    @HotSpotIntrinsicCandidate
    public native float getFloat(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putFloat(Object o, long offset, float x);

    @HotSpotIntrinsicCandidate
    public native double getDouble(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putDouble(Object o, long offset, double x);

    @ForceInline
    public long getAddress(Object o, long offset) {
        if (ADDRESS_SIZE == 4) {
            return Integer.toUnsignedLong(getInt(o, offset));
        } else {
            return getLong(o, offset);
        }
    }

    @ForceInline
    public void putAddress(Object o, long offset, long x) {
        if (ADDRESS_SIZE == 4) {
            putInt(o, offset, (int)x);
        } else {
            putLong(o, offset, x);
        }
    }

    // These read VM internal data.

    public native Object getUncompressedObject(long address);

    // These work on values in the C heap.

    @ForceInline
    public byte getByte(long address) {
        return getByte(null, address);
    }

    @ForceInline
    public void putByte(long address, byte x) {
        putByte(null, address, x);
    }

    @ForceInline
    public short getShort(long address) {
        return getShort(null, address);
    }

    @ForceInline
    public void putShort(long address, short x) {
        putShort(null, address, x);
    }

    @ForceInline
    public char getChar(long address) {
        return getChar(null, address);
    }

    @ForceInline
    public void putChar(long address, char x) {
        putChar(null, address, x);
    }

    @ForceInline
    public int getInt(long address) {
        return getInt(null, address);
    }

    @ForceInline
    public void putInt(long address, int x) {
        putInt(null, address, x);
    }

    @ForceInline
    public long getLong(long address) {
        return getLong(null, address);
    }

    @ForceInline
    public void putLong(long address, long x) {
        putLong(null, address, x);
    }

    @ForceInline
    public float getFloat(long address) {
        return getFloat(null, address);
    }

    @ForceInline
    public void putFloat(long address, float x) {
        putFloat(null, address, x);
    }

    @ForceInline
    public double getDouble(long address) {
        return getDouble(null, address);
    }

    @ForceInline
    public void putDouble(long address, double x) {
        putDouble(null, address, x);
    }

    @ForceInline
    public long getAddress(long address) {
        return getAddress(null, address);
    }

    @ForceInline
    public void putAddress(long address, long x) {
        putAddress(null, address, x);
    }

    /// helper methods for validating various types of objects/values

    private RuntimeException invalidInput() {
        return new IllegalArgumentException();
    }

    private boolean is32BitClean(long value) {
        return value >>> 32 == 0;
    }

    private void checkSize(long size) {
        if (ADDRESS_SIZE == 4) {
            // Note: this will also check for negative sizes
            if (!is32BitClean(size)) {
                throw invalidInput();
            }
        } else if (size < 0) {
            throw invalidInput();
        }
    }

    private void checkNativeAddress(long address) {
        if (ADDRESS_SIZE == 4) {
            // Accept both zero and sign extended pointers. A valid
            // pointer will, after the +1 below, either have produced
            // the value 0x0 or 0x1. Masking off the low bit allows
            // for testing against 0.
            if ((((address >> 32) + 1) & ~1) != 0) {
                throw invalidInput();
            }
        }
    }

    private void checkOffset(Object o, long offset) {
        if (ADDRESS_SIZE == 4) {
            // Note: this will also check for negative offsets
            if (!is32BitClean(offset)) {
                throw invalidInput();
            }
        } else if (offset < 0) {
            throw invalidInput();
        }
    }

    private void checkPointer(Object o, long offset) {
        if (o == null) {
            checkNativeAddress(offset);
        } else {
            checkOffset(o, offset);
        }
    }

    private void checkPrimitiveArray(Class<?> c) {
        Class<?> componentType = c.getComponentType();
        if (componentType == null || !componentType.isPrimitive()) {
            throw invalidInput();
        }
    }

    private void checkPrimitivePointer(Object o, long offset) {
        checkPointer(o, offset);

        if (o != null) {
            // If on heap, it must be a primitive array
            checkPrimitiveArray(o.getClass());
        }
    }

    /// wrappers for malloc, realloc, free:

    public long allocateMemory(long bytes) {
        allocateMemoryChecks(bytes);

        if (bytes == 0) {
            return 0;
        }

        long p = allocateMemory0(bytes);
        if (p == 0) {
            throw new OutOfMemoryError();
        }

        return p;
    }

    private void allocateMemoryChecks(long bytes) {
        checkSize(bytes);
    }

    public long reallocateMemory(long address, long bytes) {
        reallocateMemoryChecks(address, bytes);

        if (bytes == 0) {
            freeMemory(address);
            return 0;
        }

        long p = (address == 0) ? allocateMemory0(bytes) : reallocateMemory0(address, bytes);
        if (p == 0) {
            throw new OutOfMemoryError();
        }

        return p;
    }

    private void reallocateMemoryChecks(long address, long bytes) {
        checkPointer(null, address);
        checkSize(bytes);
    }

    public void setMemory(Object o, long offset, long bytes, byte value) {
        setMemoryChecks(o, offset, bytes, value);

        if (bytes == 0) {
            return;
        }

        setMemory0(o, offset, bytes, value);
    }

    public void setMemory(long address, long bytes, byte value) {
        setMemory(null, address, bytes, value);
    }

    private void setMemoryChecks(Object o, long offset, long bytes, byte value) {
        checkPrimitivePointer(o, offset);
        checkSize(bytes);
    }

    public void copyMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        copyMemoryChecks(srcBase, srcOffset, destBase, destOffset, bytes);

        if (bytes == 0) {
            return;
        }

        copyMemory0(srcBase, srcOffset, destBase, destOffset, bytes);
    }

    public void copyMemory(long srcAddress, long destAddress, long bytes) {
        copyMemory(null, srcAddress, null, destAddress, bytes);
    }

    private void copyMemoryChecks(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes) {
        checkSize(bytes);
        checkPrimitivePointer(srcBase, srcOffset);
        checkPrimitivePointer(destBase, destOffset);
    }

    public void copySwapMemory(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes,
        long elemSize) {
        copySwapMemoryChecks(srcBase, srcOffset, destBase, destOffset, bytes, elemSize);

        if (bytes == 0) {
            return;
        }

        copySwapMemory0(srcBase, srcOffset, destBase, destOffset, bytes, elemSize);
    }

    private void copySwapMemoryChecks(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes,
        long elemSize) {
        checkSize(bytes);

        if (elemSize != 2 && elemSize != 4 && elemSize != 8) {
            throw invalidInput();
        }
        if (bytes % elemSize != 0) {
            throw invalidInput();
        }

        checkPrimitivePointer(srcBase, srcOffset);
        checkPrimitivePointer(destBase, destOffset);
    }

    public void copySwapMemory(long srcAddress, long destAddress, long bytes, long elemSize) {
        copySwapMemory(null, srcAddress, null, destAddress, bytes, elemSize);
    }

    public void freeMemory(long address) {
        freeMemoryChecks(address);

        if (address == 0) {
            return;
        }

        freeMemory0(address);
    }

    private void freeMemoryChecks(long address) {
        checkPointer(null, address);
    }

    /// random queries

    public static final int INVALID_FIELD_OFFSET = -1;

    public long objectFieldOffset(Field f) {
        if (f == null) {
            throw new NullPointerException();
        }

        return objectFieldOffset0(f);
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/16 19:26
     * @param c
     * @param name
     * @return long
     */
    public long objectFieldOffset(Class<?> c, String name) {
        if (c == null || name == null) {
            throw new NullPointerException();
        }
        // 所有调用CAS的地方，都会先通过这个方法把成员变量转换成一个Offset。
        return objectFieldOffset1(c, name);
    }

    public long staticFieldOffset(Field f) {
        if (f == null) {
            throw new NullPointerException();
        }

        return staticFieldOffset0(f);
    }

    public Object staticFieldBase(Field f) {
        if (f == null) {
            throw new NullPointerException();
        }

        return staticFieldBase0(f);
    }

    public boolean shouldBeInitialized(Class<?> c) {
        if (c == null) {
            throw new NullPointerException();
        }

        return shouldBeInitialized0(c);
    }

    public void ensureClassInitialized(Class<?> c) {
        if (c == null) {
            throw new NullPointerException();
        }

        ensureClassInitialized0(c);
    }

    public int arrayBaseOffset(Class<?> arrayClass) {
        if (arrayClass == null) {
            throw new NullPointerException();
        }

        return arrayBaseOffset0(arrayClass);
    }

    public static final int ARRAY_BOOLEAN_BASE_OFFSET = theUnsafe.arrayBaseOffset(boolean[].class);

    public static final int ARRAY_BYTE_BASE_OFFSET = theUnsafe.arrayBaseOffset(byte[].class);

    public static final int ARRAY_SHORT_BASE_OFFSET = theUnsafe.arrayBaseOffset(short[].class);

    public static final int ARRAY_CHAR_BASE_OFFSET = theUnsafe.arrayBaseOffset(char[].class);

    public static final int ARRAY_INT_BASE_OFFSET = theUnsafe.arrayBaseOffset(int[].class);

    public static final int ARRAY_LONG_BASE_OFFSET = theUnsafe.arrayBaseOffset(long[].class);

    public static final int ARRAY_FLOAT_BASE_OFFSET = theUnsafe.arrayBaseOffset(float[].class);

    public static final int ARRAY_DOUBLE_BASE_OFFSET = theUnsafe.arrayBaseOffset(double[].class);

    public static final int ARRAY_OBJECT_BASE_OFFSET = theUnsafe.arrayBaseOffset(Object[].class);

    public int arrayIndexScale(Class<?> arrayClass) {
        if (arrayClass == null) {
            throw new NullPointerException();
        }

        return arrayIndexScale0(arrayClass);
    }

    public static final int ARRAY_BOOLEAN_INDEX_SCALE = theUnsafe.arrayIndexScale(boolean[].class);

    public static final int ARRAY_BYTE_INDEX_SCALE = theUnsafe.arrayIndexScale(byte[].class);

    public static final int ARRAY_SHORT_INDEX_SCALE = theUnsafe.arrayIndexScale(short[].class);

    public static final int ARRAY_CHAR_INDEX_SCALE = theUnsafe.arrayIndexScale(char[].class);

    public static final int ARRAY_INT_INDEX_SCALE = theUnsafe.arrayIndexScale(int[].class);

    public static final int ARRAY_LONG_INDEX_SCALE = theUnsafe.arrayIndexScale(long[].class);

    public static final int ARRAY_FLOAT_INDEX_SCALE = theUnsafe.arrayIndexScale(float[].class);

    public static final int ARRAY_DOUBLE_INDEX_SCALE = theUnsafe.arrayIndexScale(double[].class);

    public static final int ARRAY_OBJECT_INDEX_SCALE = theUnsafe.arrayIndexScale(Object[].class);

    public int addressSize() {
        return ADDRESS_SIZE;
    }

    public static final int ADDRESS_SIZE = theUnsafe.addressSize0();

    public native int pageSize();

    /// random trusted operations from JNI:

    public Class<?> defineClass(String name, byte[] b, int off, int len, ClassLoader loader,
        ProtectionDomain protectionDomain) {
        if (b == null) {
            throw new NullPointerException();
        }
        if (len < 0) {
            throw new ArrayIndexOutOfBoundsException();
        }

        return defineClass0(name, b, off, len, loader, protectionDomain);
    }

    public native Class<?> defineClass0(String name, byte[] b, int off, int len, ClassLoader loader,
        ProtectionDomain protectionDomain);

    public Class<?> defineAnonymousClass(Class<?> hostClass, byte[] data, Object[] cpPatches) {
        if (hostClass == null || data == null) {
            throw new NullPointerException();
        }
        if (hostClass.isArray() || hostClass.isPrimitive()) {
            throw new IllegalArgumentException();
        }

        return defineAnonymousClass0(hostClass, data, cpPatches);
    }

    @HotSpotIntrinsicCandidate
    public native Object allocateInstance(Class<?> cls) throws InstantiationException;

    public Object allocateUninitializedArray(Class<?> componentType, int length) {
        if (componentType == null) {
            throw new IllegalArgumentException("Component type is null");
        }
        if (!componentType.isPrimitive()) {
            throw new IllegalArgumentException("Component type is not primitive");
        }
        if (length < 0) {
            throw new IllegalArgumentException("Negative length");
        }
        return allocateUninitializedArray0(componentType, length);
    }

    @HotSpotIntrinsicCandidate
    private Object allocateUninitializedArray0(Class<?> componentType, int length) {
        // These fallbacks provide zeroed arrays, but intrinsic is not required to
        // return the zeroed arrays.
        if (componentType == byte.class)
            return new byte[length];
        if (componentType == boolean.class)
            return new boolean[length];
        if (componentType == short.class)
            return new short[length];
        if (componentType == char.class)
            return new char[length];
        if (componentType == int.class)
            return new int[length];
        if (componentType == float.class)
            return new float[length];
        if (componentType == long.class)
            return new long[length];
        if (componentType == double.class)
            return new double[length];
        return null;
    }

    public native void throwException(Throwable ee);

    /**
     *
     * @author liuzhen
     * @date 2022/4/16 19:34
     * @param o 要修改的对象
     * @param offset 对象的成员变量在内存中的位置（一个long型的整数）
     * @param expected 是该变量的旧值
     * @param x 该变量的新值。
     * @return boolean
     */
    @HotSpotIntrinsicCandidate
    public final native boolean compareAndSetObject(Object o, long offset, Object expected, Object x);

    @HotSpotIntrinsicCandidate
    public final native Object compareAndExchangeObject(Object o, long offset, Object expected, Object x);

    @HotSpotIntrinsicCandidate
    public final Object compareAndExchangeObjectAcquire(Object o, long offset, Object expected, Object x) {
        return compareAndExchangeObject(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final Object compareAndExchangeObjectRelease(Object o, long offset, Object expected, Object x) {
        return compareAndExchangeObject(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetObjectPlain(Object o, long offset, Object expected, Object x) {
        return compareAndSetObject(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetObjectAcquire(Object o, long offset, Object expected, Object x) {
        return compareAndSetObject(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetObjectRelease(Object o, long offset, Object expected, Object x) {
        return compareAndSetObject(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetObject(Object o, long offset, Object expected, Object x) {
        return compareAndSetObject(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final native int compareAndExchangeInt(Object o, long offset, int expected, int x);

    @HotSpotIntrinsicCandidate
    public final int compareAndExchangeIntAcquire(Object o, long offset, int expected, int x) {
        return compareAndExchangeInt(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final int compareAndExchangeIntRelease(Object o, long offset, int expected, int x) {
        return compareAndExchangeInt(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetIntPlain(Object o, long offset, int expected, int x) {
        return compareAndSetInt(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetIntAcquire(Object o, long offset, int expected, int x) {
        return compareAndSetInt(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetIntRelease(Object o, long offset, int expected, int x) {
        return compareAndSetInt(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final byte compareAndExchangeByte(Object o, long offset, byte expected, byte x) {
        long wordOffset = offset & ~3;
        int shift = (int)(offset & 3) << 3;
        if (BE) {
            shift = 24 - shift;
        }
        int mask = 0xFF << shift;
        int maskedExpected = (expected & 0xFF) << shift;
        int maskedX = (x & 0xFF) << shift;
        int fullWord;
        do {
            fullWord = getIntVolatile(o, wordOffset);
            if ((fullWord & mask) != maskedExpected)
                return (byte)((fullWord & mask) >> shift);
        } while (!weakCompareAndSetInt(o, wordOffset, fullWord, (fullWord & ~mask) | maskedX));
        return expected;
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/16 19:17
     * @param o
     * @param offset
     * @param expected
     * @param x
     * @return boolean
     */
    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetInt(Object o, long offset, int expected, int x) {
        return compareAndSetInt(o, offset, expected, x);
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/16 19:17
     * @param o 表示要修改哪个对象的属性值；
     * @param offset 该对象属性在内存的偏移量；
     * @param expected 表示期望值；
     * @param x 表示要设置为的目标值。
     * @return boolean
     */
    @HotSpotIntrinsicCandidate
    public final native boolean compareAndSetInt(Object o, long offset, int expected, int x);

    @HotSpotIntrinsicCandidate
    public final boolean compareAndSetByte(Object o, long offset, byte expected, byte x) {
        return compareAndExchangeByte(o, offset, expected, x) == expected;
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetByte(Object o, long offset, byte expected, byte x) {
        return compareAndSetByte(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetByteAcquire(Object o, long offset, byte expected, byte x) {
        return weakCompareAndSetByte(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetByteRelease(Object o, long offset, byte expected, byte x) {
        return weakCompareAndSetByte(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetBytePlain(Object o, long offset, byte expected, byte x) {
        return weakCompareAndSetByte(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final byte compareAndExchangeByteAcquire(Object o, long offset, byte expected, byte x) {
        return compareAndExchangeByte(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final byte compareAndExchangeByteRelease(Object o, long offset, byte expected, byte x) {
        return compareAndExchangeByte(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final short compareAndExchangeShort(Object o, long offset, short expected, short x) {
        if ((offset & 3) == 3) {
            throw new IllegalArgumentException("Update spans the word, not supported");
        }
        long wordOffset = offset & ~3;
        int shift = (int)(offset & 3) << 3;
        if (BE) {
            shift = 16 - shift;
        }
        int mask = 0xFFFF << shift;
        int maskedExpected = (expected & 0xFFFF) << shift;
        int maskedX = (x & 0xFFFF) << shift;
        int fullWord;
        do {
            fullWord = getIntVolatile(o, wordOffset);
            if ((fullWord & mask) != maskedExpected) {
                return (short)((fullWord & mask) >> shift);
            }
        } while (!weakCompareAndSetInt(o, wordOffset, fullWord, (fullWord & ~mask) | maskedX));
        return expected;
    }

    @HotSpotIntrinsicCandidate
    public final boolean compareAndSetShort(Object o, long offset, short expected, short x) {
        return compareAndExchangeShort(o, offset, expected, x) == expected;
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetShort(Object o, long offset, short expected, short x) {
        return compareAndSetShort(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetShortAcquire(Object o, long offset, short expected, short x) {
        return weakCompareAndSetShort(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetShortRelease(Object o, long offset, short expected, short x) {
        return weakCompareAndSetShort(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetShortPlain(Object o, long offset, short expected, short x) {
        return weakCompareAndSetShort(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final short compareAndExchangeShortAcquire(Object o, long offset, short expected, short x) {
        return compareAndExchangeShort(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final short compareAndExchangeShortRelease(Object o, long offset, short expected, short x) {
        return compareAndExchangeShort(o, offset, expected, x);
    }

    @ForceInline
    private char s2c(short s) {
        return (char)s;
    }

    @ForceInline
    private short c2s(char s) {
        return (short)s;
    }

    @ForceInline
    public final boolean compareAndSetChar(Object o, long offset, char expected, char x) {
        return compareAndSetShort(o, offset, c2s(expected), c2s(x));
    }

    @ForceInline
    public final char compareAndExchangeChar(Object o, long offset, char expected, char x) {
        return s2c(compareAndExchangeShort(o, offset, c2s(expected), c2s(x)));
    }

    @ForceInline
    public final char compareAndExchangeCharAcquire(Object o, long offset, char expected, char x) {
        return s2c(compareAndExchangeShortAcquire(o, offset, c2s(expected), c2s(x)));
    }

    @ForceInline
    public final char compareAndExchangeCharRelease(Object o, long offset, char expected, char x) {
        return s2c(compareAndExchangeShortRelease(o, offset, c2s(expected), c2s(x)));
    }

    @ForceInline
    public final boolean weakCompareAndSetChar(Object o, long offset, char expected, char x) {
        return weakCompareAndSetShort(o, offset, c2s(expected), c2s(x));
    }

    @ForceInline
    public final boolean weakCompareAndSetCharAcquire(Object o, long offset, char expected, char x) {
        return weakCompareAndSetShortAcquire(o, offset, c2s(expected), c2s(x));
    }

    @ForceInline
    public final boolean weakCompareAndSetCharRelease(Object o, long offset, char expected, char x) {
        return weakCompareAndSetShortRelease(o, offset, c2s(expected), c2s(x));
    }

    @ForceInline
    public final boolean weakCompareAndSetCharPlain(Object o, long offset, char expected, char x) {
        return weakCompareAndSetShortPlain(o, offset, c2s(expected), c2s(x));
    }

    @ForceInline
    private boolean byte2bool(byte b) {
        return b != 0;
    }

    @ForceInline
    private byte bool2byte(boolean b) {
        return b ? (byte)1 : (byte)0;
    }

    @ForceInline
    public final boolean compareAndSetBoolean(Object o, long offset, boolean expected, boolean x) {
        return compareAndSetByte(o, offset, bool2byte(expected), bool2byte(x));
    }

    @ForceInline
    public final boolean compareAndExchangeBoolean(Object o, long offset, boolean expected, boolean x) {
        return byte2bool(compareAndExchangeByte(o, offset, bool2byte(expected), bool2byte(x)));
    }

    @ForceInline
    public final boolean compareAndExchangeBooleanAcquire(Object o, long offset, boolean expected, boolean x) {
        return byte2bool(compareAndExchangeByteAcquire(o, offset, bool2byte(expected), bool2byte(x)));
    }

    @ForceInline
    public final boolean compareAndExchangeBooleanRelease(Object o, long offset, boolean expected, boolean x) {
        return byte2bool(compareAndExchangeByteRelease(o, offset, bool2byte(expected), bool2byte(x)));
    }

    @ForceInline
    public final boolean weakCompareAndSetBoolean(Object o, long offset, boolean expected, boolean x) {
        return weakCompareAndSetByte(o, offset, bool2byte(expected), bool2byte(x));
    }

    @ForceInline
    public final boolean weakCompareAndSetBooleanAcquire(Object o, long offset, boolean expected, boolean x) {
        return weakCompareAndSetByteAcquire(o, offset, bool2byte(expected), bool2byte(x));
    }

    @ForceInline
    public final boolean weakCompareAndSetBooleanRelease(Object o, long offset, boolean expected, boolean x) {
        return weakCompareAndSetByteRelease(o, offset, bool2byte(expected), bool2byte(x));
    }

    @ForceInline
    public final boolean weakCompareAndSetBooleanPlain(Object o, long offset, boolean expected, boolean x) {
        return weakCompareAndSetBytePlain(o, offset, bool2byte(expected), bool2byte(x));
    }

    @ForceInline
    public final boolean compareAndSetFloat(Object o, long offset, float expected, float x) {
        return compareAndSetInt(o, offset, Float.floatToRawIntBits(expected), Float.floatToRawIntBits(x));
    }

    @ForceInline
    public final float compareAndExchangeFloat(Object o, long offset, float expected, float x) {
        int w = compareAndExchangeInt(o, offset, Float.floatToRawIntBits(expected), Float.floatToRawIntBits(x));
        return Float.intBitsToFloat(w);
    }

    @ForceInline
    public final float compareAndExchangeFloatAcquire(Object o, long offset, float expected, float x) {
        int w = compareAndExchangeIntAcquire(o, offset, Float.floatToRawIntBits(expected), Float.floatToRawIntBits(x));
        return Float.intBitsToFloat(w);
    }

    @ForceInline
    public final float compareAndExchangeFloatRelease(Object o, long offset, float expected, float x) {
        int w = compareAndExchangeIntRelease(o, offset, Float.floatToRawIntBits(expected), Float.floatToRawIntBits(x));
        return Float.intBitsToFloat(w);
    }

    @ForceInline
    public final boolean weakCompareAndSetFloatPlain(Object o, long offset, float expected, float x) {
        return weakCompareAndSetIntPlain(o, offset, Float.floatToRawIntBits(expected), Float.floatToRawIntBits(x));
    }

    @ForceInline
    public final boolean weakCompareAndSetFloatAcquire(Object o, long offset, float expected, float x) {
        return weakCompareAndSetIntAcquire(o, offset, Float.floatToRawIntBits(expected), Float.floatToRawIntBits(x));
    }

    @ForceInline
    public final boolean weakCompareAndSetFloatRelease(Object o, long offset, float expected, float x) {
        return weakCompareAndSetIntRelease(o, offset, Float.floatToRawIntBits(expected), Float.floatToRawIntBits(x));
    }

    @ForceInline
    public final boolean weakCompareAndSetFloat(Object o, long offset, float expected, float x) {
        return weakCompareAndSetInt(o, offset, Float.floatToRawIntBits(expected), Float.floatToRawIntBits(x));
    }

    @ForceInline
    public final boolean compareAndSetDouble(Object o, long offset, double expected, double x) {
        return compareAndSetLong(o, offset, Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(x));
    }

    @ForceInline
    public final double compareAndExchangeDouble(Object o, long offset, double expected, double x) {
        long w = compareAndExchangeLong(o, offset, Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(x));
        return Double.longBitsToDouble(w);
    }

    @ForceInline
    public final double compareAndExchangeDoubleAcquire(Object o, long offset, double expected, double x) {
        long w = compareAndExchangeLongAcquire(o, offset, Double.doubleToRawLongBits(expected),
            Double.doubleToRawLongBits(x));
        return Double.longBitsToDouble(w);
    }

    @ForceInline
    public final double compareAndExchangeDoubleRelease(Object o, long offset, double expected, double x) {
        long w = compareAndExchangeLongRelease(o, offset, Double.doubleToRawLongBits(expected),
            Double.doubleToRawLongBits(x));
        return Double.longBitsToDouble(w);
    }

    @ForceInline
    public final boolean weakCompareAndSetDoublePlain(Object o, long offset, double expected, double x) {
        return weakCompareAndSetLongPlain(o, offset, Double.doubleToRawLongBits(expected),
            Double.doubleToRawLongBits(x));
    }

    @ForceInline
    public final boolean weakCompareAndSetDoubleAcquire(Object o, long offset, double expected, double x) {
        return weakCompareAndSetLongAcquire(o, offset, Double.doubleToRawLongBits(expected),
            Double.doubleToRawLongBits(x));
    }

    @ForceInline
    public final boolean weakCompareAndSetDoubleRelease(Object o, long offset, double expected, double x) {
        return weakCompareAndSetLongRelease(o, offset, Double.doubleToRawLongBits(expected),
            Double.doubleToRawLongBits(x));
    }

    @ForceInline
    public final boolean weakCompareAndSetDouble(Object o, long offset, double expected, double x) {
        return weakCompareAndSetLong(o, offset, Double.doubleToRawLongBits(expected), Double.doubleToRawLongBits(x));
    }

    @HotSpotIntrinsicCandidate
    public final native boolean compareAndSetLong(Object o, long offset, long expected, long x);

    @HotSpotIntrinsicCandidate
    public final native long compareAndExchangeLong(Object o, long offset, long expected, long x);

    @HotSpotIntrinsicCandidate
    public final long compareAndExchangeLongAcquire(Object o, long offset, long expected, long x) {
        return compareAndExchangeLong(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final long compareAndExchangeLongRelease(Object o, long offset, long expected, long x) {
        return compareAndExchangeLong(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetLongPlain(Object o, long offset, long expected, long x) {
        return compareAndSetLong(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetLongAcquire(Object o, long offset, long expected, long x) {
        return compareAndSetLong(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetLongRelease(Object o, long offset, long expected, long x) {
        return compareAndSetLong(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public final boolean weakCompareAndSetLong(Object o, long offset, long expected, long x) {
        return compareAndSetLong(o, offset, expected, x);
    }

    @HotSpotIntrinsicCandidate
    public native Object getObjectVolatile(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putObjectVolatile(Object o, long offset, Object x);

    @HotSpotIntrinsicCandidate
    public native int getIntVolatile(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putIntVolatile(Object o, long offset, int x);

    @HotSpotIntrinsicCandidate
    public native boolean getBooleanVolatile(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putBooleanVolatile(Object o, long offset, boolean x);

    @HotSpotIntrinsicCandidate
    public native byte getByteVolatile(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putByteVolatile(Object o, long offset, byte x);

    @HotSpotIntrinsicCandidate
    public native short getShortVolatile(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putShortVolatile(Object o, long offset, short x);

    @HotSpotIntrinsicCandidate
    public native char getCharVolatile(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putCharVolatile(Object o, long offset, char x);

    @HotSpotIntrinsicCandidate
    public native long getLongVolatile(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putLongVolatile(Object o, long offset, long x);

    @HotSpotIntrinsicCandidate
    public native float getFloatVolatile(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putFloatVolatile(Object o, long offset, float x);

    @HotSpotIntrinsicCandidate
    public native double getDoubleVolatile(Object o, long offset);

    @HotSpotIntrinsicCandidate
    public native void putDoubleVolatile(Object o, long offset, double x);

    @HotSpotIntrinsicCandidate
    public final Object getObjectAcquire(Object o, long offset) {
        return getObjectVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final boolean getBooleanAcquire(Object o, long offset) {
        return getBooleanVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final byte getByteAcquire(Object o, long offset) {
        return getByteVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final short getShortAcquire(Object o, long offset) {
        return getShortVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final char getCharAcquire(Object o, long offset) {
        return getCharVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final int getIntAcquire(Object o, long offset) {
        return getIntVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final float getFloatAcquire(Object o, long offset) {
        return getFloatVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final long getLongAcquire(Object o, long offset) {
        return getLongVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final double getDoubleAcquire(Object o, long offset) {
        return getDoubleVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final void putObjectRelease(Object o, long offset, Object x) {
        putObjectVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putBooleanRelease(Object o, long offset, boolean x) {
        putBooleanVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putByteRelease(Object o, long offset, byte x) {
        putByteVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putShortRelease(Object o, long offset, short x) {
        putShortVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putCharRelease(Object o, long offset, char x) {
        putCharVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putIntRelease(Object o, long offset, int x) {
        putIntVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putFloatRelease(Object o, long offset, float x) {
        putFloatVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putLongRelease(Object o, long offset, long x) {
        putLongVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putDoubleRelease(Object o, long offset, double x) {
        putDoubleVolatile(o, offset, x);
    }

    // ------------------------------ Opaque --------------------------------------

    @HotSpotIntrinsicCandidate
    public final Object getObjectOpaque(Object o, long offset) {
        return getObjectVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final boolean getBooleanOpaque(Object o, long offset) {
        return getBooleanVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final byte getByteOpaque(Object o, long offset) {
        return getByteVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final short getShortOpaque(Object o, long offset) {
        return getShortVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final char getCharOpaque(Object o, long offset) {
        return getCharVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final int getIntOpaque(Object o, long offset) {
        return getIntVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final float getFloatOpaque(Object o, long offset) {
        return getFloatVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final long getLongOpaque(Object o, long offset) {
        return getLongVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final double getDoubleOpaque(Object o, long offset) {
        return getDoubleVolatile(o, offset);
    }

    @HotSpotIntrinsicCandidate
    public final void putObjectOpaque(Object o, long offset, Object x) {
        putObjectVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putBooleanOpaque(Object o, long offset, boolean x) {
        putBooleanVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putByteOpaque(Object o, long offset, byte x) {
        putByteVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putShortOpaque(Object o, long offset, short x) {
        putShortVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putCharOpaque(Object o, long offset, char x) {
        putCharVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putIntOpaque(Object o, long offset, int x) {
        putIntVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putFloatOpaque(Object o, long offset, float x) {
        putFloatVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putLongOpaque(Object o, long offset, long x) {
        putLongVolatile(o, offset, x);
    }

    @HotSpotIntrinsicCandidate
    public final void putDoubleOpaque(Object o, long offset, double x) {
        putDoubleVolatile(o, offset, x);
    }

    public int getLoadAverage(double[] loadavg, int nelems) {
        if (nelems < 0 || nelems > 3 || nelems > loadavg.length) {
            throw new ArrayIndexOutOfBoundsException();
        }

        return getLoadAverage0(loadavg, nelems);
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/16 19:15
     * @param o
     * @param offset
     * @param delta
     * @return int
     */
    @HotSpotIntrinsicCandidate
    public final int getAndAddInt(Object o, long offset, int delta) {
        int v;
        // do-while循环直到判断条件返回true为止。该操作称为自旋。
        do {
            v = getIntVolatile(o, offset);
        } while (!weakCompareAndSetInt(o, offset, v, v + delta));
        return v;
    }

    @ForceInline
    public final int getAndAddIntRelease(Object o, long offset, int delta) {
        int v;
        do {
            v = getInt(o, offset);
        } while (!weakCompareAndSetIntRelease(o, offset, v, v + delta));
        return v;
    }

    @ForceInline
    public final int getAndAddIntAcquire(Object o, long offset, int delta) {
        int v;
        do {
            v = getIntAcquire(o, offset);
        } while (!weakCompareAndSetIntAcquire(o, offset, v, v + delta));
        return v;
    }

    @HotSpotIntrinsicCandidate
    public final long getAndAddLong(Object o, long offset, long delta) {
        long v;
        do {
            v = getLongVolatile(o, offset);
        } while (!weakCompareAndSetLong(o, offset, v, v + delta));
        return v;
    }

    @ForceInline
    public final long getAndAddLongRelease(Object o, long offset, long delta) {
        long v;
        do {
            v = getLong(o, offset);
        } while (!weakCompareAndSetLongRelease(o, offset, v, v + delta));
        return v;
    }

    @ForceInline
    public final long getAndAddLongAcquire(Object o, long offset, long delta) {
        long v;
        do {
            v = getLongAcquire(o, offset);
        } while (!weakCompareAndSetLongAcquire(o, offset, v, v + delta));
        return v;
    }

    @HotSpotIntrinsicCandidate
    public final byte getAndAddByte(Object o, long offset, byte delta) {
        byte v;
        do {
            v = getByteVolatile(o, offset);
        } while (!weakCompareAndSetByte(o, offset, v, (byte)(v + delta)));
        return v;
    }

    @ForceInline
    public final byte getAndAddByteRelease(Object o, long offset, byte delta) {
        byte v;
        do {
            v = getByte(o, offset);
        } while (!weakCompareAndSetByteRelease(o, offset, v, (byte)(v + delta)));
        return v;
    }

    @ForceInline
    public final byte getAndAddByteAcquire(Object o, long offset, byte delta) {
        byte v;
        do {
            v = getByteAcquire(o, offset);
        } while (!weakCompareAndSetByteAcquire(o, offset, v, (byte)(v + delta)));
        return v;
    }

    @HotSpotIntrinsicCandidate
    public final short getAndAddShort(Object o, long offset, short delta) {
        short v;
        do {
            v = getShortVolatile(o, offset);
        } while (!weakCompareAndSetShort(o, offset, v, (short)(v + delta)));
        return v;
    }

    @ForceInline
    public final short getAndAddShortRelease(Object o, long offset, short delta) {
        short v;
        do {
            v = getShort(o, offset);
        } while (!weakCompareAndSetShortRelease(o, offset, v, (short)(v + delta)));
        return v;
    }

    @ForceInline
    public final short getAndAddShortAcquire(Object o, long offset, short delta) {
        short v;
        do {
            v = getShortAcquire(o, offset);
        } while (!weakCompareAndSetShortAcquire(o, offset, v, (short)(v + delta)));
        return v;
    }

    @ForceInline
    public final char getAndAddChar(Object o, long offset, char delta) {
        return (char)getAndAddShort(o, offset, (short)delta);
    }

    @ForceInline
    public final char getAndAddCharRelease(Object o, long offset, char delta) {
        return (char)getAndAddShortRelease(o, offset, (short)delta);
    }

    @ForceInline
    public final char getAndAddCharAcquire(Object o, long offset, char delta) {
        return (char)getAndAddShortAcquire(o, offset, (short)delta);
    }

    @ForceInline
    public final float getAndAddFloat(Object o, long offset, float delta) {
        int expectedBits;
        float v;
        do {
            // Load and CAS with the raw bits to avoid issues with NaNs and
            // possible bit conversion from signaling NaNs to quiet NaNs that
            // may result in the loop not terminating.
            expectedBits = getIntVolatile(o, offset);
            v = Float.intBitsToFloat(expectedBits);
        } while (!weakCompareAndSetInt(o, offset, expectedBits, Float.floatToRawIntBits(v + delta)));
        return v;
    }

    @ForceInline
    public final float getAndAddFloatRelease(Object o, long offset, float delta) {
        int expectedBits;
        float v;
        do {
            // Load and CAS with the raw bits to avoid issues with NaNs and
            // possible bit conversion from signaling NaNs to quiet NaNs that
            // may result in the loop not terminating.
            expectedBits = getInt(o, offset);
            v = Float.intBitsToFloat(expectedBits);
        } while (!weakCompareAndSetIntRelease(o, offset, expectedBits, Float.floatToRawIntBits(v + delta)));
        return v;
    }

    @ForceInline
    public final float getAndAddFloatAcquire(Object o, long offset, float delta) {
        int expectedBits;
        float v;
        do {
            // Load and CAS with the raw bits to avoid issues with NaNs and
            // possible bit conversion from signaling NaNs to quiet NaNs that
            // may result in the loop not terminating.
            expectedBits = getIntAcquire(o, offset);
            v = Float.intBitsToFloat(expectedBits);
        } while (!weakCompareAndSetIntAcquire(o, offset, expectedBits, Float.floatToRawIntBits(v + delta)));
        return v;
    }

    @ForceInline
    public final double getAndAddDouble(Object o, long offset, double delta) {
        long expectedBits;
        double v;
        do {
            // Load and CAS with the raw bits to avoid issues with NaNs and
            // possible bit conversion from signaling NaNs to quiet NaNs that
            // may result in the loop not terminating.
            expectedBits = getLongVolatile(o, offset);
            v = Double.longBitsToDouble(expectedBits);
        } while (!weakCompareAndSetLong(o, offset, expectedBits, Double.doubleToRawLongBits(v + delta)));
        return v;
    }

    @ForceInline
    public final double getAndAddDoubleRelease(Object o, long offset, double delta) {
        long expectedBits;
        double v;
        do {
            // Load and CAS with the raw bits to avoid issues with NaNs and
            // possible bit conversion from signaling NaNs to quiet NaNs that
            // may result in the loop not terminating.
            expectedBits = getLong(o, offset);
            v = Double.longBitsToDouble(expectedBits);
        } while (!weakCompareAndSetLongRelease(o, offset, expectedBits, Double.doubleToRawLongBits(v + delta)));
        return v;
    }

    @ForceInline
    public final double getAndAddDoubleAcquire(Object o, long offset, double delta) {
        long expectedBits;
        double v;
        do {
            // Load and CAS with the raw bits to avoid issues with NaNs and
            // possible bit conversion from signaling NaNs to quiet NaNs that
            // may result in the loop not terminating.
            expectedBits = getLongAcquire(o, offset);
            v = Double.longBitsToDouble(expectedBits);
        } while (!weakCompareAndSetLongAcquire(o, offset, expectedBits, Double.doubleToRawLongBits(v + delta)));
        return v;
    }

    @HotSpotIntrinsicCandidate
    public final int getAndSetInt(Object o, long offset, int newValue) {
        int v;
        do {
            v = getIntVolatile(o, offset);
        } while (!weakCompareAndSetInt(o, offset, v, newValue));
        return v;
    }

    @ForceInline
    public final int getAndSetIntRelease(Object o, long offset, int newValue) {
        int v;
        do {
            v = getInt(o, offset);
        } while (!weakCompareAndSetIntRelease(o, offset, v, newValue));
        return v;
    }

    @ForceInline
    public final int getAndSetIntAcquire(Object o, long offset, int newValue) {
        int v;
        do {
            v = getIntAcquire(o, offset);
        } while (!weakCompareAndSetIntAcquire(o, offset, v, newValue));
        return v;
    }

    @HotSpotIntrinsicCandidate
    public final long getAndSetLong(Object o, long offset, long newValue) {
        long v;
        do {
            v = getLongVolatile(o, offset);
        } while (!weakCompareAndSetLong(o, offset, v, newValue));
        return v;
    }

    @ForceInline
    public final long getAndSetLongRelease(Object o, long offset, long newValue) {
        long v;
        do {
            v = getLong(o, offset);
        } while (!weakCompareAndSetLongRelease(o, offset, v, newValue));
        return v;
    }

    @ForceInline
    public final long getAndSetLongAcquire(Object o, long offset, long newValue) {
        long v;
        do {
            v = getLongAcquire(o, offset);
        } while (!weakCompareAndSetLongAcquire(o, offset, v, newValue));
        return v;
    }

    @HotSpotIntrinsicCandidate
    public final Object getAndSetObject(Object o, long offset, Object newValue) {
        Object v;
        do {
            v = getObjectVolatile(o, offset);
        } while (!weakCompareAndSetObject(o, offset, v, newValue));
        return v;
    }

    @ForceInline
    public final Object getAndSetObjectRelease(Object o, long offset, Object newValue) {
        Object v;
        do {
            v = getObject(o, offset);
        } while (!weakCompareAndSetObjectRelease(o, offset, v, newValue));
        return v;
    }

    @ForceInline
    public final Object getAndSetObjectAcquire(Object o, long offset, Object newValue) {
        Object v;
        do {
            v = getObjectAcquire(o, offset);
        } while (!weakCompareAndSetObjectAcquire(o, offset, v, newValue));
        return v;
    }

    @HotSpotIntrinsicCandidate
    public final byte getAndSetByte(Object o, long offset, byte newValue) {
        byte v;
        do {
            v = getByteVolatile(o, offset);
        } while (!weakCompareAndSetByte(o, offset, v, newValue));
        return v;
    }

    @ForceInline
    public final byte getAndSetByteRelease(Object o, long offset, byte newValue) {
        byte v;
        do {
            v = getByte(o, offset);
        } while (!weakCompareAndSetByteRelease(o, offset, v, newValue));
        return v;
    }

    @ForceInline
    public final byte getAndSetByteAcquire(Object o, long offset, byte newValue) {
        byte v;
        do {
            v = getByteAcquire(o, offset);
        } while (!weakCompareAndSetByteAcquire(o, offset, v, newValue));
        return v;
    }

    @ForceInline
    public final boolean getAndSetBoolean(Object o, long offset, boolean newValue) {
        return byte2bool(getAndSetByte(o, offset, bool2byte(newValue)));
    }

    @ForceInline
    public final boolean getAndSetBooleanRelease(Object o, long offset, boolean newValue) {
        return byte2bool(getAndSetByteRelease(o, offset, bool2byte(newValue)));
    }

    @ForceInline
    public final boolean getAndSetBooleanAcquire(Object o, long offset, boolean newValue) {
        return byte2bool(getAndSetByteAcquire(o, offset, bool2byte(newValue)));
    }

    @HotSpotIntrinsicCandidate
    public final short getAndSetShort(Object o, long offset, short newValue) {
        short v;
        do {
            v = getShortVolatile(o, offset);
        } while (!weakCompareAndSetShort(o, offset, v, newValue));
        return v;
    }

    @ForceInline
    public final short getAndSetShortRelease(Object o, long offset, short newValue) {
        short v;
        do {
            v = getShort(o, offset);
        } while (!weakCompareAndSetShortRelease(o, offset, v, newValue));
        return v;
    }

    @ForceInline
    public final short getAndSetShortAcquire(Object o, long offset, short newValue) {
        short v;
        do {
            v = getShortAcquire(o, offset);
        } while (!weakCompareAndSetShortAcquire(o, offset, v, newValue));
        return v;
    }

    @ForceInline
    public final char getAndSetChar(Object o, long offset, char newValue) {
        return s2c(getAndSetShort(o, offset, c2s(newValue)));
    }

    @ForceInline
    public final char getAndSetCharRelease(Object o, long offset, char newValue) {
        return s2c(getAndSetShortRelease(o, offset, c2s(newValue)));
    }

    @ForceInline
    public final char getAndSetCharAcquire(Object o, long offset, char newValue) {
        return s2c(getAndSetShortAcquire(o, offset, c2s(newValue)));
    }

    @ForceInline
    public final float getAndSetFloat(Object o, long offset, float newValue) {
        int v = getAndSetInt(o, offset, Float.floatToRawIntBits(newValue));
        return Float.intBitsToFloat(v);
    }

    @ForceInline
    public final float getAndSetFloatRelease(Object o, long offset, float newValue) {
        int v = getAndSetIntRelease(o, offset, Float.floatToRawIntBits(newValue));
        return Float.intBitsToFloat(v);
    }

    @ForceInline
    public final float getAndSetFloatAcquire(Object o, long offset, float newValue) {
        int v = getAndSetIntAcquire(o, offset, Float.floatToRawIntBits(newValue));
        return Float.intBitsToFloat(v);
    }

    @ForceInline
    public final double getAndSetDouble(Object o, long offset, double newValue) {
        long v = getAndSetLong(o, offset, Double.doubleToRawLongBits(newValue));
        return Double.longBitsToDouble(v);
    }

    @ForceInline
    public final double getAndSetDoubleRelease(Object o, long offset, double newValue) {
        long v = getAndSetLongRelease(o, offset, Double.doubleToRawLongBits(newValue));
        return Double.longBitsToDouble(v);
    }

    @ForceInline
    public final double getAndSetDoubleAcquire(Object o, long offset, double newValue) {
        long v = getAndSetLongAcquire(o, offset, Double.doubleToRawLongBits(newValue));
        return Double.longBitsToDouble(v);
    }

    // The following contain CAS-based Java implementations used on
    // platforms not supporting native instructions

    @ForceInline
    public final boolean getAndBitwiseOrBoolean(Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseOrByte(o, offset, bool2byte(mask)));
    }

    @ForceInline
    public final boolean getAndBitwiseOrBooleanRelease(Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseOrByteRelease(o, offset, bool2byte(mask)));
    }

    @ForceInline
    public final boolean getAndBitwiseOrBooleanAcquire(Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseOrByteAcquire(o, offset, bool2byte(mask)));
    }

    @ForceInline
    public final boolean getAndBitwiseAndBoolean(Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseAndByte(o, offset, bool2byte(mask)));
    }

    @ForceInline
    public final boolean getAndBitwiseAndBooleanRelease(Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseAndByteRelease(o, offset, bool2byte(mask)));
    }

    @ForceInline
    public final boolean getAndBitwiseAndBooleanAcquire(Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseAndByteAcquire(o, offset, bool2byte(mask)));
    }

    @ForceInline
    public final boolean getAndBitwiseXorBoolean(Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseXorByte(o, offset, bool2byte(mask)));
    }

    @ForceInline
    public final boolean getAndBitwiseXorBooleanRelease(Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseXorByteRelease(o, offset, bool2byte(mask)));
    }

    @ForceInline
    public final boolean getAndBitwiseXorBooleanAcquire(Object o, long offset, boolean mask) {
        return byte2bool(getAndBitwiseXorByteAcquire(o, offset, bool2byte(mask)));
    }

    @ForceInline
    public final byte getAndBitwiseOrByte(Object o, long offset, byte mask) {
        byte current;
        do {
            current = getByteVolatile(o, offset);
        } while (!weakCompareAndSetByte(o, offset, current, (byte)(current | mask)));
        return current;
    }

    @ForceInline
    public final byte getAndBitwiseOrByteRelease(Object o, long offset, byte mask) {
        byte current;
        do {
            current = getByte(o, offset);
        } while (!weakCompareAndSetByteRelease(o, offset, current, (byte)(current | mask)));
        return current;
    }

    @ForceInline
    public final byte getAndBitwiseOrByteAcquire(Object o, long offset, byte mask) {
        byte current;
        do {
            // Plain read, the value is a hint, the acquire CAS does the work
            current = getByte(o, offset);
        } while (!weakCompareAndSetByteAcquire(o, offset, current, (byte)(current | mask)));
        return current;
    }

    @ForceInline
    public final byte getAndBitwiseAndByte(Object o, long offset, byte mask) {
        byte current;
        do {
            current = getByteVolatile(o, offset);
        } while (!weakCompareAndSetByte(o, offset, current, (byte)(current & mask)));
        return current;
    }

    @ForceInline
    public final byte getAndBitwiseAndByteRelease(Object o, long offset, byte mask) {
        byte current;
        do {
            current = getByte(o, offset);
        } while (!weakCompareAndSetByteRelease(o, offset, current, (byte)(current & mask)));
        return current;
    }

    @ForceInline
    public final byte getAndBitwiseAndByteAcquire(Object o, long offset, byte mask) {
        byte current;
        do {
            // Plain read, the value is a hint, the acquire CAS does the work
            current = getByte(o, offset);
        } while (!weakCompareAndSetByteAcquire(o, offset, current, (byte)(current & mask)));
        return current;
    }

    @ForceInline
    public final byte getAndBitwiseXorByte(Object o, long offset, byte mask) {
        byte current;
        do {
            current = getByteVolatile(o, offset);
        } while (!weakCompareAndSetByte(o, offset, current, (byte)(current ^ mask)));
        return current;
    }

    @ForceInline
    public final byte getAndBitwiseXorByteRelease(Object o, long offset, byte mask) {
        byte current;
        do {
            current = getByte(o, offset);
        } while (!weakCompareAndSetByteRelease(o, offset, current, (byte)(current ^ mask)));
        return current;
    }

    @ForceInline
    public final byte getAndBitwiseXorByteAcquire(Object o, long offset, byte mask) {
        byte current;
        do {
            // Plain read, the value is a hint, the acquire CAS does the work
            current = getByte(o, offset);
        } while (!weakCompareAndSetByteAcquire(o, offset, current, (byte)(current ^ mask)));
        return current;
    }

    @ForceInline
    public final char getAndBitwiseOrChar(Object o, long offset, char mask) {
        return s2c(getAndBitwiseOrShort(o, offset, c2s(mask)));
    }

    @ForceInline
    public final char getAndBitwiseOrCharRelease(Object o, long offset, char mask) {
        return s2c(getAndBitwiseOrShortRelease(o, offset, c2s(mask)));
    }

    @ForceInline
    public final char getAndBitwiseOrCharAcquire(Object o, long offset, char mask) {
        return s2c(getAndBitwiseOrShortAcquire(o, offset, c2s(mask)));
    }

    @ForceInline
    public final char getAndBitwiseAndChar(Object o, long offset, char mask) {
        return s2c(getAndBitwiseAndShort(o, offset, c2s(mask)));
    }

    @ForceInline
    public final char getAndBitwiseAndCharRelease(Object o, long offset, char mask) {
        return s2c(getAndBitwiseAndShortRelease(o, offset, c2s(mask)));
    }

    @ForceInline
    public final char getAndBitwiseAndCharAcquire(Object o, long offset, char mask) {
        return s2c(getAndBitwiseAndShortAcquire(o, offset, c2s(mask)));
    }

    @ForceInline
    public final char getAndBitwiseXorChar(Object o, long offset, char mask) {
        return s2c(getAndBitwiseXorShort(o, offset, c2s(mask)));
    }

    @ForceInline
    public final char getAndBitwiseXorCharRelease(Object o, long offset, char mask) {
        return s2c(getAndBitwiseXorShortRelease(o, offset, c2s(mask)));
    }

    @ForceInline
    public final char getAndBitwiseXorCharAcquire(Object o, long offset, char mask) {
        return s2c(getAndBitwiseXorShortAcquire(o, offset, c2s(mask)));
    }

    @ForceInline
    public final short getAndBitwiseOrShort(Object o, long offset, short mask) {
        short current;
        do {
            current = getShortVolatile(o, offset);
        } while (!weakCompareAndSetShort(o, offset, current, (short)(current | mask)));
        return current;
    }

    @ForceInline
    public final short getAndBitwiseOrShortRelease(Object o, long offset, short mask) {
        short current;
        do {
            current = getShort(o, offset);
        } while (!weakCompareAndSetShortRelease(o, offset, current, (short)(current | mask)));
        return current;
    }

    @ForceInline
    public final short getAndBitwiseOrShortAcquire(Object o, long offset, short mask) {
        short current;
        do {
            // Plain read, the value is a hint, the acquire CAS does the work
            current = getShort(o, offset);
        } while (!weakCompareAndSetShortAcquire(o, offset, current, (short)(current | mask)));
        return current;
    }

    @ForceInline
    public final short getAndBitwiseAndShort(Object o, long offset, short mask) {
        short current;
        do {
            current = getShortVolatile(o, offset);
        } while (!weakCompareAndSetShort(o, offset, current, (short)(current & mask)));
        return current;
    }

    @ForceInline
    public final short getAndBitwiseAndShortRelease(Object o, long offset, short mask) {
        short current;
        do {
            current = getShort(o, offset);
        } while (!weakCompareAndSetShortRelease(o, offset, current, (short)(current & mask)));
        return current;
    }

    @ForceInline
    public final short getAndBitwiseAndShortAcquire(Object o, long offset, short mask) {
        short current;
        do {
            // Plain read, the value is a hint, the acquire CAS does the work
            current = getShort(o, offset);
        } while (!weakCompareAndSetShortAcquire(o, offset, current, (short)(current & mask)));
        return current;
    }

    @ForceInline
    public final short getAndBitwiseXorShort(Object o, long offset, short mask) {
        short current;
        do {
            current = getShortVolatile(o, offset);
        } while (!weakCompareAndSetShort(o, offset, current, (short)(current ^ mask)));
        return current;
    }

    @ForceInline
    public final short getAndBitwiseXorShortRelease(Object o, long offset, short mask) {
        short current;
        do {
            current = getShort(o, offset);
        } while (!weakCompareAndSetShortRelease(o, offset, current, (short)(current ^ mask)));
        return current;
    }

    @ForceInline
    public final short getAndBitwiseXorShortAcquire(Object o, long offset, short mask) {
        short current;
        do {
            // Plain read, the value is a hint, the acquire CAS does the work
            current = getShort(o, offset);
        } while (!weakCompareAndSetShortAcquire(o, offset, current, (short)(current ^ mask)));
        return current;
    }

    @ForceInline
    public final int getAndBitwiseOrInt(Object o, long offset, int mask) {
        int current;
        do {
            current = getIntVolatile(o, offset);
        } while (!weakCompareAndSetInt(o, offset, current, current | mask));
        return current;
    }

    @ForceInline
    public final int getAndBitwiseOrIntRelease(Object o, long offset, int mask) {
        int current;
        do {
            current = getInt(o, offset);
        } while (!weakCompareAndSetIntRelease(o, offset, current, current | mask));
        return current;
    }

    @ForceInline
    public final int getAndBitwiseOrIntAcquire(Object o, long offset, int mask) {
        int current;
        do {
            // Plain read, the value is a hint, the acquire CAS does the work
            current = getInt(o, offset);
        } while (!weakCompareAndSetIntAcquire(o, offset, current, current | mask));
        return current;
    }

    @ForceInline
    public final int getAndBitwiseAndInt(Object o, long offset, int mask) {
        int current;
        do {
            current = getIntVolatile(o, offset);
        } while (!weakCompareAndSetInt(o, offset, current, current & mask));
        return current;
    }

    @ForceInline
    public final int getAndBitwiseAndIntRelease(Object o, long offset, int mask) {
        int current;
        do {
            current = getInt(o, offset);
        } while (!weakCompareAndSetIntRelease(o, offset, current, current & mask));
        return current;
    }

    @ForceInline
    public final int getAndBitwiseAndIntAcquire(Object o, long offset, int mask) {
        int current;
        do {
            // Plain read, the value is a hint, the acquire CAS does the work
            current = getInt(o, offset);
        } while (!weakCompareAndSetIntAcquire(o, offset, current, current & mask));
        return current;
    }

    @ForceInline
    public final int getAndBitwiseXorInt(Object o, long offset, int mask) {
        int current;
        do {
            current = getIntVolatile(o, offset);
        } while (!weakCompareAndSetInt(o, offset, current, current ^ mask));
        return current;
    }

    @ForceInline
    public final int getAndBitwiseXorIntRelease(Object o, long offset, int mask) {
        int current;
        do {
            current = getInt(o, offset);
        } while (!weakCompareAndSetIntRelease(o, offset, current, current ^ mask));
        return current;
    }

    @ForceInline
    public final int getAndBitwiseXorIntAcquire(Object o, long offset, int mask) {
        int current;
        do {
            // Plain read, the value is a hint, the acquire CAS does the work
            current = getInt(o, offset);
        } while (!weakCompareAndSetIntAcquire(o, offset, current, current ^ mask));
        return current;
    }

    @ForceInline
    public final long getAndBitwiseOrLong(Object o, long offset, long mask) {
        long current;
        do {
            current = getLongVolatile(o, offset);
        } while (!weakCompareAndSetLong(o, offset, current, current | mask));
        return current;
    }

    @ForceInline
    public final long getAndBitwiseOrLongRelease(Object o, long offset, long mask) {
        long current;
        do {
            current = getLong(o, offset);
        } while (!weakCompareAndSetLongRelease(o, offset, current, current | mask));
        return current;
    }

    @ForceInline
    public final long getAndBitwiseOrLongAcquire(Object o, long offset, long mask) {
        long current;
        do {
            // Plain read, the value is a hint, the acquire CAS does the work
            current = getLong(o, offset);
        } while (!weakCompareAndSetLongAcquire(o, offset, current, current | mask));
        return current;
    }

    @ForceInline
    public final long getAndBitwiseAndLong(Object o, long offset, long mask) {
        long current;
        do {
            current = getLongVolatile(o, offset);
        } while (!weakCompareAndSetLong(o, offset, current, current & mask));
        return current;
    }

    @ForceInline
    public final long getAndBitwiseAndLongRelease(Object o, long offset, long mask) {
        long current;
        do {
            current = getLong(o, offset);
        } while (!weakCompareAndSetLongRelease(o, offset, current, current & mask));
        return current;
    }

    @ForceInline
    public final long getAndBitwiseAndLongAcquire(Object o, long offset, long mask) {
        long current;
        do {
            // Plain read, the value is a hint, the acquire CAS does the work
            current = getLong(o, offset);
        } while (!weakCompareAndSetLongAcquire(o, offset, current, current & mask));
        return current;
    }

    @ForceInline
    public final long getAndBitwiseXorLong(Object o, long offset, long mask) {
        long current;
        do {
            current = getLongVolatile(o, offset);
        } while (!weakCompareAndSetLong(o, offset, current, current ^ mask));
        return current;
    }

    @ForceInline
    public final long getAndBitwiseXorLongRelease(Object o, long offset, long mask) {
        long current;
        do {
            current = getLong(o, offset);
        } while (!weakCompareAndSetLongRelease(o, offset, current, current ^ mask));
        return current;
    }

    @ForceInline
    public final long getAndBitwiseXorLongAcquire(Object o, long offset, long mask) {
        long current;
        do {
            // Plain read, the value is a hint, the acquire CAS does the work
            current = getLong(o, offset);
        } while (!weakCompareAndSetLongAcquire(o, offset, current, current ^ mask));
        return current;
    }

    @HotSpotIntrinsicCandidate
    public native void loadFence();

    @HotSpotIntrinsicCandidate
    public native void storeFence();

    @HotSpotIntrinsicCandidate
    public native void fullFence();

    public final void loadLoadFence() {
        loadFence();
    }

    public final void storeStoreFence() {
        storeFence();
    }

    private static void throwIllegalAccessError() {
        throw new IllegalAccessError();
    }

    private static void throwNoSuchMethodError() {
        throw new NoSuchMethodError();
    }

    public final boolean isBigEndian() {
        return BE;
    }

    public final boolean unalignedAccess() {
        return unalignedAccess;
    }

    @HotSpotIntrinsicCandidate
    public final long getLongUnaligned(Object o, long offset) {
        if ((offset & 7) == 0) {
            return getLong(o, offset);
        } else if ((offset & 3) == 0) {
            return makeLong(getInt(o, offset), getInt(o, offset + 4));
        } else if ((offset & 1) == 0) {
            return makeLong(getShort(o, offset), getShort(o, offset + 2), getShort(o, offset + 4),
                getShort(o, offset + 6));
        } else {
            return makeLong(getByte(o, offset), getByte(o, offset + 1), getByte(o, offset + 2), getByte(o, offset + 3),
                getByte(o, offset + 4), getByte(o, offset + 5), getByte(o, offset + 6), getByte(o, offset + 7));
        }
    }

    public final long getLongUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getLongUnaligned(o, offset));
    }

    @HotSpotIntrinsicCandidate
    public final int getIntUnaligned(Object o, long offset) {
        if ((offset & 3) == 0) {
            return getInt(o, offset);
        } else if ((offset & 1) == 0) {
            return makeInt(getShort(o, offset), getShort(o, offset + 2));
        } else {
            return makeInt(getByte(o, offset), getByte(o, offset + 1), getByte(o, offset + 2), getByte(o, offset + 3));
        }
    }

    public final int getIntUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getIntUnaligned(o, offset));
    }

    @HotSpotIntrinsicCandidate
    public final short getShortUnaligned(Object o, long offset) {
        if ((offset & 1) == 0) {
            return getShort(o, offset);
        } else {
            return makeShort(getByte(o, offset), getByte(o, offset + 1));
        }
    }

    public final short getShortUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getShortUnaligned(o, offset));
    }

    @HotSpotIntrinsicCandidate
    public final char getCharUnaligned(Object o, long offset) {
        if ((offset & 1) == 0) {
            return getChar(o, offset);
        } else {
            return (char)makeShort(getByte(o, offset), getByte(o, offset + 1));
        }
    }

    public final char getCharUnaligned(Object o, long offset, boolean bigEndian) {
        return convEndian(bigEndian, getCharUnaligned(o, offset));
    }

    @HotSpotIntrinsicCandidate
    public final void putLongUnaligned(Object o, long offset, long x) {
        if ((offset & 7) == 0) {
            putLong(o, offset, x);
        } else if ((offset & 3) == 0) {
            putLongParts(o, offset, (int)(x >> 0), (int)(x >>> 32));
        } else if ((offset & 1) == 0) {
            putLongParts(o, offset, (short)(x >>> 0), (short)(x >>> 16), (short)(x >>> 32), (short)(x >>> 48));
        } else {
            putLongParts(o, offset, (byte)(x >>> 0), (byte)(x >>> 8), (byte)(x >>> 16), (byte)(x >>> 24),
                (byte)(x >>> 32), (byte)(x >>> 40), (byte)(x >>> 48), (byte)(x >>> 56));
        }
    }

    public final void putLongUnaligned(Object o, long offset, long x, boolean bigEndian) {
        putLongUnaligned(o, offset, convEndian(bigEndian, x));
    }

    @HotSpotIntrinsicCandidate
    public final void putIntUnaligned(Object o, long offset, int x) {
        if ((offset & 3) == 0) {
            putInt(o, offset, x);
        } else if ((offset & 1) == 0) {
            putIntParts(o, offset, (short)(x >> 0), (short)(x >>> 16));
        } else {
            putIntParts(o, offset, (byte)(x >>> 0), (byte)(x >>> 8), (byte)(x >>> 16), (byte)(x >>> 24));
        }
    }

    public final void putIntUnaligned(Object o, long offset, int x, boolean bigEndian) {
        putIntUnaligned(o, offset, convEndian(bigEndian, x));
    }

    @HotSpotIntrinsicCandidate
    public final void putShortUnaligned(Object o, long offset, short x) {
        if ((offset & 1) == 0) {
            putShort(o, offset, x);
        } else {
            putShortParts(o, offset, (byte)(x >>> 0), (byte)(x >>> 8));
        }
    }

    public final void putShortUnaligned(Object o, long offset, short x, boolean bigEndian) {
        putShortUnaligned(o, offset, convEndian(bigEndian, x));
    }

    @HotSpotIntrinsicCandidate
    public final void putCharUnaligned(Object o, long offset, char x) {
        putShortUnaligned(o, offset, (short)x);
    }

    public final void putCharUnaligned(Object o, long offset, char x, boolean bigEndian) {
        putCharUnaligned(o, offset, convEndian(bigEndian, x));
    }

    // JVM interface methods
    // BE is true iff the native endianness of this platform is big.
    private static final boolean BE = theUnsafe.isBigEndian0();

    // unalignedAccess is true iff this platform can perform unaligned accesses.
    private static final boolean unalignedAccess = theUnsafe.unalignedAccess0();

    private static int pickPos(int top, int pos) {
        return BE ? top - pos : pos;
    }

    // These methods construct integers from bytes. The byte ordering
    // is the native endianness of this platform.
    private static long makeLong(byte i0, byte i1, byte i2, byte i3, byte i4, byte i5, byte i6, byte i7) {
        return ((toUnsignedLong(i0) << pickPos(56, 0)) | (toUnsignedLong(i1) << pickPos(56, 8))
            | (toUnsignedLong(i2) << pickPos(56, 16)) | (toUnsignedLong(i3) << pickPos(56, 24))
            | (toUnsignedLong(i4) << pickPos(56, 32)) | (toUnsignedLong(i5) << pickPos(56, 40))
            | (toUnsignedLong(i6) << pickPos(56, 48)) | (toUnsignedLong(i7) << pickPos(56, 56)));
    }

    private static long makeLong(short i0, short i1, short i2, short i3) {
        return ((toUnsignedLong(i0) << pickPos(48, 0)) | (toUnsignedLong(i1) << pickPos(48, 16))
            | (toUnsignedLong(i2) << pickPos(48, 32)) | (toUnsignedLong(i3) << pickPos(48, 48)));
    }

    private static long makeLong(int i0, int i1) {
        return (toUnsignedLong(i0) << pickPos(32, 0)) | (toUnsignedLong(i1) << pickPos(32, 32));
    }

    private static int makeInt(short i0, short i1) {
        return (toUnsignedInt(i0) << pickPos(16, 0)) | (toUnsignedInt(i1) << pickPos(16, 16));
    }

    private static int makeInt(byte i0, byte i1, byte i2, byte i3) {
        return ((toUnsignedInt(i0) << pickPos(24, 0)) | (toUnsignedInt(i1) << pickPos(24, 8))
            | (toUnsignedInt(i2) << pickPos(24, 16)) | (toUnsignedInt(i3) << pickPos(24, 24)));
    }

    private static short makeShort(byte i0, byte i1) {
        return (short)((toUnsignedInt(i0) << pickPos(8, 0)) | (toUnsignedInt(i1) << pickPos(8, 8)));
    }

    private static byte pick(byte le, byte be) {
        return BE ? be : le;
    }

    private static short pick(short le, short be) {
        return BE ? be : le;
    }

    private static int pick(int le, int be) {
        return BE ? be : le;
    }

    // These methods write integers to memory from smaller parts
    // provided by their caller. The ordering in which these parts
    // are written is the native endianness of this platform.
    private void putLongParts(Object o, long offset, byte i0, byte i1, byte i2, byte i3, byte i4, byte i5, byte i6,
        byte i7) {
        putByte(o, offset + 0, pick(i0, i7));
        putByte(o, offset + 1, pick(i1, i6));
        putByte(o, offset + 2, pick(i2, i5));
        putByte(o, offset + 3, pick(i3, i4));
        putByte(o, offset + 4, pick(i4, i3));
        putByte(o, offset + 5, pick(i5, i2));
        putByte(o, offset + 6, pick(i6, i1));
        putByte(o, offset + 7, pick(i7, i0));
    }

    private void putLongParts(Object o, long offset, short i0, short i1, short i2, short i3) {
        putShort(o, offset + 0, pick(i0, i3));
        putShort(o, offset + 2, pick(i1, i2));
        putShort(o, offset + 4, pick(i2, i1));
        putShort(o, offset + 6, pick(i3, i0));
    }

    private void putLongParts(Object o, long offset, int i0, int i1) {
        putInt(o, offset + 0, pick(i0, i1));
        putInt(o, offset + 4, pick(i1, i0));
    }

    private void putIntParts(Object o, long offset, short i0, short i1) {
        putShort(o, offset + 0, pick(i0, i1));
        putShort(o, offset + 2, pick(i1, i0));
    }

    private void putIntParts(Object o, long offset, byte i0, byte i1, byte i2, byte i3) {
        putByte(o, offset + 0, pick(i0, i3));
        putByte(o, offset + 1, pick(i1, i2));
        putByte(o, offset + 2, pick(i2, i1));
        putByte(o, offset + 3, pick(i3, i0));
    }

    private void putShortParts(Object o, long offset, byte i0, byte i1) {
        putByte(o, offset + 0, pick(i0, i1));
        putByte(o, offset + 1, pick(i1, i0));
    }

    // Zero-extend an integer
    private static int toUnsignedInt(byte n) {
        return n & 0xff;
    }

    private static int toUnsignedInt(short n) {
        return n & 0xffff;
    }

    private static long toUnsignedLong(byte n) {
        return n & 0xffl;
    }

    private static long toUnsignedLong(short n) {
        return n & 0xffffl;
    }

    private static long toUnsignedLong(int n) {
        return n & 0xffffffffl;
    }

    // Maybe byte-reverse an integer
    private static char convEndian(boolean big, char n) {
        return big == BE ? n : Character.reverseBytes(n);
    }

    private static short convEndian(boolean big, short n) {
        return big == BE ? n : Short.reverseBytes(n);
    }

    private static int convEndian(boolean big, int n) {
        return big == BE ? n : Integer.reverseBytes(n);
    }

    private static long convEndian(boolean big, long n) {
        return big == BE ? n : Long.reverseBytes(n);
    }

    private native long allocateMemory0(long bytes);

    private native long reallocateMemory0(long address, long bytes);

    private native void freeMemory0(long address);

    private native void setMemory0(Object o, long offset, long bytes, byte value);

    @HotSpotIntrinsicCandidate
    private native void copyMemory0(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes);

    private native void copySwapMemory0(Object srcBase, long srcOffset, Object destBase, long destOffset, long bytes,
        long elemSize);

    private native long objectFieldOffset0(Field f);

    private native long objectFieldOffset1(Class<?> c, String name);

    private native long staticFieldOffset0(Field f);

    private native Object staticFieldBase0(Field f);

    private native boolean shouldBeInitialized0(Class<?> c);

    private native void ensureClassInitialized0(Class<?> c);

    private native int arrayBaseOffset0(Class<?> arrayClass);

    private native int arrayIndexScale0(Class<?> arrayClass);

    private native int addressSize0();

    private native Class<?> defineAnonymousClass0(Class<?> hostClass, byte[] data, Object[] cpPatches);

    private native int getLoadAverage0(double[] loadavg, int nelems);

    private native boolean unalignedAccess0();

    private native boolean isBigEndian0();

    public void invokeCleaner(java.nio.ByteBuffer directBuffer) {
        if (!directBuffer.isDirect())
            throw new IllegalArgumentException("buffer is non-direct");

        DirectBuffer db = (DirectBuffer)directBuffer;
        if (db.attachment() != null)
            throw new IllegalArgumentException("duplicate or slice");

        Cleaner cleaner = db.cleaner();
        if (cleaner != null) {
            cleaner.clean();
        }
    }
}
