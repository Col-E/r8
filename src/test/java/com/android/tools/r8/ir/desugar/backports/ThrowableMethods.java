// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.lang.reflect.Method;

public final class ThrowableMethods {

  public static void addSuppressed(Throwable receiver, Throwable suppressed) {
    try {
      Method method = Throwable.class.getDeclaredMethod("addSuppressed", Throwable.class);
      method.invoke(receiver, suppressed);
    } catch (Exception e) {
      // Don't add anything when not natively supported.
    }
  }

  public static Throwable[] getSuppressed(Throwable receiver) {
    try {
      Method method = Throwable.class.getDeclaredMethod("getSuppressed");
      return (Throwable[]) method.invoke(receiver);
    } catch (Exception e) {
      // Don't return any when not natively supported.
      return new Throwable[0];
    }
  }
}
