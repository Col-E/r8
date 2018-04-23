// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

public class InstantiatedLambdasTest {

  public static final Class[] CLASSES = {
    InstantiatedLambdasTest.class,
    Interface.class,
    ClassImplementation.class,
  };

  interface Interface {
    String method();
  }

  static class ClassImplementation implements Interface {
    @Override
    public String method() {
      return "Class implementation";
    }
  }

  public static void main(String[] args) {
    invoke(getClassImplementation());
    invoke(getLambdaImplementation());
  }

  private static Interface getClassImplementation() {
    return new ClassImplementation();
  }

  private static Interface getLambdaImplementation() {
    return () -> "Lambda implementation";
  }

  private static void invoke(Interface instance) {
    System.out.println(instance.method());
  }
}
