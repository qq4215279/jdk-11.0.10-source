/*
 * Copyright (c) 1996, 2017, Oracle and/or its affiliates. All rights reserved. ORACLE PROPRIETARY/CONFIDENTIAL. Use is
 * subject to license terms.
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
 * (C) Copyright Taligent, Inc. 1996-1998 - All Rights Reserved (C) Copyright IBM Corp. 1996-1998 - All Rights Reserved
 *
 * The original version of this source code and documentation is copyrighted and owned by Taligent, Inc., a wholly-owned
 * subsidiary of IBM. These materials are provided under terms of a License Agreement between Taligent and Sun. This
 * technology is protected by multiple US and International patents. This notice and attribution to Taligent may not be
 * removed. Taligent is a registered trademark of Taligent, Inc.
 *
 */

package java.util;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OptionalDataException;
import java.io.Serializable;
import java.security.AccessControlContext;
import java.security.AccessController;
import java.security.PermissionCollection;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.security.ProtectionDomain;
import java.text.DateFormat;
import java.text.DateFormatSymbols;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import sun.util.BuddhistCalendar;
import sun.util.calendar.ZoneInfo;
import sun.util.locale.provider.CalendarDataUtility;
import sun.util.locale.provider.LocaleProviderAdapter;
import sun.util.locale.provider.TimeZoneNameUtility;
import sun.util.spi.CalendarProvider;

public abstract class Calendar implements Serializable, Cloneable, Comparable<Calendar> {

    public static final int ERA = 0;

    /** 年 */
    public static final int YEAR = 1;
    /** 月 注意：从0开始算起，最大11；0代表1月，11代表12月） */
    public static final int MONTH = 2;
    /** 当前年的第几周 */
    public static final int WEEK_OF_YEAR = 3;
    /** 日历式的第几周。（例如今天是8月21日,是八月的第四周）。 */
    public static final int WEEK_OF_MONTH = 4;
    /** 当前月几号 */
    public static final int DATE = 5;
    /** 当前月第几天 */
    public static final int DAY_OF_MONTH = 5;
    /** 当前年第几天 */
    public static final int DAY_OF_YEAR = 6;
    /** 当前周第几天（周中的天（周几，周日为1，可以-1使用）） */
    public static final int DAY_OF_WEEK = 7;
    /** 某月中第几周。按这个月1号算，1号起就是第1周，8号起就是第2周。 */
    public static final int DAY_OF_WEEK_IN_MONTH = 8;
    /**  */
    public static final int AM_PM = 9;
    /** 现在几时（12小时制） */
    public static final int HOUR = 10;
    /** 现在几时（24小时制） */
    public static final int HOUR_OF_DAY = 11;
    /** 当前时第几分钟 */
    public static final int MINUTE = 12;
    /** 当前分钟第几秒钟 */
    public static final int SECOND = 13;
    /** 当前秒钟第几毫秒值 */
    public static final int MILLISECOND = 14;

    public static final int ZONE_OFFSET = 15;

    public static final int DST_OFFSET = 16;

    public static final int FIELD_COUNT = 17;

    // ===================== 星期开始 =========================>
    /** 星期一 */
    public static final int SUNDAY = 1;
    /** 星期二 */
    public static final int MONDAY = 2;
    /** 星期三 */
    public static final int TUESDAY = 3;
    /** 星期四 */
    public static final int WEDNESDAY = 4;
    /** 星期五 */
    public static final int THURSDAY = 5;
    /** 星期六 */
    public static final int FRIDAY = 6;
    /** 星期日 */
    public static final int SATURDAY = 7;

    // ===================== 星期结束 =========================>

    // ===================== 月份开始 =========================>
    /** 1月 */
    public static final int JANUARY = 0;
    /** 2月 */
    public static final int FEBRUARY = 1;
    /** 3月 */
    public static final int MARCH = 2;
    /** 4月 */
    public static final int APRIL = 3;
    /** 5月 */
    public static final int MAY = 4;
    /** 6月 */
    public static final int JUNE = 5;
    /** 7月 */
    public static final int JULY = 6;
    /** 8月 */
    public static final int AUGUST = 7;
    /** 9月 */
    public static final int SEPTEMBER = 8;
    /** 10月 */
    public static final int OCTOBER = 9;
    /** 11月 */
    public static final int NOVEMBER = 10;
    /** 12月 */
    public static final int DECEMBER = 11;
    /** 不存在月份 */
    public static final int UNDECIMBER = 12;

    // ===================== 月份结束 =========================>

    /** 上午（0-12） */
    public static final int AM = 0;
    /** 下午（12-24） */
    public static final int PM = 1;
    /** getDisplayNames的样式说明符，指示所有样式中的名称，例如“January”和“Jan”。  */
    public static final int ALL_STYLES = 0;

    static final int STANDALONE_MASK = 0x8000;

    public static final int SHORT = 1;

    public static final int LONG = 2;

    public static final int NARROW_FORMAT = 4;

    public static final int NARROW_STANDALONE = NARROW_FORMAT | STANDALONE_MASK;

    public static final int SHORT_FORMAT = 1;

    public static final int LONG_FORMAT = 2;

    public static final int SHORT_STANDALONE = SHORT | STANDALONE_MASK;

    public static final int LONG_STANDALONE = LONG | STANDALONE_MASK;

    @SuppressWarnings("ProtectedField")
    protected int fields[];

    @SuppressWarnings("ProtectedField")
    protected boolean isSet[];

    private transient int stamp[];

    @SuppressWarnings("ProtectedField")
    protected long time;

    @SuppressWarnings("ProtectedField")
    protected boolean isTimeSet;

    @SuppressWarnings("ProtectedField")
    protected boolean areFieldsSet;

    transient boolean areAllFieldsSet;

    private boolean lenient = true;

    private TimeZone zone;

    private transient boolean sharedZone = false;

    private int firstDayOfWeek;

    private int minimalDaysInFirstWeek;

    private static final ConcurrentMap<Locale, int[]> cachedLocaleData = new ConcurrentHashMap<>(3);

    // Special values of stamp[]
    private static final int UNSET = 0;

    private static final int COMPUTED = 1;

    private static final int MINIMUM_USER_STAMP = 2;

    static final int ALL_FIELDS = (1 << FIELD_COUNT) - 1;

    private int nextStamp = MINIMUM_USER_STAMP;

    static final int currentSerialVersion = 1;

    private int serialVersionOnStream = currentSerialVersion;

    // Proclaim serialization compatibility with JDK 1.1
    static final long serialVersionUID = -1807547505821590642L;

