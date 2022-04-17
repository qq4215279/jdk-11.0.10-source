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
import java.util.Objects;
import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.misc.VM;

import static java.lang.String.COMPACT_STRINGS;
import static java.lang.String.LATIN1;
import static java.lang.String.UTF16;

/** 
 *
 * @author liuzhen
 * @date 2022/4/10 23:38
 * @param null 
 * @return 
 */
public final class Integer extends Number implements Comparable<Integer> {
    @Native public static final int   MIN_VALUE = 0x80000000;

    @Native public static final int   MAX_VALUE = 0x7fffffff;

    public static final Class<Integer>  TYPE = (Class<Integer>) Class.getPrimitiveClass("int");

    static final char[] digits = {
        '0' , '1' , '2' , '3' , '4' , '5' ,
        '6' , '7' , '8' , '9' , 'a' , 'b' ,
        'c' , 'd' , 'e' , 'f' , 'g' , 'h' ,
        'i' , 'j' , 'k' , 'l' , 'm' , 'n' ,
        'o' , 'p' , 'q' , 'r' , 's' , 't' ,
        'u' , 'v' , 'w' , 'x' , 'y' , 'z'
    };


    private static String toStringUTF16(int i, int radix) {
        byte[] buf = new byte[33 * 2];
        boolean negative = (i < 0);
        int charPos = 32;
        if (!negative) {
            i = -i;
        }
        while (i <= -radix) {
            StringUTF16.putChar(buf, charPos--, digits[-(i % radix)]);
            i = i / radix;
        }
        StringUTF16.putChar(buf, charPos, digits[-i]);

        if (negative) {
            StringUTF16.putChar(buf, --charPos, '-');
        }
        return StringUTF16.newString(buf, charPos, (33 - charPos));
    }

    public static String toUnsignedString(int i, int radix) {
        return Long.toUnsignedString(toUnsignedLong(i), radix);
    }

    public static String toHexString(int i) {
        return toUnsignedString0(i, 4);
    }

    public static String toOctalString(int i) {
        return toUnsignedString0(i, 3);
    }

    public static String toBinaryString(int i) {
        return toUnsignedString0(i, 1);
    }

    private static String toUnsignedString0(int val, int shift) {
        // assert shift > 0 && shift <=5 : "Illegal shift value";
        int mag = Integer.SIZE - Integer.numberOfLeadingZeros(val);
        int chars = Math.max(((mag + (shift - 1)) / shift), 1);
        if (COMPACT_STRINGS) {
            byte[] buf = new byte[chars];
            formatUnsignedInt(val, shift, buf, 0, chars);
            return new String(buf, LATIN1);
        } else {
            byte[] buf = new byte[chars * 2];
            formatUnsignedIntUTF16(val, shift, buf, 0, chars);
            return new String(buf, UTF16);
        }
    }

    static void formatUnsignedInt(int val, int shift, char[] buf, int offset, int len) {
        // assert shift > 0 && shift <=5 : "Illegal shift value";
        // assert offset >= 0 && offset < buf.length : "illegal offset";
        // assert len > 0 && (offset + len) <= buf.length : "illegal length";
        int charPos = offset + len;
        int radix = 1 << shift;
        int mask = radix - 1;
        do {
            buf[--charPos] = Integer.digits[val & mask];
            val >>>= shift;
        } while (charPos > offset);
    }

    static void formatUnsignedInt(int val, int shift, byte[] buf, int offset, int len) {
        int charPos = offset + len;
        int radix = 1 << shift;
        int mask = radix - 1;
        do {
            buf[--charPos] = (byte)Integer.digits[val & mask];
            val >>>= shift;
        } while (charPos > offset);
    }

    private static void formatUnsignedIntUTF16(int val, int shift, byte[] buf, int offset, int len) {
        int charPos = offset + len;
        int radix = 1 << shift;
        int mask = radix - 1;
        do {
            StringUTF16.putChar(buf, --charPos, Integer.digits[val & mask]);
            val >>>= shift;
        } while (charPos > offset);
    }

