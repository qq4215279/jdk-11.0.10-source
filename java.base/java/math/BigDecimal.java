/*
 * Copyright (c) 1996, 2020, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.math;

import static java.math.BigInteger.LONG_MASK;
import java.util.Arrays;
import java.util.Objects;

/**
 *
 * @date 2023/7/5 18:23
 * @return
 */
public class BigDecimal extends Number implements Comparable<BigDecimal> {
    private final BigInteger intVal;

    private final int scale;  // Note: this may have any value, so
                              // calculations must be done in longs

    private transient int precision;

    private transient String stringCache;

    static final long INFLATED = Long.MIN_VALUE;

    private static final BigInteger INFLATED_BIGINT = BigInteger.valueOf(INFLATED);

    private final transient long intCompact;

    private static final int MAX_COMPACT_DIGITS = 18;

    private static final long serialVersionUID = 6108874887143696463L;

    private static final ThreadLocal<StringBuilderHelper>
        threadLocalStringBuilderHelper = new ThreadLocal<StringBuilderHelper>() {
        @Override
        protected StringBuilderHelper initialValue() {
            return new StringBuilderHelper();
        }
    };

    // Cache of common small BigDecimal values.
    private static final BigDecimal ZERO_THROUGH_TEN[] = {
        new BigDecimal(BigInteger.ZERO,       0,  0, 1),
        new BigDecimal(BigInteger.ONE,        1,  0, 1),
        new BigDecimal(BigInteger.TWO,        2,  0, 1),
        new BigDecimal(BigInteger.valueOf(3), 3,  0, 1),
        new BigDecimal(BigInteger.valueOf(4), 4,  0, 1),
        new BigDecimal(BigInteger.valueOf(5), 5,  0, 1),
        new BigDecimal(BigInteger.valueOf(6), 6,  0, 1),
        new BigDecimal(BigInteger.valueOf(7), 7,  0, 1),
        new BigDecimal(BigInteger.valueOf(8), 8,  0, 1),
        new BigDecimal(BigInteger.valueOf(9), 9,  0, 1),
        new BigDecimal(BigInteger.TEN,        10, 0, 2),
    };

    // Cache of zero scaled by 0 - 15
    private static final BigDecimal[] ZERO_SCALED_BY = {
        ZERO_THROUGH_TEN[0],
        new BigDecimal(BigInteger.ZERO, 0, 1, 1),
        new BigDecimal(BigInteger.ZERO, 0, 2, 1),
        new BigDecimal(BigInteger.ZERO, 0, 3, 1),
        new BigDecimal(BigInteger.ZERO, 0, 4, 1),
        new BigDecimal(BigInteger.ZERO, 0, 5, 1),
        new BigDecimal(BigInteger.ZERO, 0, 6, 1),
        new BigDecimal(BigInteger.ZERO, 0, 7, 1),
        new BigDecimal(BigInteger.ZERO, 0, 8, 1),
        new BigDecimal(BigInteger.ZERO, 0, 9, 1),
        new BigDecimal(BigInteger.ZERO, 0, 10, 1),
        new BigDecimal(BigInteger.ZERO, 0, 11, 1),
        new BigDecimal(BigInteger.ZERO, 0, 12, 1),
        new BigDecimal(BigInteger.ZERO, 0, 13, 1),
        new BigDecimal(BigInteger.ZERO, 0, 14, 1),
        new BigDecimal(BigInteger.ZERO, 0, 15, 1),
    };

    // Half of Long.MIN_VALUE & Long.MAX_VALUE.
    private static final long HALF_LONG_MAX_VALUE = Long.MAX_VALUE / 2;
    private static final long HALF_LONG_MIN_VALUE = Long.MIN_VALUE / 2;

    // Constants
    public static final BigDecimal ZERO =
        ZERO_THROUGH_TEN[0];

    public static final BigDecimal ONE =
        ZERO_THROUGH_TEN[1];

    public static final BigDecimal TEN =
        ZERO_THROUGH_TEN[10];

    private static final BigDecimal ONE_TENTH = valueOf(1L, 1);

    private static final BigDecimal ONE_HALF = valueOf(5L, 1);

    // Constructors

    BigDecimal(BigInteger intVal, long val, int scale, int prec) {
        this.scale = scale;
        this.precision = prec;
        this.intCompact = val;
        this.intVal = intVal;
    }

    public BigDecimal(char[] in, int offset, int len) {
        this(in,offset,len,MathContext.UNLIMITED);
    }

