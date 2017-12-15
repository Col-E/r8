// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.invokesuper;

import java.util.function.Consumer;

public class MainClassFailing {

  private static void tryInvoke(Consumer<InvokerClass> function) {
    InvokerClass invoker = new InvokerClass();
    try {
      function.accept(invoker);
    } catch (Throwable e) {
      System.out.println(e.getClass().getCanonicalName());
    }
  }

  public static void main(String... args) {
    tryInvoke(InvokerClass::invokeSubLevel2MethodOnSubClassOfInvokerClass);
  }
}
