/*
 * Copyright (c) 2012, 2020, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

/*
 * COPYRIGHT AND PERMISSION NOTICE
 *
 * Copyright (C) 1991-2016 Unicode, Inc. All rights reserved.
 * Distributed under the Terms of Use in 
 * http://www.unicode.org/copyright.html.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of the Unicode data files and any associated documentation
 * (the "Data Files") or Unicode software and any associated documentation
 * (the "Software") to deal in the Data Files or Software
 * without restriction, including without limitation the rights to use,
 * copy, modify, merge, publish, distribute, and/or sell copies of
 * the Data Files or Software, and to permit persons to whom the Data Files
 * or Software are furnished to do so, provided that
 * (a) this copyright and permission notice appear with all copies 
 * of the Data Files or Software,
 * (b) this copyright and permission notice appear in associated 
 * documentation, and
 * (c) there is clear notice in each modified Data File or in the Software
 * as well as in the documentation associated with the Data File(s) or
 * Software that the data or software has been modified.
 *
 * THE DATA FILES AND SOFTWARE ARE PROVIDED "AS IS", WITHOUT WARRANTY OF
 * ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE
 * WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT OF THIRD PARTY RIGHTS.
 * IN NO EVENT SHALL THE COPYRIGHT HOLDER OR HOLDERS INCLUDED IN THIS
 * NOTICE BE LIABLE FOR ANY CLAIM, OR ANY SPECIAL INDIRECT OR CONSEQUENTIAL
 * DAMAGES, OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE,
 * DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER
 * TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 * PERFORMANCE OF THE DATA FILES OR SOFTWARE.
 *
 * Except as contained in this notice, the name of a copyright holder
 * shall not be used in advertising or otherwise to promote the sale,
 * use or other dealings in these Data Files or Software without prior
 * written authorization of the copyright holder.
 */

package sun.text.resources.cldr.ext;

import java.util.ListResourceBundle;

