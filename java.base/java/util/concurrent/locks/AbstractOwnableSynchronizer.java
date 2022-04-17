/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */

package java.util.concurrent.locks;

public abstract class AbstractOwnableSynchronizer implements java.io.Serializable {

    private static final long serialVersionUID = 3737899427754241961L;

    /** 记录持有锁的线程 */
    private transient Thread exclusiveOwnerThread;

    protected AbstractOwnableSynchronizer() {
    }

    protected final void setExclusiveOwnerThread(Thread thread) {
        exclusiveOwnerThread = thread;
    }

    protected final Thread getExclusiveOwnerThread() {
        return exclusiveOwnerThread;
    }
}