    public BigDecimal(char[] in, int offset, int len, MathContext mc) {
        // protect against huge length, negative values, and integer overflow
        try {
            Objects.checkFromIndexSize(offset, len, in.length);
        } catch (IndexOutOfBoundsException e) {
            throw new NumberFormatException
                ("Bad offset or len arguments for char[] input.");
        }

        // This is the primary string to BigDecimal constructor; all
        // incoming strings end up here; it uses explicit (inline)
        // parsing for speed and generates at most one intermediate
        // (temporary) object (a char[] array) for non-compact case.

        // Use locals for all fields values until completion
        int prec = 0;                 // record precision value
        int scl = 0;                  // record scale value
        long rs = 0;                  // the compact value in long
        BigInteger rb = null;         // the inflated value in BigInteger
        // use array bounds checking to handle too-long, len == 0,
        // bad offset, etc.
        try {
            // handle the sign
            boolean isneg = false;          // assume positive
            if (in[offset] == '-') {
                isneg = true;               // leading minus means negative
                offset++;
                len--;
            } else if (in[offset] == '+') { // leading + allowed
                offset++;
                len--;
            }

            // should now be at numeric part of the significand
            boolean dot = false;             // true when there is a '.'
            long exp = 0;                    // exponent
            char c;                          // current character
            boolean isCompact = (len <= MAX_COMPACT_DIGITS);
            // integer significand array & idx is the index to it. The array
            // is ONLY used when we can't use a compact representation.
            int idx = 0;
            if (isCompact) {
                // First compact case, we need not to preserve the character
                // and we can just compute the value in place.
                for (; len > 0; offset++, len--) {
                    c = in[offset];
                    if ((c == '0')) { // have zero
                        if (prec == 0)
                            prec = 1;
                        else if (rs != 0) {
                            rs *= 10;
                            ++prec;
                        } // else digit is a redundant leading zero
                        if (dot)
                            ++scl;
                    } else if ((c >= '1' && c <= '9')) { // have digit
                        int digit = c - '0';
                        if (prec != 1 || rs != 0)
                            ++prec; // prec unchanged if preceded by 0s
                        rs = rs * 10 + digit;
                        if (dot)
                            ++scl;
                    } else if (c == '.') {   // have dot
                        // have dot
                        if (dot) // two dots
                            throw new NumberFormatException("Character array"
                                + " contains more than one decimal point.");
                        dot = true;
                    } else if (Character.isDigit(c)) { // slow path
                        int digit = Character.digit(c, 10);
                        if (digit == 0) {
                            if (prec == 0)
                                prec = 1;
                            else if (rs != 0) {
                                rs *= 10;
                                ++prec;
                            } // else digit is a redundant leading zero
                        } else {
                            if (prec != 1 || rs != 0)
                                ++prec; // prec unchanged if preceded by 0s
                            rs = rs * 10 + digit;
                        }
                        if (dot)
                            ++scl;
                    } else if ((c == 'e') || (c == 'E')) {
                        exp = parseExp(in, offset, len);
                        // Next test is required for backwards compatibility
                        if ((int) exp != exp) // overflow
                            throw new NumberFormatException("Exponent overflow.");
                        break; // [saves a test]
                    } else {
                        throw new NumberFormatException("Character " + c
                            + " is neither a decimal digit number, decimal point, nor"
                            + " \"e\" notation exponential mark.");
                    }
                }
                if (prec == 0) // no digits found
                    throw new NumberFormatException("No digits found.");
                // Adjust scale if exp is not zero.
                if (exp != 0) { // had significant exponent
                    scl = adjustScale(scl, exp);
                }
                rs = isneg ? -rs : rs;
                int mcp = mc.precision;
                int drop = prec - mcp; // prec has range [1, MAX_INT], mcp has range [0, MAX_INT];
                                       // therefore, this subtract cannot overflow
                if (mcp > 0 && drop > 0) {  // do rounding
                    while (drop > 0) {
                        scl = checkScaleNonZero((long) scl - drop);
                        rs = divideAndRound(rs, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                        prec = longDigitLength(rs);
                        drop = prec - mcp;
                    }
                }
            } else {
                char coeff[] = new char[len];
                for (; len > 0; offset++, len--) {
                    c = in[offset];
                    // have digit
                    if ((c >= '0' && c <= '9') || Character.isDigit(c)) {
                        // First compact case, we need not to preserve the character
                        // and we can just compute the value in place.
                        if (c == '0' || Character.digit(c, 10) == 0) {
                            if (prec == 0) {
                                coeff[idx] = c;
                                prec = 1;
                            } else if (idx != 0) {
                                coeff[idx++] = c;
                                ++prec;
                            } // else c must be a redundant leading zero
                        } else {
                            if (prec != 1 || idx != 0)
                                ++prec; // prec unchanged if preceded by 0s
                            coeff[idx++] = c;
                        }
                        if (dot)
                            ++scl;
                        continue;
                    }
                    // have dot
                    if (c == '.') {
                        // have dot
                        if (dot) // two dots
                            throw new NumberFormatException("Character array"
                                + " contains more than one decimal point.");
                        dot = true;
                        continue;
                    }
                    // exponent expected
                    if ((c != 'e') && (c != 'E'))
                        throw new NumberFormatException("Character array"
                            + " is missing \"e\" notation exponential mark.");
                    exp = parseExp(in, offset, len);
                    // Next test is required for backwards compatibility
                    if ((int) exp != exp) // overflow
                        throw new NumberFormatException("Exponent overflow.");
                    break; // [saves a test]
                }
                // here when no characters left
                if (prec == 0) // no digits found
                    throw new NumberFormatException("No digits found.");
                // Adjust scale if exp is not zero.
                if (exp != 0) { // had significant exponent
                    scl = adjustScale(scl, exp);
                }
                // Remove leading zeros from precision (digits count)
                rb = new BigInteger(coeff, isneg ? -1 : 1, prec);
                rs = compactValFor(rb);
                int mcp = mc.precision;
                if (mcp > 0 && (prec > mcp)) {
                    if (rs == INFLATED) {
                        int drop = prec - mcp;
                        while (drop > 0) {
                            scl = checkScaleNonZero((long) scl - drop);
                            rb = divideAndRoundByTenPow(rb, drop, mc.roundingMode.oldMode);
                            rs = compactValFor(rb);
                            if (rs != INFLATED) {
                                prec = longDigitLength(rs);
                                break;
                            }
                            prec = bigDigitLength(rb);
                            drop = prec - mcp;
                        }
                    }
                    if (rs != INFLATED) {
                        int drop = prec - mcp;
                        while (drop > 0) {
                            scl = checkScaleNonZero((long) scl - drop);
                            rs = divideAndRound(rs, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                            prec = longDigitLength(rs);
                            drop = prec - mcp;
                        }
                        rb = null;
                    }
                }
            }
        } catch (ArrayIndexOutOfBoundsException | NegativeArraySizeException e) {
            NumberFormatException nfe = new NumberFormatException();
            nfe.initCause(e);
            throw nfe;
        }
        this.scale = scl;
        this.precision = prec;
        this.intCompact = rs;
        this.intVal = rb;
    }

    private int adjustScale(int scl, long exp) {
        long adjustedScale = scl - exp;
        if (adjustedScale > Integer.MAX_VALUE || adjustedScale < Integer.MIN_VALUE)
            throw new NumberFormatException("Scale out of range.");
        scl = (int) adjustedScale;
        return scl;
    }

    /*
     * parse exponent
     */
    private static long parseExp(char[] in, int offset, int len){
        long exp = 0;
        offset++;
        char c = in[offset];
        len--;
        boolean negexp = (c == '-');
        // optional sign
        if (negexp || c == '+') {
            offset++;
            c = in[offset];
            len--;
        }
        if (len <= 0) // no exponent digits
            throw new NumberFormatException("No exponent digits.");
        // skip leading zeros in the exponent
        while (len > 10 && (c=='0' || (Character.digit(c, 10) == 0))) {
            offset++;
            c = in[offset];
            len--;
        }
        if (len > 10) // too many nonzero exponent digits
            throw new NumberFormatException("Too many nonzero exponent digits.");
        // c now holds first digit of exponent
        for (;; len--) {
            int v;
            if (c >= '0' && c <= '9') {
                v = c - '0';
            } else {
                v = Character.digit(c, 10);
                if (v < 0) // not a digit
                    throw new NumberFormatException("Not a digit.");
            }
            exp = exp * 10 + v;
            if (len == 1)
                break; // that was final character
            offset++;
            c = in[offset];
        }
        if (negexp) // apply sign
            exp = -exp;
        return exp;
    }

    public BigDecimal(char[] in) {
        this(in, 0, in.length);
    }

    public BigDecimal(char[] in, MathContext mc) {
        this(in, 0, in.length, mc);
    }

    public BigDecimal(String val) {
        this(val.toCharArray(), 0, val.length());
    }

    public BigDecimal(String val, MathContext mc) {
        this(val.toCharArray(), 0, val.length(), mc);
    }

    public BigDecimal(double val) {
        this(val,MathContext.UNLIMITED);
    }

    public BigDecimal(double val, MathContext mc) {
        if (Double.isInfinite(val) || Double.isNaN(val))
            throw new NumberFormatException("Infinite or NaN");
        // Translate the double into sign, exponent and significand, according
        // to the formulae in JLS, Section 20.10.22.
        long valBits = Double.doubleToLongBits(val);
        int sign = ((valBits >> 63) == 0 ? 1 : -1);
        int exponent = (int) ((valBits >> 52) & 0x7ffL);
        long significand = (exponent == 0
                ? (valBits & ((1L << 52) - 1)) << 1
                : (valBits & ((1L << 52) - 1)) | (1L << 52));
        exponent -= 1075;
        // At this point, val == sign * significand * 2**exponent.

        /*
         * Special case zero to supress nonterminating normalization and bogus
         * scale calculation.
         */
        if (significand == 0) {
            this.intVal = BigInteger.ZERO;
            this.scale = 0;
            this.intCompact = 0;
            this.precision = 1;
            return;
        }
        // Normalize
        while ((significand & 1) == 0) { // i.e., significand is even
            significand >>= 1;
            exponent++;
        }
        int scl = 0;
        // Calculate intVal and scale
        BigInteger rb;
        long compactVal = sign * significand;
        if (exponent == 0) {
            rb = (compactVal == INFLATED) ? INFLATED_BIGINT : null;
        } else {
            if (exponent < 0) {
                rb = BigInteger.valueOf(5).pow(-exponent).multiply(compactVal);
                scl = -exponent;
            } else { //  (exponent > 0)
                rb = BigInteger.TWO.pow(exponent).multiply(compactVal);
            }
            compactVal = compactValFor(rb);
        }
        int prec = 0;
        int mcp = mc.precision;
        if (mcp > 0) { // do rounding
            int mode = mc.roundingMode.oldMode;
            int drop;
            if (compactVal == INFLATED) {
                prec = bigDigitLength(rb);
                drop = prec - mcp;
                while (drop > 0) {
                    scl = checkScaleNonZero((long) scl - drop);
                    rb = divideAndRoundByTenPow(rb, drop, mode);
                    compactVal = compactValFor(rb);
                    if (compactVal != INFLATED) {
                        break;
                    }
                    prec = bigDigitLength(rb);
                    drop = prec - mcp;
                }
            }
            if (compactVal != INFLATED) {
                prec = longDigitLength(compactVal);
                drop = prec - mcp;
                while (drop > 0) {
                    scl = checkScaleNonZero((long) scl - drop);
                    compactVal = divideAndRound(compactVal, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                    prec = longDigitLength(compactVal);
                    drop = prec - mcp;
                }
                rb = null;
            }
        }
        this.intVal = rb;
        this.intCompact = compactVal;
        this.scale = scl;
        this.precision = prec;
    }

    public BigDecimal(BigInteger val) {
        scale = 0;
        intVal = val;
        intCompact = compactValFor(val);
    }

    public BigDecimal(BigInteger val, MathContext mc) {
        this(val,0,mc);
    }

    public BigDecimal(BigInteger unscaledVal, int scale) {
        // Negative scales are now allowed
        this.intVal = unscaledVal;
        this.intCompact = compactValFor(unscaledVal);
        this.scale = scale;
    }

    public BigDecimal(BigInteger unscaledVal, int scale, MathContext mc) {
        long compactVal = compactValFor(unscaledVal);
        int mcp = mc.precision;
        int prec = 0;
        if (mcp > 0) { // do rounding
            int mode = mc.roundingMode.oldMode;
            if (compactVal == INFLATED) {
                prec = bigDigitLength(unscaledVal);
                int drop = prec - mcp;
                while (drop > 0) {
                    scale = checkScaleNonZero((long) scale - drop);
                    unscaledVal = divideAndRoundByTenPow(unscaledVal, drop, mode);
                    compactVal = compactValFor(unscaledVal);
                    if (compactVal != INFLATED) {
                        break;
                    }
                    prec = bigDigitLength(unscaledVal);
                    drop = prec - mcp;
                }
            }
            if (compactVal != INFLATED) {
                prec = longDigitLength(compactVal);
                int drop = prec - mcp;     // drop can't be more than 18
                while (drop > 0) {
                    scale = checkScaleNonZero((long) scale - drop);
                    compactVal = divideAndRound(compactVal, LONG_TEN_POWERS_TABLE[drop], mode);
                    prec = longDigitLength(compactVal);
                    drop = prec - mcp;
                }
                unscaledVal = null;
            }
        }
        this.intVal = unscaledVal;
        this.intCompact = compactVal;
        this.scale = scale;
        this.precision = prec;
    }

    public BigDecimal(int val) {
        this.intCompact = val;
        this.scale = 0;
        this.intVal = null;
    }

    public BigDecimal(int val, MathContext mc) {
        int mcp = mc.precision;
        long compactVal = val;
        int scl = 0;
        int prec = 0;
        if (mcp > 0) { // do rounding
            prec = longDigitLength(compactVal);
            int drop = prec - mcp; // drop can't be more than 18
            while (drop > 0) {
                scl = checkScaleNonZero((long) scl - drop);
                compactVal = divideAndRound(compactVal, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                prec = longDigitLength(compactVal);
                drop = prec - mcp;
            }
        }
        this.intVal = null;
        this.intCompact = compactVal;
        this.scale = scl;
        this.precision = prec;
    }

    public BigDecimal(long val) {
        this.intCompact = val;
        this.intVal = (val == INFLATED) ? INFLATED_BIGINT : null;
        this.scale = 0;
    }

    public BigDecimal(long val, MathContext mc) {
        int mcp = mc.precision;
        int mode = mc.roundingMode.oldMode;
        int prec = 0;
        int scl = 0;
        BigInteger rb = (val == INFLATED) ? INFLATED_BIGINT : null;
        if (mcp > 0) { // do rounding
            if (val == INFLATED) {
                prec = 19;
                int drop = prec - mcp;
                while (drop > 0) {
                    scl = checkScaleNonZero((long) scl - drop);
                    rb = divideAndRoundByTenPow(rb, drop, mode);
                    val = compactValFor(rb);
                    if (val != INFLATED) {
                        break;
                    }
                    prec = bigDigitLength(rb);
                    drop = prec - mcp;
                }
            }
            if (val != INFLATED) {
                prec = longDigitLength(val);
                int drop = prec - mcp;
                while (drop > 0) {
                    scl = checkScaleNonZero((long) scl - drop);
                    val = divideAndRound(val, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                    prec = longDigitLength(val);
                    drop = prec - mcp;
                }
                rb = null;
            }
        }
        this.intVal = rb;
        this.intCompact = val;
        this.scale = scl;
        this.precision = prec;
    }

    // Static Factory Methods

    public static BigDecimal valueOf(long unscaledVal, int scale) {
        if (scale == 0)
            return valueOf(unscaledVal);
        else if (unscaledVal == 0) {
            return zeroValueOf(scale);
        }
        return new BigDecimal(unscaledVal == INFLATED ?
                              INFLATED_BIGINT : null,
                              unscaledVal, scale, 0);
    }

    public static BigDecimal valueOf(long val) {
        if (val >= 0 && val < ZERO_THROUGH_TEN.length)
            return ZERO_THROUGH_TEN[(int)val];
        else if (val != INFLATED)
            return new BigDecimal(null, val, 0, 0);
        return new BigDecimal(INFLATED_BIGINT, val, 0, 0);
    }

    static BigDecimal valueOf(long unscaledVal, int scale, int prec) {
        if (scale == 0 && unscaledVal >= 0 && unscaledVal < ZERO_THROUGH_TEN.length) {
            return ZERO_THROUGH_TEN[(int) unscaledVal];
        } else if (unscaledVal == 0) {
            return zeroValueOf(scale);
        }
        return new BigDecimal(unscaledVal == INFLATED ? INFLATED_BIGINT : null,
                unscaledVal, scale, prec);
    }

    static BigDecimal valueOf(BigInteger intVal, int scale, int prec) {
        long val = compactValFor(intVal);
        if (val == 0) {
            return zeroValueOf(scale);
        } else if (scale == 0 && val >= 0 && val < ZERO_THROUGH_TEN.length) {
            return ZERO_THROUGH_TEN[(int) val];
        }
        return new BigDecimal(intVal, val, scale, prec);
    }

    static BigDecimal zeroValueOf(int scale) {
        if (scale >= 0 && scale < ZERO_SCALED_BY.length)
            return ZERO_SCALED_BY[scale];
        else
            return new BigDecimal(BigInteger.ZERO, 0, scale, 1);
    }

    /**
     * Translates a {@code double} into a {@code BigDecimal}, using
     * the {@code double}'s canonical string representation provided
     * by the {@link Double#toString(double)} method.
     *
     * @apiNote This is generally the preferred way to convert a
     * {@code double} (or {@code float}) into a {@code BigDecimal}, as
     * the value returned is equal to that resulting from constructing
     * a {@code BigDecimal} from the result of using {@link
     * Double#toString(double)}.
     *
     * @param  val {@code double} to convert to a {@code BigDecimal}.
     * @return a {@code BigDecimal} whose value is equal to or approximately
     *         equal to the value of {@code val}.
     * @throws NumberFormatException if {@code val} is infinite or NaN.
     * @since  1.5
     */
    public static BigDecimal valueOf(double val) {
        // Reminder: a zero double returns '0.0', so we cannot fastpath
        // to use the constant ZERO.  This might be important enough to
        // justify a factory approach, a cache, or a few private
        // constants, later.
        return new BigDecimal(Double.toString(val));
    }

    // Arithmetic Operations
    public BigDecimal add(BigDecimal augend) {
        if (this.intCompact != INFLATED) {
            if ((augend.intCompact != INFLATED)) {
                return add(this.intCompact, this.scale, augend.intCompact, augend.scale);
            } else {
                return add(this.intCompact, this.scale, augend.intVal, augend.scale);
            }
        } else {
            if ((augend.intCompact != INFLATED)) {
                return add(augend.intCompact, augend.scale, this.intVal, this.scale);
            } else {
                return add(this.intVal, this.scale, augend.intVal, augend.scale);
            }
        }
    }

    public BigDecimal add(BigDecimal augend, MathContext mc) {
        if (mc.precision == 0)
            return add(augend);
        BigDecimal lhs = this;

        // If either number is zero then the other number, rounded and
        // scaled if necessary, is used as the result.
        {
            boolean lhsIsZero = lhs.signum() == 0;
            boolean augendIsZero = augend.signum() == 0;

            if (lhsIsZero || augendIsZero) {
                int preferredScale = Math.max(lhs.scale(), augend.scale());
                BigDecimal result;

                if (lhsIsZero && augendIsZero)
                    return zeroValueOf(preferredScale);
                result = lhsIsZero ? doRound(augend, mc) : doRound(lhs, mc);

                if (result.scale() == preferredScale)
                    return result;
                else if (result.scale() > preferredScale) {
                    return stripZerosToMatchScale(result.intVal, result.intCompact, result.scale, preferredScale);
                } else { // result.scale < preferredScale
                    int precisionDiff = mc.precision - result.precision();
                    int scaleDiff     = preferredScale - result.scale();

                    if (precisionDiff >= scaleDiff)
                        return result.setScale(preferredScale); // can achieve target scale
                    else
                        return result.setScale(result.scale() + precisionDiff);
                }
            }
        }

        long padding = (long) lhs.scale - augend.scale;
        if (padding != 0) { // scales differ; alignment needed
            BigDecimal arg[] = preAlign(lhs, augend, padding, mc);
            matchScale(arg);
            lhs = arg[0];
            augend = arg[1];
        }
        return doRound(lhs.inflated().add(augend.inflated()), lhs.scale, mc);
    }

    private BigDecimal[] preAlign(BigDecimal lhs, BigDecimal augend, long padding, MathContext mc) {
        assert padding != 0;
        BigDecimal big;
        BigDecimal small;

        if (padding < 0) { // lhs is big; augend is small
            big = lhs;
            small = augend;
        } else { // lhs is small; augend is big
            big = augend;
            small = lhs;
        }

        /*
         * This is the estimated scale of an ulp of the result; it assumes that
         * the result doesn't have a carry-out on a true add (e.g. 999 + 1 =>
         * 1000) or any subtractive cancellation on borrowing (e.g. 100 - 1.2 =>
         * 98.8)
         */
        long estResultUlpScale = (long) big.scale - big.precision() + mc.precision;

        /*
         * The low-order digit position of big is big.scale().  This
         * is true regardless of whether big has a positive or
         * negative scale.  The high-order digit position of small is
         * small.scale - (small.precision() - 1).  To do the full
         * condensation, the digit positions of big and small must be
         * disjoint *and* the digit positions of small should not be
         * directly visible in the result.
         */
        long smallHighDigitPos = (long) small.scale - small.precision() + 1;
        if (smallHighDigitPos > big.scale + 2 && // big and small disjoint
            smallHighDigitPos > estResultUlpScale + 2) { // small digits not visible
            small = BigDecimal.valueOf(small.signum(), this.checkScale(Math.max(big.scale, estResultUlpScale) + 3));
        }

        // Since addition is symmetric, preserving input order in
        // returned operands doesn't matter
        BigDecimal[] result = {big, small};
        return result;
    }

    public BigDecimal subtract(BigDecimal subtrahend) {
        if (this.intCompact != INFLATED) {
            if ((subtrahend.intCompact != INFLATED)) {
                return add(this.intCompact, this.scale, -subtrahend.intCompact, subtrahend.scale);
            } else {
                return add(this.intCompact, this.scale, subtrahend.intVal.negate(), subtrahend.scale);
            }
        } else {
            if ((subtrahend.intCompact != INFLATED)) {
                // Pair of subtrahend values given before pair of
                // values from this BigDecimal to avoid need for
                // method overloading on the specialized add method
                return add(-subtrahend.intCompact, subtrahend.scale, this.intVal, this.scale);
            } else {
                return add(this.intVal, this.scale, subtrahend.intVal.negate(), subtrahend.scale);
            }
        }
    }

    public BigDecimal subtract(BigDecimal subtrahend, MathContext mc) {
        if (mc.precision == 0)
            return subtract(subtrahend);
        // share the special rounding code in add()
        return add(subtrahend.negate(), mc);
    }

    public BigDecimal multiply(BigDecimal multiplicand) {
        int productScale = checkScale((long) scale + multiplicand.scale);
        if (this.intCompact != INFLATED) {
            if ((multiplicand.intCompact != INFLATED)) {
                return multiply(this.intCompact, multiplicand.intCompact, productScale);
            } else {
                return multiply(this.intCompact, multiplicand.intVal, productScale);
            }
        } else {
            if ((multiplicand.intCompact != INFLATED)) {
                return multiply(multiplicand.intCompact, this.intVal, productScale);
            } else {
                return multiply(this.intVal, multiplicand.intVal, productScale);
            }
        }
    }

    public BigDecimal multiply(BigDecimal multiplicand, MathContext mc) {
        if (mc.precision == 0)
            return multiply(multiplicand);
        int productScale = checkScale((long) scale + multiplicand.scale);
        if (this.intCompact != INFLATED) {
            if ((multiplicand.intCompact != INFLATED)) {
                return multiplyAndRound(this.intCompact, multiplicand.intCompact, productScale, mc);
            } else {
                return multiplyAndRound(this.intCompact, multiplicand.intVal, productScale, mc);
            }
        } else {
            if ((multiplicand.intCompact != INFLATED)) {
                return multiplyAndRound(multiplicand.intCompact, this.intVal, productScale, mc);
            } else {
                return multiplyAndRound(this.intVal, multiplicand.intVal, productScale, mc);
            }
        }
    }

    @Deprecated(since="9")
    public BigDecimal divide(BigDecimal divisor, int scale, int roundingMode) {
        if (roundingMode < ROUND_UP || roundingMode > ROUND_UNNECESSARY)
            throw new IllegalArgumentException("Invalid rounding mode");
        if (this.intCompact != INFLATED) {
            if ((divisor.intCompact != INFLATED)) {
                return divide(this.intCompact, this.scale, divisor.intCompact, divisor.scale, scale, roundingMode);
            } else {
                return divide(this.intCompact, this.scale, divisor.intVal, divisor.scale, scale, roundingMode);
            }
        } else {
            if ((divisor.intCompact != INFLATED)) {
                return divide(this.intVal, this.scale, divisor.intCompact, divisor.scale, scale, roundingMode);
            } else {
                return divide(this.intVal, this.scale, divisor.intVal, divisor.scale, scale, roundingMode);
            }
        }
    }

    public BigDecimal divide(BigDecimal divisor, int scale, RoundingMode roundingMode) {
        return divide(divisor, scale, roundingMode.oldMode);
    }

    @Deprecated(since="9")
    public BigDecimal divide(BigDecimal divisor, int roundingMode) {
        return this.divide(divisor, scale, roundingMode);
    }

    public BigDecimal divide(BigDecimal divisor, RoundingMode roundingMode) {
        return this.divide(divisor, scale, roundingMode.oldMode);
    }

    public BigDecimal divide(BigDecimal divisor) {
        /*
         * Handle zero cases first.
         */
        if (divisor.signum() == 0) {   // x/0
            if (this.signum() == 0)    // 0/0
                throw new ArithmeticException("Division undefined");  // NaN
            throw new ArithmeticException("Division by zero");
        }

        // Calculate preferred scale
        int preferredScale = saturateLong((long) this.scale - divisor.scale);

        if (this.signum() == 0) // 0/y
            return zeroValueOf(preferredScale);
        else {
            /*
             * If the quotient this/divisor has a terminating decimal
             * expansion, the expansion can have no more than
             * (a.precision() + ceil(10*b.precision)/3) digits.
             * Therefore, create a MathContext object with this
             * precision and do a divide with the UNNECESSARY rounding
             * mode.
             */
            MathContext mc = new MathContext( (int)Math.min(this.precision() +
                                                            (long)Math.ceil(10.0*divisor.precision()/3.0),
                                                            Integer.MAX_VALUE),
                                              RoundingMode.UNNECESSARY);
            BigDecimal quotient;
            try {
                quotient = this.divide(divisor, mc);
            } catch (ArithmeticException e) {
                throw new ArithmeticException("Non-terminating decimal expansion; " +
                                              "no exact representable decimal result.");
            }

            int quotientScale = quotient.scale();

            // divide(BigDecimal, mc) tries to adjust the quotient to
            // the desired one by removing trailing zeros; since the
            // exact divide method does not have an explicit digit
            // limit, we can add zeros too.
            if (preferredScale > quotientScale)
                return quotient.setScale(preferredScale, ROUND_UNNECESSARY);

            return quotient;
        }
    }

    public BigDecimal divide(BigDecimal divisor, MathContext mc) {
        int mcp = mc.precision;
        if (mcp == 0)
            return divide(divisor);

        BigDecimal dividend = this;
        long preferredScale = (long)dividend.scale - divisor.scale;
        // Now calculate the answer.  We use the existing
        // divide-and-round method, but as this rounds to scale we have
        // to normalize the values here to achieve the desired result.
        // For x/y we first handle y=0 and x=0, and then normalize x and
        // y to give x' and y' with the following constraints:
        //   (a) 0.1 <= x' < 1
        //   (b)  x' <= y' < 10*x'
        // Dividing x'/y' with the required scale set to mc.precision then
        // will give a result in the range 0.1 to 1 rounded to exactly
        // the right number of digits (except in the case of a result of
        // 1.000... which can arise when x=y, or when rounding overflows
        // The 1.000... case will reduce properly to 1.
        if (divisor.signum() == 0) {      // x/0
            if (dividend.signum() == 0)    // 0/0
                throw new ArithmeticException("Division undefined");  // NaN
            throw new ArithmeticException("Division by zero");
        }
        if (dividend.signum() == 0) // 0/y
            return zeroValueOf(saturateLong(preferredScale));
        int xscale = dividend.precision();
        int yscale = divisor.precision();
        if(dividend.intCompact!=INFLATED) {
            if(divisor.intCompact!=INFLATED) {
                return divide(dividend.intCompact, xscale, divisor.intCompact, yscale, preferredScale, mc);
            } else {
                return divide(dividend.intCompact, xscale, divisor.intVal, yscale, preferredScale, mc);
            }
        } else {
            if(divisor.intCompact!=INFLATED) {
                return divide(dividend.intVal, xscale, divisor.intCompact, yscale, preferredScale, mc);
            } else {
                return divide(dividend.intVal, xscale, divisor.intVal, yscale, preferredScale, mc);
            }
        }
    }

    public BigDecimal divideToIntegralValue(BigDecimal divisor) {
        // Calculate preferred scale
        int preferredScale = saturateLong((long) this.scale - divisor.scale);
        if (this.compareMagnitude(divisor) < 0) {
            // much faster when this << divisor
            return zeroValueOf(preferredScale);
        }

        if (this.signum() == 0 && divisor.signum() != 0)
            return this.setScale(preferredScale, ROUND_UNNECESSARY);

        // Perform a divide with enough digits to round to a correct
        // integer value; then remove any fractional digits

        int maxDigits = (int)Math.min(this.precision() +
                                      (long)Math.ceil(10.0*divisor.precision()/3.0) +
                                      Math.abs((long)this.scale() - divisor.scale()) + 2,
                                      Integer.MAX_VALUE);
        BigDecimal quotient = this.divide(divisor, new MathContext(maxDigits,
                                                                   RoundingMode.DOWN));
        if (quotient.scale > 0) {
            quotient = quotient.setScale(0, RoundingMode.DOWN);
            quotient = stripZerosToMatchScale(quotient.intVal, quotient.intCompact, quotient.scale, preferredScale);
        }

        if (quotient.scale < preferredScale) {
            // pad with zeros if necessary
            quotient = quotient.setScale(preferredScale, ROUND_UNNECESSARY);
        }

        return quotient;
    }

    public BigDecimal divideToIntegralValue(BigDecimal divisor, MathContext mc) {
        if (mc.precision == 0 || // exact result
            (this.compareMagnitude(divisor) < 0)) // zero result
            return divideToIntegralValue(divisor);

        // Calculate preferred scale
        int preferredScale = saturateLong((long)this.scale - divisor.scale);

        /*
         * Perform a normal divide to mc.precision digits.  If the
         * remainder has absolute value less than the divisor, the
         * integer portion of the quotient fits into mc.precision
         * digits.  Next, remove any fractional digits from the
         * quotient and adjust the scale to the preferred value.
         */
        BigDecimal result = this.divide(divisor, new MathContext(mc.precision, RoundingMode.DOWN));

        if (result.scale() < 0) {
            /*
             * Result is an integer. See if quotient represents the
             * full integer portion of the exact quotient; if it does,
             * the computed remainder will be less than the divisor.
             */
            BigDecimal product = result.multiply(divisor);
            // If the quotient is the full integer value,
            // |dividend-product| < |divisor|.
            if (this.subtract(product).compareMagnitude(divisor) >= 0) {
                throw new ArithmeticException("Division impossible");
            }
        } else if (result.scale() > 0) {
            /*
             * Integer portion of quotient will fit into precision
             * digits; recompute quotient to scale 0 to avoid double
             * rounding and then try to adjust, if necessary.
             */
            result = result.setScale(0, RoundingMode.DOWN);
        }
        // else result.scale() == 0;

        int precisionDiff;
        if ((preferredScale > result.scale()) &&
            (precisionDiff = mc.precision - result.precision()) > 0) {
            return result.setScale(result.scale() +
                                   Math.min(precisionDiff, preferredScale - result.scale) );
        } else {
            return stripZerosToMatchScale(result.intVal,result.intCompact,result.scale,preferredScale);
        }
    }

    public BigDecimal remainder(BigDecimal divisor) {
        BigDecimal divrem[] = this.divideAndRemainder(divisor);
        return divrem[1];
    }


    public BigDecimal remainder(BigDecimal divisor, MathContext mc) {
        BigDecimal divrem[] = this.divideAndRemainder(divisor, mc);
        return divrem[1];
    }

    public BigDecimal[] divideAndRemainder(BigDecimal divisor) {
        // we use the identity  x = i * y + r to determine r
        BigDecimal[] result = new BigDecimal[2];

        result[0] = this.divideToIntegralValue(divisor);
        result[1] = this.subtract(result[0].multiply(divisor));
        return result;
    }

    public BigDecimal[] divideAndRemainder(BigDecimal divisor, MathContext mc) {
        if (mc.precision == 0)
            return divideAndRemainder(divisor);

        BigDecimal[] result = new BigDecimal[2];
        BigDecimal lhs = this;

        result[0] = lhs.divideToIntegralValue(divisor, mc);
        result[1] = lhs.subtract(result[0].multiply(divisor));
        return result;
    }

    public BigDecimal sqrt(MathContext mc) {
        int signum = signum();
        if (signum == 1) {
            /*
             * The following code draws on the algorithm presented in
             * "Properly Rounded Variable Precision Square Root," Hull and
             * Abrham, ACM Transactions on Mathematical Software, Vol 11,
             * No. 3, September 1985, Pages 229-237.
             *
             * The BigDecimal computational model differs from the one
             * presented in the paper in several ways: first BigDecimal
             * numbers aren't necessarily normalized, second many more
             * rounding modes are supported, including UNNECESSARY, and
             * exact results can be requested.
             *
             * The main steps of the algorithm below are as follows,
             * first argument reduce the value to the numerical range
             * [1, 10) using the following relations:
             *
             * x = y * 10 ^ exp
             * sqrt(x) = sqrt(y) * 10^(exp / 2) if exp is even
             * sqrt(x) = sqrt(y/10) * 10 ^((exp+1)/2) is exp is odd
             *
             * Then use Newton's iteration on the reduced value to compute
             * the numerical digits of the desired result.
             *
             * Finally, scale back to the desired exponent range and
             * perform any adjustment to get the preferred scale in the
             * representation.
             */

            // The code below favors relative simplicity over checking
            // for special cases that could run faster.

            int preferredScale = this.scale()/2;
            BigDecimal zeroWithFinalPreferredScale = valueOf(0L, preferredScale);

            // First phase of numerical normalization, strip trailing
            // zeros and check for even powers of 10.
            BigDecimal stripped = this.stripTrailingZeros();
            int strippedScale = stripped.scale();

            // Numerically sqrt(10^2N) = 10^N
            if (stripped.isPowerOfTen() &&
                strippedScale % 2 == 0) {
                BigDecimal result = valueOf(1L, strippedScale/2);
                if (result.scale() != preferredScale) {
                    // Adjust to requested precision and preferred
                    // scale as appropriate.
                    result = result.add(zeroWithFinalPreferredScale, mc);
                }
                return result;
            }

            // After stripTrailingZeros, the representation is normalized as
            //
            // unscaledValue * 10^(-scale)
            //
            // where unscaledValue is an integer with the mimimum
            // precision for the cohort of the numerical value. To
            // allow binary floating-point hardware to be used to get
            // approximately a 15 digit approximation to the square
            // root, it is helpful to instead normalize this so that
            // the significand portion is to right of the decimal
            // point by roughly (scale() - precision() + 1).

            // Now the precision / scale adjustment
            int scaleAdjust = 0;
            int scale = stripped.scale() - stripped.precision() + 1;
            if (scale % 2 == 0) {
                scaleAdjust = scale;
            } else {
                scaleAdjust = scale - 1;
            }

            BigDecimal working = stripped.scaleByPowerOfTen(scaleAdjust);

            assert  // Verify 0.1 <= working < 10
                ONE_TENTH.compareTo(working) <= 0 && working.compareTo(TEN) < 0;

            // Use good ole' Math.sqrt to get the initial guess for
            // the Newton iteration, good to at least 15 decimal
            // digits. This approach does incur the cost of a
            //
            // BigDecimal -> double -> BigDecimal
            //
            // conversion cycle, but it avoids the need for several
            // Newton iterations in BigDecimal arithmetic to get the
            // working answer to 15 digits of precision. If many fewer
            // than 15 digits were needed, it might be faster to do
            // the loop entirely in BigDecimal arithmetic.
            //
            // (A double value might have as many as 17 decimal
            // digits of precision; it depends on the relative density
            // of binary and decimal numbers at different regions of
            // the number line.)
            //
            // (It would be possible to check for certain special
            // cases to avoid doing any Newton iterations. For
            // example, if the BigDecimal -> double conversion was
            // known to be exact and the rounding mode had a
            // low-enough precision, the post-Newton rounding logic
            // could be applied directly.)

            BigDecimal guess = new BigDecimal(Math.sqrt(working.doubleValue()));
            int guessPrecision = 15;
            int originalPrecision = mc.getPrecision();
            int targetPrecision;

            // If an exact value is requested, it must only need about
            // half of the input digits to represent since multiplying
            // an N digit number by itself yield a 2N-1 digit or 2N
            // digit result.
            if (originalPrecision == 0) {
                targetPrecision = stripped.precision()/2 + 1;
            } else {
                /*
                 * To avoid the need for post-Newton fix-up logic, in
                 * the case of half-way rounding modes, double the
                 * target precision so that the "2p + 2" property can
                 * be relied on to accomplish the final rounding.
                 */
                switch (mc.getRoundingMode()) {
                case HALF_UP:
                case HALF_DOWN:
                case HALF_EVEN:
                       targetPrecision = 2 * originalPrecision;
                       if (targetPrecision < 0) // Overflow
                           targetPrecision = Integer.MAX_VALUE - 2;
                       break;

                default:
                    targetPrecision = originalPrecision;
                    break;
             }
            }

            // When setting the precision to use inside the Newton
            // iteration loop, take care to avoid the case where the
            // precision of the input exceeds the requested precision
            // and rounding the input value too soon.
            BigDecimal approx = guess;
            int workingPrecision = working.precision();
            do {
                int tmpPrecision = Math.max(Math.max(guessPrecision, targetPrecision + 2),
                                           workingPrecision);
                MathContext mcTmp = new MathContext(tmpPrecision, RoundingMode.HALF_EVEN);
                // approx = 0.5 * (approx + fraction / approx)
                approx = ONE_HALF.multiply(approx.add(working.divide(approx, mcTmp), mcTmp));
                guessPrecision *= 2;
            } while (guessPrecision < targetPrecision + 2);

            BigDecimal result;
            RoundingMode targetRm = mc.getRoundingMode();
            if (targetRm == RoundingMode.UNNECESSARY || originalPrecision == 0) {
                RoundingMode tmpRm =
                    (targetRm == RoundingMode.UNNECESSARY) ? RoundingMode.DOWN : targetRm;
                MathContext mcTmp = new MathContext(targetPrecision, tmpRm);
                result = approx.scaleByPowerOfTen(-scaleAdjust/2).round(mcTmp);

                // If result*result != this numerically, the square
                // root isn't exact
                if (this.subtract(result.square()).compareTo(ZERO) != 0) {
                    throw new ArithmeticException("Computed square root not exact.");
                }
            } else {
                result = approx.scaleByPowerOfTen(-scaleAdjust/2).round(mc);

               switch (targetRm) {
               case DOWN:
               case FLOOR:
                     // Check if too big
                     if (result.square().compareTo(this) > 0) {
                         BigDecimal ulp = result.ulp();
                         // Adjust increment down in case of 1.0 = 10^0
                         // since the next smaller number is only 1/10
                         // as far way as the next larger at exponent
                         // boundaries. Test approx and *not* result to
                         // avoid having to detect an arbitrary power
                         // of ten.
                         if (approx.compareTo(ONE) == 0) {
                             ulp = ulp.multiply(ONE_TENTH);
                         }
                         result = result.subtract(ulp);
                     }
                     break;

                 case UP:
                 case CEILING:
                      // Check if too small
                      if (result.square().compareTo(this) < 0) {
                          result = result.add(result.ulp());
                      }
                      break;

                 default:
                     // No additional work, rely on "2p + 2" property
                     // for correct rounding. Alternatively, could
                     // instead run the Newton iteration to around p
                     // digits and then do tests and fix-ups on the
                     // rounded value. One possible set of tests and
                     // fix-ups is given in the Hull and Abrham paper;
                     // however, additional half-way cases can occur
                     // for BigDecimal given the more varied
                     // combinations of input and output precisions
                     // supported.
                     break;
             }

            }

            // Test numerical properties at full precision before any
            // scale adjustments.
            assert squareRootResultAssertions(result, mc);
            if (result.scale() != preferredScale) {
                // The preferred scale of an add is
                // max(addend.scale(), augend.scale()). Therefore, if
                // the scale of the result is first minimized using
                // stripTrailingZeros(), adding a zero of the
                // preferred scale rounding to the correct precision
                // will perform the proper scale vs precision
                // tradeoffs.
                result = result.stripTrailingZeros().
                    add(zeroWithFinalPreferredScale,
                        new MathContext(originalPrecision, RoundingMode.UNNECESSARY));
            }
            return result;
        } else {
           BigDecimal result = null;
            switch (signum) {
            case -1:
                throw new ArithmeticException("Attempted square root " +
                                              "of negative BigDecimal");
            case 0:
                result = valueOf(0L, scale()/2);
                assert squareRootResultAssertions(result, mc);
                return result;

            default:
                throw new AssertionError("Bad value from signum");
            }
        }
    }

    private BigDecimal square() {
       return this.multiply(this);
    }

    private boolean isPowerOfTen() {
        return BigInteger.ONE.equals(this.unscaledValue());
    }

    private boolean squareRootResultAssertions(BigDecimal result, MathContext mc) {
        if (result.signum() == 0) {
            return squareRootZeroResultAssertions(result, mc);
        } else {
            RoundingMode rm = mc.getRoundingMode();
            BigDecimal ulp = result.ulp();
            BigDecimal neighborUp   = result.add(ulp);
            // Make neighbor down accurate even for powers of ten
            if (result.isPowerOfTen()) {
                ulp = ulp.divide(TEN);
            }
            BigDecimal neighborDown = result.subtract(ulp);

            // Both the starting value and result should be nonzero and positive.
            assert (result.signum() == 1 &&
                    this.signum() == 1) :
                "Bad signum of this and/or its sqrt.";

            switch (rm) {
            case DOWN:
            case FLOOR:
                assert
                    result.square().compareTo(this)     <= 0 &&
                    neighborUp.square().compareTo(this) > 0:
                "Square of result out for bounds rounding " + rm;
                return true;

            case UP:
            case CEILING:
                    assert
                    result.square().compareTo(this)       >= 0 &&
                    neighborDown.square().compareTo(this) < 0:
                "Square of result out for bounds rounding " + rm;
                 return true;


            case HALF_DOWN:
            case HALF_EVEN:
            case HALF_UP:
                BigDecimal err = result.square().subtract(this).abs();
                BigDecimal errUp = neighborUp.square().subtract(this);
                BigDecimal errDown =  this.subtract(neighborDown.square());
                // All error values should be positive so don't need to
                // compare absolute values.

                int err_comp_errUp = err.compareTo(errUp);
                int err_comp_errDown = err.compareTo(errDown);

                assert
                    errUp.signum()   == 1 &&
                    errDown.signum() == 1 :
                "Errors of neighbors squared don't have correct signs";

                // For breaking a half-way tie, the return value may
                // have a larger error than one of the neighbors. For
                // example, the square root of 2.25 to a precision of
                // 1 digit is either 1 or 2 depending on how the exact
                // value of 1.5 is rounded. If 2 is returned, it will
                // have a larger rounding error than its neighbor 1.
                assert
                     err_comp_errUp   <= 0 ||
                     err_comp_errDown <= 0 :
                 "Computed square root has larger error than neighbors for " + rm;

                assert
                    ((err_comp_errUp   == 0 ) ? err_comp_errDown < 0 : true) &&
                    ((err_comp_errDown == 0 ) ? err_comp_errUp   < 0 : true) :
                       "Incorrect error relationships";
                // && could check for digit conditions for ties too
                return true;

            default: // Definition of UNNECESSARY already verified.
                return true;
            }
        }
    }

    private boolean squareRootZeroResultAssertions(BigDecimal result, MathContext mc) {
        return this.compareTo(ZERO) == 0;
    }

    public BigDecimal pow(int n) {
        if (n < 0 || n > 999999999)
            throw new ArithmeticException("Invalid operation");
        // No need to calculate pow(n) if result will over/underflow.
        // Don't attempt to support "supernormal" numbers.
        int newScale = checkScale((long)scale * n);
        return new BigDecimal(this.inflated().pow(n), newScale);
    }


    public BigDecimal pow(int n, MathContext mc) {
        if (mc.precision == 0)
            return pow(n);
        if (n < -999999999 || n > 999999999)
            throw new ArithmeticException("Invalid operation");
        if (n == 0)
            return ONE;                      // x**0 == 1 in X3.274
        BigDecimal lhs = this;
        MathContext workmc = mc;           // working settings
        int mag = Math.abs(n);               // magnitude of n
        if (mc.precision > 0) {
            int elength = longDigitLength(mag); // length of n in digits
            if (elength > mc.precision)        // X3.274 rule
                throw new ArithmeticException("Invalid operation");
            workmc = new MathContext(mc.precision + elength + 1,
                                      mc.roundingMode);
        }
        // ready to carry out power calculation...
        BigDecimal acc = ONE;           // accumulator
        boolean seenbit = false;        // set once we've seen a 1-bit
        for (int i=1;;i++) {            // for each bit [top bit ignored]
            mag += mag;                 // shift left 1 bit
            if (mag < 0) {              // top bit is set
                seenbit = true;         // OK, we're off
                acc = acc.multiply(lhs, workmc); // acc=acc*x
            }
            if (i == 31)
                break;                  // that was the last bit
            if (seenbit)
                acc=acc.multiply(acc, workmc);   // acc=acc*acc [square]
                // else (!seenbit) no point in squaring ONE
        }
        // if negative n, calculate the reciprocal using working precision
        if (n < 0) // [hence mc.precision>0]
            acc=ONE.divide(acc, workmc);
        // round to final precision and strip zeros
        return doRound(acc, mc);
    }

    public BigDecimal abs() {
        return (signum() < 0 ? negate() : this);
    }

    public BigDecimal abs(MathContext mc) {
        return (signum() < 0 ? negate(mc) : plus(mc));
    }

    public BigDecimal negate() {
        if (intCompact == INFLATED) {
            return new BigDecimal(intVal.negate(), INFLATED, scale, precision);
        } else {
            return valueOf(-intCompact, scale, precision);
        }
    }

    public BigDecimal negate(MathContext mc) {
        return negate().plus(mc);
    }

    public BigDecimal plus() {
        return this;
    }

    public BigDecimal plus(MathContext mc) {
        if (mc.precision == 0)                 // no rounding please
            return this;
        return doRound(this, mc);
    }

    public int signum() {
        return (intCompact != INFLATED)?
            Long.signum(intCompact):
            intVal.signum();
    }

    public int scale() {
        return scale;
    }

    public int precision() {
        int result = precision;
        if (result == 0) {
            long s = intCompact;
            if (s != INFLATED)
                result = longDigitLength(s);
            else
                result = bigDigitLength(intVal);
            precision = result;
        }
        return result;
    }


    public BigInteger unscaledValue() {
        return this.inflated();
    }

    // Rounding Modes

    @Deprecated(since="9")
    public static final int ROUND_UP =           0;

    @Deprecated(since="9")
    public static final int ROUND_DOWN =         1;

    @Deprecated(since="9")
    public static final int ROUND_CEILING =      2;

    @Deprecated(since="9")
    public static final int ROUND_FLOOR =        3;

    @Deprecated(since="9")
    public static final int ROUND_HALF_UP =      4;

    @Deprecated(since="9")
    public static final int ROUND_HALF_DOWN =    5;

    @Deprecated(since="9")
    public static final int ROUND_HALF_EVEN =    6;

    @Deprecated(since="9")
    public static final int ROUND_UNNECESSARY =  7;


    // Scaling/Rounding Operations

    public BigDecimal round(MathContext mc) {
        return plus(mc);
    }

    public BigDecimal setScale(int newScale, RoundingMode roundingMode) {
        return setScale(newScale, roundingMode.oldMode);
    }

    @Deprecated(since="9")
    public BigDecimal setScale(int newScale, int roundingMode) {
        if (roundingMode < ROUND_UP || roundingMode > ROUND_UNNECESSARY)
            throw new IllegalArgumentException("Invalid rounding mode");

        int oldScale = this.scale;
        if (newScale == oldScale)        // easy case
            return this;
        if (this.signum() == 0)            // zero can have any scale
            return zeroValueOf(newScale);
        if(this.intCompact!=INFLATED) {
            long rs = this.intCompact;
            if (newScale > oldScale) {
                int raise = checkScale((long) newScale - oldScale);
                if ((rs = longMultiplyPowerTen(rs, raise)) != INFLATED) {
                    return valueOf(rs,newScale);
                }
                BigInteger rb = bigMultiplyPowerTen(raise);
                return new BigDecimal(rb, INFLATED, newScale, (precision > 0) ? precision + raise : 0);
            } else {
                // newScale < oldScale -- drop some digits
                // Can't predict the precision due to the effect of rounding.
                int drop = checkScale((long) oldScale - newScale);
                if (drop < LONG_TEN_POWERS_TABLE.length) {
                    return divideAndRound(rs, LONG_TEN_POWERS_TABLE[drop], newScale, roundingMode, newScale);
                } else {
                    return divideAndRound(this.inflated(), bigTenToThe(drop), newScale, roundingMode, newScale);
                }
            }
        } else {
            if (newScale > oldScale) {
                int raise = checkScale((long) newScale - oldScale);
                BigInteger rb = bigMultiplyPowerTen(this.intVal,raise);
                return new BigDecimal(rb, INFLATED, newScale, (precision > 0) ? precision + raise : 0);
            } else {
                // newScale < oldScale -- drop some digits
                // Can't predict the precision due to the effect of rounding.
                int drop = checkScale((long) oldScale - newScale);
                if (drop < LONG_TEN_POWERS_TABLE.length)
                    return divideAndRound(this.intVal, LONG_TEN_POWERS_TABLE[drop], newScale, roundingMode,
                                          newScale);
                else
                    return divideAndRound(this.intVal,  bigTenToThe(drop), newScale, roundingMode, newScale);
            }
        }
    }

    public BigDecimal setScale(int newScale) {
        return setScale(newScale, ROUND_UNNECESSARY);
    }

    // Decimal Point Motion Operations

    public BigDecimal movePointLeft(int n) {
        // Cannot use movePointRight(-n) in case of n==Integer.MIN_VALUE
        int newScale = checkScale((long)scale + n);
        BigDecimal num = new BigDecimal(intVal, intCompact, newScale, 0);
        return num.scale < 0 ? num.setScale(0, ROUND_UNNECESSARY) : num;
    }

    public BigDecimal movePointRight(int n) {
        // Cannot use movePointLeft(-n) in case of n==Integer.MIN_VALUE
        int newScale = checkScale((long)scale - n);
        BigDecimal num = new BigDecimal(intVal, intCompact, newScale, 0);
        return num.scale < 0 ? num.setScale(0, ROUND_UNNECESSARY) : num;
    }

    public BigDecimal scaleByPowerOfTen(int n) {
        return new BigDecimal(intVal, intCompact,
                              checkScale((long)scale - n), precision);
    }

    public BigDecimal stripTrailingZeros() {
        if (intCompact == 0 || (intVal != null && intVal.signum() == 0)) {
            return BigDecimal.ZERO;
        } else if (intCompact != INFLATED) {
            return createAndStripZerosToMatchScale(intCompact, scale, Long.MIN_VALUE);
        } else {
            return createAndStripZerosToMatchScale(intVal, scale, Long.MIN_VALUE);
        }
    }

    // Comparison Operations

    @Override
    public int compareTo(BigDecimal val) {
        // Quick path for equal scale and non-inflated case.
        if (scale == val.scale) {
            long xs = intCompact;
            long ys = val.intCompact;
            if (xs != INFLATED && ys != INFLATED)
                return xs != ys ? ((xs > ys) ? 1 : -1) : 0;
        }
        int xsign = this.signum();
        int ysign = val.signum();
        if (xsign != ysign)
            return (xsign > ysign) ? 1 : -1;
        if (xsign == 0)
            return 0;
        int cmp = compareMagnitude(val);
        return (xsign > 0) ? cmp : -cmp;
    }

    private int compareMagnitude(BigDecimal val) {
        // Match scales, avoid unnecessary inflation
        long ys = val.intCompact;
        long xs = this.intCompact;
        if (xs == 0)
            return (ys == 0) ? 0 : -1;
        if (ys == 0)
            return 1;

        long sdiff = (long)this.scale - val.scale;
        if (sdiff != 0) {
            // Avoid matching scales if the (adjusted) exponents differ
            long xae = (long)this.precision() - this.scale;   // [-1]
            long yae = (long)val.precision() - val.scale;     // [-1]
            if (xae < yae)
                return -1;
            if (xae > yae)
                return 1;
            if (sdiff < 0) {
                // The cases sdiff <= Integer.MIN_VALUE intentionally fall through.
                if ( sdiff > Integer.MIN_VALUE &&
                      (xs == INFLATED ||
                      (xs = longMultiplyPowerTen(xs, (int)-sdiff)) == INFLATED) &&
                     ys == INFLATED) {
                    BigInteger rb = bigMultiplyPowerTen((int)-sdiff);
                    return rb.compareMagnitude(val.intVal);
                }
            } else { // sdiff > 0
                // The cases sdiff > Integer.MAX_VALUE intentionally fall through.
                if ( sdiff <= Integer.MAX_VALUE &&
                      (ys == INFLATED ||
                      (ys = longMultiplyPowerTen(ys, (int)sdiff)) == INFLATED) &&
                     xs == INFLATED) {
                    BigInteger rb = val.bigMultiplyPowerTen((int)sdiff);
                    return this.intVal.compareMagnitude(rb);
                }
            }
        }
        if (xs != INFLATED)
            return (ys != INFLATED) ? longCompareMagnitude(xs, ys) : -1;
        else if (ys != INFLATED)
            return 1;
        else
            return this.intVal.compareMagnitude(val.intVal);
    }

    @Override
    public boolean equals(Object x) {
        if (!(x instanceof BigDecimal))
            return false;
        BigDecimal xDec = (BigDecimal) x;
        if (x == this)
            return true;
        if (scale != xDec.scale)
            return false;
        long s = this.intCompact;
        long xs = xDec.intCompact;
        if (s != INFLATED) {
            if (xs == INFLATED)
                xs = compactValFor(xDec.intVal);
            return xs == s;
        } else if (xs != INFLATED)
            return xs == compactValFor(this.intVal);

        return this.inflated().equals(xDec.inflated());
    }

    public BigDecimal min(BigDecimal val) {
        return (compareTo(val) <= 0 ? this : val);
    }

    public BigDecimal max(BigDecimal val) {
        return (compareTo(val) >= 0 ? this : val);
    }

    // Hash Function

    @Override
    public int hashCode() {
        if (intCompact != INFLATED) {
            long val2 = (intCompact < 0)? -intCompact : intCompact;
            int temp = (int)( ((int)(val2 >>> 32)) * 31  +
                              (val2 & LONG_MASK));
            return 31*((intCompact < 0) ?-temp:temp) + scale;
        } else
            return 31*intVal.hashCode() + scale;
    }

    // Format Converters

    @Override
    public String toString() {
        String sc = stringCache;
        if (sc == null) {
            stringCache = sc = layoutChars(true);
        }
        return sc;
    }

    public String toEngineeringString() {
        return layoutChars(false);
    }

    public String toPlainString() {
        if(scale==0) {
            if(intCompact!=INFLATED) {
                return Long.toString(intCompact);
            } else {
                return intVal.toString();
            }
        }
        if(this.scale<0) { // No decimal point
            if(signum()==0) {
                return "0";
            }
            int trailingZeros = checkScaleNonZero((-(long)scale));
            StringBuilder buf;
            if(intCompact!=INFLATED) {
                buf = new StringBuilder(20+trailingZeros);
                buf.append(intCompact);
            } else {
                String str = intVal.toString();
                buf = new StringBuilder(str.length()+trailingZeros);
                buf.append(str);
            }
            for (int i = 0; i < trailingZeros; i++) {
                buf.append('0');
            }
            return buf.toString();
        }
        String str ;
        if(intCompact!=INFLATED) {
            str = Long.toString(Math.abs(intCompact));
        } else {
            str = intVal.abs().toString();
        }
        return getValueString(signum(), str, scale);
    }

    /* Returns a digit.digit string */
    private String getValueString(int signum, String intString, int scale) {
        /* Insert decimal point */
        StringBuilder buf;
        int insertionPoint = intString.length() - scale;
        if (insertionPoint == 0) {  /* Point goes right before intVal */
            return (signum<0 ? "-0." : "0.") + intString;
        } else if (insertionPoint > 0) { /* Point goes inside intVal */
            buf = new StringBuilder(intString);
            buf.insert(insertionPoint, '.');
            if (signum < 0)
                buf.insert(0, '-');
        } else { /* We must insert zeros between point and intVal */
            buf = new StringBuilder(3-insertionPoint + intString.length());
            buf.append(signum<0 ? "-0." : "0.");
            for (int i=0; i<-insertionPoint; i++) {
                buf.append('0');
            }
            buf.append(intString);
        }
        return buf.toString();
    }

    public BigInteger toBigInteger() {
        // force to an integer, quietly
        return this.setScale(0, ROUND_DOWN).inflated();
    }

    public BigInteger toBigIntegerExact() {
        // round to an integer, with Exception if decimal part non-0
        return this.setScale(0, ROUND_UNNECESSARY).inflated();
    }

    @Override
    public long longValue(){
        if (intCompact != INFLATED && scale == 0) {
            return intCompact;
        } else {
            // Fastpath zero and small values
            if (this.signum() == 0 || fractionOnly() ||
                // Fastpath very large-scale values that will result
                // in a truncated value of zero. If the scale is -64
                // or less, there are at least 64 powers of 10 in the
                // value of the numerical result. Since 10 = 2*5, in
                // that case there would also be 64 powers of 2 in the
                // result, meaning all 64 bits of a long will be zero.
                scale <= -64) {
                return 0;
            } else {
                return toBigInteger().longValue();
            }
        }
    }

    private boolean fractionOnly() {
        assert this.signum() != 0;
        return (this.precision() - this.scale) <= 0;
    }

    public long longValueExact() {
        if (intCompact != INFLATED && scale == 0)
            return intCompact;

        // Fastpath zero
        if (this.signum() == 0)
            return 0;

        // Fastpath numbers less than 1.0 (the latter can be very slow
        // to round if very small)
        if (fractionOnly())
            throw new ArithmeticException("Rounding necessary");

        // If more than 19 digits in integer part it cannot possibly fit
        if ((precision() - scale) > 19) // [OK for negative scale too]
            throw new java.lang.ArithmeticException("Overflow");

        // round to an integer, with Exception if decimal part non-0
        BigDecimal num = this.setScale(0, ROUND_UNNECESSARY);
        if (num.precision() >= 19) // need to check carefully
            LongOverflow.check(num);
        return num.inflated().longValue();
    }

    private static class LongOverflow {
        /** BigInteger equal to Long.MIN_VALUE. */
        private static final BigInteger LONGMIN = BigInteger.valueOf(Long.MIN_VALUE);

        /** BigInteger equal to Long.MAX_VALUE. */
        private static final BigInteger LONGMAX = BigInteger.valueOf(Long.MAX_VALUE);

        public static void check(BigDecimal num) {
            BigInteger intVal = num.inflated();
            if (intVal.compareTo(LONGMIN) < 0 ||
                intVal.compareTo(LONGMAX) > 0)
                throw new java.lang.ArithmeticException("Overflow");
        }
    }

    @Override
    public int intValue() {
        return  (intCompact != INFLATED && scale == 0) ?
            (int)intCompact :
            (int)longValue();
    }

    public int intValueExact() {
       long num;
       num = this.longValueExact();     // will check decimal part
       if ((int)num != num)
           throw new java.lang.ArithmeticException("Overflow");
       return (int)num;
    }

    public short shortValueExact() {
       long num;
       num = this.longValueExact();     // will check decimal part
       if ((short)num != num)
           throw new java.lang.ArithmeticException("Overflow");
       return (short)num;
    }

    public byte byteValueExact() {
       long num;
       num = this.longValueExact();     // will check decimal part
       if ((byte)num != num)
           throw new java.lang.ArithmeticException("Overflow");
       return (byte)num;
    }

    @Override
    public float floatValue(){
        if(intCompact != INFLATED) {
            if (scale == 0) {
                return (float)intCompact;
            } else {
                /*
                 * If both intCompact and the scale can be exactly
                 * represented as float values, perform a single float
                 * multiply or divide to compute the (properly
                 * rounded) result.
                 */
                if (Math.abs(intCompact) < 1L<<22 ) {
                    // Don't have too guard against
                    // Math.abs(MIN_VALUE) because of outer check
                    // against INFLATED.
                    if (scale > 0 && scale < FLOAT_10_POW.length) {
                        return (float)intCompact / FLOAT_10_POW[scale];
                    } else if (scale < 0 && scale > -FLOAT_10_POW.length) {
                        return (float)intCompact * FLOAT_10_POW[-scale];
                    }
                }
            }
        }
        // Somewhat inefficient, but guaranteed to work.
        return Float.parseFloat(this.toString());
    }

    @Override
    public double doubleValue(){
        if(intCompact != INFLATED) {
            if (scale == 0) {
                return (double)intCompact;
            } else {
                /*
                 * If both intCompact and the scale can be exactly
                 * represented as double values, perform a single
                 * double multiply or divide to compute the (properly
                 * rounded) result.
                 */
                if (Math.abs(intCompact) < 1L<<52 ) {
                    // Don't have too guard against
                    // Math.abs(MIN_VALUE) because of outer check
                    // against INFLATED.
                    if (scale > 0 && scale < DOUBLE_10_POW.length) {
                        return (double)intCompact / DOUBLE_10_POW[scale];
                    } else if (scale < 0 && scale > -DOUBLE_10_POW.length) {
                        return (double)intCompact * DOUBLE_10_POW[-scale];
                    }
                }
            }
        }
        // Somewhat inefficient, but guaranteed to work.
        return Double.parseDouble(this.toString());
    }

    private static final double DOUBLE_10_POW[] = {
        1.0e0,  1.0e1,  1.0e2,  1.0e3,  1.0e4,  1.0e5,
        1.0e6,  1.0e7,  1.0e8,  1.0e9,  1.0e10, 1.0e11,
        1.0e12, 1.0e13, 1.0e14, 1.0e15, 1.0e16, 1.0e17,
        1.0e18, 1.0e19, 1.0e20, 1.0e21, 1.0e22
    };

    private static final float FLOAT_10_POW[] = {
        1.0e0f, 1.0e1f, 1.0e2f, 1.0e3f, 1.0e4f, 1.0e5f,
        1.0e6f, 1.0e7f, 1.0e8f, 1.0e9f, 1.0e10f
    };

    public BigDecimal ulp() {
        return BigDecimal.valueOf(1, this.scale(), 1);
    }

    static class StringBuilderHelper {
        final StringBuilder sb;    // Placeholder for BigDecimal string
        final char[] cmpCharArray; // character array to place the intCompact

        StringBuilderHelper() {
            sb = new StringBuilder();
            // All non negative longs can be made to fit into 19 character array.
            cmpCharArray = new char[19];
        }

        // Accessors.
        StringBuilder getStringBuilder() {
            sb.setLength(0);
            return sb;
        }

        char[] getCompactCharArray() {
            return cmpCharArray;
        }

        /**
         * Places characters representing the intCompact in {@code long} into
         * cmpCharArray and returns the offset to the array where the
         * representation starts.
         *
         * @param intCompact the number to put into the cmpCharArray.
         * @return offset to the array where the representation starts.
         * Note: intCompact must be greater or equal to zero.
         */
        int putIntCompact(long intCompact) {
            assert intCompact >= 0;

            long q;
            int r;
            // since we start from the least significant digit, charPos points to
            // the last character in cmpCharArray.
            int charPos = cmpCharArray.length;

            // Get 2 digits/iteration using longs until quotient fits into an int
            while (intCompact > Integer.MAX_VALUE) {
                q = intCompact / 100;
                r = (int)(intCompact - q * 100);
                intCompact = q;
                cmpCharArray[--charPos] = DIGIT_ONES[r];
                cmpCharArray[--charPos] = DIGIT_TENS[r];
            }

            // Get 2 digits/iteration using ints when i2 >= 100
            int q2;
            int i2 = (int)intCompact;
            while (i2 >= 100) {
                q2 = i2 / 100;
                r  = i2 - q2 * 100;
                i2 = q2;
                cmpCharArray[--charPos] = DIGIT_ONES[r];
                cmpCharArray[--charPos] = DIGIT_TENS[r];
            }

            cmpCharArray[--charPos] = DIGIT_ONES[i2];
            if (i2 >= 10)
                cmpCharArray[--charPos] = DIGIT_TENS[i2];

            return charPos;
        }

        static final char[] DIGIT_TENS = {
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
        };

        static final char[] DIGIT_ONES = {
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
        };
    }

    private String layoutChars(boolean sci) {
        if (scale == 0)                      // zero scale is trivial
            return (intCompact != INFLATED) ?
                Long.toString(intCompact):
                intVal.toString();
        if (scale == 2  &&
            intCompact >= 0 && intCompact < Integer.MAX_VALUE) {
            // currency fast path
            int lowInt = (int)intCompact % 100;
            int highInt = (int)intCompact / 100;
            return (Integer.toString(highInt) + '.' +
                    StringBuilderHelper.DIGIT_TENS[lowInt] +
                    StringBuilderHelper.DIGIT_ONES[lowInt]) ;
        }

        StringBuilderHelper sbHelper = threadLocalStringBuilderHelper.get();
        char[] coeff;
        int offset;  // offset is the starting index for coeff array
        // Get the significand as an absolute value
        if (intCompact != INFLATED) {
            offset = sbHelper.putIntCompact(Math.abs(intCompact));
            coeff  = sbHelper.getCompactCharArray();
        } else {
            offset = 0;
            coeff  = intVal.abs().toString().toCharArray();
        }

        // Construct a buffer, with sufficient capacity for all cases.
        // If E-notation is needed, length will be: +1 if negative, +1
        // if '.' needed, +2 for "E+", + up to 10 for adjusted exponent.
        // Otherwise it could have +1 if negative, plus leading "0.00000"
        StringBuilder buf = sbHelper.getStringBuilder();
        if (signum() < 0)             // prefix '-' if negative
            buf.append('-');
        int coeffLen = coeff.length - offset;
        long adjusted = -(long)scale + (coeffLen -1);
        if ((scale >= 0) && (adjusted >= -6)) { // plain number
            int pad = scale - coeffLen;         // count of padding zeros
            if (pad >= 0) {                     // 0.xxx form
                buf.append('0');
                buf.append('.');
                for (; pad>0; pad--) {
                    buf.append('0');
                }
                buf.append(coeff, offset, coeffLen);
            } else {                         // xx.xx form
                buf.append(coeff, offset, -pad);
                buf.append('.');
                buf.append(coeff, -pad + offset, scale);
            }
        } else { // E-notation is needed
            if (sci) {                       // Scientific notation
                buf.append(coeff[offset]);   // first character
                if (coeffLen > 1) {          // more to come
                    buf.append('.');
                    buf.append(coeff, offset + 1, coeffLen - 1);
                }
            } else {                         // Engineering notation
                int sig = (int)(adjusted % 3);
                if (sig < 0)
                    sig += 3;                // [adjusted was negative]
                adjusted -= sig;             // now a multiple of 3
                sig++;
                if (signum() == 0) {
                    switch (sig) {
                    case 1:
                        buf.append('0'); // exponent is a multiple of three
                        break;
                    case 2:
                        buf.append("0.00");
                        adjusted += 3;
                        break;
                    case 3:
                        buf.append("0.0");
                        adjusted += 3;
                        break;
                    default:
                        throw new AssertionError("Unexpected sig value " + sig);
                    }
                } else if (sig >= coeffLen) {   // significand all in integer
                    buf.append(coeff, offset, coeffLen);
                    // may need some zeros, too
                    for (int i = sig - coeffLen; i > 0; i--) {
                        buf.append('0');
                    }
                } else {                     // xx.xxE form
                    buf.append(coeff, offset, sig);
                    buf.append('.');
                    buf.append(coeff, offset + sig, coeffLen - sig);
                }
            }
            if (adjusted != 0) {             // [!sci could have made 0]
                buf.append('E');
                if (adjusted > 0)            // force sign for positive
                    buf.append('+');
                buf.append(adjusted);
            }
        }
        return buf.toString();
    }

    private static BigInteger bigTenToThe(int n) {
        if (n < 0)
            return BigInteger.ZERO;

        if (n < BIG_TEN_POWERS_TABLE_MAX) {
            BigInteger[] pows = BIG_TEN_POWERS_TABLE;
            if (n < pows.length)
                return pows[n];
            else
                return expandBigIntegerTenPowers(n);
        }

        return BigInteger.TEN.pow(n);
    }

    private static BigInteger expandBigIntegerTenPowers(int n) {
        synchronized(BigDecimal.class) {
            BigInteger[] pows = BIG_TEN_POWERS_TABLE;
            int curLen = pows.length;
            // The following comparison and the above synchronized statement is
            // to prevent multiple threads from expanding the same array.
            if (curLen <= n) {
                int newLen = curLen << 1;
                while (newLen <= n) {
                    newLen <<= 1;
                }
                pows = Arrays.copyOf(pows, newLen);
                for (int i = curLen; i < newLen; i++) {
                    pows[i] = pows[i - 1].multiply(BigInteger.TEN);
                }
                // Based on the following facts:
                // 1. pows is a private local varible;
                // 2. the following store is a volatile store.
                // the newly created array elements can be safely published.
                BIG_TEN_POWERS_TABLE = pows;
            }
            return pows[n];
        }
    }

    private static final long[] LONG_TEN_POWERS_TABLE = {
        1,                     // 0 / 10^0
        10,                    // 1 / 10^1
        100,                   // 2 / 10^2
        1000,                  // 3 / 10^3
        10000,                 // 4 / 10^4
        100000,                // 5 / 10^5
        1000000,               // 6 / 10^6
        10000000,              // 7 / 10^7
        100000000,             // 8 / 10^8
        1000000000,            // 9 / 10^9
        10000000000L,          // 10 / 10^10
        100000000000L,         // 11 / 10^11
        1000000000000L,        // 12 / 10^12
        10000000000000L,       // 13 / 10^13
        100000000000000L,      // 14 / 10^14
        1000000000000000L,     // 15 / 10^15
        10000000000000000L,    // 16 / 10^16
        100000000000000000L,   // 17 / 10^17
        1000000000000000000L   // 18 / 10^18
    };

    private static volatile BigInteger BIG_TEN_POWERS_TABLE[] = {
        BigInteger.ONE,
        BigInteger.valueOf(10),
        BigInteger.valueOf(100),
        BigInteger.valueOf(1000),
        BigInteger.valueOf(10000),
        BigInteger.valueOf(100000),
        BigInteger.valueOf(1000000),
        BigInteger.valueOf(10000000),
        BigInteger.valueOf(100000000),
        BigInteger.valueOf(1000000000),
        BigInteger.valueOf(10000000000L),
        BigInteger.valueOf(100000000000L),
        BigInteger.valueOf(1000000000000L),
        BigInteger.valueOf(10000000000000L),
        BigInteger.valueOf(100000000000000L),
        BigInteger.valueOf(1000000000000000L),
        BigInteger.valueOf(10000000000000000L),
        BigInteger.valueOf(100000000000000000L),
        BigInteger.valueOf(1000000000000000000L)
    };

    private static final int BIG_TEN_POWERS_TABLE_INITLEN =
        BIG_TEN_POWERS_TABLE.length;
    private static final int BIG_TEN_POWERS_TABLE_MAX =
        16 * BIG_TEN_POWERS_TABLE_INITLEN;

    private static final long THRESHOLDS_TABLE[] = {
        Long.MAX_VALUE,                     // 0
        Long.MAX_VALUE/10L,                 // 1
        Long.MAX_VALUE/100L,                // 2
        Long.MAX_VALUE/1000L,               // 3
        Long.MAX_VALUE/10000L,              // 4
        Long.MAX_VALUE/100000L,             // 5
        Long.MAX_VALUE/1000000L,            // 6
        Long.MAX_VALUE/10000000L,           // 7
        Long.MAX_VALUE/100000000L,          // 8
        Long.MAX_VALUE/1000000000L,         // 9
        Long.MAX_VALUE/10000000000L,        // 10
        Long.MAX_VALUE/100000000000L,       // 11
        Long.MAX_VALUE/1000000000000L,      // 12
        Long.MAX_VALUE/10000000000000L,     // 13
        Long.MAX_VALUE/100000000000000L,    // 14
        Long.MAX_VALUE/1000000000000000L,   // 15
        Long.MAX_VALUE/10000000000000000L,  // 16
        Long.MAX_VALUE/100000000000000000L, // 17
        Long.MAX_VALUE/1000000000000000000L // 18
    };

    private static long longMultiplyPowerTen(long val, int n) {
        if (val == 0 || n <= 0)
            return val;
        long[] tab = LONG_TEN_POWERS_TABLE;
        long[] bounds = THRESHOLDS_TABLE;
        if (n < tab.length && n < bounds.length) {
            long tenpower = tab[n];
            if (val == 1)
                return tenpower;
            if (Math.abs(val) <= bounds[n])
                return val * tenpower;
        }
        return INFLATED;
    }

    /**
     * Compute this * 10 ^ n.
     * Needed mainly to allow special casing to trap zero value
     */
    private BigInteger bigMultiplyPowerTen(int n) {
        if (n <= 0)
            return this.inflated();

        if (intCompact != INFLATED)
            return bigTenToThe(n).multiply(intCompact);
        else
            return intVal.multiply(bigTenToThe(n));
    }

    private BigInteger inflated() {
        if (intVal == null) {
            return BigInteger.valueOf(intCompact);
        }
        return intVal;
    }

    private static void matchScale(BigDecimal[] val) {
        if (val[0].scale < val[1].scale) {
            val[0] = val[0].setScale(val[1].scale, ROUND_UNNECESSARY);
        } else if (val[1].scale < val[0].scale) {
            val[1] = val[1].setScale(val[0].scale, ROUND_UNNECESSARY);
        }
    }

    private static class UnsafeHolder {
        private static final jdk.internal.misc.Unsafe unsafe
                = jdk.internal.misc.Unsafe.getUnsafe();
        private static final long intCompactOffset
                = unsafe.objectFieldOffset(BigDecimal.class, "intCompact");
        private static final long intValOffset
                = unsafe.objectFieldOffset(BigDecimal.class, "intVal");

        static void setIntCompact(BigDecimal bd, long val) {
            unsafe.putLong(bd, intCompactOffset, val);
        }

        static void setIntValVolatile(BigDecimal bd, BigInteger val) {
            unsafe.putObjectVolatile(bd, intValOffset, val);
        }
    }

    private void readObject(java.io.ObjectInputStream s)
        throws java.io.IOException, ClassNotFoundException {
        // Read in all fields
        s.defaultReadObject();
        // validate possibly bad fields
        if (intVal == null) {
            String message = "BigDecimal: null intVal in stream";
            throw new java.io.StreamCorruptedException(message);
        // [all values of scale are now allowed]
        }
        UnsafeHolder.setIntCompact(this, compactValFor(intVal));
    }

   private void writeObject(java.io.ObjectOutputStream s)
       throws java.io.IOException {
       // Must inflate to maintain compatible serial form.
       if (this.intVal == null)
           UnsafeHolder.setIntValVolatile(this, BigInteger.valueOf(this.intCompact));
       // Could reset intVal back to null if it has to be set.
       s.defaultWriteObject();
   }

    static int longDigitLength(long x) {
        /*
         * As described in "Bit Twiddling Hacks" by Sean Anderson,
         * (http://graphics.stanford.edu/~seander/bithacks.html)
         * integer log 10 of x is within 1 of (1233/4096)* (1 +
         * integer log 2 of x). The fraction 1233/4096 approximates
         * log10(2). So we first do a version of log2 (a variant of
         * Long class with pre-checks and opposite directionality) and
         * then scale and check against powers table. This is a little
         * simpler in present context than the version in Hacker's
         * Delight sec 11-4. Adding one to bit length allows comparing
         * downward from the LONG_TEN_POWERS_TABLE that we need
         * anyway.
         */
        assert x != BigDecimal.INFLATED;
        if (x < 0)
            x = -x;
        if (x < 10) // must screen for 0, might as well 10
            return 1;
        int r = ((64 - Long.numberOfLeadingZeros(x) + 1) * 1233) >>> 12;
        long[] tab = LONG_TEN_POWERS_TABLE;
        // if r >= length, must have max possible digits for long
        return (r >= tab.length || x < tab[r]) ? r : r + 1;
    }

    private static int bigDigitLength(BigInteger b) {
        /*
         * Same idea as the long version, but we need a better
         * approximation of log10(2). Using 646456993/2^31
         * is accurate up to max possible reported bitLength.
         */
        if (b.signum == 0)
            return 1;
        int r = (int)((((long)b.bitLength() + 1) * 646456993) >>> 31);
        return b.compareMagnitude(bigTenToThe(r)) < 0? r : r+1;
    }

    private int checkScale(long val) {
        int asInt = (int)val;
        if (asInt != val) {
            asInt = val>Integer.MAX_VALUE ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            BigInteger b;
            if (intCompact != 0 &&
                ((b = intVal) == null || b.signum() != 0))
                throw new ArithmeticException(asInt>0 ? "Underflow":"Overflow");
        }
        return asInt;
    }

    private static long compactValFor(BigInteger b) {
        int[] m = b.mag;
        int len = m.length;
        if (len == 0)
            return 0;
        int d = m[0];
        if (len > 2 || (len == 2 && d < 0))
            return INFLATED;

        long u = (len == 2)?
            (((long) m[1] & LONG_MASK) + (((long)d) << 32)) :
            (((long)d)   & LONG_MASK);
        return (b.signum < 0)? -u : u;
    }

    private static int longCompareMagnitude(long x, long y) {
        if (x < 0)
            x = -x;
        if (y < 0)
            y = -y;
        return (x < y) ? -1 : ((x == y) ? 0 : 1);
    }

    private static int saturateLong(long s) {
        int i = (int)s;
        return (s == i) ? i : (s < 0 ? Integer.MIN_VALUE : Integer.MAX_VALUE);
    }

    private static void print(String name, BigDecimal bd) {
        System.err.format("%s:\tintCompact %d\tintVal %d\tscale %d\tprecision %d%n",
                          name,
                          bd.intCompact,
                          bd.intVal,
                          bd.scale,
                          bd.precision);
    }

    private BigDecimal audit() {
        if (intCompact == INFLATED) {
            if (intVal == null) {
                print("audit", this);
                throw new AssertionError("null intVal");
            }
            // Check precision
            if (precision > 0 && precision != bigDigitLength(intVal)) {
                print("audit", this);
                throw new AssertionError("precision mismatch");
            }
        } else {
            if (intVal != null) {
                long val = intVal.longValue();
                if (val != intCompact) {
                    print("audit", this);
                    throw new AssertionError("Inconsistent state, intCompact=" +
                                             intCompact + "\t intVal=" + val);
                }
            }
            // Check precision
            if (precision > 0 && precision != longDigitLength(intCompact)) {
                print("audit", this);
                throw new AssertionError("precision mismatch");
            }
        }
        return this;
    }

    private static int checkScaleNonZero(long val) {
        int asInt = (int)val;
        if (asInt != val) {
            throw new ArithmeticException(asInt>0 ? "Underflow":"Overflow");
        }
        return asInt;
    }

    private static int checkScale(long intCompact, long val) {
        int asInt = (int)val;
        if (asInt != val) {
            asInt = val>Integer.MAX_VALUE ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            if (intCompact != 0)
                throw new ArithmeticException(asInt>0 ? "Underflow":"Overflow");
        }
        return asInt;
    }

    private static int checkScale(BigInteger intVal, long val) {
        int asInt = (int)val;
        if (asInt != val) {
            asInt = val>Integer.MAX_VALUE ? Integer.MAX_VALUE : Integer.MIN_VALUE;
            if (intVal.signum() != 0)
                throw new ArithmeticException(asInt>0 ? "Underflow":"Overflow");
        }
        return asInt;
    }

    private static BigDecimal doRound(BigDecimal val, MathContext mc) {
        int mcp = mc.precision;
        boolean wasDivided = false;
        if (mcp > 0) {
            BigInteger intVal = val.intVal;
            long compactVal = val.intCompact;
            int scale = val.scale;
            int prec = val.precision();
            int mode = mc.roundingMode.oldMode;
            int drop;
            if (compactVal == INFLATED) {
                drop = prec - mcp;
                while (drop > 0) {
                    scale = checkScaleNonZero((long) scale - drop);
                    intVal = divideAndRoundByTenPow(intVal, drop, mode);
                    wasDivided = true;
                    compactVal = compactValFor(intVal);
                    if (compactVal != INFLATED) {
                        prec = longDigitLength(compactVal);
                        break;
                    }
                    prec = bigDigitLength(intVal);
                    drop = prec - mcp;
                }
            }
            if (compactVal != INFLATED) {
                drop = prec - mcp;  // drop can't be more than 18
                while (drop > 0) {
                    scale = checkScaleNonZero((long) scale - drop);
                    compactVal = divideAndRound(compactVal, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                    wasDivided = true;
                    prec = longDigitLength(compactVal);
                    drop = prec - mcp;
                    intVal = null;
                }
            }
            return wasDivided ? new BigDecimal(intVal,compactVal,scale,prec) : val;
        }
        return val;
    }

    private static BigDecimal doRound(long compactVal, int scale, MathContext mc) {
        int mcp = mc.precision;
        if (mcp > 0 && mcp < 19) {
            int prec = longDigitLength(compactVal);
            int drop = prec - mcp;  // drop can't be more than 18
            while (drop > 0) {
                scale = checkScaleNonZero((long) scale - drop);
                compactVal = divideAndRound(compactVal, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                prec = longDigitLength(compactVal);
                drop = prec - mcp;
            }
            return valueOf(compactVal, scale, prec);
        }
        return valueOf(compactVal, scale);
    }

    private static BigDecimal doRound(BigInteger intVal, int scale, MathContext mc) {
        int mcp = mc.precision;
        int prec = 0;
        if (mcp > 0) {
            long compactVal = compactValFor(intVal);
            int mode = mc.roundingMode.oldMode;
            int drop;
            if (compactVal == INFLATED) {
                prec = bigDigitLength(intVal);
                drop = prec - mcp;
                while (drop > 0) {
                    scale = checkScaleNonZero((long) scale - drop);
                    intVal = divideAndRoundByTenPow(intVal, drop, mode);
                    compactVal = compactValFor(intVal);
                    if (compactVal != INFLATED) {
                        break;
                    }
                    prec = bigDigitLength(intVal);
                    drop = prec - mcp;
                }
            }
            if (compactVal != INFLATED) {
                prec = longDigitLength(compactVal);
                drop = prec - mcp;     // drop can't be more than 18
                while (drop > 0) {
                    scale = checkScaleNonZero((long) scale - drop);
                    compactVal = divideAndRound(compactVal, LONG_TEN_POWERS_TABLE[drop], mc.roundingMode.oldMode);
                    prec = longDigitLength(compactVal);
                    drop = prec - mcp;
                }
                return valueOf(compactVal,scale,prec);
            }
        }
        return new BigDecimal(intVal,INFLATED,scale,prec);
    }

    private static BigInteger divideAndRoundByTenPow(BigInteger intVal, int tenPow, int roundingMode) {
        if (tenPow < LONG_TEN_POWERS_TABLE.length)
            intVal = divideAndRound(intVal, LONG_TEN_POWERS_TABLE[tenPow], roundingMode);
        else
            intVal = divideAndRound(intVal, bigTenToThe(tenPow), roundingMode);
        return intVal;
    }

    private static BigDecimal divideAndRound(long ldividend, long ldivisor, int scale, int roundingMode,
                                             int preferredScale) {

        int qsign; // quotient sign
        long q = ldividend / ldivisor; // store quotient in long
        if (roundingMode == ROUND_DOWN && scale == preferredScale)
            return valueOf(q, scale);
        long r = ldividend % ldivisor; // store remainder in long
        qsign = ((ldividend < 0) == (ldivisor < 0)) ? 1 : -1;
        if (r != 0) {
            boolean increment = needIncrement(ldivisor, roundingMode, qsign, q, r);
            return valueOf((increment ? q + qsign : q), scale);
        } else {
            if (preferredScale != scale)
                return createAndStripZerosToMatchScale(q, scale, preferredScale);
            else
                return valueOf(q, scale);
        }
    }

    private static long divideAndRound(long ldividend, long ldivisor, int roundingMode) {
        int qsign; // quotient sign
        long q = ldividend / ldivisor; // store quotient in long
        if (roundingMode == ROUND_DOWN)
            return q;
        long r = ldividend % ldivisor; // store remainder in long
        qsign = ((ldividend < 0) == (ldivisor < 0)) ? 1 : -1;
        if (r != 0) {
            boolean increment = needIncrement(ldivisor, roundingMode, qsign, q,     r);
            return increment ? q + qsign : q;
        } else {
            return q;
        }
    }

    private static boolean commonNeedIncrement(int roundingMode, int qsign,
                                        int cmpFracHalf, boolean oddQuot) {
        switch(roundingMode) {
        case ROUND_UNNECESSARY:
            throw new ArithmeticException("Rounding necessary");

        case ROUND_UP: // Away from zero
            return true;

        case ROUND_DOWN: // Towards zero
            return false;

        case ROUND_CEILING: // Towards +infinity
            return qsign > 0;

        case ROUND_FLOOR: // Towards -infinity
            return qsign < 0;

        default: // Some kind of half-way rounding
            assert roundingMode >= ROUND_HALF_UP &&
                roundingMode <= ROUND_HALF_EVEN: "Unexpected rounding mode" + RoundingMode.valueOf(roundingMode);

            if (cmpFracHalf < 0 ) // We're closer to higher digit
                return false;
            else if (cmpFracHalf > 0 ) // We're closer to lower digit
                return true;
            else { // half-way
                assert cmpFracHalf == 0;

                switch(roundingMode) {
                case ROUND_HALF_DOWN:
                    return false;

                case ROUND_HALF_UP:
                    return true;

                case ROUND_HALF_EVEN:
                    return oddQuot;

                default:
                    throw new AssertionError("Unexpected rounding mode" + roundingMode);
                }
            }
        }
    }

    private static boolean needIncrement(long ldivisor, int roundingMode,
                                         int qsign, long q, long r) {
        assert r != 0L;

        int cmpFracHalf;
        if (r <= HALF_LONG_MIN_VALUE || r > HALF_LONG_MAX_VALUE) {
            cmpFracHalf = 1; // 2 * r can't fit into long
        } else {
            cmpFracHalf = longCompareMagnitude(2 * r, ldivisor);
        }

        return commonNeedIncrement(roundingMode, qsign, cmpFracHalf, (q & 1L) != 0L);
    }

    private static BigInteger divideAndRound(BigInteger bdividend, long ldivisor, int roundingMode) {
        // Descend into mutables for faster remainder checks
        MutableBigInteger mdividend = new MutableBigInteger(bdividend.mag);
        // store quotient
        MutableBigInteger mq = new MutableBigInteger();
        // store quotient & remainder in long
        long r = mdividend.divide(ldivisor, mq);
        // record remainder is zero or not
        boolean isRemainderZero = (r == 0);
        // quotient sign
        int qsign = (ldivisor < 0) ? -bdividend.signum : bdividend.signum;
        if (!isRemainderZero) {
            if(needIncrement(ldivisor, roundingMode, qsign, mq, r)) {
                mq.add(MutableBigInteger.ONE);
            }
        }
        return mq.toBigInteger(qsign);
    }

    private static BigDecimal divideAndRound(BigInteger bdividend,
                                             long ldivisor, int scale, int roundingMode, int preferredScale) {
        // Descend into mutables for faster remainder checks
        MutableBigInteger mdividend = new MutableBigInteger(bdividend.mag);
        // store quotient
        MutableBigInteger mq = new MutableBigInteger();
        // store quotient & remainder in long
        long r = mdividend.divide(ldivisor, mq);
        // record remainder is zero or not
        boolean isRemainderZero = (r == 0);
        // quotient sign
        int qsign = (ldivisor < 0) ? -bdividend.signum : bdividend.signum;
        if (!isRemainderZero) {
            if(needIncrement(ldivisor, roundingMode, qsign, mq, r)) {
                mq.add(MutableBigInteger.ONE);
            }
            return mq.toBigDecimal(qsign, scale);
        } else {
            if (preferredScale != scale) {
                long compactVal = mq.toCompactValue(qsign);
                if(compactVal!=INFLATED) {
                    return createAndStripZerosToMatchScale(compactVal, scale, preferredScale);
                }
                BigInteger intVal =  mq.toBigInteger(qsign);
                return createAndStripZerosToMatchScale(intVal,scale, preferredScale);
            } else {
                return mq.toBigDecimal(qsign, scale);
            }
        }
    }

    private static boolean needIncrement(long ldivisor, int roundingMode,
                                         int qsign, MutableBigInteger mq, long r) {
        assert r != 0L;

        int cmpFracHalf;
        if (r <= HALF_LONG_MIN_VALUE || r > HALF_LONG_MAX_VALUE) {
            cmpFracHalf = 1; // 2 * r can't fit into long
        } else {
            cmpFracHalf = longCompareMagnitude(2 * r, ldivisor);
        }

        return commonNeedIncrement(roundingMode, qsign, cmpFracHalf, mq.isOdd());
    }

    private static BigInteger divideAndRound(BigInteger bdividend, BigInteger bdivisor, int roundingMode) {
        boolean isRemainderZero; // record remainder is zero or not
        int qsign; // quotient sign
        // Descend into mutables for faster remainder checks
        MutableBigInteger mdividend = new MutableBigInteger(bdividend.mag);
        MutableBigInteger mq = new MutableBigInteger();
        MutableBigInteger mdivisor = new MutableBigInteger(bdivisor.mag);
        MutableBigInteger mr = mdividend.divide(mdivisor, mq);
        isRemainderZero = mr.isZero();
        qsign = (bdividend.signum != bdivisor.signum) ? -1 : 1;
        if (!isRemainderZero) {
            if (needIncrement(mdivisor, roundingMode, qsign, mq, mr)) {
                mq.add(MutableBigInteger.ONE);
            }
        }
        return mq.toBigInteger(qsign);
    }

    private static BigDecimal divideAndRound(BigInteger bdividend, BigInteger bdivisor, int scale, int roundingMode,
                                             int preferredScale) {
        boolean isRemainderZero; // record remainder is zero or not
        int qsign; // quotient sign
        // Descend into mutables for faster remainder checks
        MutableBigInteger mdividend = new MutableBigInteger(bdividend.mag);
        MutableBigInteger mq = new MutableBigInteger();
        MutableBigInteger mdivisor = new MutableBigInteger(bdivisor.mag);
        MutableBigInteger mr = mdividend.divide(mdivisor, mq);
        isRemainderZero = mr.isZero();
        qsign = (bdividend.signum != bdivisor.signum) ? -1 : 1;
        if (!isRemainderZero) {
            if (needIncrement(mdivisor, roundingMode, qsign, mq, mr)) {
                mq.add(MutableBigInteger.ONE);
            }
            return mq.toBigDecimal(qsign, scale);
        } else {
            if (preferredScale != scale) {
                long compactVal = mq.toCompactValue(qsign);
                if (compactVal != INFLATED) {
                    return createAndStripZerosToMatchScale(compactVal, scale, preferredScale);
                }
                BigInteger intVal = mq.toBigInteger(qsign);
                return createAndStripZerosToMatchScale(intVal, scale, preferredScale);
            } else {
                return mq.toBigDecimal(qsign, scale);
            }
        }
    }

    private static boolean needIncrement(MutableBigInteger mdivisor, int roundingMode,
                                         int qsign, MutableBigInteger mq, MutableBigInteger mr) {
        assert !mr.isZero();
        int cmpFracHalf = mr.compareHalf(mdivisor);
        return commonNeedIncrement(roundingMode, qsign, cmpFracHalf, mq.isOdd());
    }

    private static BigDecimal createAndStripZerosToMatchScale(BigInteger intVal, int scale, long preferredScale) {
        BigInteger qr[]; // quotient-remainder pair
        while (intVal.compareMagnitude(BigInteger.TEN) >= 0
               && scale > preferredScale) {
            if (intVal.testBit(0))
                break; // odd number cannot end in 0
            qr = intVal.divideAndRemainder(BigInteger.TEN);
            if (qr[1].signum() != 0)
                break; // non-0 remainder
            intVal = qr[0];
            scale = checkScale(intVal,(long) scale - 1); // could Overflow
        }
        return valueOf(intVal, scale, 0);
    }

    private static BigDecimal createAndStripZerosToMatchScale(long compactVal, int scale, long preferredScale) {
        while (Math.abs(compactVal) >= 10L && scale > preferredScale) {
            if ((compactVal & 1L) != 0L)
                break; // odd number cannot end in 0
            long r = compactVal % 10L;
            if (r != 0L)
                break; // non-0 remainder
            compactVal /= 10;
            scale = checkScale(compactVal, (long) scale - 1); // could Overflow
        }
        return valueOf(compactVal, scale);
    }

    private static BigDecimal stripZerosToMatchScale(BigInteger intVal, long intCompact, int scale, int preferredScale) {
        if(intCompact!=INFLATED) {
            return createAndStripZerosToMatchScale(intCompact, scale, preferredScale);
        } else {
            return createAndStripZerosToMatchScale(intVal==null ? INFLATED_BIGINT : intVal,
                                                   scale, preferredScale);
        }
    }

    private static long add(long xs, long ys){
        long sum = xs + ys;
        // See "Hacker's Delight" section 2-12 for explanation of
        // the overflow test.
        if ( (((sum ^ xs) & (sum ^ ys))) >= 0L) { // not overflowed
            return sum;
        }
        return INFLATED;
    }

    private static BigDecimal add(long xs, long ys, int scale){
        long sum = add(xs, ys);
        if (sum!=INFLATED)
            return BigDecimal.valueOf(sum, scale);
        return new BigDecimal(BigInteger.valueOf(xs).add(ys), scale);
    }

    private static BigDecimal add(final long xs, int scale1, final long ys, int scale2) {
        long sdiff = (long) scale1 - scale2;
        if (sdiff == 0) {
            return add(xs, ys, scale1);
        } else if (sdiff < 0) {
            int raise = checkScale(xs,-sdiff);
            long scaledX = longMultiplyPowerTen(xs, raise);
            if (scaledX != INFLATED) {
                return add(scaledX, ys, scale2);
            } else {
                BigInteger bigsum = bigMultiplyPowerTen(xs,raise).add(ys);
                return ((xs^ys)>=0) ? // same sign test
                    new BigDecimal(bigsum, INFLATED, scale2, 0)
                    : valueOf(bigsum, scale2, 0);
            }
        } else {
            int raise = checkScale(ys,sdiff);
            long scaledY = longMultiplyPowerTen(ys, raise);
            if (scaledY != INFLATED) {
                return add(xs, scaledY, scale1);
            } else {
                BigInteger bigsum = bigMultiplyPowerTen(ys,raise).add(xs);
                return ((xs^ys)>=0) ?
                    new BigDecimal(bigsum, INFLATED, scale1, 0)
                    : valueOf(bigsum, scale1, 0);
            }
        }
    }

    private static BigDecimal add(final long xs, int scale1, BigInteger snd, int scale2) {
        int rscale = scale1;
        long sdiff = (long)rscale - scale2;
        boolean sameSigns =  (Long.signum(xs) == snd.signum);
        BigInteger sum;
        if (sdiff < 0) {
            int raise = checkScale(xs,-sdiff);
            rscale = scale2;
            long scaledX = longMultiplyPowerTen(xs, raise);
            if (scaledX == INFLATED) {
                sum = snd.add(bigMultiplyPowerTen(xs,raise));
            } else {
                sum = snd.add(scaledX);
            }
        } else { //if (sdiff > 0) {
            int raise = checkScale(snd,sdiff);
            snd = bigMultiplyPowerTen(snd,raise);
            sum = snd.add(xs);
        }
        return (sameSigns) ?
            new BigDecimal(sum, INFLATED, rscale, 0) :
            valueOf(sum, rscale, 0);
    }

    private static BigDecimal add(BigInteger fst, int scale1, BigInteger snd, int scale2) {
        int rscale = scale1;
        long sdiff = (long)rscale - scale2;
        if (sdiff != 0) {
            if (sdiff < 0) {
                int raise = checkScale(fst,-sdiff);
                rscale = scale2;
                fst = bigMultiplyPowerTen(fst,raise);
            } else {
                int raise = checkScale(snd,sdiff);
                snd = bigMultiplyPowerTen(snd,raise);
            }
        }
        BigInteger sum = fst.add(snd);
        return (fst.signum == snd.signum) ?
                new BigDecimal(sum, INFLATED, rscale, 0) :
                valueOf(sum, rscale, 0);
    }

    private static BigInteger bigMultiplyPowerTen(long value, int n) {
        if (n <= 0)
            return BigInteger.valueOf(value);
        return bigTenToThe(n).multiply(value);
    }

    private static BigInteger bigMultiplyPowerTen(BigInteger value, int n) {
        if (n <= 0)
            return value;
        if(n<LONG_TEN_POWERS_TABLE.length) {
                return value.multiply(LONG_TEN_POWERS_TABLE[n]);
        }
        return value.multiply(bigTenToThe(n));
    }

    private static BigDecimal divideSmallFastPath(final long xs, int xscale,
                                                  final long ys, int yscale,
                                                  long preferredScale, MathContext mc) {
        int mcp = mc.precision;
        int roundingMode = mc.roundingMode.oldMode;

        assert (xscale <= yscale) && (yscale < 18) && (mcp < 18);
        int xraise = yscale - xscale; // xraise >=0
        long scaledX = (xraise==0) ? xs :
            longMultiplyPowerTen(xs, xraise); // can't overflow here!
        BigDecimal quotient;

        int cmp = longCompareMagnitude(scaledX, ys);
        if(cmp > 0) { // satisfy constraint (b)
            yscale -= 1; // [that is, divisor *= 10]
            int scl = checkScaleNonZero(preferredScale + yscale - xscale + mcp);
            if (checkScaleNonZero((long) mcp + yscale - xscale) > 0) {
                // assert newScale >= xscale
                int raise = checkScaleNonZero((long) mcp + yscale - xscale);
                long scaledXs;
                if ((scaledXs = longMultiplyPowerTen(xs, raise)) == INFLATED) {
                    quotient = null;
                    if((mcp-1) >=0 && (mcp-1)<LONG_TEN_POWERS_TABLE.length) {
                        quotient = multiplyDivideAndRound(LONG_TEN_POWERS_TABLE[mcp-1], scaledX, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
                    }
                    if(quotient==null) {
                        BigInteger rb = bigMultiplyPowerTen(scaledX,mcp-1);
                        quotient = divideAndRound(rb, ys,
                                                  scl, roundingMode, checkScaleNonZero(preferredScale));
                    }
                } else {
                    quotient = divideAndRound(scaledXs, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
                }
            } else {
                int newScale = checkScaleNonZero((long) xscale - mcp);
                // assert newScale >= yscale
                if (newScale == yscale) { // easy case
                    quotient = divideAndRound(xs, ys, scl, roundingMode,checkScaleNonZero(preferredScale));
                } else {
                    int raise = checkScaleNonZero((long) newScale - yscale);
                    long scaledYs;
                    if ((scaledYs = longMultiplyPowerTen(ys, raise)) == INFLATED) {
                        BigInteger rb = bigMultiplyPowerTen(ys,raise);
                        quotient = divideAndRound(BigInteger.valueOf(xs),
                                                  rb, scl, roundingMode,checkScaleNonZero(preferredScale));
                    } else {
                        quotient = divideAndRound(xs, scaledYs, scl, roundingMode,checkScaleNonZero(preferredScale));
                    }
                }
            }
        } else {
            // abs(scaledX) <= abs(ys)
            // result is "scaledX * 10^msp / ys"
            int scl = checkScaleNonZero(preferredScale + yscale - xscale + mcp);
            if(cmp==0) {
                // abs(scaleX)== abs(ys) => result will be scaled 10^mcp + correct sign
                quotient = roundedTenPower(((scaledX < 0) == (ys < 0)) ? 1 : -1, mcp, scl, checkScaleNonZero(preferredScale));
            } else {
                // abs(scaledX) < abs(ys)
                long scaledXs;
                if ((scaledXs = longMultiplyPowerTen(scaledX, mcp)) == INFLATED) {
                    quotient = null;
                    if(mcp<LONG_TEN_POWERS_TABLE.length) {
                        quotient = multiplyDivideAndRound(LONG_TEN_POWERS_TABLE[mcp], scaledX, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
                    }
                    if(quotient==null) {
                        BigInteger rb = bigMultiplyPowerTen(scaledX,mcp);
                        quotient = divideAndRound(rb, ys,
                                                  scl, roundingMode, checkScaleNonZero(preferredScale));
                    }
                } else {
                    quotient = divideAndRound(scaledXs, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
                }
            }
        }
        // doRound, here, only affects 1000000000 case.
        return doRound(quotient,mc);
    }

    private static BigDecimal divide(final long xs, int xscale, final long ys, int yscale, long preferredScale, MathContext mc) {
        int mcp = mc.precision;
        if(xscale <= yscale && yscale < 18 && mcp<18) {
            return divideSmallFastPath(xs, xscale, ys, yscale, preferredScale, mc);
        }
        if (compareMagnitudeNormalized(xs, xscale, ys, yscale) > 0) {// satisfy constraint (b)
            yscale -= 1; // [that is, divisor *= 10]
        }
        int roundingMode = mc.roundingMode.oldMode;
        // In order to find out whether the divide generates the exact result,
        // we avoid calling the above divide method. 'quotient' holds the
        // return BigDecimal object whose scale will be set to 'scl'.
        int scl = checkScaleNonZero(preferredScale + yscale - xscale + mcp);
        BigDecimal quotient;
        if (checkScaleNonZero((long) mcp + yscale - xscale) > 0) {
            int raise = checkScaleNonZero((long) mcp + yscale - xscale);
            long scaledXs;
            if ((scaledXs = longMultiplyPowerTen(xs, raise)) == INFLATED) {
                BigInteger rb = bigMultiplyPowerTen(xs,raise);
                quotient = divideAndRound(rb, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
            } else {
                quotient = divideAndRound(scaledXs, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
            }
        } else {
            int newScale = checkScaleNonZero((long) xscale - mcp);
            // assert newScale >= yscale
            if (newScale == yscale) { // easy case
                quotient = divideAndRound(xs, ys, scl, roundingMode,checkScaleNonZero(preferredScale));
            } else {
                int raise = checkScaleNonZero((long) newScale - yscale);
                long scaledYs;
                if ((scaledYs = longMultiplyPowerTen(ys, raise)) == INFLATED) {
                    BigInteger rb = bigMultiplyPowerTen(ys,raise);
                    quotient = divideAndRound(BigInteger.valueOf(xs),
                                              rb, scl, roundingMode,checkScaleNonZero(preferredScale));
                } else {
                    quotient = divideAndRound(xs, scaledYs, scl, roundingMode,checkScaleNonZero(preferredScale));
                }
            }
        }
        // doRound, here, only affects 1000000000 case.
        return doRound(quotient,mc);
    }

    private static BigDecimal divide(BigInteger xs, int xscale, long ys, int yscale, long preferredScale, MathContext mc) {
        // Normalize dividend & divisor so that both fall into [0.1, 0.999...]
        if ((-compareMagnitudeNormalized(ys, yscale, xs, xscale)) > 0) {// satisfy constraint (b)
            yscale -= 1; // [that is, divisor *= 10]
        }
        int mcp = mc.precision;
        int roundingMode = mc.roundingMode.oldMode;

        // In order to find out whether the divide generates the exact result,
        // we avoid calling the above divide method. 'quotient' holds the
        // return BigDecimal object whose scale will be set to 'scl'.
        BigDecimal quotient;
        int scl = checkScaleNonZero(preferredScale + yscale - xscale + mcp);
        if (checkScaleNonZero((long) mcp + yscale - xscale) > 0) {
            int raise = checkScaleNonZero((long) mcp + yscale - xscale);
            BigInteger rb = bigMultiplyPowerTen(xs,raise);
            quotient = divideAndRound(rb, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
        } else {
            int newScale = checkScaleNonZero((long) xscale - mcp);
            // assert newScale >= yscale
            if (newScale == yscale) { // easy case
                quotient = divideAndRound(xs, ys, scl, roundingMode,checkScaleNonZero(preferredScale));
            } else {
                int raise = checkScaleNonZero((long) newScale - yscale);
                long scaledYs;
                if ((scaledYs = longMultiplyPowerTen(ys, raise)) == INFLATED) {
                    BigInteger rb = bigMultiplyPowerTen(ys,raise);
                    quotient = divideAndRound(xs, rb, scl, roundingMode,checkScaleNonZero(preferredScale));
                } else {
                    quotient = divideAndRound(xs, scaledYs, scl, roundingMode,checkScaleNonZero(preferredScale));
                }
            }
        }
        // doRound, here, only affects 1000000000 case.
        return doRound(quotient, mc);
    }

    private static BigDecimal divide(long xs, int xscale, BigInteger ys, int yscale, long preferredScale, MathContext mc) {
        // Normalize dividend & divisor so that both fall into [0.1, 0.999...]
        if (compareMagnitudeNormalized(xs, xscale, ys, yscale) > 0) {// satisfy constraint (b)
            yscale -= 1; // [that is, divisor *= 10]
        }
        int mcp = mc.precision;
        int roundingMode = mc.roundingMode.oldMode;

        // In order to find out whether the divide generates the exact result,
        // we avoid calling the above divide method. 'quotient' holds the
        // return BigDecimal object whose scale will be set to 'scl'.
        BigDecimal quotient;
        int scl = checkScaleNonZero(preferredScale + yscale - xscale + mcp);
        if (checkScaleNonZero((long) mcp + yscale - xscale) > 0) {
            int raise = checkScaleNonZero((long) mcp + yscale - xscale);
            BigInteger rb = bigMultiplyPowerTen(xs,raise);
            quotient = divideAndRound(rb, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
        } else {
            int newScale = checkScaleNonZero((long) xscale - mcp);
            int raise = checkScaleNonZero((long) newScale - yscale);
            BigInteger rb = bigMultiplyPowerTen(ys,raise);
            quotient = divideAndRound(BigInteger.valueOf(xs), rb, scl, roundingMode,checkScaleNonZero(preferredScale));
        }
        // doRound, here, only affects 1000000000 case.
        return doRound(quotient, mc);
    }

    private static BigDecimal divide(BigInteger xs, int xscale, BigInteger ys, int yscale, long preferredScale, MathContext mc) {
        // Normalize dividend & divisor so that both fall into [0.1, 0.999...]
        if (compareMagnitudeNormalized(xs, xscale, ys, yscale) > 0) {// satisfy constraint (b)
            yscale -= 1; // [that is, divisor *= 10]
        }
        int mcp = mc.precision;
        int roundingMode = mc.roundingMode.oldMode;

        // In order to find out whether the divide generates the exact result,
        // we avoid calling the above divide method. 'quotient' holds the
        // return BigDecimal object whose scale will be set to 'scl'.
        BigDecimal quotient;
        int scl = checkScaleNonZero(preferredScale + yscale - xscale + mcp);
        if (checkScaleNonZero((long) mcp + yscale - xscale) > 0) {
            int raise = checkScaleNonZero((long) mcp + yscale - xscale);
            BigInteger rb = bigMultiplyPowerTen(xs,raise);
            quotient = divideAndRound(rb, ys, scl, roundingMode, checkScaleNonZero(preferredScale));
        } else {
            int newScale = checkScaleNonZero((long) xscale - mcp);
            int raise = checkScaleNonZero((long) newScale - yscale);
            BigInteger rb = bigMultiplyPowerTen(ys,raise);
            quotient = divideAndRound(xs, rb, scl, roundingMode,checkScaleNonZero(preferredScale));
        }
        // doRound, here, only affects 1000000000 case.
        return doRound(quotient, mc);
    }

    private static BigDecimal multiplyDivideAndRound(long dividend0, long dividend1, long divisor, int scale, int roundingMode,
                                                     int preferredScale) {
        int qsign = Long.signum(dividend0)*Long.signum(dividend1)*Long.signum(divisor);
        dividend0 = Math.abs(dividend0);
        dividend1 = Math.abs(dividend1);
        divisor = Math.abs(divisor);
        // multiply dividend0 * dividend1
        long d0_hi = dividend0 >>> 32;
        long d0_lo = dividend0 & LONG_MASK;
        long d1_hi = dividend1 >>> 32;
        long d1_lo = dividend1 & LONG_MASK;
        long product = d0_lo * d1_lo;
        long d0 = product & LONG_MASK;
        long d1 = product >>> 32;
        product = d0_hi * d1_lo + d1;
        d1 = product & LONG_MASK;
        long d2 = product >>> 32;
        product = d0_lo * d1_hi + d1;
        d1 = product & LONG_MASK;
        d2 += product >>> 32;
        long d3 = d2>>>32;
        d2 &= LONG_MASK;
        product = d0_hi*d1_hi + d2;
        d2 = product & LONG_MASK;
        d3 = ((product>>>32) + d3) & LONG_MASK;
        final long dividendHi = make64(d3,d2);
        final long dividendLo = make64(d1,d0);
        // divide
        return divideAndRound128(dividendHi, dividendLo, divisor, qsign, scale, roundingMode, preferredScale);
    }

    private static final long DIV_NUM_BASE = (1L<<32); // Number base (32 bits).

    private static BigDecimal divideAndRound128(final long dividendHi, final long dividendLo, long divisor, int sign,
                                                int scale, int roundingMode, int preferredScale) {
        if (dividendHi >= divisor) {
            return null;
        }

        final int shift = Long.numberOfLeadingZeros(divisor);
        divisor <<= shift;

        final long v1 = divisor >>> 32;
        final long v0 = divisor & LONG_MASK;

        long tmp = dividendLo << shift;
        long u1 = tmp >>> 32;
        long u0 = tmp & LONG_MASK;

        tmp = (dividendHi << shift) | (dividendLo >>> 64 - shift);
        long u2 = tmp & LONG_MASK;
        long q1, r_tmp;
        if (v1 == 1) {
            q1 = tmp;
            r_tmp = 0;
        } else if (tmp >= 0) {
            q1 = tmp / v1;
            r_tmp = tmp - q1 * v1;
        } else {
            long[] rq = divRemNegativeLong(tmp, v1);
            q1 = rq[1];
            r_tmp = rq[0];
        }

        while(q1 >= DIV_NUM_BASE || unsignedLongCompare(q1*v0, make64(r_tmp, u1))) {
            q1--;
            r_tmp += v1;
            if (r_tmp >= DIV_NUM_BASE)
                break;
        }

        tmp = mulsub(u2,u1,v1,v0,q1);
        u1 = tmp & LONG_MASK;
        long q0;
        if (v1 == 1) {
            q0 = tmp;
            r_tmp = 0;
        } else if (tmp >= 0) {
            q0 = tmp / v1;
            r_tmp = tmp - q0 * v1;
        } else {
            long[] rq = divRemNegativeLong(tmp, v1);
            q0 = rq[1];
            r_tmp = rq[0];
        }

        while(q0 >= DIV_NUM_BASE || unsignedLongCompare(q0*v0,make64(r_tmp,u0))) {
            q0--;
            r_tmp += v1;
            if (r_tmp >= DIV_NUM_BASE)
                break;
        }

        if((int)q1 < 0) {
            // result (which is positive and unsigned here)
            // can't fit into long due to sign bit is used for value
            MutableBigInteger mq = new MutableBigInteger(new int[]{(int)q1, (int)q0});
            if (roundingMode == ROUND_DOWN && scale == preferredScale) {
                return mq.toBigDecimal(sign, scale);
            }
            long r = mulsub(u1, u0, v1, v0, q0) >>> shift;
            if (r != 0) {
                if(needIncrement(divisor >>> shift, roundingMode, sign, mq, r)){
                    mq.add(MutableBigInteger.ONE);
                }
                return mq.toBigDecimal(sign, scale);
            } else {
                if (preferredScale != scale) {
                    BigInteger intVal =  mq.toBigInteger(sign);
                    return createAndStripZerosToMatchScale(intVal,scale, preferredScale);
                } else {
                    return mq.toBigDecimal(sign, scale);
                }
            }
        }

        long q = make64(q1,q0);
        q*=sign;

        if (roundingMode == ROUND_DOWN && scale == preferredScale)
            return valueOf(q, scale);

        long r = mulsub(u1, u0, v1, v0, q0) >>> shift;
        if (r != 0) {
            boolean increment = needIncrement(divisor >>> shift, roundingMode, sign, q, r);
            return valueOf((increment ? q + sign : q), scale);
        } else {
            if (preferredScale != scale) {
                return createAndStripZerosToMatchScale(q, scale, preferredScale);
            } else {
                return valueOf(q, scale);
            }
        }
    }

    private static BigDecimal roundedTenPower(int qsign, int raise, int scale, int preferredScale) {
        if (scale > preferredScale) {
            int diff = scale - preferredScale;
            if(diff < raise) {
                return scaledTenPow(raise - diff, qsign, preferredScale);
            } else {
                return valueOf(qsign,scale-raise);
            }
        } else {
            return scaledTenPow(raise, qsign, scale);
        }
    }

    static BigDecimal scaledTenPow(int n, int sign, int scale) {
        if (n < LONG_TEN_POWERS_TABLE.length)
            return valueOf(sign*LONG_TEN_POWERS_TABLE[n],scale);
        else {
            BigInteger unscaledVal = bigTenToThe(n);
            if(sign==-1) {
                unscaledVal = unscaledVal.negate();
            }
            return new BigDecimal(unscaledVal, INFLATED, scale, n+1);
        }
    }

    private static long[] divRemNegativeLong(long n, long d) {
        assert n < 0 : "Non-negative numerator " + n;
        assert d != 1 : "Unity denominator";

        // Approximate the quotient and remainder
        long q = (n >>> 1) / (d >>> 1);
        long r = n - q * d;

        // Correct the approximation
        while (r < 0) {
            r += d;
            q--;
        }
        while (r >= d) {
            r -= d;
            q++;
        }

        // n - q*d == r && 0 <= r < d, hence we're done.
        return new long[] {r, q};
    }

    private static long make64(long hi, long lo) {
        return hi<<32 | lo;
    }

    private static long mulsub(long u1, long u0, final long v1, final long v0, long q0) {
        long tmp = u0 - q0*v0;
        return make64(u1 + (tmp>>>32) - q0*v1,tmp & LONG_MASK);
    }

    private static boolean unsignedLongCompare(long one, long two) {
        return (one+Long.MIN_VALUE) > (two+Long.MIN_VALUE);
    }

    private static boolean unsignedLongCompareEq(long one, long two) {
        return (one+Long.MIN_VALUE) >= (two+Long.MIN_VALUE);
    }


    // Compare Normalize dividend & divisor so that both fall into [0.1, 0.999...]
    private static int compareMagnitudeNormalized(long xs, int xscale, long ys, int yscale) {
        // assert xs!=0 && ys!=0
        int sdiff = xscale - yscale;
        if (sdiff != 0) {
            if (sdiff < 0) {
                xs = longMultiplyPowerTen(xs, -sdiff);
            } else { // sdiff > 0
                ys = longMultiplyPowerTen(ys, sdiff);
            }
        }
        if (xs != INFLATED)
            return (ys != INFLATED) ? longCompareMagnitude(xs, ys) : -1;
        else
            return 1;
    }

    // Compare Normalize dividend & divisor so that both fall into [0.1, 0.999...]
    private static int compareMagnitudeNormalized(long xs, int xscale, BigInteger ys, int yscale) {
        // assert "ys can't be represented as long"
        if (xs == 0)
            return -1;
        int sdiff = xscale - yscale;
        if (sdiff < 0) {
            if (longMultiplyPowerTen(xs, -sdiff) == INFLATED ) {
                return bigMultiplyPowerTen(xs, -sdiff).compareMagnitude(ys);
            }
        }
        return -1;
    }

    // Compare Normalize dividend & divisor so that both fall into [0.1, 0.999...]
    private static int compareMagnitudeNormalized(BigInteger xs, int xscale, BigInteger ys, int yscale) {
        int sdiff = xscale - yscale;
        if (sdiff < 0) {
            return bigMultiplyPowerTen(xs, -sdiff).compareMagnitude(ys);
        } else { // sdiff >= 0
            return xs.compareMagnitude(bigMultiplyPowerTen(ys, sdiff));
        }
    }

    private static long multiply(long x, long y){
                long product = x * y;
        long ax = Math.abs(x);
        long ay = Math.abs(y);
        if (((ax | ay) >>> 31 == 0) || (y == 0) || (product / y == x)){
                        return product;
                }
        return INFLATED;
    }

    private static BigDecimal multiply(long x, long y, int scale) {
        long product = multiply(x, y);
        if(product!=INFLATED) {
            return valueOf(product,scale);
        }
        return new BigDecimal(BigInteger.valueOf(x).multiply(y),INFLATED,scale,0);
    }

    private static BigDecimal multiply(long x, BigInteger y, int scale) {
        if(x==0) {
            return zeroValueOf(scale);
        }
        return new BigDecimal(y.multiply(x),INFLATED,scale,0);
    }

    private static BigDecimal multiply(BigInteger x, BigInteger y, int scale) {
        return new BigDecimal(x.multiply(y),INFLATED,scale,0);
    }

    /**
     * Multiplies two long values and rounds according {@code MathContext}
     */
    private static BigDecimal multiplyAndRound(long x, long y, int scale, MathContext mc) {
        long product = multiply(x, y);
        if(product!=INFLATED) {
            return doRound(product, scale, mc);
        }
        // attempt to do it in 128 bits
        int rsign = 1;
        if(x < 0) {
            x = -x;
            rsign = -1;
        }
        if(y < 0) {
            y = -y;
            rsign *= -1;
        }
        // multiply dividend0 * dividend1
        long m0_hi = x >>> 32;
        long m0_lo = x & LONG_MASK;
        long m1_hi = y >>> 32;
        long m1_lo = y & LONG_MASK;
        product = m0_lo * m1_lo;
        long m0 = product & LONG_MASK;
        long m1 = product >>> 32;
        product = m0_hi * m1_lo + m1;
        m1 = product & LONG_MASK;
        long m2 = product >>> 32;
        product = m0_lo * m1_hi + m1;
        m1 = product & LONG_MASK;
        m2 += product >>> 32;
        long m3 = m2>>>32;
        m2 &= LONG_MASK;
        product = m0_hi*m1_hi + m2;
        m2 = product & LONG_MASK;
        m3 = ((product>>>32) + m3) & LONG_MASK;
        final long mHi = make64(m3,m2);
        final long mLo = make64(m1,m0);
        BigDecimal res = doRound128(mHi, mLo, rsign, scale, mc);
        if(res!=null) {
            return res;
        }
        res = new BigDecimal(BigInteger.valueOf(x).multiply(y*rsign), INFLATED, scale, 0);
        return doRound(res,mc);
    }

    private static BigDecimal multiplyAndRound(long x, BigInteger y, int scale, MathContext mc) {
        if(x==0) {
            return zeroValueOf(scale);
        }
        return doRound(y.multiply(x), scale, mc);
    }

    private static BigDecimal multiplyAndRound(BigInteger x, BigInteger y, int scale, MathContext mc) {
        return doRound(x.multiply(y), scale, mc);
    }

    /**
     * rounds 128-bit value according {@code MathContext}
     * returns null if result can't be repsented as compact BigDecimal.
     */
    private static BigDecimal doRound128(long hi, long lo, int sign, int scale, MathContext mc) {
        int mcp = mc.precision;
        int drop;
        BigDecimal res = null;
        if(((drop = precision(hi, lo) - mcp) > 0)&&(drop<LONG_TEN_POWERS_TABLE.length)) {
            scale = checkScaleNonZero((long)scale - drop);
            res = divideAndRound128(hi, lo, LONG_TEN_POWERS_TABLE[drop], sign, scale, mc.roundingMode.oldMode, scale);
        }
        if(res!=null) {
            return doRound(res,mc);
        }
        return null;
    }

    private static final long[][] LONGLONG_TEN_POWERS_TABLE = {
        {   0L, 0x8AC7230489E80000L },  //10^19
        {       0x5L, 0x6bc75e2d63100000L },  //10^20
        {       0x36L, 0x35c9adc5dea00000L },  //10^21
        {       0x21eL, 0x19e0c9bab2400000L  },  //10^22
        {       0x152dL, 0x02c7e14af6800000L  },  //10^23
        {       0xd3c2L, 0x1bcecceda1000000L  },  //10^24
        {       0x84595L, 0x161401484a000000L  },  //10^25
        {       0x52b7d2L, 0xdcc80cd2e4000000L  },  //10^26
        {       0x33b2e3cL, 0x9fd0803ce8000000L  },  //10^27
        {       0x204fce5eL, 0x3e25026110000000L  },  //10^28
        {       0x1431e0faeL, 0x6d7217caa0000000L  },  //10^29
        {       0xc9f2c9cd0L, 0x4674edea40000000L  },  //10^30
        {       0x7e37be2022L, 0xc0914b2680000000L  },  //10^31
        {       0x4ee2d6d415bL, 0x85acef8100000000L  },  //10^32
        {       0x314dc6448d93L, 0x38c15b0a00000000L  },  //10^33
        {       0x1ed09bead87c0L, 0x378d8e6400000000L  },  //10^34
        {       0x13426172c74d82L, 0x2b878fe800000000L  },  //10^35
        {       0xc097ce7bc90715L, 0xb34b9f1000000000L  },  //10^36
        {       0x785ee10d5da46d9L, 0x00f436a000000000L  },  //10^37
        {       0x4b3b4ca85a86c47aL, 0x098a224000000000L  },  //10^38
    };

    private static int precision(long hi, long lo){
        if(hi==0) {
            if(lo>=0) {
                return longDigitLength(lo);
            }
            return (unsignedLongCompareEq(lo, LONGLONG_TEN_POWERS_TABLE[0][1])) ? 20 : 19;
            // 0x8AC7230489E80000L  = unsigned 2^19
        }
        int r = ((128 - Long.numberOfLeadingZeros(hi) + 1) * 1233) >>> 12;
        int idx = r-19;
        return (idx >= LONGLONG_TEN_POWERS_TABLE.length || longLongCompareMagnitude(hi, lo,
                                                                                    LONGLONG_TEN_POWERS_TABLE[idx][0], LONGLONG_TEN_POWERS_TABLE[idx][1])) ? r : r + 1;
    }

    private static boolean longLongCompareMagnitude(long hi0, long lo0, long hi1, long lo1) {
        if(hi0!=hi1) {
            return hi0<hi1;
        }
        return (lo0+Long.MIN_VALUE) <(lo1+Long.MIN_VALUE);
    }

    private static BigDecimal divide(long dividend, int dividendScale, long divisor, int divisorScale, int scale, int roundingMode) {
        if (checkScale(dividend,(long)scale + divisorScale) > dividendScale) {
            int newScale = scale + divisorScale;
            int raise = newScale - dividendScale;
            if(raise<LONG_TEN_POWERS_TABLE.length) {
                long xs = dividend;
                if ((xs = longMultiplyPowerTen(xs, raise)) != INFLATED) {
                    return divideAndRound(xs, divisor, scale, roundingMode, scale);
                }
                BigDecimal q = multiplyDivideAndRound(LONG_TEN_POWERS_TABLE[raise], dividend, divisor, scale, roundingMode, scale);
                if(q!=null) {
                    return q;
                }
            }
            BigInteger scaledDividend = bigMultiplyPowerTen(dividend, raise);
            return divideAndRound(scaledDividend, divisor, scale, roundingMode, scale);
        } else {
            int newScale = checkScale(divisor,(long)dividendScale - scale);
            int raise = newScale - divisorScale;
            if(raise<LONG_TEN_POWERS_TABLE.length) {
                long ys = divisor;
                if ((ys = longMultiplyPowerTen(ys, raise)) != INFLATED) {
                    return divideAndRound(dividend, ys, scale, roundingMode, scale);
                }
            }
            BigInteger scaledDivisor = bigMultiplyPowerTen(divisor, raise);
            return divideAndRound(BigInteger.valueOf(dividend), scaledDivisor, scale, roundingMode, scale);
        }
    }

    private static BigDecimal divide(BigInteger dividend, int dividendScale, long divisor, int divisorScale, int scale, int roundingMode) {
        if (checkScale(dividend,(long)scale + divisorScale) > dividendScale) {
            int newScale = scale + divisorScale;
            int raise = newScale - dividendScale;
            BigInteger scaledDividend = bigMultiplyPowerTen(dividend, raise);
            return divideAndRound(scaledDividend, divisor, scale, roundingMode, scale);
        } else {
            int newScale = checkScale(divisor,(long)dividendScale - scale);
            int raise = newScale - divisorScale;
            if(raise<LONG_TEN_POWERS_TABLE.length) {
                long ys = divisor;
                if ((ys = longMultiplyPowerTen(ys, raise)) != INFLATED) {
                    return divideAndRound(dividend, ys, scale, roundingMode, scale);
                }
            }
            BigInteger scaledDivisor = bigMultiplyPowerTen(divisor, raise);
            return divideAndRound(dividend, scaledDivisor, scale, roundingMode, scale);
        }
    }

    private static BigDecimal divide(long dividend, int dividendScale, BigInteger divisor, int divisorScale, int scale, int roundingMode) {
        if (checkScale(dividend,(long)scale + divisorScale) > dividendScale) {
            int newScale = scale + divisorScale;
            int raise = newScale - dividendScale;
            BigInteger scaledDividend = bigMultiplyPowerTen(dividend, raise);
            return divideAndRound(scaledDividend, divisor, scale, roundingMode, scale);
        } else {
            int newScale = checkScale(divisor,(long)dividendScale - scale);
            int raise = newScale - divisorScale;
            BigInteger scaledDivisor = bigMultiplyPowerTen(divisor, raise);
            return divideAndRound(BigInteger.valueOf(dividend), scaledDivisor, scale, roundingMode, scale);
        }
    }

    private static BigDecimal divide(BigInteger dividend, int dividendScale, BigInteger divisor, int divisorScale, int scale, int roundingMode) {
        if (checkScale(dividend,(long)scale + divisorScale) > dividendScale) {
            int newScale = scale + divisorScale;
            int raise = newScale - dividendScale;
            BigInteger scaledDividend = bigMultiplyPowerTen(dividend, raise);
            return divideAndRound(scaledDividend, divisor, scale, roundingMode, scale);
        } else {
            int newScale = checkScale(divisor,(long)dividendScale - scale);
            int raise = newScale - divisorScale;
            BigInteger scaledDivisor = bigMultiplyPowerTen(divisor, raise);
            return divideAndRound(dividend, scaledDivisor, scale, roundingMode, scale);
        }
    }

}