    // Mask values for calendar fields
    @SuppressWarnings("PointlessBitwiseExpression")
    static final int ERA_MASK = (1 << ERA);
    static final int YEAR_MASK = (1 << YEAR);
    static final int MONTH_MASK = (1 << MONTH);
    static final int WEEK_OF_YEAR_MASK = (1 << WEEK_OF_YEAR);
    static final int WEEK_OF_MONTH_MASK = (1 << WEEK_OF_MONTH);
    static final int DAY_OF_MONTH_MASK = (1 << DAY_OF_MONTH);
    static final int DATE_MASK = DAY_OF_MONTH_MASK;
    static final int DAY_OF_YEAR_MASK = (1 << DAY_OF_YEAR);
    static final int DAY_OF_WEEK_MASK = (1 << DAY_OF_WEEK);
    static final int DAY_OF_WEEK_IN_MONTH_MASK = (1 << DAY_OF_WEEK_IN_MONTH);
    static final int AM_PM_MASK = (1 << AM_PM);
    static final int HOUR_MASK = (1 << HOUR);
    static final int HOUR_OF_DAY_MASK = (1 << HOUR_OF_DAY);
    static final int MINUTE_MASK = (1 << MINUTE);
    static final int SECOND_MASK = (1 << SECOND);
    static final int MILLISECOND_MASK = (1 << MILLISECOND);
    static final int ZONE_OFFSET_MASK = (1 << ZONE_OFFSET);
    static final int DST_OFFSET_MASK = (1 << DST_OFFSET);

    public static class Builder {
        private static final int NFIELDS = FIELD_COUNT + 1; // +1 for WEEK_YEAR
        private static final int WEEK_YEAR = FIELD_COUNT;

        private long instant;
        // Calendar.stamp[] (lower half) and Calendar.fields[] (upper half) combined
        private int[] fields;
        // Pseudo timestamp starting from MINIMUM_USER_STAMP.
        // (COMPUTED is used to indicate that the instant has been set.)
        private int nextStamp;
        // maxFieldIndex keeps the max index of fields which have been set.
        // (WEEK_YEAR is never included.)
        private int maxFieldIndex;
        private String type;
        private TimeZone zone;
        private boolean lenient = true;
        private Locale locale;
        private int firstDayOfWeek, minimalDaysInFirstWeek;

        public Builder() {}

        public Builder setInstant(long instant) {
            if (fields != null) {
                throw new IllegalStateException();
            }
            this.instant = instant;
            nextStamp = COMPUTED;
            return this;
        }

        public Builder setInstant(Date instant) {
            return setInstant(instant.getTime()); // NPE if instant == null
        }

        public Builder set(int field, int value) {
            // Note: WEEK_YEAR can't be set with this method.
            if (field < 0 || field >= FIELD_COUNT) {
                throw new IllegalArgumentException("field is invalid");
            }
            if (isInstantSet()) {
                throw new IllegalStateException("instant has been set");
            }
            allocateFields();
            internalSet(field, value);
            return this;
        }

        public Builder setFields(int... fieldValuePairs) {
            int len = fieldValuePairs.length;
            if ((len % 2) != 0) {
                throw new IllegalArgumentException();
            }
            if (isInstantSet()) {
                throw new IllegalStateException("instant has been set");
            }
            if ((nextStamp + len / 2) < 0) {
                throw new IllegalStateException("stamp counter overflow");
            }
            allocateFields();
            for (int i = 0; i < len;) {
                int field = fieldValuePairs[i++];
                // Note: WEEK_YEAR can't be set with this method.
                if (field < 0 || field >= FIELD_COUNT) {
                    throw new IllegalArgumentException("field is invalid");
                }
                internalSet(field, fieldValuePairs[i++]);
            }
            return this;
        }

        public Builder setDate(int year, int month, int dayOfMonth) {
            return setFields(YEAR, year, MONTH, month, DAY_OF_MONTH, dayOfMonth);
        }

        public Builder setTimeOfDay(int hourOfDay, int minute, int second) {
            return setTimeOfDay(hourOfDay, minute, second, 0);
        }

        public Builder setTimeOfDay(int hourOfDay, int minute, int second, int millis) {
            return setFields(HOUR_OF_DAY, hourOfDay, MINUTE, minute, SECOND, second, MILLISECOND, millis);
        }

        public Builder setWeekDate(int weekYear, int weekOfYear, int dayOfWeek) {
            allocateFields();
            internalSet(WEEK_YEAR, weekYear);
            internalSet(WEEK_OF_YEAR, weekOfYear);
            internalSet(DAY_OF_WEEK, dayOfWeek);
            return this;
        }

        public Builder setTimeZone(TimeZone zone) {
            if (zone == null) {
                throw new NullPointerException();
            }
            this.zone = zone;
            return this;
        }

        public Builder setLenient(boolean lenient) {
            this.lenient = lenient;
            return this;
        }

        public Builder setCalendarType(String type) {
            if (type.equals("gregorian")) { // NPE if type == null
                type = "gregory";
            }
            if (!Calendar.getAvailableCalendarTypes().contains(type) && !type.equals("iso8601")) {
                throw new IllegalArgumentException("unknown calendar type: " + type);
            }
            if (this.type == null) {
                this.type = type;
            } else {
                if (!this.type.equals(type)) {
                    throw new IllegalStateException("calendar type override");
                }
            }
            return this;
        }

        public Builder setLocale(Locale locale) {
            if (locale == null) {
                throw new NullPointerException();
            }
            this.locale = locale;
            return this;
        }

        public Builder setWeekDefinition(int firstDayOfWeek, int minimalDaysInFirstWeek) {
            if (!isValidWeekParameter(firstDayOfWeek) || !isValidWeekParameter(minimalDaysInFirstWeek)) {
                throw new IllegalArgumentException();
            }
            this.firstDayOfWeek = firstDayOfWeek;
            this.minimalDaysInFirstWeek = minimalDaysInFirstWeek;
            return this;
        }

        public Calendar build() {
            if (locale == null) {
                locale = Locale.getDefault();
            }
            if (zone == null) {
                zone = defaultTimeZone(locale);
            }
            Calendar cal;
            if (type == null) {
                type = locale.getUnicodeLocaleType("ca");
            }
            if (type == null) {
                if (locale.getCountry() == "TH" && locale.getLanguage() == "th") {
                    type = "buddhist";
                } else {
                    type = "gregory";
                }
            }
            switch (type) {
                case "gregory":
                    cal = new GregorianCalendar(zone, locale, true);
                    break;
                case "iso8601":
                    GregorianCalendar gcal = new GregorianCalendar(zone, locale, true);
                    // make gcal a proleptic Gregorian
                    gcal.setGregorianChange(new Date(Long.MIN_VALUE));
                    // and week definition to be compatible with ISO 8601
                    setWeekDefinition(MONDAY, 4);
                    cal = gcal;
                    break;
                case "buddhist":
                    cal = new BuddhistCalendar(zone, locale);
                    cal.clear();
                    break;
                case "japanese":
                    cal = new JapaneseImperialCalendar(zone, locale, true);
                    break;
                default:
                    throw new IllegalArgumentException("unknown calendar type: " + type);
            }
            cal.setLenient(lenient);
            if (firstDayOfWeek != 0) {
                cal.setFirstDayOfWeek(firstDayOfWeek);
                cal.setMinimalDaysInFirstWeek(minimalDaysInFirstWeek);
            }
            if (isInstantSet()) {
                cal.setTimeInMillis(instant);
                cal.complete();
                return cal;
            }

            if (fields != null) {
                boolean weekDate = isSet(WEEK_YEAR) && fields[WEEK_YEAR] > fields[YEAR];
                if (weekDate && !cal.isWeekDateSupported()) {
                    throw new IllegalArgumentException("week date is unsupported by " + type);
                }

                // Set the fields from the min stamp to the max stamp so that
                // the fields resolution works in the Calendar.
                for (int stamp = MINIMUM_USER_STAMP; stamp < nextStamp; stamp++) {
                    for (int index = 0; index <= maxFieldIndex; index++) {
                        if (fields[index] == stamp) {
                            cal.set(index, fields[NFIELDS + index]);
                            break;
                        }
                    }
                }

                if (weekDate) {
                    int weekOfYear = isSet(WEEK_OF_YEAR) ? fields[NFIELDS + WEEK_OF_YEAR] : 1;
                    int dayOfWeek = isSet(DAY_OF_WEEK) ? fields[NFIELDS + DAY_OF_WEEK] : cal.getFirstDayOfWeek();
                    cal.setWeekDate(fields[NFIELDS + WEEK_YEAR], weekOfYear, dayOfWeek);
                }
                cal.complete();
            }

            return cal;
        }

