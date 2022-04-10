/*
 * Copyright (c) 1994, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.lang;

import java.io.ObjectStreamField;
import java.io.UnsupportedEncodingException;
import java.lang.annotation.Native;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Formatter;
import java.util.Locale;
import java.util.Objects;
import java.util.Spliterator;
import java.util.StringJoiner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import jdk.internal.HotSpotIntrinsicCandidate;
import jdk.internal.vm.annotation.Stable;

/**
 *
 * @author liuzhen
 * @date 2022/4/10 22:34
 * @return
 */
public final class String implements java.io.Serializable, Comparable<String>, CharSequence {

    /**⽤来存储字符串 */
    @Stable
    private final byte[] value;

    private final byte coder;

    /** 缓存字符串的哈希码 */
    private int hash; // Default to 0

    /** 实现序列化的标识 */
    private static final long serialVersionUID = -6849794470754667710L;

    static final boolean COMPACT_STRINGS;

    static {
        COMPACT_STRINGS = true;
    }

    private static final ObjectStreamField[] serialPersistentFields =
        new ObjectStreamField[0];

    /**
     * ⼀个 String 字符串实际上是⼀个 char 数组。
     * 构造方法：String 类的构造⽅法很多。可以通过初始化⼀个字符串，或者字符数组，或者字节数组等等来创建⼀个 String 对象。
     * @author liuzhen
     * @date 2022/4/9 17:25
     * @param
     * @return
     */
    public String() {
        this.value = "".value;
        this.coder = "".coder;
    }

    @HotSpotIntrinsicCandidate
    public String(String original) {
        this.value = original.value;
        this.coder = original.coder;
        this.hash = original.hash;
    }

    public String(char value[]) {
        this(value, 0, value.length, null);
    }

    public String(char value[], int offset, int count) {
        this(value, offset, count, rangeCheck(value, offset, count));
    }

    private static Void rangeCheck(char[] value, int offset, int count) {
        checkBoundsOffCount(offset, count, value.length);
        return null;
    }

    public String(int[] codePoints, int offset, int count) {
        checkBoundsOffCount(offset, count, codePoints.length);
        if (count == 0) {
            this.value = "".value;
            this.coder = "".coder;
            return;
        }
        if (COMPACT_STRINGS) {
            byte[] val = StringLatin1.toBytes(codePoints, offset, count);
            if (val != null) {
                this.coder = LATIN1;
                this.value = val;
                return;
            }
        }
        this.coder = UTF16;
        this.value = StringUTF16.toBytes(codePoints, offset, count);
    }

    @Deprecated(since="1.1")
    public String(byte ascii[], int hibyte, int offset, int count) {
        checkBoundsOffCount(offset, count, ascii.length);
        if (count == 0) {
            this.value = "".value;
            this.coder = "".coder;
            return;
        }
        if (COMPACT_STRINGS && (byte)hibyte == 0) {
            this.value = Arrays.copyOfRange(ascii, offset, offset + count);
            this.coder = LATIN1;
        } else {
            hibyte <<= 8;
            byte[] val = StringUTF16.newBytesFor(count);
            for (int i = 0; i < count; i++) {
                StringUTF16.putChar(val, i, hibyte | (ascii[offset++] & 0xff));
            }
            this.value = val;
            this.coder = UTF16;
        }
    }

    @Deprecated(since="1.1")
    public String(byte ascii[], int hibyte) {
        this(ascii, hibyte, 0, ascii.length);
    }

    public String(byte bytes[], int offset, int length, String charsetName)
            throws UnsupportedEncodingException {
        if (charsetName == null)
            throw new NullPointerException("charsetName");
        checkBoundsOffCount(offset, length, bytes.length);
        StringCoding.Result ret =
            StringCoding.decode(charsetName, bytes, offset, length);
        this.value = ret.value;
        this.coder = ret.coder;
    }

    public String(byte bytes[], int offset, int length, Charset charset) {
        if (charset == null)
            throw new NullPointerException("charset");
        checkBoundsOffCount(offset, length, bytes.length);
        StringCoding.Result ret =
            StringCoding.decode(charset, bytes, offset, length);
        this.value = ret.value;
        this.coder = ret.coder;
    }

    public String(byte bytes[], String charsetName)
            throws UnsupportedEncodingException {
        this(bytes, 0, bytes.length, charsetName);
    }

    public String(byte bytes[], Charset charset) {
        this(bytes, 0, bytes.length, charset);
    }

    public String(byte bytes[], int offset, int length) {
        checkBoundsOffCount(offset, length, bytes.length);
        StringCoding.Result ret = StringCoding.decode(bytes, offset, length);
        this.value = ret.value;
        this.coder = ret.coder;
    }

    public String(byte[] bytes) {
        this(bytes, 0, bytes.length);
    }

    public String(StringBuffer buffer) {
        this(buffer.toString());
    }

    public String(StringBuilder builder) {
        this(builder, null);
    }

    public int length() {
        return value.length >> coder();
    }

