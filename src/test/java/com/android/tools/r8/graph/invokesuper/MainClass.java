// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.invokesuper;

public class MainClass {

  private static void tryInvoke(Consumer<InvokerClass> function) {
    InvokerClass invoker = new InvokerClass();
    try {
      function.accept(invoker);
    } catch (Throwable e) {
      System.out.println(e.getClass().getCanonicalName());
    }
  }

  public static void main(String... args) {
    tryInvoke(InvokerClass::invokeSuperMethodOnSuper);
    tryInvoke(InvokerClass::invokeSuperMethodOnSubLevel1);
    tryInvoke(InvokerClass::invokeSuperMethodOnSubLevel2);
    tryInvoke(InvokerClass::invokeSubLevel1MethodOnSuper);
    tryInvoke(InvokerClass::invokeSubLevel1MethodOnSubLevel1);
    tryInvoke(InvokerClass::invokeSubLevel1MethodOnSubLevel2);
    tryInvoke(InvokerClass::invokeSubLevel2MethodOnSubLevel2);
    tryInvoke(InvokerClass::callOtherSuperMethodIndirect);
  }
}