        private void allocateFields() {
            if (fields == null) {
                fields = new int[NFIELDS * 2];
                nextStamp = MINIMUM_USER_STAMP;
                maxFieldIndex = -1;
            }
        }

        private void internalSet(int field, int value) {
            fields[field] = nextStamp++;
            if (nextStamp < 0) {
                throw new IllegalStateException("stamp counter overflow");
            }
            fields[NFIELDS + field] = value;
            if (field > maxFieldIndex && field < WEEK_YEAR) {
                maxFieldIndex = field;
            }
        }

        private boolean isInstantSet() {
            return nextStamp == COMPUTED;
        }

        private boolean isSet(int index) {
            return fields != null && fields[index] > UNSET;
        }

        private boolean isValidWeekParameter(int value) {
            return value > 0 && value <= 7;
        }
    }

    protected Calendar() {
        this(TimeZone.getDefaultRef(), Locale.getDefault(Locale.Category.FORMAT));
        sharedZone = true;
    }

    protected Calendar(TimeZone zone, Locale aLocale) {
        fields = new int[FIELD_COUNT];
        isSet = new boolean[FIELD_COUNT];
        stamp = new int[FIELD_COUNT];

        this.zone = zone;
        setWeekCountData(aLocale);
    }

    public static Calendar getInstance() {
        Locale aLocale = Locale.getDefault(Locale.Category.FORMAT);
        return createCalendar(defaultTimeZone(aLocale), aLocale);
    }

    public static Calendar getInstance(TimeZone zone) {
        return createCalendar(zone, Locale.getDefault(Locale.Category.FORMAT));
    }

    public static Calendar getInstance(Locale aLocale) {
        return createCalendar(defaultTimeZone(aLocale), aLocale);
    }

    public static Calendar getInstance(TimeZone zone, Locale aLocale) {
        return createCalendar(zone, aLocale);
    }

    private static TimeZone defaultTimeZone(Locale l) {
        TimeZone defaultTZ = TimeZone.getDefault();
        String shortTZID = l.getUnicodeLocaleType("tz");
        return shortTZID != null
            ? TimeZoneNameUtility.convertLDMLShortID(shortTZID).map(TimeZone::getTimeZone).orElse(defaultTZ)
            : defaultTZ;
    }

    private static Calendar createCalendar(TimeZone zone, Locale aLocale) {
        CalendarProvider provider =
            LocaleProviderAdapter.getAdapter(CalendarProvider.class, aLocale).getCalendarProvider();
        if (provider != null) {
            try {
                return provider.getInstance(zone, aLocale);
            } catch (IllegalArgumentException iae) {
                // fall back to the default instantiation
            }
        }

        Calendar cal = null;

        if (aLocale.hasExtensions()) {
            String caltype = aLocale.getUnicodeLocaleType("ca");
            if (caltype != null) {
                switch (caltype) {
                    case "buddhist":
                        cal = new BuddhistCalendar(zone, aLocale);
                        break;
                    case "japanese":
                        cal = new JapaneseImperialCalendar(zone, aLocale);
                        break;
                    case "gregory":
                        cal = new GregorianCalendar(zone, aLocale);
                        break;
                }
            }
        }
        if (cal == null) {
            // If no known calendar type is explicitly specified,
            // perform the traditional way to create a Calendar:
            // create a BuddhistCalendar for th_TH locale,
            // a JapaneseImperialCalendar for ja_JP_JP locale, or
            // a GregorianCalendar for any other locales.
            // NOTE: The language, country and variant strings are interned.
            if (aLocale.getLanguage() == "th" && aLocale.getCountry() == "TH") {
                cal = new BuddhistCalendar(zone, aLocale);
            } else if (aLocale.getVariant() == "JP" && aLocale.getLanguage() == "ja" && aLocale.getCountry() == "JP") {
                cal = new JapaneseImperialCalendar(zone, aLocale);
            } else {
                cal = new GregorianCalendar(zone, aLocale);
            }
        }
        return cal;
    }

    public static synchronized Locale[] getAvailableLocales() {
        return DateFormat.getAvailableLocales();
    }

    /**
     * 将日历的时间域作为毫秒值转换为 UTC。
     * @date 2022/4/30 18:27
     * @param
     * @return void
     */
    protected abstract void computeTime();

    /** 
     * 将 UTC 作为毫秒值转换为时间域值。 允许使时间域值与日历设置的新时间同步。
     * 开始不重新计算该时间；为了重新计算时间和域，调用 complete方法。
     * @date 2022/4/30 18:27 
     * @param  
     * @return void
     */
    protected abstract void computeFields();

    /**
     * 获取当前日期
     * @date 2022/4/30 18:11
     * @param
     * @return java.util.Date
     */
    public final Date getTime() {
        return new Date(getTimeInMillis());
    }

    /**
     * 获得日历的作为长整型的当前时间。
     * @date 2022/4/30 18:13
     * @param
     * @return long当前时间，作为从开始时间的 UTC 毫秒值。
     */
    public long getTimeInMillis() {
        if (!isTimeSet) {
            updateTime();
        }
        return time;
    }

    /**
     * 用给定的 Date 设置 Calendar 的当前时间。
     * @date 2022/4/30 18:11
     * @param date 给定的 Date。
     * @return void
     */
    public final void setTime(Date date) {
        setTimeInMillis(date.getTime());
    }

    /**
     * 用给定的长整数设置 Calendar 的当前时间。
     * @date 2022/4/30 18:28
     * @param millis 新时间，从开始时间的 UTC 毫秒时间。
     * @return void
     */
    public void setTimeInMillis(long millis) {
        // If we don't need to recalculate the calendar field values,
        // do nothing.
        if (time == millis && isTimeSet && areFieldsSet && areAllFieldsSet && (zone instanceof ZoneInfo)
            && !((ZoneInfo)zone).isDirty()) {
            return;
        }
        time = millis;
        isTimeSet = true;
        areFieldsSet = false;
        computeFields();
        areAllFieldsSet = areFieldsSet = true;
    }