    public boolean isEmpty() {
        return value.length == 0;
    }

    /** 
     * 我们知道⼀个字符串是由⼀个字符数组组成，这个⽅法是通过传⼊的索引（数组下标），返回指定索引的单个字符。
     * @author liuzhen
     * @date 2022/4/9 17:33 
     * @param index 
     * @return char
     */
    public char charAt(int index) {
        if (isLatin1()) {
            return StringLatin1.charAt(value, index);
        } else {
            return StringUTF16.charAt(value, index);
        }
    }

    public int codePointAt(int index) {
        if (isLatin1()) {
            checkIndex(index, value.length);
            return value[index] & 0xff;
        }
        int length = value.length >> 1;
        checkIndex(index, length);
        return StringUTF16.codePointAt(value, index, length);
    }

    public int codePointBefore(int index) {
        int i = index - 1;
        if (i < 0 || i >= length()) {
            throw new StringIndexOutOfBoundsException(index);
        }
        if (isLatin1()) {
            return (value[i] & 0xff);
        }
        return StringUTF16.codePointBefore(value, index);
    }

    public int codePointCount(int beginIndex, int endIndex) {
        if (beginIndex < 0 || beginIndex > endIndex ||
            endIndex > length()) {
            throw new IndexOutOfBoundsException();
        }
        if (isLatin1()) {
            return endIndex - beginIndex;
        }
        return StringUTF16.codePointCount(value, beginIndex, endIndex);
    }

    public int offsetByCodePoints(int index, int codePointOffset) {
        if (index < 0 || index > length()) {
            throw new IndexOutOfBoundsException();
        }
        return Character.offsetByCodePoints(this, index, codePointOffset);
    }

    public void getChars(int srcBegin, int srcEnd, char dst[], int dstBegin) {
        checkBoundsBeginEnd(srcBegin, srcEnd, length());
        checkBoundsOffCount(dstBegin, srcEnd - srcBegin, dst.length);
        if (isLatin1()) {
            StringLatin1.getChars(value, srcBegin, srcEnd, dst, dstBegin);
        } else {
            StringUTF16.getChars(value, srcBegin, srcEnd, dst, dstBegin);
        }
    }

    @Deprecated(since="1.1")
    public void getBytes(int srcBegin, int srcEnd, byte dst[], int dstBegin) {
        checkBoundsBeginEnd(srcBegin, srcEnd, length());
        Objects.requireNonNull(dst);
        checkBoundsOffCount(dstBegin, srcEnd - srcBegin, dst.length);
        if (isLatin1()) {
            StringLatin1.getBytes(value, srcBegin, srcEnd, dst, dstBegin);
        } else {
            StringUTF16.getBytes(value, srcBegin, srcEnd, dst, dstBegin);
        }
    }

    public byte[] getBytes(String charsetName)
            throws UnsupportedEncodingException {
        if (charsetName == null) throw new NullPointerException();
        return StringCoding.encode(charsetName, coder(), value);
    }

    public byte[] getBytes(Charset charset) {
        if (charset == null) throw new NullPointerException();
        return StringCoding.encode(charset, coder(), value);
     }

    public byte[] getBytes() {
        return StringCoding.encode(coder(), value);
    }

    /**
     * 重写了 equals ⽅法，⽐较的是组成字符串的每⼀个字符是否相同，如果都相同则返回true，否则返回false。
     * @author liuzhen
     * @date 2022/4/9 17:27
     * @param anObject
     * @return boolean
     */
    public boolean equals(Object anObject) {
        if (this == anObject) {
            return true;
        }
        if (anObject instanceof String) {
            String aString = (String)anObject;
            if (coder() == aString.coder()) {
                return isLatin1() ? StringLatin1.equals(value, aString.value) : StringUTF16.equals(value, aString.value);
            }
        }
        return false;
    }

    public boolean contentEquals(StringBuffer sb) {
        return contentEquals((CharSequence)sb);
    }

    private boolean nonSyncContentEquals(AbstractStringBuilder sb) {
        int len = length();
        if (len != sb.length()) {
            return false;
        }
        byte v1[] = value;
        byte v2[] = sb.getValue();
        if (coder() == sb.getCoder()) {
            int n = v1.length;
            for (int i = 0; i < n; i++) {
                if (v1[i] != v2[i]) {
                    return false;
                }
            }
        } else {
            if (!isLatin1()) {  // utf16 str and latin1 abs can never be "equal"
                return false;
            }
            return StringUTF16.contentEquals(v1, v2, len);
        }
        return true;
    }

