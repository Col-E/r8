// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

public class DefaultLambdaWithUnderscoreThisTest {

  public interface I {
    String foo();

    default I stateful() {
      String _this = "My _this variable";
      return () -> {
        String ___this = "Another ___this variable";
        return "stateful(" + _this + " " + foo() + " " + ___this + ")";
      };
    }
  }

  public static void main(String[] args) {
    I i = () -> "foo";
    I stateful = i.stateful();
    String foo = stateful.foo();
    System.out.println(foo);
  }
}
