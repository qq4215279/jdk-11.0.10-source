/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.lang.instrument;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;

public interface ClassFileTransformer {

    default byte[] transform(ClassLoader loader,
                             String className,
                             Class<?> classBeingRedefined,
                             ProtectionDomain protectionDomain,
                             byte[] classfileBuffer)
            throws IllegalClassFormatException {
        return null;
    }


    default byte[] transform(Module module,
                             ClassLoader loader,
                             String className,
                             Class<?> classBeingRedefined,
                             ProtectionDomain protectionDomain,
                             byte[] classfileBuffer)
            throws IllegalClassFormatException {

        // invoke the legacy transform method
        return transform(loader,
                className,
                classBeingRedefined,
                protectionDomain,
                classfileBuffer);
    }
}