    public boolean contentEquals(CharSequence cs) {
        // Argument is a StringBuffer, StringBuilder
        if (cs instanceof AbstractStringBuilder) {
            if (cs instanceof StringBuffer) {
                synchronized(cs) {
                   return nonSyncContentEquals((AbstractStringBuilder)cs);
                }
            } else {
                return nonSyncContentEquals((AbstractStringBuilder)cs);
            }
        }
        // Argument is a String
        if (cs instanceof String) {
            return equals(cs);
        }
        // Argument is a generic CharSequence
        int n = cs.length();
        if (n != length()) {
            return false;
        }
        byte[] val = this.value;
        if (isLatin1()) {
            for (int i = 0; i < n; i++) {
                if ((val[i] & 0xff) != cs.charAt(i)) {
                    return false;
                }
            }
        } else {
            if (!StringUTF16.contentEquals(val, cs, n)) {
                return false;
            }
        }
        return true;
    }

    public boolean equalsIgnoreCase(String anotherString) {
        return (this == anotherString) ? true
                : (anotherString != null)
                && (anotherString.length() == length())
                && regionMatches(true, 0, anotherString, 0, length());
    }

    /**
     * 源码也很好理解，该⽅法是按字⺟顺序⽐较两个字符串，是基于字符串中每个字符的 Unicode 值。
     * 当两个字符串某个位置的字符不同时，返回的是这⼀位置的字符 Unicode 值之差，当两个字符串都相同
     * 时，返回两个字符串⻓度之差。
     * 　　compareToIgnoreCase() ⽅法在 compareTo ⽅法的基础上忽略⼤⼩写，我们知道⼤写字⺟是⽐⼩
     * 写字⺟的Unicode值⼩32的，底层实现是先都转换成⼤写⽐较，然后都转换成⼩写进⾏⽐较。
     * @author liuzhen
     * @date 2022/4/9 17:35
     * @param anotherString
     * @return int
     */
    public int compareTo(String anotherString) {
        byte v1[] = value;
        byte v2[] = anotherString.value;
        if (coder() == anotherString.coder()) {
            return isLatin1() ? StringLatin1.compareTo(v1, v2) : StringUTF16.compareTo(v1, v2);
        }
        return isLatin1() ? StringLatin1.compareToUTF16(v1, v2) : StringUTF16.compareToLatin1(v1, v2);
    }

    public static final Comparator<String> CASE_INSENSITIVE_ORDER
                                         = new CaseInsensitiveComparator();
    private static class CaseInsensitiveComparator
            implements Comparator<String>, java.io.Serializable {
        // use serialVersionUID from JDK 1.2.2 for interoperability
        private static final long serialVersionUID = 8575799808933029326L;

        public int compare(String s1, String s2) {
            byte v1[] = s1.value;
            byte v2[] = s2.value;
            if (s1.coder() == s2.coder()) {
                return s1.isLatin1() ? StringLatin1.compareToCI(v1, v2)
                                     : StringUTF16.compareToCI(v1, v2);
            }
            return s1.isLatin1() ? StringLatin1.compareToCI_UTF16(v1, v2)
                                 : StringUTF16.compareToCI_Latin1(v1, v2);
        }

        /** Replaces the de-serialized object. */
        private Object readResolve() { return CASE_INSENSITIVE_ORDER; }
    }

    public int compareToIgnoreCase(String str) {
        return CASE_INSENSITIVE_ORDER.compare(this, str);
    }

