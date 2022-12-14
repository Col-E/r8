// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace.testclasses;

public class DesugarInterfaceInstanceLambdaRetrace {

  public interface ConsumerDesugarLambda {
    String accept();

    default void foo() {
      Main.method1(
          () -> {
            if (System.currentTimeMillis() > 0) {
              throw null;
            }
            return accept();
          });
    }
  }

  public static class Main {

    public static void method1(ConsumerDesugarLambda iface) {
      System.out.println(iface.accept());
    }

    public static void main(String[] args) {
      ((ConsumerDesugarLambda) () -> "Hello World").foo();
    }
  }
}
