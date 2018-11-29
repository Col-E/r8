// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b118075510;

public class Regress118075510Test {

  public static void fooNoTryCatch(long a, long b) {
    // Call a method on the runner class that will not be on the classpath at runtime.
    // This causes the optimizing 6.0.1 compiler to delegate to the old quick compiler.
    if (a == b) Regress118075510Runner.class.getMethods();
    // The else branch of the conditional here ends up with an invalid previous pointer at its
    // block header (ie, previous of mul-long (2addr) points to epilogue-end which is self-linked.
    System.out.println(a < b ? 0 : a * b + b);
  }

  public static void fooWithTryCatch(long a, long b) {
    // Call a method on the runner class that will not be on the classpath at runtime.
    // This causes the optimizing 6.0.1 compiler to delegate to the old quick compiler.
    if (a == b) Regress118075510Runner.class.getMethods();
    // The else branch of the conditional here ends up with an invalid previous pointer at its
    // block header (ie, previous of mul-long (2addr) points to epilogue-end which is self-linked.
    try {
      if (a < b) {
        System.out.println((long) 0);
      } else {
        System.out.println(a * b + b);
      }
    } catch (RuntimeException e) {
      e.printStackTrace();
    }
  }

  public static void main(String[] args) {
    fooNoTryCatch(args.length, 456);
    fooWithTryCatch(456, args.length);
  }
}
