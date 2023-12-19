/*
 * Copyright (c) 1994, 2017, Oracle and/or its affiliates. All rights reserved.
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

package java.lang;

import jdk.internal.math.FloatingDecimal;
import jdk.internal.HotSpotIntrinsicCandidate;

public final class Float extends Number implements Comparable<Float> {
    public static final float POSITIVE_INFINITY = 1.0f / 0.0f;

    public static final float NEGATIVE_INFINITY = -1.0f / 0.0f;

    public static final float NaN = 0.0f / 0.0f;

    public static final float MAX_VALUE = 0x1.fffffeP+127f; // 3.4028235e+38f

    public static final float MIN_NORMAL = 0x1.0p-126f; // 1.17549435E-38f

    public static final float MIN_VALUE = 0x0.000002P-126f; // 1.4e-45f

    public static final int MAX_EXPONENT = 127;

    public static final int MIN_EXPONENT = -126;

    public static final int SIZE = 32;

    public static final int BYTES = SIZE / Byte.SIZE;

    @SuppressWarnings("unchecked")
    public static final Class<Float> TYPE = (Class<Float>) Class.getPrimitiveClass("float");

    public static String toString(float f) {
        return FloatingDecimal.toJavaFormatString(f);
    }

    public static String toHexString(float f) {
        if (Math.abs(f) < Float.MIN_NORMAL
            &&  f != 0.0f ) {// float subnormal
            // Adjust exponent to create subnormal double, then
            // replace subnormal double exponent with subnormal float
            // exponent
            String s = Double.toHexString(Math.scalb((double)f,
                                                     /* -1022+126 */
                                                     Double.MIN_EXPONENT-
                                                     Float.MIN_EXPONENT));
            return s.replaceFirst("p-1022$", "p-126");
        }
        else // double string will be the same as float string
            return Double.toHexString(f);
    }

    public static Float valueOf(String s) throws NumberFormatException {
        return new Float(parseFloat(s));
    }

    @HotSpotIntrinsicCandidate
    public static Float valueOf(float f) {
        return new Float(f);
    }

    public static float parseFloat(String s) throws NumberFormatException {
        return FloatingDecimal.parseFloat(s);
    }

    public static boolean isNaN(float v) {
        return (v != v);
    }

    public static boolean isInfinite(float v) {
        return (v == POSITIVE_INFINITY) || (v == NEGATIVE_INFINITY);
    }


     public static boolean isFinite(float f) {
        return Math.abs(f) <= Float.MAX_VALUE;
    }

    private final float value;

    @Deprecated(since="9")
    public Float(float value) {
        this.value = value;
    }

    @Deprecated(since="9")
    public Float(double value) {
        this.value = (float)value;
    }

    @Deprecated(since="9")
    public Float(String s) throws NumberFormatException {
        value = parseFloat(s);
    }

    public boolean isNaN() {
        return isNaN(value);
    }

    public boolean isInfinite() {
        return isInfinite(value);
    }

    public String toString() {
        return Float.toString(value);
    }

    public byte byteValue() {
        return (byte)value;
    }

    public short shortValue() {
        return (short)value;
    }

    public int intValue() {
        return (int)value;
    }

    public long longValue() {
        return (long)value;
    }

    @HotSpotIntrinsicCandidate
    public float floatValue() {
        return value;
    }

    public double doubleValue() {
        return (double)value;
    }

    @Override
    public int hashCode() {
        return Float.hashCode(value);
    }

    public static int hashCode(float value) {
        return floatToIntBits(value);
    }

    public boolean equals(Object obj) {
        return (obj instanceof Float)
               && (floatToIntBits(((Float)obj).value) == floatToIntBits(value));
    }

    /**
     * Returns a representation of the specified floating-point value
     * according to the IEEE 754 floating-point "single format" bit
     * layout.
     *
     * <p>Bit 31 (the bit that is selected by the mask
     * {@code 0x80000000}) represents the sign of the floating-point
     * number.
     * Bits 30-23 (the bits that are selected by the mask
     * {@code 0x7f800000}) represent the exponent.
     * Bits 22-0 (the bits that are selected by the mask
     * {@code 0x007fffff}) represent the significand (sometimes called
     * the mantissa) of the floating-point number.
     *
     * <p>If the argument is positive infinity, the result is
     * {@code 0x7f800000}.
     *
     * <p>If the argument is negative infinity, the result is
     * {@code 0xff800000}.
     *
     * <p>If the argument is NaN, the result is {@code 0x7fc00000}.
     *
     * <p>In all cases, the result is an integer that, when given to the
     * {@link #intBitsToFloat(int)} method, will produce a floating-point
     * value the same as the argument to {@code floatToIntBits}
     * (except all NaN values are collapsed to a single
     * "canonical" NaN value).
     *
     * @param   value   a floating-point number.
     * @return the bits that represent the floating-point number.
     */
    @HotSpotIntrinsicCandidate
    public static int floatToIntBits(float value) {
        if (!isNaN(value)) {
            return floatToRawIntBits(value);
        }
        return 0x7fc00000;
    }

    @HotSpotIntrinsicCandidate
    public static native int floatToRawIntBits(float value);

    @HotSpotIntrinsicCandidate
    public static native float intBitsToFloat(int bits);

    public int compareTo(Float anotherFloat) {
        return Float.compare(value, anotherFloat.value);
    }

    public static int compare(float f1, float f2) {
        if (f1 < f2)
            return -1;           // Neither val is NaN, thisVal is smaller
        if (f1 > f2)
            return 1;            // Neither val is NaN, thisVal is larger

        // Cannot use floatToRawIntBits because of possibility of NaNs.
        int thisBits    = Float.floatToIntBits(f1);
        int anotherBits = Float.floatToIntBits(f2);

        return (thisBits == anotherBits ?  0 : // Values are equal
                (thisBits < anotherBits ? -1 : // (-0.0, 0.0) or (!NaN, NaN)
                 1));                          // (0.0, -0.0) or (NaN, !NaN)
    }

    public static float sum(float a, float b) {
        return a + b;
    }

    public static float max(float a, float b) {
        return Math.max(a, b);
    }

    public static float min(float a, float b) {
        return Math.min(a, b);
    }

    private static final long serialVersionUID = -2671257302660747028L;
}
