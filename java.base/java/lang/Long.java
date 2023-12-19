/*
 * Copyright (c) 1994, 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.lang.annotation.Native;
import java.math.*;
import java.util.Objects;
import jdk.internal.HotSpotIntrinsicCandidate;

import static java.lang.String.COMPACT_STRINGS;
import static java.lang.String.LATIN1;
import static java.lang.String.UTF16;

public final class Long extends Number implements Comparable<Long> {
    @Native public static final long MIN_VALUE = 0x8000000000000000L;

    @Native public static final long MAX_VALUE = 0x7fffffffffffffffL;

    @SuppressWarnings("unchecked")
    public static final Class<Long>     TYPE = (Class<Long>) Class.getPrimitiveClass("long");

    public static String toString(long i, int radix) {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX)
            radix = 10;
        if (radix == 10)
            return toString(i);

        if (COMPACT_STRINGS) {
            byte[] buf = new byte[65];
            int charPos = 64;
            boolean negative = (i < 0);

            if (!negative) {
                i = -i;
            }

            while (i <= -radix) {
                buf[charPos--] = (byte)Integer.digits[(int)(-(i % radix))];
                i = i / radix;
            }
            buf[charPos] = (byte)Integer.digits[(int)(-i)];

            if (negative) {
                buf[--charPos] = '-';
            }
            return StringLatin1.newString(buf, charPos, (65 - charPos));
        }
        return toStringUTF16(i, radix);
    }

    private static String toStringUTF16(long i, int radix) {
        byte[] buf = new byte[65 * 2];
        int charPos = 64;
        boolean negative = (i < 0);
        if (!negative) {
            i = -i;
        }
        while (i <= -radix) {
            StringUTF16.putChar(buf, charPos--, Integer.digits[(int)(-(i % radix))]);
            i = i / radix;
        }
        StringUTF16.putChar(buf, charPos, Integer.digits[(int)(-i)]);
        if (negative) {
            StringUTF16.putChar(buf, --charPos, '-');
        }
        return StringUTF16.newString(buf, charPos, (65 - charPos));
    }

    public static String toUnsignedString(long i, int radix) {
        if (i >= 0)
            return toString(i, radix);
        else {
            switch (radix) {
            case 2:
                return toBinaryString(i);

            case 4:
                return toUnsignedString0(i, 2);

            case 8:
                return toOctalString(i);

            case 10:
                /*
                 * We can get the effect of an unsigned division by 10
                 * on a long value by first shifting right, yielding a
                 * positive value, and then dividing by 5.  This
                 * allows the last digit and preceding digits to be
                 * isolated more quickly than by an initial conversion
                 * to BigInteger.
                 */
                long quot = (i >>> 1) / 5;
                long rem = i - quot * 10;
                return toString(quot) + rem;

            case 16:
                return toHexString(i);

            case 32:
                return toUnsignedString0(i, 5);

            default:
                return toUnsignedBigInteger(i).toString(radix);
            }
        }
    }

    private static BigInteger toUnsignedBigInteger(long i) {
        if (i >= 0L)
            return BigInteger.valueOf(i);
        else {
            int upper = (int) (i >>> 32);
            int lower = (int) i;

            // return (upper << 32) + lower
            return (BigInteger.valueOf(Integer.toUnsignedLong(upper))).shiftLeft(32).
                add(BigInteger.valueOf(Integer.toUnsignedLong(lower)));
        }
    }

    public static String toHexString(long i) {
        return toUnsignedString0(i, 4);
    }

    public static String toOctalString(long i) {
        return toUnsignedString0(i, 3);
    }

    public static String toBinaryString(long i) {
        return toUnsignedString0(i, 1);
    }

    static String toUnsignedString0(long val, int shift) {
        // assert shift > 0 && shift <=5 : "Illegal shift value";
        int mag = Long.SIZE - Long.numberOfLeadingZeros(val);
        int chars = Math.max(((mag + (shift - 1)) / shift), 1);
        if (COMPACT_STRINGS) {
            byte[] buf = new byte[chars];
            formatUnsignedLong0(val, shift, buf, 0, chars);
            return new String(buf, LATIN1);
        } else {
            byte[] buf = new byte[chars * 2];
            formatUnsignedLong0UTF16(val, shift, buf, 0, chars);
            return new String(buf, UTF16);
        }
    }


    static void formatUnsignedLong0(long val, int shift, byte[] buf, int offset, int len) {
        int charPos = offset + len;
        int radix = 1 << shift;
        int mask = radix - 1;
        do {
            buf[--charPos] = (byte)Integer.digits[((int) val) & mask];
            val >>>= shift;
        } while (charPos > offset);
    }

    private static void formatUnsignedLong0UTF16(long val, int shift, byte[] buf, int offset, int len) {
        int charPos = offset + len;
        int radix = 1 << shift;
        int mask = radix - 1;
        do {
            StringUTF16.putChar(buf, --charPos, Integer.digits[((int) val) & mask]);
            val >>>= shift;
        } while (charPos > offset);
    }

    static String fastUUID(long lsb, long msb) {
        if (COMPACT_STRINGS) {
            byte[] buf = new byte[36];
            formatUnsignedLong0(lsb,        4, buf, 24, 12);
            formatUnsignedLong0(lsb >>> 48, 4, buf, 19, 4);
            formatUnsignedLong0(msb,        4, buf, 14, 4);
            formatUnsignedLong0(msb >>> 16, 4, buf, 9,  4);
            formatUnsignedLong0(msb >>> 32, 4, buf, 0,  8);

            buf[23] = '-';
            buf[18] = '-';
            buf[13] = '-';
            buf[8]  = '-';

            return new String(buf, LATIN1);
        } else {
            byte[] buf = new byte[72];

            formatUnsignedLong0UTF16(lsb,        4, buf, 24, 12);
            formatUnsignedLong0UTF16(lsb >>> 48, 4, buf, 19, 4);
            formatUnsignedLong0UTF16(msb,        4, buf, 14, 4);
            formatUnsignedLong0UTF16(msb >>> 16, 4, buf, 9,  4);
            formatUnsignedLong0UTF16(msb >>> 32, 4, buf, 0,  8);

            StringUTF16.putChar(buf, 23, '-');
            StringUTF16.putChar(buf, 18, '-');
            StringUTF16.putChar(buf, 13, '-');
            StringUTF16.putChar(buf,  8, '-');

            return new String(buf, UTF16);
        }
    }

    public static String toString(long i) {
        int size = stringSize(i);
        if (COMPACT_STRINGS) {
            byte[] buf = new byte[size];
            getChars(i, size, buf);
            return new String(buf, LATIN1);
        } else {
            byte[] buf = new byte[size * 2];
            StringUTF16.getChars(i, size, buf);
            return new String(buf, UTF16);
        }
    }

    public static String toUnsignedString(long i) {
        return toUnsignedString(i, 10);
    }

    static int getChars(long i, int index, byte[] buf) {
        long q;
        int r;
        int charPos = index;

        boolean negative = (i < 0);
        if (!negative) {
            i = -i;
        }

        // Get 2 digits/iteration using longs until quotient fits into an int
        while (i <= Integer.MIN_VALUE) {
            q = i / 100;
            r = (int)((q * 100) - i);
            i = q;
            buf[--charPos] = Integer.DigitOnes[r];
            buf[--charPos] = Integer.DigitTens[r];
        }

        // Get 2 digits/iteration using ints
        int q2;
        int i2 = (int)i;
        while (i2 <= -100) {
            q2 = i2 / 100;
            r  = (q2 * 100) - i2;
            i2 = q2;
            buf[--charPos] = Integer.DigitOnes[r];
            buf[--charPos] = Integer.DigitTens[r];
        }

        // We know there are at most two digits left at this point.
        q2 = i2 / 10;
        r  = (q2 * 10) - i2;
        buf[--charPos] = (byte)('0' + r);

        // Whatever left is the remaining digit.
        if (q2 < 0) {
            buf[--charPos] = (byte)('0' - q2);
        }

        if (negative) {
            buf[--charPos] = (byte)'-';
        }
        return charPos;
    }

    static int stringSize(long x) {
        int d = 1;
        if (x >= 0) {
            d = 0;
            x = -x;
        }
        long p = -10;
        for (int i = 1; i < 19; i++) {
            if (x > p)
                return i + d;
            p = 10 * p;
        }
        return 19 + d;
    }

    public static long parseLong(String s, int radix)
              throws NumberFormatException
    {
        if (s == null) {
            throw new NumberFormatException("null");
        }

        if (radix < Character.MIN_RADIX) {
            throw new NumberFormatException("radix " + radix +
                                            " less than Character.MIN_RADIX");
        }
        if (radix > Character.MAX_RADIX) {
            throw new NumberFormatException("radix " + radix +
                                            " greater than Character.MAX_RADIX");
        }

        boolean negative = false;
        int i = 0, len = s.length();
        long limit = -Long.MAX_VALUE;

        if (len > 0) {
            char firstChar = s.charAt(0);
            if (firstChar < '0') { // Possible leading "+" or "-"
                if (firstChar == '-') {
                    negative = true;
                    limit = Long.MIN_VALUE;
                } else if (firstChar != '+') {
                    throw NumberFormatException.forInputString(s);
                }

                if (len == 1) { // Cannot have lone "+" or "-"
                    throw NumberFormatException.forInputString(s);
                }
                i++;
            }
            long multmin = limit / radix;
            long result = 0;
            while (i < len) {
                // Accumulating negatively avoids surprises near MAX_VALUE
                int digit = Character.digit(s.charAt(i++),radix);
                if (digit < 0 || result < multmin) {
                    throw NumberFormatException.forInputString(s);
                }
                result *= radix;
                if (result < limit + digit) {
                    throw NumberFormatException.forInputString(s);
                }
                result -= digit;
            }
            return negative ? result : -result;
        } else {
            throw NumberFormatException.forInputString(s);
        }
    }

    public static long parseLong(CharSequence s, int beginIndex, int endIndex, int radix)
                throws NumberFormatException {
        s = Objects.requireNonNull(s);

        if (beginIndex < 0 || beginIndex > endIndex || endIndex > s.length()) {
            throw new IndexOutOfBoundsException();
        }
        if (radix < Character.MIN_RADIX) {
            throw new NumberFormatException("radix " + radix +
                    " less than Character.MIN_RADIX");
        }
        if (radix > Character.MAX_RADIX) {
            throw new NumberFormatException("radix " + radix +
                    " greater than Character.MAX_RADIX");
        }

        boolean negative = false;
        int i = beginIndex;
        long limit = -Long.MAX_VALUE;

        if (i < endIndex) {
            char firstChar = s.charAt(i);
            if (firstChar < '0') { // Possible leading "+" or "-"
                if (firstChar == '-') {
                    negative = true;
                    limit = Long.MIN_VALUE;
                } else if (firstChar != '+') {
                    throw NumberFormatException.forCharSequence(s, beginIndex,
                            endIndex, i);
                }
                i++;
            }
            if (i >= endIndex) { // Cannot have lone "+", "-" or ""
                throw NumberFormatException.forCharSequence(s, beginIndex,
                        endIndex, i);
            }
            long multmin = limit / radix;
            long result = 0;
            while (i < endIndex) {
                // Accumulating negatively avoids surprises near MAX_VALUE
                int digit = Character.digit(s.charAt(i), radix);
                if (digit < 0 || result < multmin) {
                    throw NumberFormatException.forCharSequence(s, beginIndex,
                            endIndex, i);
                }
                result *= radix;
                if (result < limit + digit) {
                    throw NumberFormatException.forCharSequence(s, beginIndex,
                            endIndex, i);
                }
                i++;
                result -= digit;
            }
            return negative ? result : -result;
        } else {
            throw new NumberFormatException("");
        }
    }

    public static long parseLong(String s) throws NumberFormatException {
        return parseLong(s, 10);
    }

    public static long parseUnsignedLong(String s, int radix)
                throws NumberFormatException {
        if (s == null)  {
            throw new NumberFormatException("null");
        }

        int len = s.length();
        if (len > 0) {
            char firstChar = s.charAt(0);
            if (firstChar == '-') {
                throw new
                    NumberFormatException(String.format("Illegal leading minus sign " +
                                                       "on unsigned string %s.", s));
            } else {
                if (len <= 12 || // Long.MAX_VALUE in Character.MAX_RADIX is 13 digits
                    (radix == 10 && len <= 18) ) { // Long.MAX_VALUE in base 10 is 19 digits
                    return parseLong(s, radix);
                }

                // No need for range checks on len due to testing above.
                long first = parseLong(s, 0, len - 1, radix);
                int second = Character.digit(s.charAt(len - 1), radix);
                if (second < 0) {
                    throw new NumberFormatException("Bad digit at end of " + s);
                }
                long result = first * radix + second;

                /*
                 * Test leftmost bits of multiprecision extension of first*radix
                 * for overflow. The number of bits needed is defined by
                 * GUARD_BIT = ceil(log2(Character.MAX_RADIX)) + 1 = 7. Then
                 * int guard = radix*(int)(first >>> (64 - GUARD_BIT)) and
                 * overflow is tested by splitting guard in the ranges
                 * guard < 92, 92 <= guard < 128, and 128 <= guard, where
                 * 92 = 128 - Character.MAX_RADIX. Note that guard cannot take
                 * on a value which does not include a prime factor in the legal
                 * radix range.
                 */
                int guard = radix * (int) (first >>> 57);
                if (guard >= 128 ||
                    (result >= 0 && guard >= 128 - Character.MAX_RADIX)) {
                    /*
                     * For purposes of exposition, the programmatic statements
                     * below should be taken to be multi-precision, i.e., not
                     * subject to overflow.
                     *
                     * A) Condition guard >= 128:
                     * If guard >= 128 then first*radix >= 2^7 * 2^57 = 2^64
                     * hence always overflow.
                     *
                     * B) Condition guard < 92:
                     * Define left7 = first >>> 57.
                     * Given first = (left7 * 2^57) + (first & (2^57 - 1)) then
                     * result <= (radix*left7)*2^57 + radix*(2^57 - 1) + second.
                     * Thus if radix*left7 < 92, radix <= 36, and second < 36,
                     * then result < 92*2^57 + 36*(2^57 - 1) + 36 = 2^64 hence
                     * never overflow.
                     *
                     * C) Condition 92 <= guard < 128:
                     * first*radix + second >= radix*left7*2^57 + second
                     * so that first*radix + second >= 92*2^57 + 0 > 2^63
                     *
                     * D) Condition guard < 128:
                     * radix*first <= (radix*left7) * 2^57 + radix*(2^57 - 1)
                     * so
                     * radix*first + second <= (radix*left7) * 2^57 + radix*(2^57 - 1) + 36
                     * thus
                     * radix*first + second < 128 * 2^57 + 36*2^57 - radix + 36
                     * whence
                     * radix*first + second < 2^64 + 2^6*2^57 = 2^64 + 2^63
                     *
                     * E) Conditions C, D, and result >= 0:
                     * C and D combined imply the mathematical result
                     * 2^63 < first*radix + second < 2^64 + 2^63. The lower
                     * bound is therefore negative as a signed long, but the
                     * upper bound is too small to overflow again after the
                     * signed long overflows to positive above 2^64 - 1. Hence
                     * result >= 0 implies overflow given C and D.
                     */
                    throw new NumberFormatException(String.format("String value %s exceeds " +
                                                                  "range of unsigned long.", s));
                }
                return result;
            }
        } else {
            throw NumberFormatException.forInputString(s);
        }
    }

    public static long parseUnsignedLong(CharSequence s, int beginIndex, int endIndex, int radix)
                throws NumberFormatException {
        s = Objects.requireNonNull(s);

        if (beginIndex < 0 || beginIndex > endIndex || endIndex > s.length()) {
            throw new IndexOutOfBoundsException();
        }
        int start = beginIndex, len = endIndex - beginIndex;

        if (len > 0) {
            char firstChar = s.charAt(start);
            if (firstChar == '-') {
                throw new NumberFormatException(String.format("Illegal leading minus sign " +
                        "on unsigned string %s.", s.subSequence(start, start + len)));
            } else {
                if (len <= 12 || // Long.MAX_VALUE in Character.MAX_RADIX is 13 digits
                    (radix == 10 && len <= 18) ) { // Long.MAX_VALUE in base 10 is 19 digits
                    return parseLong(s, start, start + len, radix);
                }

                // No need for range checks on end due to testing above.
                long first = parseLong(s, start, start + len - 1, radix);
                int second = Character.digit(s.charAt(start + len - 1), radix);
                if (second < 0) {
                    throw new NumberFormatException("Bad digit at end of " +
                            s.subSequence(start, start + len));
                }
                long result = first * radix + second;

                /*
                 * Test leftmost bits of multiprecision extension of first*radix
                 * for overflow. The number of bits needed is defined by
                 * GUARD_BIT = ceil(log2(Character.MAX_RADIX)) + 1 = 7. Then
                 * int guard = radix*(int)(first >>> (64 - GUARD_BIT)) and
                 * overflow is tested by splitting guard in the ranges
                 * guard < 92, 92 <= guard < 128, and 128 <= guard, where
                 * 92 = 128 - Character.MAX_RADIX. Note that guard cannot take
                 * on a value which does not include a prime factor in the legal
                 * radix range.
                 */
                int guard = radix * (int) (first >>> 57);
                if (guard >= 128 ||
                        (result >= 0 && guard >= 128 - Character.MAX_RADIX)) {
                    /*
                     * For purposes of exposition, the programmatic statements
                     * below should be taken to be multi-precision, i.e., not
                     * subject to overflow.
                     *
                     * A) Condition guard >= 128:
                     * If guard >= 128 then first*radix >= 2^7 * 2^57 = 2^64
                     * hence always overflow.
                     *
                     * B) Condition guard < 92:
                     * Define left7 = first >>> 57.
                     * Given first = (left7 * 2^57) + (first & (2^57 - 1)) then
                     * result <= (radix*left7)*2^57 + radix*(2^57 - 1) + second.
                     * Thus if radix*left7 < 92, radix <= 36, and second < 36,
                     * then result < 92*2^57 + 36*(2^57 - 1) + 36 = 2^64 hence
                     * never overflow.
                     *
                     * C) Condition 92 <= guard < 128:
                     * first*radix + second >= radix*left7*2^57 + second
                     * so that first*radix + second >= 92*2^57 + 0 > 2^63
                     *
                     * D) Condition guard < 128:
                     * radix*first <= (radix*left7) * 2^57 + radix*(2^57 - 1)
                     * so
                     * radix*first + second <= (radix*left7) * 2^57 + radix*(2^57 - 1) + 36
                     * thus
                     * radix*first + second < 128 * 2^57 + 36*2^57 - radix + 36
                     * whence
                     * radix*first + second < 2^64 + 2^6*2^57 = 2^64 + 2^63
                     *
                     * E) Conditions C, D, and result >= 0:
                     * C and D combined imply the mathematical result
                     * 2^63 < first*radix + second < 2^64 + 2^63. The lower
                     * bound is therefore negative as a signed long, but the
                     * upper bound is too small to overflow again after the
                     * signed long overflows to positive above 2^64 - 1. Hence
                     * result >= 0 implies overflow given C and D.
                     */
                    throw new NumberFormatException(String.format("String value %s exceeds " +
                            "range of unsigned long.", s.subSequence(start, start + len)));
                }
                return result;
            }
        } else {
            throw NumberFormatException.forInputString("");
        }
    }

    public static long parseUnsignedLong(String s) throws NumberFormatException {
        return parseUnsignedLong(s, 10);
    }

    public static Long valueOf(String s, int radix) throws NumberFormatException {
        return Long.valueOf(parseLong(s, radix));
    }

    public static Long valueOf(String s) throws NumberFormatException
    {
        return Long.valueOf(parseLong(s, 10));
    }

    private static class LongCache {
        private LongCache(){}

        static final Long cache[] = new Long[-(-128) + 127 + 1];

        static {
            for(int i = 0; i < cache.length; i++)
                cache[i] = new Long(i - 128);
        }
    }

    @HotSpotIntrinsicCandidate
    public static Long valueOf(long l) {
        final int offset = 128;
        if (l >= -128 && l <= 127) { // will cache
            return LongCache.cache[(int)l + offset];
        }
        return new Long(l);
    }

    public static Long decode(String nm) throws NumberFormatException {
        int radix = 10;
        int index = 0;
        boolean negative = false;
        Long result;

        if (nm.isEmpty())
            throw new NumberFormatException("Zero length string");
        char firstChar = nm.charAt(0);
        // Handle sign, if present
        if (firstChar == '-') {
            negative = true;
            index++;
        } else if (firstChar == '+')
            index++;

        // Handle radix specifier, if present
        if (nm.startsWith("0x", index) || nm.startsWith("0X", index)) {
            index += 2;
            radix = 16;
        }
        else if (nm.startsWith("#", index)) {
            index ++;
            radix = 16;
        }
        else if (nm.startsWith("0", index) && nm.length() > 1 + index) {
            index ++;
            radix = 8;
        }

        if (nm.startsWith("-", index) || nm.startsWith("+", index))
            throw new NumberFormatException("Sign character in wrong position");

        try {
            result = Long.valueOf(nm.substring(index), radix);
            result = negative ? Long.valueOf(-result.longValue()) : result;
        } catch (NumberFormatException e) {
            // If number is Long.MIN_VALUE, we'll end up here. The next line
            // handles this case, and causes any genuine format error to be
            // rethrown.
            String constant = negative ? ("-" + nm.substring(index))
                                       : nm.substring(index);
            result = Long.valueOf(constant, radix);
        }
        return result;
    }

    private final long value;

    @Deprecated(since="9")
    public Long(long value) {
        this.value = value;
    }

    @Deprecated(since="9")
    public Long(String s) throws NumberFormatException {
        this.value = parseLong(s, 10);
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

    @HotSpotIntrinsicCandidate
    public long longValue() {
        return value;
    }

    public float floatValue() {
        return (float)value;
    }

    public double doubleValue() {
        return (double)value;
    }

    public String toString() {
        return toString(value);
    }

    @Override
    public int hashCode() {
        return Long.hashCode(value);
    }

    public static int hashCode(long value) {
        return (int)(value ^ (value >>> 32));
    }

    public boolean equals(Object obj) {
        if (obj instanceof Long) {
            return value == ((Long)obj).longValue();
        }
        return false;
    }

    public static Long getLong(String nm) {
        return getLong(nm, null);
    }

    public static Long getLong(String nm, long val) {
        Long result = Long.getLong(nm, null);
        return (result == null) ? Long.valueOf(val) : result;
    }

    public static Long getLong(String nm, Long val) {
        String v = null;
        try {
            v = System.getProperty(nm);
        } catch (IllegalArgumentException | NullPointerException e) {
        }
        if (v != null) {
            try {
                return Long.decode(v);
            } catch (NumberFormatException e) {
            }
        }
        return val;
    }

    public int compareTo(Long anotherLong) {
        return compare(this.value, anotherLong.value);
    }

    public static int compare(long x, long y) {
        return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }

    public static int compareUnsigned(long x, long y) {
        return compare(x + MIN_VALUE, y + MIN_VALUE);
    }


    public static long divideUnsigned(long dividend, long divisor) {
        if (divisor < 0L) { // signed comparison
            // Answer must be 0 or 1 depending on relative magnitude
            // of dividend and divisor.
            return (compareUnsigned(dividend, divisor)) < 0 ? 0L :1L;
        }

        if (dividend > 0) //  Both inputs non-negative
            return dividend/divisor;
        else {
            /*
             * For simple code, leveraging BigInteger.  Longer and faster
             * code written directly in terms of operations on longs is
             * possible; see "Hacker's Delight" for divide and remainder
             * algorithms.
             */
            return toUnsignedBigInteger(dividend).
                divide(toUnsignedBigInteger(divisor)).longValue();
        }
    }

    public static long remainderUnsigned(long dividend, long divisor) {
        if (dividend > 0 && divisor > 0) { // signed comparisons
            return dividend % divisor;
        } else {
            if (compareUnsigned(dividend, divisor) < 0) // Avoid explicit check for 0 divisor
                return dividend;
            else
                return toUnsignedBigInteger(dividend).
                    remainder(toUnsignedBigInteger(divisor)).longValue();
        }
    }

    // Bit Twiddling

    @Native public static final int SIZE = 64;

    public static final int BYTES = SIZE / Byte.SIZE;

    public static long highestOneBit(long i) {
        return i & (MIN_VALUE >>> numberOfLeadingZeros(i));
    }

    public static long lowestOneBit(long i) {
        // HD, Section 2-1
        return i & -i;
    }

    @HotSpotIntrinsicCandidate
    public static int numberOfLeadingZeros(long i) {
        int x = (int)(i >>> 32);
        return x == 0 ? 32 + Integer.numberOfLeadingZeros((int)i)
                : Integer.numberOfLeadingZeros(x);
    }

    @HotSpotIntrinsicCandidate
    public static int numberOfTrailingZeros(long i) {
        // HD, Figure 5-14
        int x, y;
        if (i == 0) return 64;
        int n = 63;
        y = (int)i; if (y != 0) { n = n -32; x = y; } else x = (int)(i>>>32);
        y = x <<16; if (y != 0) { n = n -16; x = y; }
        y = x << 8; if (y != 0) { n = n - 8; x = y; }
        y = x << 4; if (y != 0) { n = n - 4; x = y; }
        y = x << 2; if (y != 0) { n = n - 2; x = y; }
        return n - ((x << 1) >>> 31);
    }

     @HotSpotIntrinsicCandidate
     public static int bitCount(long i) {
        // HD, Figure 5-2
        i = i - ((i >>> 1) & 0x5555555555555555L);
        i = (i & 0x3333333333333333L) + ((i >>> 2) & 0x3333333333333333L);
        i = (i + (i >>> 4)) & 0x0f0f0f0f0f0f0f0fL;
        i = i + (i >>> 8);
        i = i + (i >>> 16);
        i = i + (i >>> 32);
        return (int)i & 0x7f;
     }

    public static long rotateLeft(long i, int distance) {
        return (i << distance) | (i >>> -distance);
    }

    public static long rotateRight(long i, int distance) {
        return (i >>> distance) | (i << -distance);
    }

    public static long reverse(long i) {
        // HD, Figure 7-1
        i = (i & 0x5555555555555555L) << 1 | (i >>> 1) & 0x5555555555555555L;
        i = (i & 0x3333333333333333L) << 2 | (i >>> 2) & 0x3333333333333333L;
        i = (i & 0x0f0f0f0f0f0f0f0fL) << 4 | (i >>> 4) & 0x0f0f0f0f0f0f0f0fL;

        return reverseBytes(i);
    }

    public static int signum(long i) {
        // HD, Section 2-7
        return (int) ((i >> 63) | (-i >>> 63));
    }

    @HotSpotIntrinsicCandidate
    public static long reverseBytes(long i) {
        i = (i & 0x00ff00ff00ff00ffL) << 8 | (i >>> 8) & 0x00ff00ff00ff00ffL;
        return (i << 48) | ((i & 0xffff0000L) << 16) |
            ((i >>> 16) & 0xffff0000L) | (i >>> 48);
    }

    public static long sum(long a, long b) {
        return a + b;
    }

    public static long max(long a, long b) {
        return Math.max(a, b);
    }

    public static long min(long a, long b) {
        return Math.min(a, b);
    }

    @Native private static final long serialVersionUID = 4290774380558885855L;
}
