/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util;

import java.io.FilterOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

import sun.nio.cs.ISO_8859_1;

import jdk.internal.HotSpotIntrinsicCandidate;

/**
 *
 * @date 2023/7/6 10:07
 */
public class Base64 {

    private Base64() {}

    public static Encoder getEncoder() {
         return Encoder.RFC4648;
    }

    public static Encoder getUrlEncoder() {
         return Encoder.RFC4648_URLSAFE;
    }

    public static Encoder getMimeEncoder() {
        return Encoder.RFC2045;
    }

    public static Encoder getMimeEncoder(int lineLength, byte[] lineSeparator) {
         Objects.requireNonNull(lineSeparator);
         int[] base64 = Decoder.fromBase64;
         for (byte b : lineSeparator) {
             if (base64[b & 0xff] != -1)
                 throw new IllegalArgumentException(
                     "Illegal base64 line separator character 0x" + Integer.toString(b, 16));
         }
         // round down to nearest multiple of 4
         lineLength &= ~0b11;
         if (lineLength <= 0) {
             return Encoder.RFC4648;
         }
         return new Encoder(false, lineSeparator, lineLength, true);
    }

    public static Decoder getDecoder() {
         return Decoder.RFC4648;
    }

    public static Decoder getUrlDecoder() {
         return Decoder.RFC4648_URLSAFE;
    }

    public static Decoder getMimeDecoder() {
         return Decoder.RFC2045;
    }

    /**
     * 编码
     * @date 2023/7/6 10:08
     */
    public static class Encoder {

        private final byte[] newline;
        private final int linemax;
        private final boolean isURL;
        private final boolean doPadding;

        private Encoder(boolean isURL, byte[] newline, int linemax, boolean doPadding) {
            this.isURL = isURL;
            this.newline = newline;
            this.linemax = linemax;
            this.doPadding = doPadding;
        }

        /**
         * This array is a lookup table that translates 6-bit positive integer
         * index values into their "Base64 Alphabet" equivalents as specified
         * in "Table 1: The Base64 Alphabet" of RFC 2045 (and RFC 4648).
         */
        private static final char[] toBase64 = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '+', '/'
        };

        /**
         * It's the lookup table for "URL and Filename safe Base64" as specified
         * in Table 2 of the RFC 4648, with the '+' and '/' changed to '-' and
         * '_'. This table is used when BASE64_URL is specified.
         */
        private static final char[] toBase64URL = {
            'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'I', 'J', 'K', 'L', 'M',
            'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z',
            'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm',
            'n', 'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z',
            '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', '-', '_'
        };

        private static final int MIMELINEMAX = 76;
        private static final byte[] CRLF = new byte[] {'\r', '\n'};

        static final Encoder RFC4648 = new Encoder(false, null, -1, true);
        static final Encoder RFC4648_URLSAFE = new Encoder(true, null, -1, true);
        static final Encoder RFC2045 = new Encoder(false, CRLF, MIMELINEMAX, true);

        private final int outLength(int srclen) {
            int len = 0;
            if (doPadding) {
                len = 4 * ((srclen + 2) / 3);
            } else {
                int n = srclen % 3;
                len = 4 * (srclen / 3) + (n == 0 ? 0 : n + 1);
            }
            if (linemax > 0)                                  // line separators
                len += (len - 1) / linemax * newline.length;
            return len;
        }

        public byte[] encode(byte[] src) {
            int len = outLength(src.length);          // dst array size
            byte[] dst = new byte[len];
            int ret = encode0(src, 0, src.length, dst);
            if (ret != dst.length)
                 return Arrays.copyOf(dst, ret);
            return dst;
        }

        public int encode(byte[] src, byte[] dst) {
            int len = outLength(src.length);         // dst array size
            if (dst.length < len)
                throw new IllegalArgumentException(
                    "Output byte array is too small for encoding all input bytes");
            return encode0(src, 0, src.length, dst);
        }

        @SuppressWarnings("deprecation")
        public String encodeToString(byte[] src) {
            byte[] encoded = encode(src);
            return new String(encoded, 0, 0, encoded.length);
        }

        public ByteBuffer encode(ByteBuffer buffer) {
            int len = outLength(buffer.remaining());
            byte[] dst = new byte[len];
            int ret = 0;
            if (buffer.hasArray()) {
                ret = encode0(buffer.array(),
                              buffer.arrayOffset() + buffer.position(),
                              buffer.arrayOffset() + buffer.limit(),
                              dst);
                buffer.position(buffer.limit());
            } else {
                byte[] src = new byte[buffer.remaining()];
                buffer.get(src);
                ret = encode0(src, 0, src.length, dst);
            }
            if (ret != dst.length)
                 dst = Arrays.copyOf(dst, ret);
            return ByteBuffer.wrap(dst);
        }

