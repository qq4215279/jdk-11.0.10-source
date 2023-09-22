/*
 * Copyright (c) 2004, 2008, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.lang.instrument;

public class UnmodifiableClassException extends Exception {
    private static final long serialVersionUID = 1716652643585309178L;

    public UnmodifiableClassException() {
        super();
    }

    public UnmodifiableClassException(String s) {
        super(s);
    }
}
