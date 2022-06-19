/*
 * Copyright (c) 1994, 2017, Oracle and/or its affiliates. All rights reserved. ORACLE PROPRIETARY/CONFIDENTIAL. Use is
 * subject to license terms.
 */

package java.lang;

import jdk.internal.math.FloatingDecimal;
import jdk.internal.math.DoubleConsts;
import jdk.internal.HotSpotIntrinsicCandidate;

public final class Double extends Number implements Comparable<Double> {
    public static final double POSITIVE_INFINITY = 1.0 / 0.0;

    public static final double NEGATIVE_INFINITY = -1.0 / 0.0;

    public static final double NaN = 0.0d / 0.0;

    public static final double MAX_VALUE = 0x1.fffffffffffffP+1023; // 1.7976931348623157e+308

    public static final double MIN_NORMAL = 0x1.0p-1022; // 2.2250738585072014E-308

    public static final double MIN_VALUE = 0x0.0000000000001P-1022; // 4.9e-324

    public static final int MAX_EXPONENT = 1023;

    public static final int MIN_EXPONENT = -1022;

    public static final int SIZE = 64;

    public static final int BYTES = SIZE / Byte.SIZE;

    @SuppressWarnings("unchecked")
    public static final Class<Double> TYPE = (Class<Double>)Class.getPrimitiveClass("double");

    public static String toString(double d) {
        return FloatingDecimal.toJavaFormatString(d);
    }

    public static String toHexString(double d) {
        /*
         * Modeled after the "a" conversion specifier in C99, section
         * 7.19.6.1; however, the output of this method is more
         * tightly specified.
         */
        if (!isFinite(d))
            // For infinity and NaN, use the decimal output.
            return Double.toString(d);
        else {
            // Initialized to maximum size of output.
            StringBuilder answer = new StringBuilder(24);

            if (Math.copySign(1.0, d) == -1.0) // value is negative,
                answer.append("-"); // so append sign info

            answer.append("0x");

            d = Math.abs(d);

            if (d == 0.0) {
                answer.append("0.0p0");
            } else {
                boolean subnormal = (d < Double.MIN_NORMAL);

                // Isolate significand bits and OR in a high-order bit
                // so that the string representation has a known
                // length.
                long signifBits = (Double.doubleToLongBits(d) & DoubleConsts.SIGNIF_BIT_MASK) | 0x1000000000000000L;

                // Subnormal values have a 0 implicit bit; normal
                // values have a 1 implicit bit.
                answer.append(subnormal ? "0." : "1.");

                // Isolate the low-order 13 digits of the hex
                // representation. If all the digits are zero,
                // replace with a single 0; otherwise, remove all
                // trailing zeros.
                String signif = Long.toHexString(signifBits).substring(3, 16);
                answer.append(signif.equals("0000000000000") ? // 13 zeros
                    "0" : signif.replaceFirst("0{1,12}$", ""));

                answer.append('p');
                // If the value is subnormal, use the E_min exponent
                // value for double; otherwise, extract and report d's
                // exponent (the representation of a subnormal uses
                // E_min -1).
                answer.append(subnormal ? Double.MIN_EXPONENT : Math.getExponent(d));
            }
            return answer.toString();
        }
    }

    public static Double valueOf(String s) throws NumberFormatException {
        return new Double(parseDouble(s));
    }

    @HotSpotIntrinsicCandidate
    public static Double valueOf(double d) {
        return new Double(d);
    }

    public static double parseDouble(String s) throws NumberFormatException {
        return FloatingDecimal.parseDouble(s);
    }

    public static boolean isNaN(double v) {
        return (v != v);
    }

    public static boolean isInfinite(double v) {
        return (v == POSITIVE_INFINITY) || (v == NEGATIVE_INFINITY);
    }

    public static boolean isFinite(double d) {
        return Math.abs(d) <= Double.MAX_VALUE;
    }

    private final double value;

    @Deprecated(since = "9")
    public Double(double value) {
        this.value = value;
    }

    @Deprecated(since = "9")
    public Double(String s) throws NumberFormatException {
        value = parseDouble(s);
    }

    public boolean isNaN() {
        return isNaN(value);
    }

    public boolean isInfinite() {
        return isInfinite(value);
    }

    public String toString() {
        return toString(value);
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

    public float floatValue() {
        return (float)value;
    }

    @HotSpotIntrinsicCandidate
    public double doubleValue() {
        return value;
    }

    @Override
    public int hashCode() {
        return Double.hashCode(value);
    }

    public static int hashCode(double value) {
        long bits = doubleToLongBits(value);
        return (int)(bits ^ (bits >>> 32));
    }

    public boolean equals(Object obj) {
        return (obj instanceof Double) && (doubleToLongBits(((Double)obj).value) == doubleToLongBits(value));
    }

    @HotSpotIntrinsicCandidate
    public static long doubleToLongBits(double value) {
        if (!isNaN(value)) {
            return doubleToRawLongBits(value);
        }
        return 0x7ff8000000000000L;
    }

    /**
     * double 转 long
     * @date 2022/6/18 16:15
     * @param value
     * @return long
     */
    @HotSpotIntrinsicCandidate
    public static native long doubleToRawLongBits(double value);

    /** 
     * long 转 double
     * @date 2022/6/18 15:46
     * @param bits 
     * @return double
     */
    @HotSpotIntrinsicCandidate
    public static native double longBitsToDouble(long bits);

    public int compareTo(Double anotherDouble) {
        return Double.compare(value, anotherDouble.value);
    }

    public static int compare(double d1, double d2) {
        if (d1 < d2)
            return -1; // Neither val is NaN, thisVal is smaller
        if (d1 > d2)
            return 1; // Neither val is NaN, thisVal is larger

        // Cannot use doubleToRawLongBits because of possibility of NaNs.
        long thisBits = Double.doubleToLongBits(d1);
        long anotherBits = Double.doubleToLongBits(d2);

        return (thisBits == anotherBits ? 0 : // Values are equal
            (thisBits < anotherBits ? -1 : // (-0.0, 0.0) or (!NaN, NaN)
                1)); // (0.0, -0.0) or (NaN, !NaN)
    }

    public static double sum(double a, double b) {
        return a + b;
    }

    public static double max(double a, double b) {
        return Math.max(a, b);
    }

    public static double min(double a, double b) {
        return Math.min(a, b);
    }

    private static final long serialVersionUID = -9172774392245257468L;
}