        public OutputStream wrap(OutputStream os) {
            Objects.requireNonNull(os);
            return new EncOutputStream(os, isURL ? toBase64URL : toBase64,
                                       newline, linemax, doPadding);
        }

        public Encoder withoutPadding() {
            if (!doPadding)
                return this;
            return new Encoder(isURL, newline, linemax, false);
        }

        @HotSpotIntrinsicCandidate
        private void encodeBlock(byte[] src, int sp, int sl, byte[] dst, int dp, boolean isURL) {
            char[] base64 = isURL ? toBase64URL : toBase64;
            for (int sp0 = sp, dp0 = dp ; sp0 < sl; ) {
                int bits = (src[sp0++] & 0xff) << 16 |
                           (src[sp0++] & 0xff) <<  8 |
                           (src[sp0++] & 0xff);
                dst[dp0++] = (byte)base64[(bits >>> 18) & 0x3f];
                dst[dp0++] = (byte)base64[(bits >>> 12) & 0x3f];
                dst[dp0++] = (byte)base64[(bits >>> 6)  & 0x3f];
                dst[dp0++] = (byte)base64[bits & 0x3f];
            }
        }

        private int encode0(byte[] src, int off, int end, byte[] dst) {
            char[] base64 = isURL ? toBase64URL : toBase64;
            int sp = off;
            int slen = (end - off) / 3 * 3;
            int sl = off + slen;
            if (linemax > 0 && slen  > linemax / 4 * 3)
                slen = linemax / 4 * 3;
            int dp = 0;
            while (sp < sl) {
                int sl0 = Math.min(sp + slen, sl);
                encodeBlock(src, sp, sl0, dst, dp, isURL);
                int dlen = (sl0 - sp) / 3 * 4;
                dp += dlen;
                sp = sl0;
                if (dlen == linemax && sp < end) {
                    for (byte b : newline){
                        dst[dp++] = b;
                    }
                }
            }
            if (sp < end) {               // 1 or 2 leftover bytes
                int b0 = src[sp++] & 0xff;
                dst[dp++] = (byte)base64[b0 >> 2];
                if (sp == end) {
                    dst[dp++] = (byte)base64[(b0 << 4) & 0x3f];
                    if (doPadding) {
                        dst[dp++] = '=';
                        dst[dp++] = '=';
                    }
                } else {
                    int b1 = src[sp++] & 0xff;
                    dst[dp++] = (byte)base64[(b0 << 4) & 0x3f | (b1 >> 4)];
                    dst[dp++] = (byte)base64[(b1 << 2) & 0x3f];
                    if (doPadding) {
                        dst[dp++] = '=';
                    }
                }
            }
            return dp;
        }
    }

    /**
     * 解码
     * @date 2023/7/6 10:08
     */
    public static class Decoder {

        private final boolean isURL;
        private final boolean isMIME;

        private Decoder(boolean isURL, boolean isMIME) {
            this.isURL = isURL;
            this.isMIME = isMIME;
        }

        private static final int[] fromBase64 = new int[256];
        static {
            Arrays.fill(fromBase64, -1);
            for (int i = 0; i < Encoder.toBase64.length; i++)
                fromBase64[Encoder.toBase64[i]] = i;
            fromBase64['='] = -2;
        }

        private static final int[] fromBase64URL = new int[256];

        static {
            Arrays.fill(fromBase64URL, -1);
            for (int i = 0; i < Encoder.toBase64URL.length; i++)
                fromBase64URL[Encoder.toBase64URL[i]] = i;
            fromBase64URL['='] = -2;
        }

        static final Decoder RFC4648         = new Decoder(false, false);
        static final Decoder RFC4648_URLSAFE = new Decoder(true, false);
        static final Decoder RFC2045         = new Decoder(false, true);

        public byte[] decode(byte[] src) {
            byte[] dst = new byte[outLength(src, 0, src.length)];
            int ret = decode0(src, 0, src.length, dst);
            if (ret != dst.length) {
                dst = Arrays.copyOf(dst, ret);
            }
            return dst;
        }

        public byte[] decode(String src) {
            return decode(src.getBytes(ISO_8859_1.INSTANCE));
        }

        public int decode(byte[] src, byte[] dst) {
            int len = outLength(src, 0, src.length);
            if (dst.length < len)
                throw new IllegalArgumentException(
                    "Output byte array is too small for decoding all input bytes");
            return decode0(src, 0, src.length, dst);
        }

        public ByteBuffer decode(ByteBuffer buffer) {
            int pos0 = buffer.position();
            try {
                byte[] src;
                int sp, sl;
                if (buffer.hasArray()) {
                    src = buffer.array();
                    sp = buffer.arrayOffset() + buffer.position();
                    sl = buffer.arrayOffset() + buffer.limit();
                    buffer.position(buffer.limit());
                } else {
                    src = new byte[buffer.remaining()];
                    buffer.get(src);
                    sp = 0;
                    sl = src.length;
                }
                byte[] dst = new byte[outLength(src, sp, sl)];
                return ByteBuffer.wrap(dst, 0, decode0(src, sp, sl, dst));
            } catch (IllegalArgumentException iae) {
                buffer.position(pos0);
                throw iae;
            }
        }

        public InputStream wrap(InputStream is) {
            Objects.requireNonNull(is);
            return new DecInputStream(is, isURL ? fromBase64URL : fromBase64, isMIME);
        }

        private int outLength(byte[] src, int sp, int sl) {
            int[] base64 = isURL ? fromBase64URL : fromBase64;
            int paddings = 0;
            int len = sl - sp;
            if (len == 0)
                return 0;
            if (len < 2) {
                if (isMIME && base64[0] == -1)
                    return 0;
                throw new IllegalArgumentException(
                    "Input byte[] should at least have 2 bytes for base64 bytes");
            }
            if (isMIME) {
                // scan all bytes to fill out all non-alphabet. a performance
                // trade-off of pre-scan or Arrays.copyOf
                int n = 0;
                while (sp < sl) {
                    int b = src[sp++] & 0xff;
                    if (b == '=') {
                        len -= (sl - sp + 1);
                        break;
                    }
                    if ((b = base64[b]) == -1)
                        n++;
                }
                len -= n;
            } else {
                if (src[sl - 1] == '=') {
                    paddings++;
                    if (src[sl - 2] == '=')
                        paddings++;
                }
            }
            if (paddings == 0 && (len & 0x3) !=  0)
                paddings = 4 - (len & 0x3);
            return 3 * ((len + 3) / 4) - paddings;
        }

        private int decode0(byte[] src, int sp, int sl, byte[] dst) {
            int[] base64 = isURL ? fromBase64URL : fromBase64;
            int dp = 0;
            int bits = 0;
            int shiftto = 18;       // pos of first byte of 4-byte atom

            while (sp < sl) {
                if (shiftto == 18 && sp + 4 < sl) {       // fast path
                    int sl0 = sp + ((sl - sp) & ~0b11);
                    while (sp < sl0) {
                        int b1 = base64[src[sp++] & 0xff];
                        int b2 = base64[src[sp++] & 0xff];
                        int b3 = base64[src[sp++] & 0xff];
                        int b4 = base64[src[sp++] & 0xff];
                        if ((b1 | b2 | b3 | b4) < 0) {    // non base64 byte
                            sp -= 4;
                            break;
                        }
                        int bits0 = b1 << 18 | b2 << 12 | b3 << 6 | b4;
                        dst[dp++] = (byte)(bits0 >> 16);
                        dst[dp++] = (byte)(bits0 >>  8);
                        dst[dp++] = (byte)(bits0);
                    }
                    if (sp >= sl)
                        break;
                }
                int b = src[sp++] & 0xff;
                if ((b = base64[b]) < 0) {
                    if (b == -2) {         // padding byte '='
                        // =     shiftto==18 unnecessary padding
                        // x=    shiftto==12 a dangling single x
                        // x     to be handled together with non-padding case
                        // xx=   shiftto==6&&sp==sl missing last =
                        // xx=y  shiftto==6 last is not =
                        if (shiftto == 6 && (sp == sl || src[sp++] != '=') ||
                            shiftto == 18) {
                            throw new IllegalArgumentException(
                                "Input byte array has wrong 4-byte ending unit");
                        }
                        break;
                    }
                    if (isMIME)    // skip if for rfc2045
                        continue;
                    else
                        throw new IllegalArgumentException(
                            "Illegal base64 character " +
                            Integer.toString(src[sp - 1], 16));
                }
                bits |= (b << shiftto);
                shiftto -= 6;
                if (shiftto < 0) {
                    dst[dp++] = (byte)(bits >> 16);
                    dst[dp++] = (byte)(bits >>  8);
                    dst[dp++] = (byte)(bits);
                    shiftto = 18;
                    bits = 0;
                }
            }
            // reached end of byte array or hit padding '=' characters.
            if (shiftto == 6) {
                dst[dp++] = (byte)(bits >> 16);
            } else if (shiftto == 0) {
                dst[dp++] = (byte)(bits >> 16);
                dst[dp++] = (byte)(bits >>  8);
            } else if (shiftto == 12) {
                // dangling single "x", incorrectly encoded.
                throw new IllegalArgumentException(
                    "Last unit does not have enough valid bits");
            }
            // anything left is invalid, if is not MIME.
            // if MIME, ignore all non-base64 character
            while (sp < sl) {
                if (isMIME && base64[src[sp++] & 0xff] < 0)
                    continue;
                throw new IllegalArgumentException(
                    "Input byte array has incorrect ending byte at " + sp);
            }
            return dp;
        }
    }

    private static class EncOutputStream extends FilterOutputStream {

        private int leftover = 0;
        private int b0, b1, b2;
        private boolean closed = false;

        private final char[] base64;    // byte->base64 mapping
        private final byte[] newline;   // line separator, if needed
        private final int linemax;
        private final boolean doPadding;// whether or not to pad
        private int linepos = 0;
        private byte[] buf;

        EncOutputStream(OutputStream os, char[] base64,
                        byte[] newline, int linemax, boolean doPadding) {
            super(os);
            this.base64 = base64;
            this.newline = newline;
            this.linemax = linemax;
            this.doPadding = doPadding;
            this.buf = new byte[linemax <= 0 ? 8124 : linemax];
        }

        @Override
        public void write(int b) throws IOException {
            byte[] buf = new byte[1];
            buf[0] = (byte)(b & 0xff);
            write(buf, 0, 1);
        }

        private void checkNewline() throws IOException {
            if (linepos == linemax) {
                out.write(newline);
                linepos = 0;
            }
        }

        private void writeb4(char b1, char b2, char b3, char b4) throws IOException {
            buf[0] = (byte)b1;
            buf[1] = (byte)b2;
            buf[2] = (byte)b3;
            buf[3] = (byte)b4;
            out.write(buf, 0, 4);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (closed)
                throw new IOException("Stream is closed");
            if (off < 0 || len < 0 || len > b.length - off)
                throw new ArrayIndexOutOfBoundsException();
            if (len == 0)
                return;
            if (leftover != 0) {
                if (leftover == 1) {
                    b1 = b[off++] & 0xff;
                    len--;
                    if (len == 0) {
                        leftover++;
                        return;
                    }
                }
                b2 = b[off++] & 0xff;
                len--;
                checkNewline();
                writeb4(base64[b0 >> 2],
                        base64[(b0 << 4) & 0x3f | (b1 >> 4)],
                        base64[(b1 << 2) & 0x3f | (b2 >> 6)],
                        base64[b2 & 0x3f]);
                linepos += 4;
            }
            int nBits24 = len / 3;
            leftover = len - (nBits24 * 3);

            while (nBits24 > 0) {
                checkNewline();
                int dl = linemax <= 0 ? buf.length : buf.length - linepos;
                int sl = off + Math.min(nBits24, dl / 4) * 3;
                int dp = 0;
                for (int sp = off; sp < sl; ) {
                    int bits = (b[sp++] & 0xff) << 16 |
                               (b[sp++] & 0xff) <<  8 |
                               (b[sp++] & 0xff);
                    buf[dp++] = (byte)base64[(bits >>> 18) & 0x3f];
                    buf[dp++] = (byte)base64[(bits >>> 12) & 0x3f];
                    buf[dp++] = (byte)base64[(bits >>> 6)  & 0x3f];
                    buf[dp++] = (byte)base64[bits & 0x3f];
                }
                out.write(buf, 0, dp);
                off = sl;
                linepos += dp;
                nBits24 -= dp / 4;
            }
            if (leftover == 1) {
                b0 = b[off++] & 0xff;
            } else if (leftover == 2) {
                b0 = b[off++] & 0xff;
                b1 = b[off++] & 0xff;
            }
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                if (leftover == 1) {
                    checkNewline();
                    out.write(base64[b0 >> 2]);
                    out.write(base64[(b0 << 4) & 0x3f]);
                    if (doPadding) {
                        out.write('=');
                        out.write('=');
                    }
                } else if (leftover == 2) {
                    checkNewline();
                    out.write(base64[b0 >> 2]);
                    out.write(base64[(b0 << 4) & 0x3f | (b1 >> 4)]);
                    out.write(base64[(b1 << 2) & 0x3f]);
                    if (doPadding) {
                       out.write('=');
                    }
                }
                leftover = 0;
                out.close();
            }
        }
    }

    private static class DecInputStream extends InputStream {

        private final InputStream is;
        private final boolean isMIME;
        private final int[] base64;      // base64 -> byte mapping
        private int bits = 0;            // 24-bit buffer for decoding
        private int nextin = 18;         // next available "off" in "bits" for input;
                                         // -> 18, 12, 6, 0
        private int nextout = -8;        // next available "off" in "bits" for output;
                                         // -> 8, 0, -8 (no byte for output)
        private boolean eof = false;
        private boolean closed = false;

        DecInputStream(InputStream is, int[] base64, boolean isMIME) {
            this.is = is;
            this.base64 = base64;
            this.isMIME = isMIME;
        }

        private byte[] sbBuf = new byte[1];

        @Override
        public int read() throws IOException {
            return read(sbBuf, 0, 1) == -1 ? -1 : sbBuf[0] & 0xff;
        }

        private int eof(byte[] b, int off, int len, int oldOff)
            throws IOException
        {
            eof = true;
            if (nextin != 18) {
                if (nextin == 12)
                    throw new IOException("Base64 stream has one un-decoded dangling byte.");
                // treat ending xx/xxx without padding character legal.
                // same logic as v == '=' below
                b[off++] = (byte)(bits >> (16));
                if (nextin == 0) {           // only one padding byte
                    if (len == 1) {          // no enough output space
                        bits >>= 8;          // shift to lowest byte
                        nextout = 0;
                    } else {
                        b[off++] = (byte) (bits >>  8);
                    }
                }
            }
            return off == oldOff ? -1 : off - oldOff;
        }

        private int padding(byte[] b, int off, int len, int oldOff)
            throws IOException
        {
            // =     shiftto==18 unnecessary padding
            // x=    shiftto==12 dangling x, invalid unit
            // xx=   shiftto==6 && missing last '='
            // xx=y  or last is not '='
            if (nextin == 18 || nextin == 12 ||
                nextin == 6 && is.read() != '=') {
                throw new IOException("Illegal base64 ending sequence:" + nextin);
            }
            b[off++] = (byte)(bits >> (16));
            if (nextin == 0) {           // only one padding byte
                if (len == 1) {          // no enough output space
                    bits >>= 8;          // shift to lowest byte
                    nextout = 0;
                } else {
                    b[off++] = (byte) (bits >>  8);
                }
            }
            eof = true;
            return off - oldOff;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (closed)
                throw new IOException("Stream is closed");
            if (eof && nextout < 0)    // eof and no leftover
                return -1;
            if (off < 0 || len < 0 || len > b.length - off)
                throw new IndexOutOfBoundsException();
            int oldOff = off;
            while (nextout >= 0) {       // leftover output byte(s) in bits buf
                if (len == 0)
                    return off - oldOff;
                b[off++] = (byte)(bits >> nextout);
                len--;
                nextout -= 8;
            }
            bits = 0;
            while (len > 0) {
                int v = is.read();
                if (v == -1) {
                    return eof(b, off, len, oldOff);
                }
                if ((v = base64[v]) < 0) {
                    if (v == -2) {       // padding byte(s)
                        return padding(b, off, len, oldOff);
                    }
                    if (v == -1) {
                        if (!isMIME)
                            throw new IOException("Illegal base64 character " +
                                Integer.toString(v, 16));
                        continue;        // skip if for rfc2045
                    }
                    // neve be here
                }
                bits |= (v << nextin);
                if (nextin == 0) {
                    nextin = 18;         // clear for next in
                    b[off++] = (byte)(bits >> 16);
                    if (len == 1) {
                        nextout = 8;    // 2 bytes left in bits
                        break;
                    }
                    b[off++] = (byte)(bits >> 8);
                    if (len == 2) {
                        nextout = 0;    // 1 byte left in bits
                        break;
                    }
                    b[off++] = (byte)bits;
                    len -= 3;
                    bits = 0;
                } else {
                    nextin -= 6;
                }
            }
            return off - oldOff;
        }

        @Override
        public int available() throws IOException {
            if (closed)
                throw new IOException("Stream is closed");
            return is.available();   // TBD:
        }

        @Override
        public void close() throws IOException {
            if (!closed) {
                closed = true;
                is.close();
            }
        }
    }
}
