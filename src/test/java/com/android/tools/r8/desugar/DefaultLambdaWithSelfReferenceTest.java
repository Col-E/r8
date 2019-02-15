// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar;

public class DefaultLambdaWithSelfReferenceTest {

  interface I {
    String foo();

    default I stateless() {
      return () -> "stateless";
    }

    default I stateful() {
      return () -> {
        I stateless = stateless();
        String foo = stateless.foo();
        return "stateful(" + foo + ")";
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
