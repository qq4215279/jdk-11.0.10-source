/*
 * Copyright (c) 2003, 2018, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.lang.instrument;

import java.security.ProtectionDomain;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;

/**
 * Java SE 6新特性,
 * 获取 Instrumentation 接口的实例有两种方式：
 *  1. 当 JVM 以指示一个代理类的方式启动时，将传递给代理类的 premain 方法一个 Instrumentation 实例。
 *  2. 当 JVM 提供某种机制在 JVM 启动之后某一时刻启动代理时，将传递给代理代码的 agentmain 方法一个 Instrumentation 实例
 *
 * 提供检测 Java 编程语言代码所需的服务。检测是向方法中添加字节码
 * 以搜集各种工具所使用的数据
 * @date 2023/11/17 14:15
 */
public interface Instrumentation {

    /**
     * 增加一个Class 文件的转换器，转换器用于改变 Class 二进制流的数据
     * @param transformer transformer
     * @param canRetransform 当前转换的结果时候能够被再次转换。即设置是否允许重新转换。
     * @return void
     * @date 2023/11/14 14:44
     */
    void addTransformer(ClassFileTransformer transformer, boolean canRetransform);

    /**
     * 在类加载之前，重新定义 Class 文件，ClassDefinition 表示对一个类新的定义，如果在类加载之后，需要使用 retransformClasses 方法重新定义。
     * addTransformer方法配置之后，后续的类加载都会被Transformer拦截。对于已经加载过的类，可以执行retransformClasses来重新触发这个Transformer的拦截。
     * 类加载的字节码被修改后，除非再次被retransform，否则不会恢复。
     * @param transformer transformer 
     * @return void
     * @date 2023/11/14 15:32
     */
    void addTransformer(ClassFileTransformer transformer);

    /**
     * 删除一个类转换器
     * @param transformer transformer 
     * @return boolean
     * @date 2023/11/14 15:32
     */
    boolean removeTransformer(ClassFileTransformer transformer);

    /**
     * 返回当前 JVM 配置是否支持类的重转换。
     * @return boolean
     * @date 2023/11/17 14:16
     */
    boolean isRetransformClassesSupported();

    /**
     * 在类加载之后，重新定义 Class。这个很重要，该方法是1.6 之后加入的，事实上，该方法是 update 了一个类。即重转换提供的类集
     * @param classes classes
     * @return void
     * @date 2023/11/14 11:38
     */
    void retransformClasses(Class<?>... classes) throws UnmodifiableClassException;

    /**
     * 返回当前 JVM 配置是否支持类的重定义
     * @return boolean
     * @date 2023/11/17 14:17
     */
    boolean isRedefineClassesSupported();

    /**
     * 使用提供的类文件重定义提供的类集
     * @param definitions definitions
     * @return void
     * @date 2023/11/14 11:38
     */
    void redefineClasses(ClassDefinition... definitions) throws ClassNotFoundException, UnmodifiableClassException;

    /**
     * 确定一个类是否可以被 retransformation 或 redefinition 修改。
     * @param theClass theClass 
     * @return boolean
     * @date 2023/11/17 14:17
     */
    boolean isModifiableClass(Class<?> theClass);

    /**
     * 返回 JVM 当前加载的所有类的数组 
     * @return java.lang.Class[]
     * @date 2023/11/17 14:17
     */
    @SuppressWarnings("rawtypes")
    Class[] getAllLoadedClasses();

    /**
     * 返回所有初始化加载器是 loader 的类的数组。
     * @param loader loader 
     * @return java.lang.Class[]
     * @date 2023/11/17 14:18
     */
    @SuppressWarnings("rawtypes")
    Class[] getInitiatedClasses(ClassLoader loader);

    // 获取一个对象的大小
    long getObjectSize(Object objectToSize);

    /**
     * 指定 JAR 文件，检测类由引导类加载器定义
     */
    void appendToBootstrapClassLoaderSearch(JarFile jarfile);

    /**
     * 指定 JAR 文件，检测类由系统类加载器定义。
     */
    void appendToSystemClassLoaderSearch(JarFile jarfile);

    /**
     * 返回当前 JVM 配置是否支持设置本机方法前缀。
     */
    boolean isNativeMethodPrefixSupported();

    /**
     * 通过允许重试，将前缀应用到名称，此方法修改本机方法解析的失败处理
     */
    void setNativeMethodPrefix(ClassFileTransformer transformer, String prefix);

    void redefineModule(Module module,
                        Set<Module> extraReads,
                        Map<String, Set<Module>> extraExports,
                        Map<String, Set<Module>> extraOpens,
                        Set<Class<?>> extraUses,
                        Map<Class<?>, List<Class<?>>> extraProvides);

    boolean isModifiableModule(Module module);
}
