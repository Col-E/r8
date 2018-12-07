// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;
// NOTE: This file is just used to create the initial dump in InnerClassNameTestDump.
class OuterClass {
  static class InnerClass {}
}

public class InnerClassNameTest {

  public static void main(String[] args) {
    Class<?> test = OuterClass.InnerClass.class;
    System.out.println("getName: " + test.getName());
    System.out.println("getTypeName: " + test.getTypeName());
    System.out.println("getCanonicalName: " + test.getCanonicalName());
    System.out.println("getSimpleName: " + test.getSimpleName());
  }
}
