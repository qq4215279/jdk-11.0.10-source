/*
 * Copyright (c) 1995, 2011, Oracle and/or its affiliates. All rights reserved. ORACLE PROPRIETARY/CONFIDENTIAL. Use is
 * subject to license terms.
 */

package java.lang;

public class Error extends Throwable {
    static final long serialVersionUID = 4980196508277280342L;

    public Error() {
        super();
    }

    public Error(String message) {
        super(message);
    }

    public Error(String message, Throwable cause) {
        super(message, cause);
    }

    public Error(Throwable cause) {
        super(cause);
    }

    protected Error(String message, Throwable cause, boolean enableSuppression, boolean writableStackTrace) {
        super(message, cause, enableSuppression, writableStackTrace);
    }
}
