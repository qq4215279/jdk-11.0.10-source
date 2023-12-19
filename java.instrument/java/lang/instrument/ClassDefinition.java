/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.lang.instrument;

public final class ClassDefinition {
    /**
     * 自身class
     */
    private final Class<?> mClass;
    /**
     *  本地class文件
     */
    private final byte[] mClassFile;

    /**
     * 使用提供的类和类文件字节创建一个新的 ClassDefinition 绑定
     */
    public ClassDefinition(Class<?> theClass, byte[] theClassFile) {
        if (theClass == null || theClassFile == null) {
            throw new NullPointerException();
        }
        mClass = theClass;
        mClassFile = theClassFile;
    }

    /**
     * 返回该类。
     */
    public Class<?> getDefinitionClass() {
        return mClass;
    }

    /**
     * 返回包含新的类文件的 byte 数组。
     */
    public byte[] getDefinitionClassFile() {
        return mClassFile;
    }
}
