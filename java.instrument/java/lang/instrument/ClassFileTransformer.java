/*
 * Copyright (c) 2003, 2016, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.lang.instrument;

import java.security.AccessController;
import java.security.PrivilegedAction;
import java.security.ProtectionDomain;

/**
 * 转换类文件的代理接口
 * @date 2023/11/17 14:14
 */
public interface ClassFileTransformer {

    /**
     * protectionDomain - 要定义或重定义的类的保护域
     * classfileBuffer - 类文件格式的输入字节缓冲区（不得修改）
     * @param loader 类文件被转换时的类加载器
     * @param className 指jvm中定义的完整的类名或接口名，比如java/util/List，那么在每个类都要进行加载时就需要进行一个判断，只对满足我们需求的类进行匹配
     * @param classBeingRedefined 指transform是类加载时触发还是类被重新转换时触发，感觉像个标志的作用
     * @param protectionDomain 保护域，这里涉及到了类的加载，所以涉及到了Java Security(主要是一种定义了一些代码执行行为的约束)
     * @param classfileBuffer classfileBuffer
     * @return byte[]
     * @date 2023/11/14 14:52
     */
    default byte[] transform(ClassLoader loader,
                             String className,
                             Class<?> classBeingRedefined,
                             ProtectionDomain protectionDomain,
                             byte[] classfileBuffer)
            throws IllegalClassFormatException {
        return null;
    }

    /**
     * 
     * @param module module
     * @param loader loader
     * @param className className
     * @param classBeingRedefined classBeingRedefined
     * @param protectionDomain protectionDomain
     * @param classfileBuffer classfileBuffer
     * @return byte[]
     * @date 2023/11/17 14:14
     */
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
