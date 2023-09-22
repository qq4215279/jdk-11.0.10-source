/*
 * Copyright (c) 2003, 2008, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.lang.instrument;

public class IllegalClassFormatException extends Exception {
    private static final long serialVersionUID = -3841736710924794009L;

    public IllegalClassFormatException() {
        super();
    }

    public IllegalClassFormatException(String s) {
        super(s);
    }
}