    /**
     * 获得给定时间域的值。
     * @date 2022/4/30 18:28
     * @param field 给定的时间域。
     * @return int 给定的时间域值。
     */
    public int get(int field) {
        complete();
        return internalGet(field);
    }

    protected final int internalGet(int field) {
        return fields[field];
    }

    final void internalSet(int field, int value) {
        fields[field] = value;
    }

    /**
     * 用给定的值设置时间域。
     * @date 2022/4/30 18:13
     * @param field 给定的时间域。
     * @param value 要设置的给定时间域的值。
     * @return void
     */
    public void set(int field, int value) {
        // If the fields are partially normalized, calculate all the
        // fields before changing any fields.
        if (areFieldsSet && !areAllFieldsSet) {
            computeFields();
        }
        internalSet(field, value);
        isTimeSet = false;
        areFieldsSet = false;
        isSet[field] = true;
        stamp[field] = nextStamp++;
        if (nextStamp == Integer.MAX_VALUE) {
            adjustStamp();
        }
    }

    /**
     * 设置年、月、日期域的数值。保留其它域上次的值。 如果不需要保留，首先调用 clear。
     * @date 2022/4/30 18:14
     * @param year 用于设置 YEAR 时间域的值。
     * @param month 用于设置 MONTH 时间域的值。Month 值以 0 开始。 例如，0 代表一月。
     * @param date 用于设置 DATE 时间域的值。
     * @return void
     */
    public final void set(int year, int month, int date) {
        set(YEAR, year);
        set(MONTH, month);
        set(DATE, date);
    }

    public final void set(int year, int month, int date, int hourOfDay, int minute) {
        set(YEAR, year);
        set(MONTH, month);
        set(DATE, date);
        set(HOUR_OF_DAY, hourOfDay);
        set(MINUTE, minute);
    }

    public final void set(int year, int month, int date, int hourOfDay, int minute, int second) {
        set(YEAR, year);
        set(MONTH, month);
        set(DATE, date);
        set(HOUR_OF_DAY, hourOfDay);
        set(MINUTE, minute);
        set(SECOND, second);
    }

    /** 
     * 将所有时间域值清零。
     * @date 2022/4/30 18:14
     * @param  
     * @return void
     */
    public final void clear() {
        for (int i = 0; i < fields.length;) {
            stamp[i] = fields[i] = 0; // UNSET == 0
            isSet[i++] = false;
        }
        areAllFieldsSet = areFieldsSet = false;
        isTimeSet = false;
    }

    /**
     * 将给定的时间域值清零。
     * @date 2022/4/30 18:15
     * @param field 要清零的时间域。
     * @return void
     */
    public final void clear(int field) {
        fields[field] = 0;
        stamp[field] = UNSET;
        isSet[field] = false;

        areAllFieldsSet = areFieldsSet = false;
        isTimeSet = false;
    }

    /**
     * 确定给定的时间域是否设置了数值。
     * @date 2022/4/30 18:15
     * @param field
     * @return boolean 如果给定的时间域设置了数值则返回 true；否则返回 false。
     */
    public final boolean isSet(int field) {
        return stamp[field] != UNSET;
    }

    public String getDisplayName(int field, int style, Locale locale) {
        if (!checkDisplayNameParams(field, style, SHORT, NARROW_FORMAT, locale,
            ERA_MASK | MONTH_MASK | DAY_OF_WEEK_MASK | AM_PM_MASK)) {
            return null;
        }

        String calendarType = getCalendarType();
        int fieldValue = get(field);
        // the standalone/narrow styles and short era are supported only through
        // CalendarNameProviders.
        if (isStandaloneStyle(style) || isNarrowFormatStyle(style) || field == ERA && (style & SHORT) == SHORT) {
            String val = CalendarDataUtility.retrieveFieldValueName(calendarType, field, fieldValue, style, locale);
            // Perform fallback here to follow the CLDR rules
            if (val == null) {
                if (isNarrowFormatStyle(style)) {
                    val = CalendarDataUtility.retrieveFieldValueName(calendarType, field, fieldValue,
                        toStandaloneStyle(style), locale);
                } else if (isStandaloneStyle(style)) {
                    val = CalendarDataUtility.retrieveFieldValueName(calendarType, field, fieldValue,
                        getBaseStyle(style), locale);
                }
            }
            return val;
        }

        DateFormatSymbols symbols = DateFormatSymbols.getInstance(locale);
        String[] strings = getFieldStrings(field, style, symbols);
        if (strings != null) {
            if (fieldValue < strings.length) {
                return strings[fieldValue];
            }
        }
        return null;
    }

    public Map<String, Integer> getDisplayNames(int field, int style, Locale locale) {
        if (!checkDisplayNameParams(field, style, ALL_STYLES, NARROW_FORMAT, locale,
            ERA_MASK | MONTH_MASK | DAY_OF_WEEK_MASK | AM_PM_MASK)) {
            return null;
        }

        String calendarType = getCalendarType();
        if (style == ALL_STYLES || isStandaloneStyle(style) || isNarrowFormatStyle(style)
            || field == ERA && (style & SHORT) == SHORT) {
            Map<String, Integer> map;
            map = CalendarDataUtility.retrieveFieldValueNames(calendarType, field, style, locale);

            // Perform fallback here to follow the CLDR rules
            if (map == null) {
                if (isNarrowFormatStyle(style)) {
                    map = CalendarDataUtility.retrieveFieldValueNames(calendarType, field, toStandaloneStyle(style),
                        locale);
                } else if (style != ALL_STYLES) {
                    map = CalendarDataUtility.retrieveFieldValueNames(calendarType, field, getBaseStyle(style), locale);
                }
            }
            return map;
        }

        // SHORT or LONG
        return getDisplayNamesImpl(field, style, locale);
    }

    private Map<String, Integer> getDisplayNamesImpl(int field, int style, Locale locale) {
        DateFormatSymbols symbols = DateFormatSymbols.getInstance(locale);
        String[] strings = getFieldStrings(field, style, symbols);
        if (strings != null) {
            Map<String, Integer> names = new HashMap<>();
            for (int i = 0; i < strings.length; i++) {
                if (strings[i].isEmpty()) {
                    continue;
                }
                names.put(strings[i], i);
            }
            return names;
        }
        return null;
    }

    boolean checkDisplayNameParams(int field, int style, int minStyle, int maxStyle, Locale locale, int fieldMask) {
        int baseStyle = getBaseStyle(style); // Ignore the standalone mask
        if (field < 0 || field >= fields.length || baseStyle < minStyle || baseStyle > maxStyle || baseStyle == 3) {
            throw new IllegalArgumentException();
        }
        if (locale == null) {
            throw new NullPointerException();
        }
        return isFieldSet(fieldMask, field);
    }

    private String[] getFieldStrings(int field, int style, DateFormatSymbols symbols) {
        int baseStyle = getBaseStyle(style); // ignore the standalone mask

        // DateFormatSymbols doesn't support any narrow names.
        if (baseStyle == NARROW_FORMAT) {
            return null;
        }

        String[] strings = null;
        switch (field) {
            case ERA:
                strings = symbols.getEras();
                break;

            case MONTH:
                strings = (baseStyle == LONG) ? symbols.getMonths() : symbols.getShortMonths();
                break;

            case DAY_OF_WEEK:
                strings = (baseStyle == LONG) ? symbols.getWeekdays() : symbols.getShortWeekdays();
                break;

            case AM_PM:
                strings = symbols.getAmPmStrings();
                break;
        }
        return strings;
    }