    static final byte[] DigitTens = {
        '0', '0', '0', '0', '0', '0', '0', '0', '0', '0',
        '1', '1', '1', '1', '1', '1', '1', '1', '1', '1',
        '2', '2', '2', '2', '2', '2', '2', '2', '2', '2',
        '3', '3', '3', '3', '3', '3', '3', '3', '3', '3',
        '4', '4', '4', '4', '4', '4', '4', '4', '4', '4',
        '5', '5', '5', '5', '5', '5', '5', '5', '5', '5',
        '6', '6', '6', '6', '6', '6', '6', '6', '6', '6',
        '7', '7', '7', '7', '7', '7', '7', '7', '7', '7',
        '8', '8', '8', '8', '8', '8', '8', '8', '8', '8',
        '9', '9', '9', '9', '9', '9', '9', '9', '9', '9',
        } ;

    static final byte[] DigitOnes = {
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        '0', '1', '2', '3', '4', '5', '6', '7', '8', '9',
        } ;


    public static String toUnsignedString(int i) {
        return Long.toString(toUnsignedLong(i));
    }

    /**
     * 第⼀个if判断，如果i<0,sign记下它的符号“-”，同时将i转成整数。
     * 下⾯所有的操作也就只针对整数了，最后在判断sign如果不等于零将 sign 你的值放在char数组的⾸位buf [--charPos] = sign;。
     * @author liuzhen
     * @date 2022/4/9 17:08
     * @param i 被初始化的数字，
     * @param index 这个数字的⻓度(包含了负数的符号“-”)，
     * @param buf 字符串的容器-⼀个char型数组。
     * @return int
     */
    static int getChars(int i, int index, byte[] buf) {
        int q, r;
        int charPos = index;

        boolean negative = i < 0;
        if (!negative) {
            i = -i;
        }

        // Generate two digits per iteration
        while (i <= -100) {
            q = i / 100;
            r = (q * 100) - i;
            i = q;
            buf[--charPos] = DigitOnes[r];
            buf[--charPos] = DigitTens[r];
        }

        // We know there are at most two digits left at this point.
        q = i / 10;
        r = (q * 10) - i;
        buf[--charPos] = (byte)('0' + r);

        // Whatever left is the remaining digit.
        if (q < 0) {
            buf[--charPos] = (byte)('0' - q);
        }

        if (negative) {
            buf[--charPos] = (byte)'-';
        }
        return charPos;
    }

    /**
     * sizeTable
     * @author liuzhen
     * @date 2022/4/9 17:06
     * @param null
     * @return
     */
    // Left here for compatibility reasons, see JDK-8143900.
    static final int [] sizeTable = { 9, 99, 999, 9999, 99999, 999999, 9999999,
                                      99999999, 999999999, Integer.MAX_VALUE };

    /**
     * ⽤来计算参数 x 的位数也就是转成字符串之后的字符串的⻓度，内部结合⼀个已经初始化好的int类型的数组sizeTable来完成这个计算。
     * 实现的形式很巧妙。注意负数包含符号位，所以对于负数的位数是 stringSize(-i) + 1。
     * @author liuzhen
     * @date 2022/4/9 17:05
     * @param x
     * @return int
     */
    static int stringSize(int x) {
        int d = 1;
        if (x >= 0) {
            d = 0;
            x = -x;
        }
        int p = -10;
        for (int i = 1; i < 10; i++) {
            if (x > p)
                return i + d;
            p = 10 * p;
        }
        return 10 + d;
    }

