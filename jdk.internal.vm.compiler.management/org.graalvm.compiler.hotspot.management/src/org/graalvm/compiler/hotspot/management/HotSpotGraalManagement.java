/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
 */


package org.graalvm.compiler.hotspot.management;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;

import javax.management.InstanceAlreadyExistsException;
import javax.management.MBeanRegistrationException;
import javax.management.MBeanServer;
import javax.management.MBeanServerFactory;
import javax.management.MalformedObjectNameException;
import javax.management.NotCompliantMBeanException;
import javax.management.ObjectName;

import org.graalvm.compiler.debug.TTY;
import org.graalvm.compiler.hotspot.HotSpotGraalManagementRegistration;
import org.graalvm.compiler.hotspot.HotSpotGraalRuntime;
import org.graalvm.compiler.serviceprovider.ServiceProvider;

/**
 * Dynamically registers an MBean with the {@link ManagementFactory#getPlatformMBeanServer()}.
 *
 * Polling for an active platform MBean server is done by calling
 * {@link MBeanServerFactory#findMBeanServer(String)} with an argument value of {@code null}. Once
 * this returns an non-empty list, {@link ManagementFactory#getPlatformMBeanServer()} can be called
 * to obtain a reference to the platform MBean server instance.
 */
@ServiceProvider(HotSpotGraalManagementRegistration.class)
public final class HotSpotGraalManagement implements HotSpotGraalManagementRegistration {

    private HotSpotGraalRuntimeMBean bean;
    private volatile boolean needsRegistration = true;
    HotSpotGraalManagement nextDeferred;

    @Override
    public void initialize(HotSpotGraalRuntime runtime) {
        if (bean == null) {
            if (runtime.getManagement() != this) {
                throw new IllegalArgumentException("Cannot initialize a second management object for runtime " + runtime.getName());
            }
            try {
                String name = runtime.getName().replace(':', '_');
                ObjectName objectName = new ObjectName("org.graalvm.compiler.hotspot:type=" + name);
                bean = new HotSpotGraalRuntimeMBean(objectName, runtime);
                registration.add(this);
            } catch (MalformedObjectNameException err) {
                err.printStackTrace(TTY.out);
            }
        } else if (bean.getRuntime() != runtime) {
            throw new IllegalArgumentException("Cannot change the runtime a management interface is associated with");
        }
    }

    static final class RegistrationThread extends Thread {

        private MBeanServer platformMBeanServer;
        private HotSpotGraalManagement deferred;

        RegistrationThread() {
            super("HotSpotGraalManagement Bean Registration");
            this.setPriority(Thread.MIN_PRIORITY);
            this.setDaemon(true);
            this.start();
        }

        /**
         * Poll for active MBean server every 2 seconds.
         */
        private static final int POLL_INTERVAL_MS = 2000;

        /**
         * Adds a {@link HotSpotGraalManagement} to register with an active MBean server when one
         * becomes available.
         */
        synchronized void add(HotSpotGraalManagement e) {
            if (deferred != null) {
                e.nextDeferred = deferred;
            }
            deferred = e;

            // Notify the registration thread that there is now
            // a deferred registration to process
            notify();
        }

        /**
         * Processes and clears any deferred registrations.
         */
        private void process() {
            for (HotSpotGraalManagement m = deferred; m != null; m = m.nextDeferred) {
                HotSpotGraalRuntimeMBean bean = m.bean;
                if (m.needsRegistration && bean != null) {
                    try {
                        platformMBeanServer.registerMBean(bean, bean.getObjectName());
                    } catch (InstanceAlreadyExistsException | MBeanRegistrationException | NotCompliantMBeanException e) {
                        e.printStackTrace(TTY.out);
                        // Registration failed - don't try again
                        m.bean = null;
                    }
                    m.needsRegistration = false;
                }
            }
            deferred = null;
        }

        @Override
        public void run() {
            while (true) {
                try {
                    synchronized (this) {
                        // Wait until there are deferred registrations to process
                        while (deferred == null) {
                            wait();
                        }
                    }
                    poll();
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (InterruptedException e) {
                    // Be verbose about unexpected interruption and then continue
                    e.printStackTrace(TTY.out);
                }
            }
        }

        /**
         * Checks for active MBean server and if available, processes deferred registrations.
         */
        synchronized void poll() {
            if (platformMBeanServer == null) {
                try {
                    ArrayList<MBeanServer> servers = MBeanServerFactory.findMBeanServer(null);
                    if (!servers.isEmpty()) {
                        platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
                        process();
                    }
                } catch (SecurityException | UnsatisfiedLinkError | NoClassDefFoundError | UnsupportedOperationException e) {
                    // Without permission to find or create the MBeanServer,
                    // we cannot process any Graal mbeans.
                    // Various other errors can occur in the ManagementFactory (JDK-8076557)
                    deferred = null;
                }
            } else {
                process();
            }
        }
    }

    private static final RegistrationThread registration = new RegistrationThread();

    @Override
    public ObjectName poll(boolean sync) {
        if (sync) {
            registration.poll();
        }
        if (bean == null || needsRegistration) {
            // initialize() has not been called, it failed or registration failed
            return null;
        }
        return bean.getObjectName();
    }
}
