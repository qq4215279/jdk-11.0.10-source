/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.lang.instrument;

public class UnmodifiableModuleException extends RuntimeException {
    private static final long serialVersionUID = 6912511912351080644L;

    public UnmodifiableModuleException() {
        super();
    }

    public UnmodifiableModuleException(String msg) {
        super(msg);
    }
}
