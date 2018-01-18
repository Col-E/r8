// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.forceproguardcompatibility.defaultmethods;

public class TestClass {

  public void useInterfaceMethod() {
    InterfaceWithDefaultMethods iface = new ClassImplementingInterface();
    System.out.println(iface.method());
  }

  public void useInterfaceMethod2() {
    InterfaceWithDefaultMethods iface = new ClassImplementingInterface();
    iface.method2("a", 1);
  }

  public static void main(String[] args) {
  }
}
