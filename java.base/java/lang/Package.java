/*
 * Copyright (c) 1997, 2015, Oracle and/or its affiliates. All rights reserved.
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
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

package java.lang;

import java.lang.annotation.Annotation;
import java.lang.reflect.AnnotatedElement;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Objects;

import jdk.internal.loader.BootLoader;
import jdk.internal.reflect.CallerSensitive;
import jdk.internal.reflect.Reflection;

/**
 *
 * @date 2023/7/7 13:59
 */
public class Package extends NamedPackage implements java.lang.reflect.AnnotatedElement {
    public String getName() {
        return packageName();
    }

    public String getSpecificationTitle() {
        return versionInfo.specTitle;
    }

    public String getSpecificationVersion() {
        return versionInfo.specVersion;
    }

    public String getSpecificationVendor() {
        return versionInfo.specVendor;
    }

    public String getImplementationTitle() {
        return versionInfo.implTitle;
    }

    public String getImplementationVersion() {
        return versionInfo.implVersion;
    }

    public String getImplementationVendor() {
        return versionInfo.implVendor;
    }

    public boolean isSealed() {
        return module().isNamed() || versionInfo.sealBase != null;
    }

    public boolean isSealed(URL url) {
        Objects.requireNonNull(url);

        URL sealBase = null;
        if (versionInfo != VersionInfo.NULL_VERSION_INFO) {
            sealBase = versionInfo.sealBase;
        } else {
            try {
                URI uri = location();
                sealBase = uri != null ? uri.toURL() : null;
            } catch (MalformedURLException e) {
            }
        }
        return url.equals(sealBase);
    }

    public boolean isCompatibleWith(String desired)
            throws NumberFormatException {
        if (versionInfo.specVersion == null || versionInfo.specVersion.length() < 1) {
            throw new NumberFormatException("Empty version string");
        }

        String[] sa = versionInfo.specVersion.split("\\.", -1);
        int[] si = new int[sa.length];
        for (int i = 0; i < sa.length; i++) {
            si[i] = Integer.parseInt(sa[i]);
            if (si[i] < 0)
                throw NumberFormatException.forInputString("" + si[i]);
        }

        String[] da = desired.split("\\.", -1);
        int[] di = new int[da.length];
        for (int i = 0; i < da.length; i++) {
            di[i] = Integer.parseInt(da[i]);
            if (di[i] < 0)
                throw NumberFormatException.forInputString("" + di[i]);
        }

        int len = Math.max(di.length, si.length);
        for (int i = 0; i < len; i++) {
            int d = (i < di.length ? di[i] : 0);
            int s = (i < si.length ? si[i] : 0);
            if (s < d)
                return false;
            if (s > d)
                return true;
        }
        return true;
    }

    @CallerSensitive
    @Deprecated(since = "9")
    @SuppressWarnings("deprecation")
    public static Package getPackage(String name) {
        ClassLoader l = ClassLoader.getClassLoader(Reflection.getCallerClass());
        return l != null ? l.getPackage(name) : BootLoader.getDefinedPackage(name);
    }

    @CallerSensitive
    public static Package[] getPackages() {
        ClassLoader cl = ClassLoader.getClassLoader(Reflection.getCallerClass());
        return cl != null ? cl.getPackages() : BootLoader.packages().toArray(Package[]::new);
    }

    @Override
    public int hashCode() {
        return packageName().hashCode();
    }

    @Override
    public String toString() {
        String spec = versionInfo.specTitle;
        String ver = versionInfo.specVersion;
        if (spec != null && !spec.isEmpty())
            spec = ", " + spec;
        else
            spec = "";
        if (ver != null && !ver.isEmpty())
            ver = ", version " + ver;
        else
            ver = "";
        return "package " + packageName() + spec + ver;
    }

    private Class<?> getPackageInfo() {
        if (packageInfo == null) {
            // find package-info.class defined by loader
            String cn = packageName() + ".package-info";
            Module module = module();
            PrivilegedAction<ClassLoader> pa = module::getClassLoader;
            ClassLoader loader = AccessController.doPrivileged(pa);
            Class<?> c;
            if (loader != null) {
                c = loader.loadClass(module, cn);
            } else {
                c = BootLoader.loadClass(module, cn);
            }

            if (c != null) {
                packageInfo = c;
            } else {
                // store a proxy for the package info that has no annotations
                class PackageInfoProxy {
                }
                packageInfo = PackageInfoProxy.class;
            }
        }
        return packageInfo;
    }

    public <A extends Annotation> A getAnnotation(Class<A> annotationClass) {
        return getPackageInfo().getAnnotation(annotationClass);
    }

    @Override
    public boolean isAnnotationPresent(Class<? extends Annotation> annotationClass) {
        return AnnotatedElement.super.isAnnotationPresent(annotationClass);
    }

    @Override
    public <A extends Annotation> A[] getAnnotationsByType(Class<A> annotationClass) {
        return getPackageInfo().getAnnotationsByType(annotationClass);
    }

    /**
     * @since 1.5
     */
    public Annotation[] getAnnotations() {
        return getPackageInfo().getAnnotations();
    }

    @Override
    public <A extends Annotation> A getDeclaredAnnotation(Class<A> annotationClass) {
        return getPackageInfo().getDeclaredAnnotation(annotationClass);
    }

    @Override
    public <A extends Annotation> A[] getDeclaredAnnotationsByType(Class<A> annotationClass) {
        return getPackageInfo().getDeclaredAnnotationsByType(annotationClass);
    }

    public Annotation[] getDeclaredAnnotations() {
        return getPackageInfo().getDeclaredAnnotations();
    }

    Package(String name,
            String spectitle, String specversion, String specvendor,
            String impltitle, String implversion, String implvendor,
            URL sealbase, ClassLoader loader) {
        super(Objects.requireNonNull(name),
                loader != null ? loader.getUnnamedModule()
                        : BootLoader.getUnnamedModule());

        this.versionInfo = VersionInfo.getInstance(spectitle, specversion,
                specvendor, impltitle,
                implversion, implvendor,
                sealbase);
    }

    Package(String name, Module module) {
        super(name, module);
        this.versionInfo = VersionInfo.NULL_VERSION_INFO;
    }

    static class VersionInfo {
        static final VersionInfo NULL_VERSION_INFO
                = new VersionInfo(null, null, null, null, null, null, null);

        private final String specTitle;
        private final String specVersion;
        private final String specVendor;
        private final String implTitle;
        private final String implVersion;
        private final String implVendor;
        private final URL sealBase;

        static VersionInfo getInstance(String spectitle, String specversion,
                                       String specvendor, String impltitle,
                                       String implversion, String implvendor,
                                       URL sealbase) {
            if (spectitle == null && specversion == null &&
                    specvendor == null && impltitle == null &&
                    implversion == null && implvendor == null &&
                    sealbase == null) {
                return NULL_VERSION_INFO;
            }
            return new VersionInfo(spectitle, specversion, specvendor,
                    impltitle, implversion, implvendor,
                    sealbase);
        }

        private VersionInfo(String spectitle, String specversion,
                            String specvendor, String impltitle,
                            String implversion, String implvendor,
                            URL sealbase) {
            this.implTitle = impltitle;
            this.implVersion = implversion;
            this.implVendor = implvendor;
            this.specTitle = spectitle;
            this.specVersion = specversion;
            this.specVendor = specvendor;
            this.sealBase = sealbase;
        }
    }

    private final VersionInfo versionInfo;
    private Class<?> packageInfo;
}
