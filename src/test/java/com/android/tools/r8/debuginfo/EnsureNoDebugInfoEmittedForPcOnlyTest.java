// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

public class EnsureNoDebugInfoEmittedForPcOnlyTest {

  public static void a() {
    if (System.currentTimeMillis() > 0) {
      throw new RuntimeException("Hello World!");
    }
    System.out.println("Foo");
    System.out.println("Bar");
  }

  public static void b() {
    a();
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      b();
    } else {
      other();
    }
  }

  public static void other() {
    System.out.println("FOO");
  }
}