    public static int parseInt(String s) throws NumberFormatException {
        // ⾸先调⽤parseInt(s,10)⽅法，其中s表示我们需要转换的字符串，10表示以⼗进制输出，默认也是10进制
        return parseInt(s,10);
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/9 17:12
     * @param s
     * @param radix
     * @return int
     */
    public static int parseInt(String s, int radix) throws NumberFormatException {
        /*
         * WARNING: This method may be invoked early during VM initialization
         * before IntegerCache is initialized. Care must be taken to not use
         * the valueOf method.
         */

        // 如果转换的字符串如果为null，直接抛出空指针异常
        if (s == null) {
            throw new NumberFormatException("null");
        }

        // 如果转换的radix(默认是10)<2 则抛出数字格式异常，因为进制最⼩是 2 进制
        if (radix < Character.MIN_RADIX) {
            throw new NumberFormatException("radix " + radix + " less than Character.MIN_RADIX");
        }
        // 如果转换的radix(默认是10)>36 则抛出数字格式异常，因为0到9⼀共10位，a到z⼀共26位，所以⼀共36位
        // 也就是最⾼只能有36进制数
        if (radix > Character.MAX_RADIX) {
            throw new NumberFormatException("radix " + radix + " greater than Character.MAX_RADIX");
        }

        boolean negative = false;
        int i = 0, len = s.length(); // len是待转换字符串的⻓度
        int limit = -Integer.MAX_VALUE;

        // 如果待转换字符串⻓度⼤于 0
        if (len > 0) {
            // 获取待转换字符串的第⼀个字符
            char firstChar = s.charAt(0);
            //这⾥主要⽤来判断第⼀个字符是"+"或者"-"，因为这两个字符的 ASCII码都⼩于字符'0'
            if (firstChar < '0') { // Possible leading "+" or "-"
                // 如果第⼀个字符是'-'
                if (firstChar == '-') {
                    negative = true;
                    limit = Integer.MIN_VALUE;
                } else if (firstChar != '+') { // 如果第⼀个字符是不是 '+'，直接抛出异常
                    throw NumberFormatException.forInputString(s);
                }

                // 待转换字符⻓度是1，不能是单独的"+"或者"-"，否则抛出异常
                if (len == 1) { // Cannot have lone "+" or "-"
                    throw NumberFormatException.forInputString(s);
                }
                i++;
            }
            int multmin = limit / radix;

            int result = 0;
            // 通过不断循环，将字符串除掉第⼀个字符之后，根据进制不断相乘在相加得到⼀个正整数
            // ⽐如 parseInt("2abc",16) = 2*16的3次⽅+10*16的2次⽅+11*16+12*1
            // parseInt("123",10) = 1*10的2次⽅+2*10+3*1
            while (i < len) {
                // Accumulating negatively avoids surprises near MAX_VALUE
                int digit = Character.digit(s.charAt(i++), radix);
                if (digit < 0 || result < multmin) {
                    throw NumberFormatException.forInputString(s);
                }
                result *= radix;
                if (result < limit + digit) {
                    throw NumberFormatException.forInputString(s);
                }
                result -= digit;
            }

            // 根据第⼀个字符得到的正负号，在结果前⾯加上符号
            return negative ? result : -result;
        } else { // 如果待转换字符串⻓度⼩于等于0，直接抛出异常
            throw NumberFormatException.forInputString(s);
        }
    }

    public static int parseInt(CharSequence s, int beginIndex, int endIndex, int radix) throws NumberFormatException {
        s = Objects.requireNonNull(s);

        if (beginIndex < 0 || beginIndex > endIndex || endIndex > s.length()) {
            throw new IndexOutOfBoundsException();
        }
        if (radix < Character.MIN_RADIX) {
            throw new NumberFormatException("radix " + radix + " less than Character.MIN_RADIX");
        }
        if (radix > Character.MAX_RADIX) {
            throw new NumberFormatException("radix " + radix + " greater than Character.MAX_RADIX");
        }

        boolean negative = false;
        int i = beginIndex;
        int limit = -Integer.MAX_VALUE;

        if (i < endIndex) {
            char firstChar = s.charAt(i);
            if (firstChar < '0') { // Possible leading "+" or "-"
                if (firstChar == '-') {
                    negative = true;
                    limit = Integer.MIN_VALUE;
                } else if (firstChar != '+') {
                    throw NumberFormatException.forCharSequence(s, beginIndex, endIndex, i);
                }
                i++;
                if (i == endIndex) { // Cannot have lone "+" or "-"
                    throw NumberFormatException.forCharSequence(s, beginIndex, endIndex, i);
                }
            }
            int multmin = limit / radix;
            int result = 0;
            while (i < endIndex) {
                // Accumulating negatively avoids surprises near MAX_VALUE
                int digit = Character.digit(s.charAt(i), radix);
                if (digit < 0 || result < multmin) {
                    throw NumberFormatException.forCharSequence(s, beginIndex, endIndex, i);
                }
                result *= radix;
                if (result < limit + digit) {
                    throw NumberFormatException.forCharSequence(s, beginIndex, endIndex, i);
                }
                i++;
                result -= digit;
            }
            return negative ? result : -result;
        } else {
            throw NumberFormatException.forInputString("");
        }
    }

    public static int parseUnsignedInt(String s, int radix)
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
                if (len <= 5 || // Integer.MAX_VALUE in Character.MAX_RADIX is 6 digits
                    (radix == 10 && len <= 9) ) { // Integer.MAX_VALUE in base 10 is 10 digits
                    return parseInt(s, radix);
                } else {
                    long ell = Long.parseLong(s, radix);
                    if ((ell & 0xffff_ffff_0000_0000L) == 0) {
                        return (int) ell;
                    } else {
                        throw new
                            NumberFormatException(String.format("String value %s exceeds " +
                                                                "range of unsigned int.", s));
                    }
                }
            }
        } else {
            throw NumberFormatException.forInputString(s);
        }
    }

    public static int parseUnsignedInt(CharSequence s, int beginIndex, int endIndex, int radix)
                throws NumberFormatException {
        s = Objects.requireNonNull(s);

        if (beginIndex < 0 || beginIndex > endIndex || endIndex > s.length()) {
            throw new IndexOutOfBoundsException();
        }
        int start = beginIndex, len = endIndex - beginIndex;

        if (len > 0) {
            char firstChar = s.charAt(start);
            if (firstChar == '-') {
                throw new
                    NumberFormatException(String.format("Illegal leading minus sign " +
                                                       "on unsigned string %s.", s));
            } else {
                if (len <= 5 || // Integer.MAX_VALUE in Character.MAX_RADIX is 6 digits
                        (radix == 10 && len <= 9)) { // Integer.MAX_VALUE in base 10 is 10 digits
                    return parseInt(s, start, start + len, radix);
                } else {
                    long ell = Long.parseLong(s, start, start + len, radix);
                    if ((ell & 0xffff_ffff_0000_0000L) == 0) {
                        return (int) ell;
                    } else {
                        throw new
                            NumberFormatException(String.format("String value %s exceeds " +
                                                                "range of unsigned int.", s));
                    }
                }
            }
        } else {
            throw new NumberFormatException("");
        }
    }

    public static int parseUnsignedInt(String s) throws NumberFormatException {
        return parseUnsignedInt(s, 10);
    }


    private static class IntegerCache {
        static final int low = -128;
        static final int high;
        static final Integer cache[];

        static {
            // high value may be configured by property
            int h = 127;
            String integerCacheHighPropValue =
                VM.getSavedProperty("java.lang.Integer.IntegerCache.high");
            if (integerCacheHighPropValue != null) {
                try {
                    int i = parseInt(integerCacheHighPropValue);
                    i = Math.max(i, 127);
                    // Maximum array size is Integer.MAX_VALUE
                    h = Math.min(i, Integer.MAX_VALUE - (-low) -1);
                } catch( NumberFormatException nfe) {
                    // If the property cannot be parsed into an int, ignore it.
                }
            }
            high = h;

            cache = new Integer[(high - low) + 1];
            int j = low;
            for(int k = 0; k < cache.length; k++)
                cache[k] = new Integer(j++);

            // range [-128, 127] must be interned (JLS7 5.1.7)
            assert IntegerCache.high >= 127;
        }

        private IntegerCache() {}
    }

    /**
     * valueOf(int i)
     * 其实最后返回的也是通过new Integer() 产⽣的对象！
     * 但是这⾥要注意前⾯的⼀段代码，当i的值-128 <= i <= 127 返回的是缓存类中的对象，并没有重新创建⼀个新的对象，
     * 这在通过 equals 进⾏⽐较的时候我们要注意。
     * 这就是基本数据类型的⾃动装箱，128是基本数据类型，然后被解析成Integer类。
     *
     * 简单来讲：
     *  ⾃动装箱就是Integer.valueOf(int i);
     *  ⾃动拆箱就是 i.intValue();
     * @author liuzhen
     * @date 2022/4/9 17:13
     * @param i
     * @return java.lang.Integer
     */
    @HotSpotIntrinsicCandidate
    public static Integer valueOf(int i) {
        // 从缓存中取出来
        if (i >= IntegerCache.low && i <= IntegerCache.high)
            return IntegerCache.cache[i + (-IntegerCache.low)];
        return new Integer(i);
    }

    public static Integer valueOf(String s) throws NumberFormatException {
        return Integer.valueOf(parseInt(s, 10));
    }

    public static Integer valueOf(String s, int radix) throws NumberFormatException {
        return Integer.valueOf(parseInt(s,radix));
    }

    private final int value;

    @Deprecated(since="9")
    public Integer(int value) {
        this.value = value;
    }

    @Deprecated(since="9")
    public Integer(String s) throws NumberFormatException {
        this.value = parseInt(s, 10);
    }

    public byte byteValue() {
        return (byte)value;
    }

    public short shortValue() {
        return (short)value;
    }

    @HotSpotIntrinsicCandidate
    public int intValue() {
        return value;
    }

    public long longValue() {
        return (long)value;
    }

    public float floatValue() {
        return (float)value;
    }

    public double doubleValue() {
        return (double)value;
    }

    /**
     * 这三个⽅法重载，能返回⼀个整型数据所表示的字符串形式，其中最后⼀个⽅法 toString(int,int) 第⼆个参数是表示的进制数。
     * @author liuzhen
     * @date 2022/4/9 17:05
     * @param
     * @return java.lang.String
     */
    public String toString() {
        return toString(value);
    }

    @HotSpotIntrinsicCandidate
    public static String toString(int i) {
        // ⽤来计算参数 x 的位数也就是转成字符串之后的字符串的⻓度，内部结合⼀个已经初始化好的int类型的数组sizeTable来完成这个计算。
        int size = stringSize(i);
        if (COMPACT_STRINGS) {
            byte[] buf = new byte[size];
            //
            getChars(i, size, buf);
            return new String(buf, LATIN1);
        } else {
            byte[] buf = new byte[size * 2];
            StringUTF16.getChars(i, size, buf);
            return new String(buf, UTF16);
        }
    }

    public static String toString(int i, int radix) {
        if (radix < Character.MIN_RADIX || radix > Character.MAX_RADIX)
            radix = 10;

        /* Use the faster version */
        if (radix == 10) {
            return toString(i);
        }

        if (COMPACT_STRINGS) {
            byte[] buf = new byte[33];
            boolean negative = (i < 0);
            int charPos = 32;

            if (!negative) {
                i = -i;
            }

            while (i <= -radix) {
                buf[charPos--] = (byte)digits[-(i % radix)];
                i = i / radix;
            }
            buf[charPos] = (byte)digits[-i];

            if (negative) {
                buf[--charPos] = '-';
            }

            return StringLatin1.newString(buf, charPos, (33 - charPos));
        }
        return toStringUTF16(i, radix);
    }

    /**
     * 直接返回其 int 类型的数据。
     * @author liuzhen
     * @date 2022/4/9 17:18
     * @param
     * @return int
     */
    @Override
    public int hashCode() {
        return Integer.hashCode(value);
    }

    public static int hashCode(int value) {
        return value;
    }

    /**
     * 先通过 instanceof 关键字判断两个⽐较对象的关系，然后将对象强转为Integer，在通过⾃动拆箱，转换成两个基本数据类 int，然后通过 == ⽐较。
     * @author liuzhen
     * @date 2022/4/9 17:17
     * @param obj
     * @return boolean
     */
    public boolean equals(Object obj) {
        if (obj instanceof Integer) {
            return value == ((Integer)obj).intValue();
        }
        return false;
    }

    public static Integer getInteger(String nm) {
        return getInteger(nm, null);
    }

    public static Integer getInteger(String nm, int val) {
        Integer result = getInteger(nm, null);
        return (result == null) ? Integer.valueOf(val) : result;
    }

    public static Integer getInteger(String nm, Integer val) {
        String v = null;
        try {
            v = System.getProperty(nm);
        } catch (IllegalArgumentException | NullPointerException e) {
        }
        if (v != null) {
            try {
                return Integer.decode(v);
            } catch (NumberFormatException e) {
            }
        }
        return val;
    }

    public static Integer decode(String nm) throws NumberFormatException {
        int radix = 10;
        int index = 0;
        boolean negative = false;
        Integer result;

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
            result = Integer.valueOf(nm.substring(index), radix);
            result = negative ? Integer.valueOf(-result.intValue()) : result;
        } catch (NumberFormatException e) {
            // If number is Integer.MIN_VALUE, we'll end up here. The next line
            // handles this case, and causes any genuine format error to be
            // rethrown.
            String constant = negative ? ("-" + nm.substring(index))
                                       : nm.substring(index);
            result = Integer.valueOf(constant, radix);
        }
        return result;
    }

    /**
     * compareTo(Integer anotherInteger)
     * @author liuzhen
     * @date 2022/4/9 17:20
     * @param anotherInteger
     * @return int
     */
    public int compareTo(Integer anotherInteger) {
        return compare(this.value, anotherInteger.value);
    }

    public static int compare(int x, int y) {
        return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }

    public static int compareUnsigned(int x, int y) {
        return compare(x + MIN_VALUE, y + MIN_VALUE);
    }

    public static long toUnsignedLong(int x) {
        return ((long) x) & 0xffffffffL;
    }

    public static int divideUnsigned(int dividend, int divisor) {
        // In lieu of tricky code, for now just use long arithmetic.
        return (int)(toUnsignedLong(dividend) / toUnsignedLong(divisor));
    }

    public static int remainderUnsigned(int dividend, int divisor) {
        // In lieu of tricky code, for now just use long arithmetic.
        return (int)(toUnsignedLong(dividend) % toUnsignedLong(divisor));
    }



    @Native public static final int SIZE = 32;

    public static final int BYTES = SIZE / Byte.SIZE;

    public static int highestOneBit(int i) {
        return i & (MIN_VALUE >>> numberOfLeadingZeros(i));
    }

    public static int lowestOneBit(int i) {
        // HD, Section 2-1
        return i & -i;
    }

    @HotSpotIntrinsicCandidate
    public static int numberOfLeadingZeros(int i) {
        // HD, Count leading 0's
        if (i <= 0)
            return i == 0 ? 32 : 0;
        int n = 31;
        if (i >= 1 << 16) { n -= 16; i >>>= 16; }
        if (i >= 1 <<  8) { n -=  8; i >>>=  8; }
        if (i >= 1 <<  4) { n -=  4; i >>>=  4; }
        if (i >= 1 <<  2) { n -=  2; i >>>=  2; }
        return n - (i >>> 1);
    }

    @HotSpotIntrinsicCandidate
    public static int numberOfTrailingZeros(int i) {
        // HD, Figure 5-14
        int y;
        if (i == 0) return 32;
        int n = 31;
        y = i <<16; if (y != 0) { n = n -16; i = y; }
        y = i << 8; if (y != 0) { n = n - 8; i = y; }
        y = i << 4; if (y != 0) { n = n - 4; i = y; }
        y = i << 2; if (y != 0) { n = n - 2; i = y; }
        return n - ((i << 1) >>> 31);
    }

    @HotSpotIntrinsicCandidate
    public static int bitCount(int i) {
        // HD, Figure 5-2
        i = i - ((i >>> 1) & 0x55555555);
        i = (i & 0x33333333) + ((i >>> 2) & 0x33333333);
        i = (i + (i >>> 4)) & 0x0f0f0f0f;
        i = i + (i >>> 8);
        i = i + (i >>> 16);
        return i & 0x3f;
    }

    public static int rotateLeft(int i, int distance) {
        return (i << distance) | (i >>> -distance);
    }

    public static int rotateRight(int i, int distance) {
        return (i >>> distance) | (i << -distance);
    }

    public static int reverse(int i) {
        // HD, Figure 7-1
        i = (i & 0x55555555) << 1 | (i >>> 1) & 0x55555555;
        i = (i & 0x33333333) << 2 | (i >>> 2) & 0x33333333;
        i = (i & 0x0f0f0f0f) << 4 | (i >>> 4) & 0x0f0f0f0f;

        return reverseBytes(i);
    }

    public static int signum(int i) {
        // HD, Section 2-7
        return (i >> 31) | (-i >>> 31);
    }

    @HotSpotIntrinsicCandidate
    public static int reverseBytes(int i) {
        return (i << 24)            |
               ((i & 0xff00) << 8)  |
               ((i >>> 8) & 0xff00) |
               (i >>> 24);
    }

    public static int sum(int a, int b) {
        return a + b;
    }

    public static int max(int a, int b) {
        return Math.max(a, b);
    }

    public static int min(int a, int b) {
        return Math.min(a, b);
    }

    @Native private static final long serialVersionUID = 1360826667806852920L;
}