    protected void complete() {
        if (!isTimeSet) {
            updateTime();
        }
        if (!areFieldsSet || !areAllFieldsSet) {
            computeFields(); // fills in unset fields
            areAllFieldsSet = areFieldsSet = true;
        }
    }

    final boolean isExternallySet(int field) {
        return stamp[field] >= MINIMUM_USER_STAMP;
    }

    final int getSetStateFields() {
        int mask = 0;
        for (int i = 0; i < fields.length; i++) {
            if (stamp[i] != UNSET) {
                mask |= 1 << i;
            }
        }
        return mask;
    }

    final void setFieldsComputed(int fieldMask) {
        if (fieldMask == ALL_FIELDS) {
            for (int i = 0; i < fields.length; i++) {
                stamp[i] = COMPUTED;
                isSet[i] = true;
            }
            areFieldsSet = areAllFieldsSet = true;
        } else {
            for (int i = 0; i < fields.length; i++) {
                if ((fieldMask & 1) == 1) {
                    stamp[i] = COMPUTED;
                    isSet[i] = true;
                } else {
                    if (areAllFieldsSet && !isSet[i]) {
                        areAllFieldsSet = false;
                    }
                }
                fieldMask >>>= 1;
            }
        }
    }

    final void setFieldsNormalized(int fieldMask) {
        if (fieldMask != ALL_FIELDS) {
            for (int i = 0; i < fields.length; i++) {
                if ((fieldMask & 1) == 0) {
                    stamp[i] = fields[i] = 0; // UNSET == 0
                    isSet[i] = false;
                }
                fieldMask >>= 1;
            }
        }

        // Some or all of the fields are in sync with the
        // milliseconds, but the stamp values are not normalized yet.
        areFieldsSet = true;
        areAllFieldsSet = false;
    }

    final boolean isPartiallyNormalized() {
        return areFieldsSet && !areAllFieldsSet;
    }

    final boolean isFullyNormalized() {
        return areFieldsSet && areAllFieldsSet;
    }

    final void setUnnormalized() {
        areFieldsSet = areAllFieldsSet = false;
    }

    static boolean isFieldSet(int fieldMask, int field) {
        return (fieldMask & (1 << field)) != 0;
    }

    final int selectFields() {
        // This implementation has been taken from the GregorianCalendar class.

        // The YEAR field must always be used regardless of its SET
        // state because YEAR is a mandatory field to determine the date
        // and the default value (EPOCH_YEAR) may change through the
        // normalization process.
        int fieldMask = YEAR_MASK;

        if (stamp[ERA] != UNSET) {
            fieldMask |= ERA_MASK;
        }

        int dowStamp = stamp[DAY_OF_WEEK];
        int monthStamp = stamp[MONTH];
        int domStamp = stamp[DAY_OF_MONTH];
        int womStamp = aggregateStamp(stamp[WEEK_OF_MONTH], dowStamp);
        int dowimStamp = aggregateStamp(stamp[DAY_OF_WEEK_IN_MONTH], dowStamp);
        int doyStamp = stamp[DAY_OF_YEAR];
        int woyStamp = aggregateStamp(stamp[WEEK_OF_YEAR], dowStamp);

        int bestStamp = domStamp;
        if (womStamp > bestStamp) {
            bestStamp = womStamp;
        }
        if (dowimStamp > bestStamp) {
            bestStamp = dowimStamp;
        }
        if (doyStamp > bestStamp) {
            bestStamp = doyStamp;
        }
        if (woyStamp > bestStamp) {
            bestStamp = woyStamp;
        }

        /* No complete combination exists.  Look for WEEK_OF_MONTH,
         * DAY_OF_WEEK_IN_MONTH, or WEEK_OF_YEAR alone.  Treat DAY_OF_WEEK alone
         * as DAY_OF_WEEK_IN_MONTH.
         */
        if (bestStamp == UNSET) {
            womStamp = stamp[WEEK_OF_MONTH];
            dowimStamp = Math.max(stamp[DAY_OF_WEEK_IN_MONTH], dowStamp);
            woyStamp = stamp[WEEK_OF_YEAR];
            bestStamp = Math.max(Math.max(womStamp, dowimStamp), woyStamp);

            /* Treat MONTH alone or no fields at all as DAY_OF_MONTH.  This may
             * result in bestStamp = domStamp = UNSET if no fields are set,
             * which indicates DAY_OF_MONTH.
             */
            if (bestStamp == UNSET) {
                bestStamp = domStamp = monthStamp;
            }
        }

        if (bestStamp == domStamp || (bestStamp == womStamp && stamp[WEEK_OF_MONTH] >= stamp[WEEK_OF_YEAR])
            || (bestStamp == dowimStamp && stamp[DAY_OF_WEEK_IN_MONTH] >= stamp[WEEK_OF_YEAR])) {
            fieldMask |= MONTH_MASK;
            if (bestStamp == domStamp) {
                fieldMask |= DAY_OF_MONTH_MASK;
            } else {
                assert (bestStamp == womStamp || bestStamp == dowimStamp);
                if (dowStamp != UNSET) {
                    fieldMask |= DAY_OF_WEEK_MASK;
                }
                if (womStamp == dowimStamp) {
                    // When they are equal, give the priority to
                    // WEEK_OF_MONTH for compatibility.
                    if (stamp[WEEK_OF_MONTH] >= stamp[DAY_OF_WEEK_IN_MONTH]) {
                        fieldMask |= WEEK_OF_MONTH_MASK;
                    } else {
                        fieldMask |= DAY_OF_WEEK_IN_MONTH_MASK;
                    }
                } else {
                    if (bestStamp == womStamp) {
                        fieldMask |= WEEK_OF_MONTH_MASK;
                    } else {
                        assert (bestStamp == dowimStamp);
                        if (stamp[DAY_OF_WEEK_IN_MONTH] != UNSET) {
                            fieldMask |= DAY_OF_WEEK_IN_MONTH_MASK;
                        }
                    }
                }
            }
        } else {
            assert (bestStamp == doyStamp || bestStamp == woyStamp || bestStamp == UNSET);
            if (bestStamp == doyStamp) {
                fieldMask |= DAY_OF_YEAR_MASK;
            } else {
                assert (bestStamp == woyStamp);
                if (dowStamp != UNSET) {
                    fieldMask |= DAY_OF_WEEK_MASK;
                }
                fieldMask |= WEEK_OF_YEAR_MASK;
            }
        }

        // Find the best set of fields specifying the time of day. There
        // are only two possibilities here; the HOUR_OF_DAY or the
        // AM_PM and the HOUR.
        int hourOfDayStamp = stamp[HOUR_OF_DAY];
        int hourStamp = aggregateStamp(stamp[HOUR], stamp[AM_PM]);
        bestStamp = (hourStamp > hourOfDayStamp) ? hourStamp : hourOfDayStamp;

        // if bestStamp is still UNSET, then take HOUR or AM_PM. (See 4846659)
        if (bestStamp == UNSET) {
            bestStamp = Math.max(stamp[HOUR], stamp[AM_PM]);
        }

        // Hours
        if (bestStamp != UNSET) {
            if (bestStamp == hourOfDayStamp) {
                fieldMask |= HOUR_OF_DAY_MASK;
            } else {
                fieldMask |= HOUR_MASK;
                if (stamp[AM_PM] != UNSET) {
                    fieldMask |= AM_PM_MASK;
                }
            }
        }
        if (stamp[MINUTE] != UNSET) {
            fieldMask |= MINUTE_MASK;
        }
        if (stamp[SECOND] != UNSET) {
            fieldMask |= SECOND_MASK;
        }
        if (stamp[MILLISECOND] != UNSET) {
            fieldMask |= MILLISECOND_MASK;
        }
        if (stamp[ZONE_OFFSET] >= MINIMUM_USER_STAMP) {
            fieldMask |= ZONE_OFFSET_MASK;
        }
        if (stamp[DST_OFFSET] >= MINIMUM_USER_STAMP) {
            fieldMask |= DST_OFFSET_MASK;
        }

        return fieldMask;
    }