    public boolean regionMatches(int toffset, String other, int ooffset, int len) {
        byte tv[] = value;
        byte ov[] = other.value;
        // Note: toffset, ooffset, or len might be near -1>>>1.
        if ((ooffset < 0) || (toffset < 0) ||
             (toffset > (long)length() - len) ||
             (ooffset > (long)other.length() - len)) {
            return false;
        }
        if (coder() == other.coder()) {
            if (!isLatin1() && (len > 0)) {
                toffset = toffset << 1;
                ooffset = ooffset << 1;
                len = len << 1;
            }
            while (len-- > 0) {
                if (tv[toffset++] != ov[ooffset++]) {
                    return false;
                }
            }
        } else {
            if (coder() == LATIN1) {
                while (len-- > 0) {
                    if (StringLatin1.getChar(tv, toffset++) !=
                        StringUTF16.getChar(ov, ooffset++)) {
                        return false;
                    }
                }
            } else {
                while (len-- > 0) {
                    if (StringUTF16.getChar(tv, toffset++) !=
                        StringLatin1.getChar(ov, ooffset++)) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public boolean regionMatches(boolean ignoreCase, int toffset,
            String other, int ooffset, int len) {
        if (!ignoreCase) {
            return regionMatches(toffset, other, ooffset, len);
        }
        // Note: toffset, ooffset, or len might be near -1>>>1.
        if ((ooffset < 0) || (toffset < 0)
                || (toffset > (long)length() - len)
                || (ooffset > (long)other.length() - len)) {
            return false;
        }
        byte tv[] = value;
        byte ov[] = other.value;
        if (coder() == other.coder()) {
            return isLatin1()
              ? StringLatin1.regionMatchesCI(tv, toffset, ov, ooffset, len)
              : StringUTF16.regionMatchesCI(tv, toffset, ov, ooffset, len);
        }
        return isLatin1()
              ? StringLatin1.regionMatchesCI_UTF16(tv, toffset, ov, ooffset, len)
              : StringUTF16.regionMatchesCI_Latin1(tv, toffset, ov, ooffset, len);
    }

    public boolean startsWith(String prefix, int toffset) {
        // Note: toffset might be near -1>>>1.
        if (toffset < 0 || toffset > length() - prefix.length()) {
            return false;
        }
        byte ta[] = value;
        byte pa[] = prefix.value;
        int po = 0;
        int pc = pa.length;
        if (coder() == prefix.coder()) {
            int to = isLatin1() ? toffset : toffset << 1;
            while (po < pc) {
                if (ta[to++] != pa[po++]) {
                    return false;
                }
            }
        } else {
            if (isLatin1()) {  // && pcoder == UTF16
                return false;
            }
            // coder == UTF16 && pcoder == LATIN1)
            while (po < pc) {
                if (StringUTF16.getChar(ta, toffset++) != (pa[po++] & 0xff)) {
                    return false;
               }
            }
        }
        return true;
    }

    public boolean startsWith(String prefix) {
        return startsWith(prefix, 0);
    }

    public boolean endsWith(String suffix) {
        return startsWith(suffix, length() - suffix.length());
    }

    /**
     *
     * String 类的 hashCode 算法很简单，主要就是中间的 for 循环，计算公式如下：s[0]31^(n-1) + s[1]31^(n-2) + ... + s[n-1]
     * s 数组即源码中的 val 数组，也就是构成字符串的字符数组。这⾥有个数字 31 ，为什么选择31作为乘积因⼦，⽽且没有⽤⼀个常量来声明？
     * 主要原因有两个：
     * 　1. 31是⼀个不⼤不⼩的质数，是作为 hashCode 乘⼦的优选质数之⼀。
     * 　2. 31可以被 JVM 优化，31 * i = (i << 5) - i。因为移位运算⽐乘法运⾏更快更省性能。
     * @author liuzhen
     * @date 2022/4/9 17:28
     * @param
     * @return int
     */
    public int hashCode() {
        int h = hash;
        if (h == 0 && value.length > 0) {
            hash = h = isLatin1() ? StringLatin1.hashCode(value) : StringUTF16.hashCode(value);
        }
        return h;
    }

    /**
     *
     * indexOf(int ch)，参数 ch 其实是字符的 Unicode 值，这⾥也可以放单个字符（默认转成int），作⽤是返回指定字符第⼀次出现的此字符串中的索引。
     * 其内部是调⽤ indexOf(int ch, int fromIndex)，只不过这⾥的 fromIndex =0 ，因为是从 0 开始搜索；
     * ⽽ indexOf(int ch, int fromIndex) 作⽤也是返回⾸次出现的此字符串内的索引，但是从指定索引处开始搜索。　　
     * @author liuzhen
     * @date 2022/4/9 17:41
     * @param ch
     * @return int
     */
    public int indexOf(int ch) {
        // 从第⼀个字符开始搜索
        return indexOf(ch, 0);
    }

    public int indexOf(int ch, int fromIndex) {
        return isLatin1() ? StringLatin1.indexOf(value, ch, fromIndex) : StringUTF16.indexOf(value, ch, fromIndex);
    }

    public int indexOf(String str) {
        if (coder() == str.coder()) {
            return isLatin1() ? StringLatin1.indexOf(value, str.value) : StringUTF16.indexOf(value, str.value);
        }
        if (coder() == LATIN1) {  // str.coder == UTF16
            return -1;
        }
        return StringUTF16.indexOfLatin1(value, str.value);
    }

    public int indexOf(String str, int fromIndex) {
        return indexOf(value, coder(), length(), str, fromIndex);
    }

    static int indexOf(byte[] src, byte srcCoder, int srcCount, String tgtStr, int fromIndex) {
        byte[] tgt = tgtStr.value;
        byte tgtCoder = tgtStr.coder();
        int tgtCount = tgtStr.length();

        if (fromIndex >= srcCount) {
            return (tgtCount == 0 ? srcCount : -1);
        }
        if (fromIndex < 0) {
            fromIndex = 0;
        }
        if (tgtCount == 0) {
            return fromIndex;
        }
        if (tgtCount > srcCount) {
            return -1;
        }
        if (srcCoder == tgtCoder) {
            return srcCoder == LATIN1 ? StringLatin1.indexOf(src, srcCount, tgt, tgtCount, fromIndex) : StringUTF16.indexOf(src, srcCount, tgt,
                                                                                                                            tgtCount, fromIndex);
        }
        if (srcCoder == LATIN1) {    //  && tgtCoder == UTF16
            return -1;
        }
        // srcCoder == UTF16 && tgtCoder == LATIN1) {
        return StringUTF16.indexOfLatin1(src, srcCount, tgt, tgtCount, fromIndex);
    }

    public int lastIndexOf(int ch) {
        return lastIndexOf(ch, length() - 1);
    }

    public int lastIndexOf(int ch, int fromIndex) {
        return isLatin1() ? StringLatin1.lastIndexOf(value, ch, fromIndex) : StringUTF16.lastIndexOf(value, ch, fromIndex);
    }

    public int lastIndexOf(String str) {
        return lastIndexOf(str, length());
    }

    public int lastIndexOf(String str, int fromIndex) {
        return lastIndexOf(value, coder(), length(), str, fromIndex);
    }

    static int lastIndexOf(byte[] src, byte srcCoder, int srcCount, String tgtStr, int fromIndex) {
        byte[] tgt = tgtStr.value;
        byte tgtCoder = tgtStr.coder();
        int tgtCount = tgtStr.length();
        /*
         * Check arguments; return immediately where possible. For
         * consistency, don't check for null str.
         */
        int rightIndex = srcCount - tgtCount;
        if (fromIndex > rightIndex) {
            fromIndex = rightIndex;
        }
        if (fromIndex < 0) {
            return -1;
        }
        /* Empty string always matches. */
        if (tgtCount == 0) {
            return fromIndex;
        }
        if (srcCoder == tgtCoder) {
            return srcCoder == LATIN1 ? StringLatin1.lastIndexOf(src, srcCount, tgt, tgtCount, fromIndex) : StringUTF16.lastIndexOf(src, srcCount,
                                                                                                                                    tgt, tgtCount,
                                                                                                                                    fromIndex);
        }
        if (srcCoder == LATIN1) {    // && tgtCoder == UTF16
            return -1;
        }
        // srcCoder == UTF16 && tgtCoder == LATIN1
        return StringUTF16.lastIndexOfLatin1(src, srcCount, tgt, tgtCount, fromIndex);
    }

    /**
     * 返回⼀个从索引 beginIndex 开始⼀直到结尾的⼦字符串。
     * @author liuzhen
     * @date 2022/4/9 18:04
     * @param beginIndex
     * @return java.lang.String
     */
    public String substring(int beginIndex) {
        // 如果索引⼩于0，直接抛出异常
        if (beginIndex < 0) {
            throw new StringIndexOutOfBoundsException(beginIndex);
        }
        // subLen等于字符串⻓度减去索引
        int subLen = length() - beginIndex;
        if (subLen < 0) {
            throw new StringIndexOutOfBoundsException(subLen);
        }
        if (beginIndex == 0) {
            return this;
        }

        // 1、如果索引值beginIdex == 0，直接返回原字符串
        // 2、如果不等于0，则返回从beginIndex开始，⼀直到结尾
        return isLatin1() ? StringLatin1.newString(value, beginIndex, subLen) : StringUTF16.newString(value, beginIndex, subLen);
    }

    /**
     * substring(int beginIndex, int endIndex)：返回⼀个从索引 beginIndex 开始，到 endIndex结尾的⼦字符串。
     * @author liuzhen
     * @date 2022/4/9 18:05
     * @param beginIndex
     * @param endIndex
     * @return java.lang.String
     */
    public String substring(int beginIndex, int endIndex) {
        int length = length();
        checkBoundsBeginEnd(beginIndex, endIndex, length);
        int subLen = endIndex - beginIndex;
        if (beginIndex == 0 && endIndex == length) {
            return this;
        }
        return isLatin1() ? StringLatin1.newString(value, beginIndex, subLen) : StringUTF16.newString(value, beginIndex, subLen);
    }

    public CharSequence subSequence(int beginIndex, int endIndex) {
        return this.substring(beginIndex, endIndex);
    }

    /**
     * 该⽅法是将指定的字符串连接到此字符串的末尾。
     * ⾸先判断要拼接的字符串⻓度是否为0，如果为0，则直接返回原字符串。如果不为0，则通过Arrays ⼯具类（后⾯会详细介绍这个⼯具类）的copyOf⽅法创建⼀个新的字符数组，
     * ⻓度为原字符串和要拼接的字符串之和，前⾯填充原字符串，后⾯为空。接着在通过 getChars ⽅法将要拼接的字符串放⼊新字符串后⾯为空的位置。
     * 注意：返回值是 new String(buf, true)，也就是重新通过 new 关键字创建了⼀个新的字符串，原字符串是不变的。这也是前⾯我们说的⼀旦⼀个String对象被创建, 包含在这个对象中的字符序列是不可改变的。
     * @author liuzhen
     * @date 2022/4/9 17:38
     * @param str
     * @return java.lang.String
     */
    public String concat(String str) {
        if (str.isEmpty()) {
            return this;
        }
        if (coder() == str.coder()) {
            byte[] val = this.value;
            byte[] oval = str.value;
            int len = val.length + oval.length;
            byte[] buf = Arrays.copyOf(val, len);
            System.arraycopy(oval, 0, buf, val.length, oval.length);
            return new String(buf, coder);
        }

        int len = length();
        int olen = str.length();
        byte[] buf = StringUTF16.newBytesFor(len + olen);
        getBytes(buf, 0, UTF16);
        str.getBytes(buf, len, UTF16);
        return new String(buf, UTF16);
    }

    /** 
     * 将原字符串中所有的oldChar字符都替换成newChar字符，返回⼀个新的字符串。
     * @author liuzhen
     * @date 2022/4/9 18:02 
     * @param oldChar
     * @param newChar 
     * @return java.lang.String
     */
    public String replace(char oldChar, char newChar) {
        if (oldChar != newChar) {
            String ret = isLatin1() ? StringLatin1.replace(value, oldChar, newChar) : StringUTF16.replace(value, oldChar, newChar);
            if (ret != null) {
                return ret;
            }
        }
        return this;
    }

    public String replace(CharSequence target, CharSequence replacement) {
        String tgtStr = target.toString();
        String replStr = replacement.toString();
        int j = indexOf(tgtStr);
        if (j < 0) {
            return this;
        }
        int tgtLen = tgtStr.length();
        int tgtLen1 = Math.max(tgtLen, 1);
        int thisLen = length();

        int newLenHint = thisLen - tgtLen + replStr.length();
        if (newLenHint < 0) {
            throw new OutOfMemoryError();
        }
        StringBuilder sb = new StringBuilder(newLenHint);
        int i = 0;
        do {
            sb.append(this, i, j).append(replStr);
            i = j + tgtLen;
        } while (j < thisLen && (j = indexOf(tgtStr, j + tgtLen1)) > 0);
        return sb.append(this, i, thisLen).toString();
    }

    /**
     * 将匹配正则表达式regex的匹配项都替换成replacement字符串，返回⼀个新的字符串。
     * @author liuzhen
     * @date 2022/4/9 18:03
     * @param regex
     * @param replacement
     * @return java.lang.String
     */
    public String replaceAll(String regex, String replacement) {
        return Pattern.compile(regex).matcher(this).replaceAll(replacement);
    }

    public boolean matches(String regex) {
        return Pattern.matches(regex, this);
    }

    public boolean contains(CharSequence s) {
        return indexOf(s.toString()) >= 0;
    }

    public String replaceFirst(String regex, String replacement) {
        return Pattern.compile(regex).matcher(this).replaceFirst(replacement);
    }

    /**
     *
     * @author liuzhen
     * @date 2022/4/9 17:43
     * @param regex
     * @return java.lang.String[]
     */
    public String[] split(String regex) {
        return split(regex, 0);
    }

    /**
     * split(String regex) 将该字符串拆分为给定正则表达式的匹配。split(String regex , int limit) 也是⼀样，不过对于 limit 的取值有三种情况：
     * 1. limit > 0 ，则pattern（模式）应⽤n - 1 次
     *      String str = "a,b,c";
     *      String[] c1 = str.split(",", 2);
     *      System.out.println(c1.length);//2
     *      System.out.println(Arrays.toString(c1));//{"a","b,c"}
     * 2. limit = 0 ，则pattern（模式）应⽤⽆限次并且省略末尾的空字串
     *      String str2 = "a,b,c,,";
     *      String[] c2 = str2.split(",", 0);
     *      System.out.println(c2.length);//3
     *      System.out.println(Arrays.toString(c2));//{"a","b","c"}
     * 3. limit < 0 ，则pattern（模式）应⽤⽆限次
     *      String str2 = "a,b,c,,";
     *      String[] c2 = str2.split(",", -1);
     *      System.out.println(c2.length);//5
     *      System.out.println(Arrays.toString(c2));//{"a","b","c","",""}
     * @author liuzhen
     * @date 2022/4/9 17:48
     * @param regex
     * @param limit
     * @return java.lang.String[]
     */
    public String[] split(String regex, int limit) {
        /* fastpath if the regex is a
         (1)one-char String and this character is not one of the
            RegEx's meta characters ".$|()[{^?*+\\", or
         (2)two-char String and the first char is the backslash and
            the second is not the ascii digit or ascii letter.
         */

        /* 1、单个字符，且不是".$|()[{^?*+\\"其中⼀个
         * 2、两个字符，第⼀个是"\"，第⼆个⼤⼩写字⺟或者数字
         */
        char ch = 0;
        if (((regex.length() == 1 && ".$|()[{^?*+\\".indexOf(ch = regex.charAt(0)) == -1) || (regex.length() == 2 && regex.charAt(0) == '\\' &&
                                                                                              (((ch = regex.charAt(1)) - '0') | ('9' - ch)) < 0 &&
                                                                                              ((ch - 'a') | ('z' - ch)) < 0 &&
                                                                                              ((ch - 'A') | ('Z' - ch)) < 0)) &&
            (ch < Character.MIN_HIGH_SURROGATE || ch > Character.MAX_LOW_SURROGATE)) {
            int off = 0;
            int next = 0;
            // ⼤于0，limited==true,反之 limited==false
            boolean limited = limit > 0;
            ArrayList<String> list = new ArrayList<>();

            while ((next = indexOf(ch, off)) != -1) {
                // 当参数limit<=0 或者 集合list的⻓度⼩于 limit-1
                if (!limited || list.size() < limit - 1) {
                    list.add(substring(off, next));
                    off = next + 1;
                } else { // 判断最后⼀个list.size() == limit - 1   // last one
                    //assert (list.size() == limit - 1);
                    int last = length();
                    list.add(substring(off, last));
                    off = last;
                    break;
                }
            }

            // 如果没有⼀个能匹配的，返回⼀个新的字符串，内容和原来的⼀样
            // If no match was found, return this
            if (off == 0)
                return new String[] {this};

            // 当 limit<=0 时，limited==false,或者集合的⻓度 ⼩于 limit是，截取添加剩下的字符串
            // Add remaining segment
            if (!limited || list.size() < limit)
                list.add(substring(off, length()));

            // 当 limit == 0 时，如果末尾添加的元素为空（⻓度为0），则集合⻓度不断减1，直到末尾不为空
            // Construct result
            int resultSize = list.size();
            if (limit == 0) {
                while (resultSize > 0 && list.get(resultSize - 1).isEmpty()) {
                    resultSize--;
                }
            }
            String[] result = new String[resultSize];
            return list.subList(0, resultSize).toArray(result);
        }
        return Pattern.compile(regex).split(this, limit);
    }

    public static String join(CharSequence delimiter, CharSequence... elements) {
        Objects.requireNonNull(delimiter);
        Objects.requireNonNull(elements);
        // Number of elements not likely worth Arrays.stream overhead.
        StringJoiner joiner = new StringJoiner(delimiter);
        for (CharSequence cs: elements) {
            joiner.add(cs);
        }
        return joiner.toString();
    }

    public static String join(CharSequence delimiter,
            Iterable<? extends CharSequence> elements) {
        Objects.requireNonNull(delimiter);
        Objects.requireNonNull(elements);
        StringJoiner joiner = new StringJoiner(delimiter);
        for (CharSequence cs: elements) {
            joiner.add(cs);
        }
        return joiner.toString();
    }

    public String toLowerCase(Locale locale) {
        return isLatin1() ? StringLatin1.toLowerCase(this, value, locale)
                          : StringUTF16.toLowerCase(this, value, locale);
    }

    public String toLowerCase() {
        return toLowerCase(Locale.getDefault());
    }

    public String toUpperCase(Locale locale) {
        return isLatin1() ? StringLatin1.toUpperCase(this, value, locale)
                          : StringUTF16.toUpperCase(this, value, locale);
    }

    public String toUpperCase() {
        return toUpperCase(Locale.getDefault());
    }

    public String trim() {
        String ret = isLatin1() ? StringLatin1.trim(value)
                                : StringUTF16.trim(value);
        return ret == null ? this : ret;
    }

    public String strip() {
        String ret = isLatin1() ? StringLatin1.strip(value)
                                : StringUTF16.strip(value);
        return ret == null ? this : ret;
    }

    public String stripLeading() {
        String ret = isLatin1() ? StringLatin1.stripLeading(value)
                                : StringUTF16.stripLeading(value);
        return ret == null ? this : ret;
    }

    public String stripTrailing() {
        String ret = isLatin1() ? StringLatin1.stripTrailing(value)
                                : StringUTF16.stripTrailing(value);
        return ret == null ? this : ret;
    }

    public boolean isBlank() {
        return indexOfNonWhitespace() == length();
    }

    private int indexOfNonWhitespace() {
        if (isLatin1()) {
            return StringLatin1.indexOfNonWhitespace(value);
        } else {
            return StringUTF16.indexOfNonWhitespace(value);
        }
    }

    public Stream<String> lines() {
        return isLatin1() ? StringLatin1.lines(value)
                          : StringUTF16.lines(value);
    }

    public String toString() {
        return this;
    }

    @Override
    public IntStream chars() {
        return StreamSupport.intStream(
            isLatin1() ? new StringLatin1.CharsSpliterator(value, Spliterator.IMMUTABLE)
                       : new StringUTF16.CharsSpliterator(value, Spliterator.IMMUTABLE),
            false);
    }


    @Override
    public IntStream codePoints() {
        return StreamSupport.intStream(
            isLatin1() ? new StringLatin1.CharsSpliterator(value, Spliterator.IMMUTABLE)
                       : new StringUTF16.CodePointsSpliterator(value, Spliterator.IMMUTABLE),
            false);
    }

    public char[] toCharArray() {
        return isLatin1() ? StringLatin1.toChars(value)
                          : StringUTF16.toChars(value);
    }

    public static String format(String format, Object... args) {
        return new Formatter().format(format, args).toString();
    }

    public static String format(Locale l, String format, Object... args) {
        return new Formatter(l).format(format, args).toString();
    }

    public static String valueOf(Object obj) {
        return (obj == null) ? "null" : obj.toString();
    }

    public static String valueOf(char data[]) {
        return new String(data);
    }

    public static String valueOf(char data[], int offset, int count) {
        return new String(data, offset, count);
    }

    public static String copyValueOf(char data[], int offset, int count) {
        return new String(data, offset, count);
    }

    public static String copyValueOf(char data[]) {
        return new String(data);
    }

    public static String valueOf(boolean b) {
        return b ? "true" : "false";
    }

    public static String valueOf(char c) {
        if (COMPACT_STRINGS && StringLatin1.canEncode(c)) {
            return new String(StringLatin1.toBytes(c), LATIN1);
        }
        return new String(StringUTF16.toBytes(c), UTF16);
    }

    public static String valueOf(int i) {
        return Integer.toString(i);
    }

    public static String valueOf(long l) {
        return Long.toString(l);
    }

    public static String valueOf(float f) {
        return Float.toString(f);
    }

    public static String valueOf(double d) {
        return Double.toString(d);
    }

    public native String intern();

    public String repeat(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count is negative: " + count);
        }
        if (count == 1) {
            return this;
        }
        final int len = value.length;
        if (len == 0 || count == 0) {
            return "";
        }
        if (len == 1) {
            final byte[] single = new byte[count];
            Arrays.fill(single, value[0]);
            return new String(single, coder);
        }
        if (Integer.MAX_VALUE / count < len) {
            throw new OutOfMemoryError("Repeating " + len + " bytes String " + count +
                    " times will produce a String exceeding maximum size.");
        }
        final int limit = len * count;
        final byte[] multiple = new byte[limit];
        System.arraycopy(value, 0, multiple, 0, len);
        int copied = len;
        for (; copied < limit - copied; copied <<= 1) {
            System.arraycopy(multiple, 0, multiple, copied, copied);
        }
        System.arraycopy(multiple, 0, multiple, copied, limit - copied);
        return new String(multiple, coder);
    }

    ////////////////////////////////////////////////////////////////

    void getBytes(byte dst[], int dstBegin, byte coder) {
        if (coder() == coder) {
            System.arraycopy(value, 0, dst, dstBegin << coder, value.length);
        } else {    // this.coder == LATIN && coder == UTF16
            StringLatin1.inflate(value, 0, dst, dstBegin, value.length);
        }
    }

    String(char[] value, int off, int len, Void sig) {
        if (len == 0) {
            this.value = "".value;
            this.coder = "".coder;
            return;
        }
        if (COMPACT_STRINGS) {
            byte[] val = StringUTF16.compress(value, off, len);
            if (val != null) {
                this.value = val;
                this.coder = LATIN1;
                return;
            }
        }
        this.coder = UTF16;
        this.value = StringUTF16.toBytes(value, off, len);
    }

    String(AbstractStringBuilder asb, Void sig) {
        byte[] val = asb.getValue();
        int length = asb.length();
        if (asb.isLatin1()) {
            this.coder = LATIN1;
            this.value = Arrays.copyOfRange(val, 0, length);
        } else {
            if (COMPACT_STRINGS) {
                byte[] buf = StringUTF16.compress(val, 0, length);
                if (buf != null) {
                    this.coder = LATIN1;
                    this.value = buf;
                    return;
                }
            }
            this.coder = UTF16;
            this.value = Arrays.copyOfRange(val, 0, length << 1);
        }
    }

    String(byte[] value, byte coder) {
        this.value = value;
        this.coder = coder;
    }

    byte coder() {
        return COMPACT_STRINGS ? coder : UTF16;
    }

    byte[] value() {
        return value;
    }

    private boolean isLatin1() {
        return COMPACT_STRINGS && coder == LATIN1;
    }

    @Native static final byte LATIN1 = 0;
    @Native static final byte UTF16  = 1;

    static void checkIndex(int index, int length) {
        if (index < 0 || index >= length) {
            throw new StringIndexOutOfBoundsException("index " + index +
                                                      ",length " + length);
        }
    }

    static void checkOffset(int offset, int length) {
        if (offset < 0 || offset > length) {
            throw new StringIndexOutOfBoundsException("offset " + offset +
                                                      ",length " + length);
        }
    }

    static void checkBoundsOffCount(int offset, int count, int length) {
        if (offset < 0 || count < 0 || offset > length - count) {
            throw new StringIndexOutOfBoundsException(
                "offset " + offset + ", count " + count + ", length " + length);
        }
    }

    static void checkBoundsBeginEnd(int begin, int end, int length) {
        if (begin < 0 || begin > end || end > length) {
            throw new StringIndexOutOfBoundsException(
                "begin " + begin + ", end " + end + ", length " + length);
        }
    }

    static String valueOfCodePoint(int codePoint) {
        if (COMPACT_STRINGS && StringLatin1.canEncode(codePoint)) {
            return new String(StringLatin1.toBytes((char)codePoint), LATIN1);
        } else if (Character.isBmpCodePoint(codePoint)) {
            return new String(StringUTF16.toBytes((char)codePoint), UTF16);
        } else if (Character.isSupplementaryCodePoint(codePoint)) {
            return new String(StringUTF16.toBytesSupplementary(codePoint), UTF16);
        }

        throw new IllegalArgumentException(
            format("Not a valid Unicode code point: 0x%X", codePoint));
    }
}
