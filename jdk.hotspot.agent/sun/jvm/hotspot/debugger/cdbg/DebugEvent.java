/*
 * Copyright (c) 2001, Oracle and/or its affiliates. All rights reserved.
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
 */

package sun.jvm.hotspot.debugger.cdbg;

import sun.jvm.hotspot.debugger.*;

/** Describes in an abstract sense the kind of debug events which may
    be received from the target process. On UNIX platforms these are
    typically signals intercepted via ptrace or some other mechanism,
    while on Windows they are usually exceptions. Also describes
    certain types of events like loading and unloading of DSOs/DLLs
    ("LoadObjects"). */

public interface DebugEvent {
  public static class Type {
    private Type() {}
    /** Indicates a DSO/DLL was loaded by the target process */
    public static final Type LOADOBJECT_LOAD   = new Type();
    /** Indicates a DSO/DLL was unloaded by the target process */
    public static final Type LOADOBJECT_UNLOAD = new Type();
    /** Indicates a breakpoint was hit */
    public static final Type BREAKPOINT        = new Type();
    /** Indicates a single machine instruction was stepped */
    public static final Type SINGLE_STEP       = new Type();
    /** Indicates an unmapped memory address was read from or written
        to by the target process */
    public static final Type ACCESS_VIOLATION  = new Type();
    /** Indicates an event of an unknown type occurred in the target
        process (catch-all for implementations; but add more event
        types) */
    public static final Type UNKNOWN           = new Type();
  }

  /** The type of this debug event; BREAKPOINT, SINGLE_STEP, etc. */
  public Type getType();

  /** Retrieves the ThreadProxy for the thread on which the event
      occurred. This is always present. */
  public ThreadProxy getThread();

  /** For BREAKPOINT, SINGLE_STEP, and ACCESS_VIOLATION events,
      returns the program counter at which the event occurred.  For
      other types of events returns an undefined value. */
  public Address getPC();

  /** For ACCESS_VIOLATION events, indicates whether the fault
      occurred on a write (vs. a read). For other types of events
      returns an undefined value. */
  public boolean getWasWrite();

  /** For ACCESS_VIOLATION events, returns the address at which the
      fault occurred. For LOADOBJECT_LOAD and LOADOBJECT_UNLOAD
      events, returns the base address of the loadobject in the target
      process's address space. For other types of events returns an
      undefined value. */
  public Address getAddress();

  /** For UNKNOWN events, may return a detail message or may return
      null. For other types of events returns an undefined value. */
  public String getUnknownEventDetail();
}