    int getBaseStyle(int style) {
        return style & ~STANDALONE_MASK;
    }

    private int toStandaloneStyle(int style) {
        return style | STANDALONE_MASK;
    }

    private boolean isStandaloneStyle(int style) {
        return (style & STANDALONE_MASK) != 0;
    }

    private boolean isNarrowStyle(int style) {
        return style == NARROW_FORMAT || style == NARROW_STANDALONE;
    }

    private boolean isNarrowFormatStyle(int style) {
        return style == NARROW_FORMAT;
    }

    private static int aggregateStamp(int stamp_a, int stamp_b) {
        if (stamp_a == UNSET || stamp_b == UNSET) {
            return UNSET;
        }
        return (stamp_a > stamp_b) ? stamp_a : stamp_b;
    }

    public static Set<String> getAvailableCalendarTypes() {
        return AvailableCalendarTypes.SET;
    }

    private static class AvailableCalendarTypes {
        private static final Set<String> SET;
        static {
            Set<String> set = new HashSet<>(3);
            set.add("gregory");
            set.add("buddhist");
            set.add("japanese");
            SET = Collections.unmodifiableSet(set);
        }

        private AvailableCalendarTypes() {}
    }

    public String getCalendarType() {
        return this.getClass().getName();
    }

    @SuppressWarnings("EqualsWhichDoesntCheckParameterClass")
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        try {
            Calendar that = (Calendar)obj;
            return compareTo(getMillisOf(that)) == 0 && lenient == that.lenient && firstDayOfWeek == that.firstDayOfWeek
                && minimalDaysInFirstWeek == that.minimalDaysInFirstWeek
                && (zone instanceof ZoneInfo ? zone.equals(that.zone) : zone.equals(that.getTimeZone()));
        } catch (Exception e) {
            // Note: GregorianCalendar.computeTime throws
            // IllegalArgumentException if the ERA value is invalid
            // even it's in lenient mode.
        }
        return false;
    }

    @Override
    public int hashCode() {
        // 'otheritems' represents the hash code for the previous versions.
        int otheritems =
            (lenient ? 1 : 0) | (firstDayOfWeek << 1) | (minimalDaysInFirstWeek << 4) | (zone.hashCode() << 7);
        long t = getMillisOf(this);
        return (int)t ^ (int)(t >> 32) ^ otheritems;
    }

    /**
     * 比较时间域记录。 等价于比较转换到 UTC 的结果。
     * @date 2022/4/30 18:16
     * @param when 与该 Calendar 比较的 Calendar。
     * @return boolean 如果当前时间在 Calendar when 的时间之前则为 true；否则为 false。
     */
    public boolean before(Object when) {
        return when instanceof Calendar && compareTo((Calendar)when) < 0;
    }

    /** 
     * 比较时间域记录。 等价于比较转换到 UTC 的结果。
     * @date 2022/4/30 18:16
     * @param when  与该 Calendar 比较的 Calendar。
     * @return boolean 如果该日历的当前时间在 Calendar when 的时间之后则为 true；否则为 false。
     */
    public boolean after(Object when) {
        return when instanceof Calendar && compareTo((Calendar)when) > 0;
    }

    @Override
    public int compareTo(Calendar anotherCalendar) {
        return compareTo(getMillisOf(anotherCalendar));
    }

    /** 
     * 日期的计算功能。 按照日历的规则，将指定 ( 带符号的 ) 数量的时间添加到给定的时间域。
     * 例如，从日历的当前时间减 5 ，可调用：add(Calendar.DATE, -5)。
     * @date 2022/4/30 18:17 
     * @param field 时间域。amount - 添加到该域的日期和时间的数量。
     * @param amount 
     * @return void
     */
    public abstract void add(int field, int amount);

    /** 
     * 时间域滚动功能。 在给定的时间域上 ( 向上 / 向下 ) 滚动一个单个的时间单元。
     * 例如，为了将当前日期向上滚动一天，可调用：roll(Calendar.DATE, true)。
     * 当在年或 Calendar.YEAR 域滚动时，年值将在 1 和调用 getMaximum(Calendar.YEAR) 的返回值之间滚动。
     * 当在月或 Calendar.MONTH 域滚动时，其它的域，例如日期，可能会发生冲突需要改变。
     * 例如，将日期 01/31/96 滚动一月结果是 03/02/96。 当在小时域或 Calendar.HOUR_OF_DAY 域滚动，小时值将在范围 0 到 23 之间滚动，它以 0 开始。
     * @date 2022/4/30 18:17 
     * @param field  时间域。
     * @param up 指明指定时间域值向上还是向下滚动。 如果向上滚动用 true ，否则用 false。
     * @return void
     */
    public abstract void roll(int field, boolean up);

    public void roll(int field, int amount) {
        while (amount > 0) {
            roll(field, true);
            amount--;
        }
        while (amount < 0) {
            roll(field, false);
            amount++;
        }
    }

    /**
     * 用给定的时区值设置时区。
     * @date 2022/4/30 18:25
     * @param value 给定的时区。
     * @return void
     */
    public void setTimeZone(TimeZone value) {
        zone = value;
        sharedZone = false;
        areAllFieldsSet = areFieldsSet = false;
    }

    /**
     * 获得时区。
     * @date 2022/4/30 18:19
     * @param
     * @return java.util.TimeZone 与日历相应的时区对象。
     */
    public TimeZone getTimeZone() {
        // If the TimeZone object is shared by other Calendar instances, then
        // create a clone.
        if (sharedZone) {
            zone = (TimeZone)zone.clone();
            sharedZone = false;
        }
        return zone;
    }

    TimeZone getZone() {
        return zone;
    }

    void setZoneShared(boolean shared) {
        sharedZone = shared;
    }

    /** 
     * 指明对日期／时间的解释是否是宽松的。 在宽松的解释下，一个诸如 "February 942, 1996" 的日期将被看作与1996 年 2 月后的第 941 天等价。
     * 在严格的解释下，这样的日期将引起抛出异常。
     * @date 2022/4/30 18:20 
     * @param lenient 
     * @return void
     */
    public void setLenient(boolean lenient) {
        this.lenient = lenient;
    }

    /**
     * 指明对日期／时间的解释是否是宽松的。
     * @return
     */
    public boolean isLenient() {
        return lenient;
    }

    public void setFirstDayOfWeek(int value) {
        if (firstDayOfWeek == value) {
            return;
        }
        firstDayOfWeek = value;
        invalidateWeekFields();
    }

    /**
     * 获得一周的第一天；例如在美国为 Sunday，在法国为 Monday。
     * @date 2022/4/30 18:21
     * @param
     * @return int
     */
    public int getFirstDayOfWeek() {
        return firstDayOfWeek;
    }

    public void setMinimalDaysInFirstWeek(int value) {
        if (minimalDaysInFirstWeek == value) {
            return;
        }
        minimalDaysInFirstWeek = value;
        invalidateWeekFields();
    }

    public int getMinimalDaysInFirstWeek() {
        return minimalDaysInFirstWeek;
    }

    public boolean isWeekDateSupported() {
        return false;
    }

    public int getWeekYear() {
        throw new UnsupportedOperationException();
    }

    public void setWeekDate(int weekYear, int weekOfYear, int dayOfWeek) {
        throw new UnsupportedOperationException();
    }

    public int getWeeksInWeekYear() {
        throw new UnsupportedOperationException();
    }

    /** 
     * 获得给定时间域最小值。 例如对于格里高里 DAY_OF_MONTH 为 1。
     * @date 2022/4/30 18:22 
     * @param field 给定的时间域。
     * @return int 给定时间域最高的最小值。
     */
    public abstract int getMinimum(int field);

    /**
     * 获得给定时间域最大值。 例如对于 Gregorian DAY_OF_MONTH 为 31。
     * @date 2022/4/30 18:21
     * @param field  给定的时间域。
     * @return int
     */
    public abstract int getMaximum(int field);

    /** 
     * 获得给定域变化时的最高的最小值。 否则与 getMinimum() 相同。对格里高里日历没有区别。
     * @date 2022/4/30 18:23 
     * @param field 给定的时间域。
     * @return int 给定时间域最大值。
     */
    public abstract int getGreatestMinimum(int field);

    /**
     * 获得给定域变化时的最低的最大值。 否则与 getMaximum() 相同。例如对于格里高里日历 DAY_OF_MONTH 为 28。
     * @date 2022/4/30 18:23
     * @param field
     * @return int 定时间域最低的最大值。
     */
    public abstract int getLeastMaximum(int field);

    public int getActualMinimum(int field) {
        int fieldValue = getGreatestMinimum(field);
        int endValue = getMinimum(field);

        // if we know that the minimum value is always the same, just return it
        if (fieldValue == endValue) {
            return fieldValue;
        }

        // clone the calendar so we don't mess with the real one, and set it to
        // accept anything for the field values
        Calendar work = (Calendar)this.clone();
        work.setLenient(true);

        // now try each value from getLeastMaximum() to getMaximum() one by one until
        // we get a value that normalizes to another value. The last value that
        // normalizes to itself is the actual minimum for the current date
        int result = fieldValue;

        do {
            work.set(field, fieldValue);
            if (work.get(field) != fieldValue) {
                break;
            } else {
                result = fieldValue;
                fieldValue--;
            }
        } while (fieldValue >= endValue);

        return result;
    }

    public int getActualMaximum(int field) {
        int fieldValue = getLeastMaximum(field);
        int endValue = getMaximum(field);

        // if we know that the maximum value is always the same, just return it.
        if (fieldValue == endValue) {
            return fieldValue;
        }

        // clone the calendar so we don't mess with the real one, and set it to
        // accept anything for the field values.
        Calendar work = (Calendar)this.clone();
        work.setLenient(true);

        // if we're counting weeks, set the day of the week to Sunday. We know the
        // last week of a month or year will contain the first day of the week.
        if (field == WEEK_OF_YEAR || field == WEEK_OF_MONTH) {
            work.set(DAY_OF_WEEK, firstDayOfWeek);
        }

        // now try each value from getLeastMaximum() to getMaximum() one by one until
        // we get a value that normalizes to another value. The last value that
        // normalizes to itself is the actual maximum for the current date
        int result = fieldValue;

        do {
            work.set(field, fieldValue);
            if (work.get(field) != fieldValue) {
                break;
            } else {
                result = fieldValue;
                fieldValue++;
            }
        } while (fieldValue <= endValue);

        return result;
    }

    @Override
    public Object clone() {
        try {
            Calendar other = (Calendar)super.clone();

            other.fields = new int[FIELD_COUNT];
            other.isSet = new boolean[FIELD_COUNT];
            other.stamp = new int[FIELD_COUNT];
            for (int i = 0; i < FIELD_COUNT; i++) {
                other.fields[i] = fields[i];
                other.stamp[i] = stamp[i];
                other.isSet[i] = isSet[i];
            }
            other.zone = (TimeZone)zone.clone();
            return other;
        } catch (CloneNotSupportedException e) {
            // this shouldn't happen, since we are Cloneable
            throw new InternalError(e);
        }
    }

    private static final String[] FIELD_NAME = {"ERA", "YEAR", "MONTH", "WEEK_OF_YEAR", "WEEK_OF_MONTH", "DAY_OF_MONTH",
        "DAY_OF_YEAR", "DAY_OF_WEEK", "DAY_OF_WEEK_IN_MONTH", "AM_PM", "HOUR", "HOUR_OF_DAY", "MINUTE", "SECOND",
        "MILLISECOND", "ZONE_OFFSET", "DST_OFFSET"};

    static String getFieldName(int field) {
        return FIELD_NAME[field];
    }

    @Override
    public String toString() {
        // NOTE: BuddhistCalendar.toString() interprets the string
        // produced by this method so that the Gregorian year number
        // is substituted by its B.E. year value. It relies on
        // "...,YEAR=<year>,..." or "...,YEAR=?,...".
        StringBuilder buffer = new StringBuilder(800);
        buffer.append(getClass().getName()).append('[');
        appendValue(buffer, "time", isTimeSet, time);
        buffer.append(",areFieldsSet=").append(areFieldsSet);
        buffer.append(",areAllFieldsSet=").append(areAllFieldsSet);
        buffer.append(",lenient=").append(lenient);
        buffer.append(",zone=").append(zone);
        appendValue(buffer, ",firstDayOfWeek", true, (long)firstDayOfWeek);
        appendValue(buffer, ",minimalDaysInFirstWeek", true, (long)minimalDaysInFirstWeek);
        for (int i = 0; i < FIELD_COUNT; ++i) {
            buffer.append(',');
            appendValue(buffer, FIELD_NAME[i], isSet(i), (long)fields[i]);
        }
        buffer.append(']');
        return buffer.toString();
    }

    // =======================privates===============================

    private static void appendValue(StringBuilder sb, String item, boolean valid, long value) {
        sb.append(item).append('=');
        if (valid) {
            sb.append(value);
        } else {
            sb.append('?');
        }
    }

    private void setWeekCountData(Locale desiredLocale) {
        /* try to get the Locale data from the cache */
        int[] data = cachedLocaleData.get(desiredLocale);
        if (data == null) { /* cache miss */
            data = new int[2];
            data[0] = CalendarDataUtility.retrieveFirstDayOfWeek(desiredLocale);
            data[1] = CalendarDataUtility.retrieveMinimalDaysInFirstWeek(desiredLocale);
            cachedLocaleData.putIfAbsent(desiredLocale, data);
        }
        firstDayOfWeek = data[0];
        minimalDaysInFirstWeek = data[1];
    }

    private void updateTime() {
        computeTime();
        // The areFieldsSet and areAllFieldsSet values are no longer
        // controlled here (as of 1.5).
        isTimeSet = true;
    }

    private int compareTo(long t) {
        long thisTime = getMillisOf(this);
        return (thisTime > t) ? 1 : (thisTime == t) ? 0 : -1;
    }

    private static long getMillisOf(Calendar calendar) {
        if (calendar.isTimeSet) {
            return calendar.time;
        }
        Calendar cal = (Calendar)calendar.clone();
        cal.setLenient(true);
        return cal.getTimeInMillis();
    }

    private void adjustStamp() {
        int max = MINIMUM_USER_STAMP;
        int newStamp = MINIMUM_USER_STAMP;

        for (;;) {
            int min = Integer.MAX_VALUE;
            for (int v : stamp) {
                if (v >= newStamp && min > v) {
                    min = v;
                }
                if (max < v) {
                    max = v;
                }
            }
            if (max != min && min == Integer.MAX_VALUE) {
                break;
            }
            for (int i = 0; i < stamp.length; i++) {
                if (stamp[i] == min) {
                    stamp[i] = newStamp;
                }
            }
            newStamp++;
            if (min == max) {
                break;
            }
        }
        nextStamp = newStamp;
    }

    private void invalidateWeekFields() {
        if (stamp[WEEK_OF_MONTH] != COMPUTED && stamp[WEEK_OF_YEAR] != COMPUTED) {
            return;
        }

        // We have to check the new values of these fields after changing
        // firstDayOfWeek and/or minimalDaysInFirstWeek. If the field values
        // have been changed, then set the new values. (4822110)
        Calendar cal = (Calendar)clone();
        cal.setLenient(true);
        cal.clear(WEEK_OF_MONTH);
        cal.clear(WEEK_OF_YEAR);

        if (stamp[WEEK_OF_MONTH] == COMPUTED) {
            int weekOfMonth = cal.get(WEEK_OF_MONTH);
            if (fields[WEEK_OF_MONTH] != weekOfMonth) {
                fields[WEEK_OF_MONTH] = weekOfMonth;
            }
        }

        if (stamp[WEEK_OF_YEAR] == COMPUTED) {
            int weekOfYear = cal.get(WEEK_OF_YEAR);
            if (fields[WEEK_OF_YEAR] != weekOfYear) {
                fields[WEEK_OF_YEAR] = weekOfYear;
            }
        }
    }

    private synchronized void writeObject(ObjectOutputStream stream) throws IOException {
        // Try to compute the time correctly, for the future (stream
        // version 2) in which we don't write out fields[] or isSet[].
        if (!isTimeSet) {
            try {
                updateTime();
            } catch (IllegalArgumentException e) {
            }
        }

        // If this Calendar has a ZoneInfo, save it and set a
        // SimpleTimeZone equivalent (as a single DST schedule) for
        // backward compatibility.
        TimeZone savedZone = null;
        if (zone instanceof ZoneInfo) {
            SimpleTimeZone stz = ((ZoneInfo)zone).getLastRuleInstance();
            if (stz == null) {
                stz = new SimpleTimeZone(zone.getRawOffset(), zone.getID());
            }
            savedZone = zone;
            zone = stz;
        }

        // Write out the 1.1 FCS object.
        stream.defaultWriteObject();

        // Write out the ZoneInfo object
        // 4802409: we write out even if it is null, a temporary workaround
        // the real fix for bug 4844924 in corba-iiop
        stream.writeObject(savedZone);
        if (savedZone != null) {
            zone = savedZone;
        }
    }

    private static class CalendarAccessControlContext {
        private static final AccessControlContext INSTANCE;
        static {
            RuntimePermission perm = new RuntimePermission("accessClassInPackage.sun.util.calendar");
            PermissionCollection perms = perm.newPermissionCollection();
            perms.add(perm);
            INSTANCE = new AccessControlContext(new ProtectionDomain[] {new ProtectionDomain(null, perms)});
        }

        private CalendarAccessControlContext() {}
    }

    private void readObject(ObjectInputStream stream) throws IOException, ClassNotFoundException {
        final ObjectInputStream input = stream;
        input.defaultReadObject();

        stamp = new int[FIELD_COUNT];

        // Starting with version 2 (not implemented yet), we expect that
        // fields[], isSet[], isTimeSet, and areFieldsSet may not be
        // streamed out anymore. We expect 'time' to be correct.
        if (serialVersionOnStream >= 2) {
            isTimeSet = true;
            if (fields == null) {
                fields = new int[FIELD_COUNT];
            }
            if (isSet == null) {
                isSet = new boolean[FIELD_COUNT];
            }
        } else if (serialVersionOnStream >= 0) {
            for (int i = 0; i < FIELD_COUNT; ++i) {
                stamp[i] = isSet[i] ? COMPUTED : UNSET;
            }
        }

        serialVersionOnStream = currentSerialVersion;

        // If there's a ZoneInfo object, use it for zone.
        ZoneInfo zi = null;
        try {
            zi = AccessController.doPrivileged(new PrivilegedExceptionAction<>() {
                @Override
                public ZoneInfo run() throws Exception {
                    return (ZoneInfo)input.readObject();
                }
            }, CalendarAccessControlContext.INSTANCE);
        } catch (PrivilegedActionException pae) {
            Exception e = pae.getException();
            if (!(e instanceof OptionalDataException)) {
                if (e instanceof RuntimeException) {
                    throw (RuntimeException)e;
                } else if (e instanceof IOException) {
                    throw (IOException)e;
                } else if (e instanceof ClassNotFoundException) {
                    throw (ClassNotFoundException)e;
                }
                throw new RuntimeException(e);
            }
        }
        if (zi != null) {
            zone = zi;
        }

        // If the deserialized object has a SimpleTimeZone, try to
        // replace it with a ZoneInfo equivalent (as of 1.4) in order
        // to be compatible with the SimpleTimeZone-based
        // implementation as much as possible.
        if (zone instanceof SimpleTimeZone) {
            String id = zone.getID();
            TimeZone tz = TimeZone.getTimeZone(id);
            if (tz != null && tz.hasSameRules(zone) && tz.getID().equals(id)) {
                zone = tz;
            }
        }
    }

    public final Instant toInstant() {
        return Instant.ofEpochMilli(getTimeInMillis());
    }
}
