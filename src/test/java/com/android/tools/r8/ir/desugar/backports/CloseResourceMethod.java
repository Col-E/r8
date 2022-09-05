// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.desugar.backports;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class CloseResourceMethod {

  // The following method defines the code of
  //
  //     public static void $closeResource(Throwable throwable, Object resource)
  //
  // method to be inserted into utility class, and will be used instead
  // of the following implementation added by java 9 compiler:
  //
  //     private static void $closeResource(Throwable, AutoCloseable);
  //        0: aload_0
  //        1: ifnull        22
  //        4: aload_1
  //        5: invokeinterface #1,  1  // java/lang/AutoCloseable.close:()V
  //       10: goto          28
  //       13: astore_2
  //       14: aload_0
  //       15: aload_2
  //       16: invokevirtual #3 // java/lang/Throwable.addSuppressed:(Ljava/lang/Throwable;)V
  //       19: goto          28
  //       22: aload_1
  //       23: invokeinterface #1,  1  // java/lang/AutoCloseable.close:()V
  //       28: return
  //
  public static void closeResourceImpl(Throwable throwable, Object resource) throws Throwable {
    try {
      if (resource instanceof AutoCloseable) {
        ((AutoCloseable) resource).close();
      } else {
        try {
          Method method = resource.getClass().getMethod("close");
          method.invoke(resource);
        } catch (NoSuchMethodException | SecurityException e) {
          throw new RuntimeException(resource.getClass() + " does not have a close() method.", e);
        } catch (IllegalAccessException
            | IllegalArgumentException
            | ExceptionInInitializerError e) {
          throw new RuntimeException("Fail to call close() on " + resource.getClass(), e);
        } catch (InvocationTargetException e) {
          throw e.getCause();
        }
      }
    } catch (Throwable e) {
      if (throwable != null) {
        // TODO(b/168568827): Directly call Throwable.addSuppressed once fixed.
        try {
          Method method = Throwable.class.getDeclaredMethod("addSuppressed", Throwable.class);
          method.invoke(throwable, e);
        } catch (Exception ignore) {
          // Don't add anything when not natively supported.
        }
        throw throwable;
      } else {
        throw e;
      }
    }
  }
}
