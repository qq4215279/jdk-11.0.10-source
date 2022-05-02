/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public enum TimeUnit {
    /** 纳秒 */
    NANOSECONDS(TimeUnit.NANO_SCALE),
    /** 微秒 */
    MICROSECONDS(TimeUnit.MICRO_SCALE),
    /** 毫秒 */
    MILLISECONDS(TimeUnit.MILLI_SCALE),
    /** 秒 */
    SECONDS(TimeUnit.SECOND_SCALE),
    /** 分 */
    MINUTES(TimeUnit.MINUTE_SCALE),
    /** 时 */
    HOURS(TimeUnit.HOUR_SCALE),
    /** 天 */
    DAYS(TimeUnit.DAY_SCALE);

    /** Scales as constants 以纳秒为单位 */
    /** 1纳秒 */
    private static final long NANO_SCALE = 1L;
    /** 1微秒纳秒值 */
    private static final long MICRO_SCALE = 1000L * NANO_SCALE;
    /** 1毫秒纳秒值 */
    private static final long MILLI_SCALE = 1000L * MICRO_SCALE;
    /** 1秒纳秒值 */
    private static final long SECOND_SCALE = 1000L * MILLI_SCALE;
    /** 1分钟纳秒值 */
    private static final long MINUTE_SCALE = 60L * SECOND_SCALE;
    /** 1小时纳秒值 */
    private static final long HOUR_SCALE = 60L * MINUTE_SCALE;
    /** 1天纳秒值 */
    private static final long DAY_SCALE = 24L * HOUR_SCALE;

    /** 当前纳秒值 */
    private final long scale;
    private final long maxNanos;
    private final long maxMicros;
    private final long maxMillis;
    private final long maxSecs;
    private final long microRatio;
    private final int milliRatio; // fits in 32 bits
    private final int secRatio; // fits in 32 bits

    /**
     *
     * @date 2022/4/30 18:03
     * @param s 纳秒值
     * @return
     */
    private TimeUnit(long s) {
        this.scale = s;
        this.maxNanos = Long.MAX_VALUE / s;
        long ur = (s >= MICRO_SCALE) ? (s / MICRO_SCALE) : (MICRO_SCALE / s);
        this.microRatio = ur;
        this.maxMicros = Long.MAX_VALUE / ur;
        long mr = (s >= MILLI_SCALE) ? (s / MILLI_SCALE) : (MILLI_SCALE / s);
        this.milliRatio = (int)mr;
        this.maxMillis = Long.MAX_VALUE / mr;
        long sr = (s >= SECOND_SCALE) ? (s / SECOND_SCALE) : (SECOND_SCALE / s);
        this.secRatio = (int)sr;
        this.maxSecs = Long.MAX_VALUE / sr;
    }

    /**
     * 转化纳秒
     * @date 2022/4/30 17:55
     * @param duration
     * @return long
     */
    public long toNanos(long duration) {
        long s, m;
        if ((s = scale) == NANO_SCALE)
            return duration;
        else if (duration > (m = maxNanos))
            return Long.MAX_VALUE;
        else if (duration < -m)
            return Long.MIN_VALUE;
        else
            return duration * s;
    }

    /**
     * 转化微秒
     * @date 2022/4/30 17:55
     * @param duration
     * @return long
     */
    public long toMicros(long duration) {
        long s, m;
        if ((s = scale) <= MICRO_SCALE)
            return (s == MICRO_SCALE) ? duration : duration / microRatio;
        else if (duration > (m = maxMicros))
            return Long.MAX_VALUE;
        else if (duration < -m)
            return Long.MIN_VALUE;
        else
            return duration * microRatio;
    }

    /**
     * 转化毫秒
     * @date 2022/4/30 17:55
     * @param duration
     * @return long
     */
    public long toMillis(long duration) {
        long s, m;
        if ((s = scale) <= MILLI_SCALE)
            return (s == MILLI_SCALE) ? duration : duration / milliRatio;
        else if (duration > (m = maxMillis))
            return Long.MAX_VALUE;
        else if (duration < -m)
            return Long.MIN_VALUE;
        else
            return duration * milliRatio;
    }

    /**
     * 转化秒
     * @date 2022/4/30 17:55
     * @param duration
     * @return long
     */
    public long toSeconds(long duration) {
        long s, m;
        if ((s = scale) <= SECOND_SCALE)
            return (s == SECOND_SCALE) ? duration : duration / secRatio;
        else if (duration > (m = maxSecs))
            return Long.MAX_VALUE;
        else if (duration < -m)
            return Long.MIN_VALUE;
        else
            return duration * secRatio;
    }

    /**
     * 转化分钟
     * @date 2022/4/30 17:55
     * @param duration
     * @return long
     */
    public long toMinutes(long duration) {
        return cvt(duration, MINUTE_SCALE, scale);
    }

    /**
     * 转化小时
     * @date 2022/4/30 17:55
     * @param duration
     * @return long
     */
    public long toHours(long duration) {
        return cvt(duration, HOUR_SCALE, scale);
    }

    /**
     * 转化天
     * @date 2022/4/30 17:54
     * @param duration
     * @return long
     */
    public long toDays(long duration) {
        return cvt(duration, DAY_SCALE, scale);
    }

    /**
     *
     * @param d
     * @param dst
     * @param src
     * @return
     */
    private static long cvt(long d, long dst, long src) {
        long r, m;
        if (src == dst)
            return d;
        else if (src < dst)
            return d / (dst / src);
        else if (d > (m = Long.MAX_VALUE / (r = src / dst)))
            return Long.MAX_VALUE;
        else if (d < -m)
            return Long.MIN_VALUE;
        else
            return d * r;
    }

    public void timedWait(Object obj, long timeout) throws InterruptedException {
        if (timeout > 0) {
            long ms = toMillis(timeout);
            int ns = excessNanos(timeout, ms);
            obj.wait(ms, ns);
        }
    }

    public void timedJoin(Thread thread, long timeout) throws InterruptedException {
        if (timeout > 0) {
            long ms = toMillis(timeout);
            int ns = excessNanos(timeout, ms);
            thread.join(ms, ns);
        }
    }

    /**
     * 延时
     * @date 2022/4/30 17:57
     * @param timeout
     * @return void
     */
    public void sleep(long timeout) throws InterruptedException {
        if (timeout > 0) {
            long ms = toMillis(timeout);
            int ns = excessNanos(timeout, ms);
            Thread.sleep(ms, ns);
        }
    }

    private int excessNanos(long d, long m) {
        long s;
        if ((s = scale) == NANO_SCALE)
            return (int)(d - (m * MILLI_SCALE));
        else if (s == MICRO_SCALE)
            return (int)((d * 1000L) - (m * MILLI_SCALE));
        else
            return 0;
    }

    public ChronoUnit toChronoUnit() {
        switch (this) {
            case NANOSECONDS:
                return ChronoUnit.NANOS;
            case MICROSECONDS:
                return ChronoUnit.MICROS;
            case MILLISECONDS:
                return ChronoUnit.MILLIS;
            case SECONDS:
                return ChronoUnit.SECONDS;
            case MINUTES:
                return ChronoUnit.MINUTES;
            case HOURS:
                return ChronoUnit.HOURS;
            case DAYS:
                return ChronoUnit.DAYS;
            default:
                throw new AssertionError();
        }
    }

    public static TimeUnit of(ChronoUnit chronoUnit) {
        switch (Objects.requireNonNull(chronoUnit, "chronoUnit")) {
            case NANOS:
                return TimeUnit.NANOSECONDS;
            case MICROS:
                return TimeUnit.MICROSECONDS;
            case MILLIS:
                return TimeUnit.MILLISECONDS;
            case SECONDS:
                return TimeUnit.SECONDS;
            case MINUTES:
                return TimeUnit.MINUTES;
            case HOURS:
                return TimeUnit.HOURS;
            case DAYS:
                return TimeUnit.DAYS;
            default:
                throw new IllegalArgumentException("No TimeUnit equivalent for " + chronoUnit);
        }
    }

    public long convert(long sourceDuration, TimeUnit sourceUnit) {
        switch (this) {
            case NANOSECONDS:
                return sourceUnit.toNanos(sourceDuration);
            case MICROSECONDS:
                return sourceUnit.toMicros(sourceDuration);
            case MILLISECONDS:
                return sourceUnit.toMillis(sourceDuration);
            case SECONDS:
                return sourceUnit.toSeconds(sourceDuration);
            default:
                return cvt(sourceDuration, scale, sourceUnit.scale);
        }
    }

    public long convert(Duration duration) {
        long secs = duration.getSeconds();
        int nano = duration.getNano();
        if (secs < 0 && nano > 0) {
            // use representation compatible with integer division
            secs++;
            nano -= (int)SECOND_SCALE;
        }
        final long s, nanoVal;
        // Optimize for the common case - NANOSECONDS without overflow
        if (this == NANOSECONDS)
            nanoVal = nano;
        else if ((s = scale) < SECOND_SCALE)
            nanoVal = nano / s;
        else if (this == SECONDS)
            return secs;
        else
            return secs / secRatio;
        long val = secs * secRatio + nanoVal;
        return ((secs < maxSecs && secs > -maxSecs) || (secs == maxSecs && val > 0) || (secs == -maxSecs && val < 0))
               ? val : (secs > 0) ? Long.MAX_VALUE : Long.MIN_VALUE;
    }

}
