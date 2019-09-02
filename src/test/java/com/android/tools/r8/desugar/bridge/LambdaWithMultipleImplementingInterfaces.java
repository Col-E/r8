// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.bridge;

public class LambdaWithMultipleImplementingInterfaces {

  public interface I {
    Object get();
  }

  public interface J {
    String get();
  }

  public interface K extends I, J {}

  public static void main(String[] args) {
    K k = () -> "Hello World!";
    testI(k);
    testJ(k);
  }

  private static void testI(I i) {
    System.out.println(i.get());
  }

  private static void testJ(J j) {
    System.out.println(j.get());
  }
}
