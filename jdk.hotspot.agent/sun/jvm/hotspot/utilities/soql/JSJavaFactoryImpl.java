/*
 * Copyright (c) 2004, 2020, Oracle and/or its affiliates. All rights reserved.
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

package sun.jvm.hotspot.utilities.soql;

import java.lang.ref.*;
import java.util.*;
import sun.jvm.hotspot.oops.*;
import sun.jvm.hotspot.runtime.*;
import sun.jvm.hotspot.utilities.*;

public class JSJavaFactoryImpl implements JSJavaFactory {
   public JSJavaObject newJSJavaObject(Oop oop) {
      if (oop == null) return null;
      SoftReference sref = (SoftReference) om.get(oop);
      JSJavaObject res = (sref != null)? (JSJavaObject) sref.get() : null;
      if (res == null) {
         if (oop instanceof TypeArray) {
            res = new JSJavaTypeArray((TypeArray)oop, this);
         } else if (oop instanceof ObjArray) {
             res = new JSJavaObjArray((ObjArray)oop, this);
         } else if (oop instanceof Instance) {
            res = newJavaInstance((Instance) oop);
         }
      }
      if (res != null) {
         om.put(oop, new SoftReference(res));
      }
      return res;
   }

   public JSJavaKlass newJSJavaKlass(Klass klass) {
      JSJavaKlass res = null;
      if (klass instanceof InstanceKlass) {
          res = new JSJavaInstanceKlass((InstanceKlass) klass, this);
      } else if (klass instanceof ObjArrayKlass) {
          res = new JSJavaObjArrayKlass((ObjArrayKlass) klass, this);
      } else if (klass instanceof TypeArrayKlass) {
          res = new JSJavaTypeArrayKlass((TypeArrayKlass) klass, this);
      }
      if (res != null) {
         om.put(klass, new SoftReference(res));
      }
      return res;
   }

   public JSJavaMethod newJSJavaMethod(Method method) {
      JSJavaMethod res = new JSJavaMethod(method, this);
      if (res != null) {
         om.put(method, new SoftReference(res));
      }
      return res;
   }

   public JSJavaField newJSJavaField(Field field) {
      if (field == null) return null;
      return new JSJavaField(field, this);
   }

   public JSJavaThread newJSJavaThread(JavaThread jthread) {
      if (jthread == null) return null;
      return new JSJavaThread(jthread, this);
   }

   public JSJavaFrame newJSJavaFrame(JavaVFrame jvf) {
      if (jvf == null) return null;
      return new JSJavaFrame(jvf, this);
   }

   public JSList newJSList(List list) {
      if (list == null) return null;
      return new JSList(list, this);
   }

   public JSMap newJSMap(Map map) {
      if (map == null) return null;
      return new JSMap(map, this);
   }

   public Object newJSJavaWrapper(Object item) {
      if (item == null) return null;
      if (item instanceof Oop) {
         return newJSJavaObject((Oop) item);
      } else if (item instanceof Field) {
         return newJSJavaField((Field) item);
      } else if (item instanceof JavaThread) {
         return newJSJavaThread((JavaThread) item);
      } else if (item instanceof JavaVFrame) {
         return newJSJavaFrame((JavaVFrame) item);
      } else if (item instanceof List) {
         return newJSList((List) item);
      } else if (item instanceof Map) {
         return newJSMap((Map) item);
      } else {
         // not-a-special-type, just return the input item
         return item;
      }
   }

   public JSJavaHeap newJSJavaHeap() {
      return new JSJavaHeap(this);
   }

   public JSJavaVM newJSJavaVM() {
      return new JSJavaVM(this);
   }

   // -- Internals only below this point
   private String javaLangString() {
      if (javaLangString == null) {
         javaLangString = "java/lang/String";
      }
      return javaLangString;
   }

   private String javaLangThread() {
      if (javaLangThread == null) {
         javaLangThread = "java/lang/Thread";
      }
      return javaLangThread;
   }

   private String javaLangClass() {
      if (javaLangClass == null) {
         javaLangClass = "java/lang/Class";
      }
      return javaLangClass;
   }

   private JSJavaObject newJavaInstance(Instance instance) {
      // look for well-known classes
      Symbol className = instance.getKlass().getName();
      if (Assert.ASSERTS_ENABLED) {
         Assert.that(className != null, "Null class name");
      }
      JSJavaObject res = null;
      if (className.equals(javaLangString())) {
         res = new JSJavaString(instance, this);
      } else if (className.equals(javaLangThread())) {
         res = new JSJavaThread(instance, this);
      } else if (className.equals(javaLangClass())) {
         Klass reflectedType = java_lang_Class.asKlass(instance);
         if (reflectedType != null) {
             JSJavaKlass jk = newJSJavaKlass(reflectedType);
             // we don't support mirrors of VM internal Klasses
             if (jk == null) return null;
             res = new JSJavaClass(instance, jk, this);
         } else {
             // for primitive Classes, the reflected type is null
             return null;
         }
      } else {
         // not a well-known class. But the base class may be
         // one of the known classes.
         Klass kls = instance.getKlass().getSuper();
         while (kls != null) {
            className = kls.getName();
            // java.lang.Class and java.lang.String are final classes
            if (className.equals(javaLangThread())) {
               res = new JSJavaThread(instance, this);
               break;
            }
            kls = kls.getSuper();
         }
      }
      if (res == null) {
         res = new JSJavaInstance(instance, this);
      }
      return res;
   }

   // Map<Oop, SoftReference<JSJavaObject>>
   private Map om = new HashMap();
   private String javaLangString;
   private String javaLangThread;
   private String javaLangClass;
}