public class FormatData_hr extends ListResourceBundle {
    @Override
    protected final Object[][] getContents() {
        final String[] metaValue_MonthNames = new String[] {
               "sije\u010dnja",
               "velja\u010de",
               "o\u017eujka",
               "travnja",
               "svibnja",
               "lipnja",
               "srpnja",
               "kolovoza",
               "rujna",
               "listopada",
               "studenoga",
               "prosinca",
               "",
            };
        final String[] metaValue_MonthAbbreviations = new String[] {
               "sij",
               "velj",
               "o\u017eu",
               "tra",
               "svi",
               "lip",
               "srp",
               "kol",
               "ruj",
               "lis",
               "stu",
               "pro",
               "",
            };
        final String[] metaValue_MonthNarrows = new String[] {
               "1.",
               "2.",
               "3.",
               "4.",
               "5.",
               "6.",
               "7.",
               "8.",
               "9.",
               "10.",
               "11.",
               "12.",
               "",
            };
        final String[] metaValue_DayNames = new String[] {
               "nedjelja",
               "ponedjeljak",
               "utorak",
               "srijeda",
               "\u010detvrtak",
               "petak",
               "subota",
            };
        final String[] metaValue_DayAbbreviations = new String[] {
               "ned",
               "pon",
               "uto",
               "sri",
               "\u010det",
               "pet",
               "sub",
            };
        final String[] metaValue_DayNarrows = new String[] {
               "N",
               "P",
               "U",
               "S",
               "\u010c",
               "P",
               "S",
            };
        final String[] metaValue_QuarterNames = new String[] {
               "1. kvartal",
               "2. kvartal",
               "3. kvartal",
               "4. kvartal",
            };
        final String[] metaValue_QuarterAbbreviations = new String[] {
               "1kv",
               "2kv",
               "3kv",
               "4kv",
            };
        final String[] metaValue_QuarterNarrows = new String[] {
               "1.",
               "2.",
               "3.",
               "4.",
            };
        final String[] metaValue_TimePatterns = new String[] {
               "HH:mm:ss (zzzz)",
               "HH:mm:ss z",
               "HH:mm:ss",
               "HH:mm",
            };
        final String[] metaValue_buddhist_long_Eras = new String[] {
               "BC",
               "BE",
            };
        final String[] metaValue_java_time_buddhist_DatePatterns = new String[] {
               "EEEE, d. MMMM y. G",
               "d. MMMM y. G",
               "d. MMM y. G",
               "dd. MM. y. GGGGG",
            };
        final String[] metaValue_buddhist_DatePatterns = new String[] {
               "EEEE, d. MMMM y. GGGG",
               "d. MMMM y. GGGG",
               "d. MMM y. GGGG",
               "dd. MM. y. G",
            };
        final String[] metaValue_japanese_narrow_AmPmMarkers = new String[] {
               "AM",
               "PM",
            };
        final String[] metaValue_java_time_japanese_DatePatterns = new String[] {
               "EEEE, d. MMMM y. G",
               "d. MMMM y. G",
               "d. M. y. G",
               "d. M. y. GGGGG",
            };
        final String[] metaValue_japanese_DatePatterns = new String[] {
               "EEEE, d. MMMM y. GGGG",
               "d. MMMM y. GGGG",
               "d. M. y. GGGG",
               "d. M. y. G",
            };
        final String[] metaValue_roc_long_Eras = new String[] {
               "prije R.O.C.",
               "R.O.C.",
            };
        final String[] metaValue_islamic_long_Eras = new String[] {
               "",
               "AH",
            };
        final String metaValue_calendarname_gregorian = "gregorijanski kalendar";
        final Object[][] data = new Object[][] {
            { "MonthNames", metaValue_MonthNames },
            { "field.year", "godina" },
            { "calendarname.islamic-umalqura", "islamski kalendar (Umm al-Qura)" },
            { "buddhist.narrow.Eras", metaValue_buddhist_long_Eras },
            { "java.time.japanese.DatePatterns", metaValue_java_time_japanese_DatePatterns },
            { "standalone.QuarterAbbreviations",
                new String[] {
                    "1. kv.",
                    "2. kv.",
                    "3. kv.",
                    "4. kv.",
                }
            },
            { "roc.QuarterNames", metaValue_QuarterNames },
            { "roc.MonthNarrows", metaValue_MonthNarrows },
            { "calendarname.islamic-civil", "islamski civilni kalendar" },
            { "islamic.narrow.AmPmMarkers", metaValue_japanese_narrow_AmPmMarkers },
            { "japanese.TimePatterns", metaValue_TimePatterns },
            { "roc.long.Eras", metaValue_roc_long_Eras },
            { "narrow.Eras",
                new String[] {
                    "pr.n.e.",
                    "AD",
                }
            },
            { "timezone.regionFormat.standard", "{0}, standardno vrijeme" },
            { "calendarname.japanese", "japanski kalendar" },
            { "japanese.MonthNames", metaValue_MonthNames },
            { "standalone.DayAbbreviations", metaValue_DayAbbreviations },
            { "roc.MonthAbbreviations", metaValue_MonthAbbreviations },
            { "long.Eras",
                new String[] {
                    "prije Krista",
                    "poslije Krista",
                }
            },
            { "roc.QuarterNarrows", metaValue_QuarterNarrows },
            { "islamic.DayNames", metaValue_DayNames },
            { "buddhist.MonthAbbreviations", metaValue_MonthAbbreviations },
            { "buddhist.MonthNames", metaValue_MonthNames },
            { "DateTimePatterns",
                new String[] {
                    "{1} 'u' {0}",
                    "{1} 'u' {0}",
                    "{1} {0}",
                    "{1} {0}",
                }
            },
            { "latn.NumberElements",
                new String[] {
                    ",",
                    ".",
                    ";",
                    "%",
                    "0",
                    "#",
                    "-",
                    "E",
                    "\u2030",
                    "\u221e",
                    "NaN",
                }
            },
            { "MonthNarrows", metaValue_MonthNarrows },
            { "japanese.DatePatterns", metaValue_japanese_DatePatterns },
            { "buddhist.DayNames", metaValue_DayNames },
            { "field.minute", "minuta" },
            { "field.era", "era" },
            { "japanese.long.Eras",
                new String[] {
                    "poslije Krista",
                    "Meiji",
                    "Taish\u014d",
                    "Sh\u014dwa",
                    "Heisei",
                    "",
                }
            },
            { "field.dayperiod", "AM/PM" },
            { "standalone.MonthNarrows", metaValue_MonthNarrows },
            { "japanese.QuarterNarrows", metaValue_QuarterNarrows },
            { "calendarname.roc", "kalendar Republike Kine" },
            { "islamic.DatePatterns", metaValue_japanese_DatePatterns },
            { "roc.QuarterAbbreviations", metaValue_QuarterAbbreviations },
            { "islamic.Eras", metaValue_islamic_long_Eras },
            { "field.month", "mjesec" },
            { "roc.Eras", metaValue_roc_long_Eras },
            { "field.second", "sekunda" },
            { "DayAbbreviations", metaValue_DayAbbreviations },
            { "DayNarrows", metaValue_DayNarrows },
            { "NumberPatterns",
                new String[] {
                    "#,##0.###",
                    "#,##0.00\u00a0\u00a4",
                    "#,##0%",
                }
            },
            { "roc.DatePatterns", metaValue_buddhist_DatePatterns },
            { "calendarname.islamic", "islamski kalendar" },
            { "japanese.narrow.AmPmMarkers", metaValue_japanese_narrow_AmPmMarkers },
            { "buddhist.TimePatterns", metaValue_TimePatterns },
            { "standalone.MonthAbbreviations", metaValue_MonthAbbreviations },
            { "buddhist.QuarterNarrows", metaValue_QuarterNarrows },
            { "roc.narrow.AmPmMarkers", metaValue_japanese_narrow_AmPmMarkers },
            { "standalone.QuarterNames", metaValue_QuarterNames },
            { "japanese.MonthNarrows", metaValue_MonthNarrows },
            { "islamic.QuarterAbbreviations", metaValue_QuarterAbbreviations },
            { "roc.DayAbbreviations", metaValue_DayAbbreviations },
            { "standalone.DayNarrows",
                new String[] {
                    "n",
                    "p",
                    "u",
                    "s",
                    "\u010d",
                    "p",
                    "s",
                }
            },
            { "buddhist.long.Eras", metaValue_buddhist_long_Eras },
            { "islamic.AmPmMarkers", metaValue_japanese_narrow_AmPmMarkers },
            { "TimePatterns", metaValue_TimePatterns },
            { "islamic.DayNarrows", metaValue_DayNarrows },
            { "field.zone", "vremenska zona" },
            { "japanese.QuarterAbbreviations", metaValue_QuarterAbbreviations },
            { "roc.narrow.Eras", metaValue_roc_long_Eras },
            { "Eras",
                new String[] {
                    "pr. Kr.",
                    "po. Kr.",
                }
            },
            { "roc.DayNames", metaValue_DayNames },
            { "islamic.QuarterNames", metaValue_QuarterNames },
            { "QuarterNarrows", metaValue_QuarterNarrows },
            { "standalone.QuarterNarrows", metaValue_QuarterNarrows },
            { "java.time.islamic.DatePatterns", metaValue_java_time_japanese_DatePatterns },
            { "field.weekday", "dan u tjednu" },
            { "japanese.MonthAbbreviations", metaValue_MonthAbbreviations },
            { "islamic.DayAbbreviations", metaValue_DayAbbreviations },
            { "islamic.long.Eras", metaValue_islamic_long_Eras },
            { "japanese.QuarterNames", metaValue_QuarterNames },
            { "buddhist.QuarterAbbreviations", metaValue_QuarterAbbreviations },
            { "timezone.hourFormat", "+HH:mm; -HH:mm" },
            { "japanese.DayNames", metaValue_DayNames },
            { "japanese.DayAbbreviations", metaValue_DayAbbreviations },
            { "DayNames", metaValue_DayNames },
            { "buddhist.DatePatterns", metaValue_buddhist_DatePatterns },
            { "roc.MonthNames", metaValue_MonthNames },
            { "buddhist.Eras", metaValue_buddhist_long_Eras },
            { "field.week", "tjedan" },
            { "buddhist.MonthNarrows", metaValue_MonthNarrows },
            { "buddhist.QuarterNames", metaValue_QuarterNames },
            { "islamic.QuarterNarrows", metaValue_QuarterNarrows },
            { "roc.DayNarrows", metaValue_DayNarrows },
            { "roc.AmPmMarkers", metaValue_japanese_narrow_AmPmMarkers },
            { "java.time.roc.DatePatterns", metaValue_java_time_buddhist_DatePatterns },
            { "java.time.buddhist.DatePatterns", metaValue_java_time_buddhist_DatePatterns },
            { "calendarname.gregorian", metaValue_calendarname_gregorian },
            { "timezone.regionFormat.daylight", "{0}, ljetno vrijeme" },
            { "DatePatterns",
                new String[] {
                    "EEEE, d. MMMM y.",
                    "d. MMMM y.",
                    "d. MMM y.",
                    "dd. MM. y.",
                }
            },
            { "buddhist.DayAbbreviations", metaValue_DayAbbreviations },
            { "islamic.TimePatterns", metaValue_TimePatterns },
            { "japanese.DateTimePatterns",
                new String[] {
                    "{1} {0}",
                    "{1} {0}",
                    "{1} {0}",
                    "{1} {0}",
                }
            },
            { "MonthAbbreviations", metaValue_MonthAbbreviations },
            { "standalone.DayNames", metaValue_DayNames },
            { "field.hour", "sat" },
            { "islamic.narrow.Eras", metaValue_islamic_long_Eras },
            { "calendarname.buddhist", "budisti\u010dki kalendar" },
            { "standalone.MonthNames",
                new String[] {
                    "sije\u010danj",
                    "velja\u010da",
                    "o\u017eujak",
                    "travanj",
                    "svibanj",
                    "lipanj",
                    "srpanj",
                    "kolovoz",
                    "rujan",
                    "listopad",
                    "studeni",
                    "prosinac",
                    "",
                }
            },
            { "buddhist.DayNarrows", metaValue_DayNarrows },
            { "japanese.DayNarrows", metaValue_DayNarrows },
            { "QuarterNames", metaValue_QuarterNames },
            { "roc.TimePatterns", metaValue_TimePatterns },
            { "QuarterAbbreviations", metaValue_QuarterAbbreviations },
            { "japanese.Eras",
                new String[] {
                    "po. Kr.",
                    "Meiji",
                    "Taish\u014d",
                    "Sh\u014dwa",
                    "Heisei",
                    "",
                }
            },
            { "calendarname.gregory", metaValue_calendarname_gregorian },
        };
        return data;
    }
}
