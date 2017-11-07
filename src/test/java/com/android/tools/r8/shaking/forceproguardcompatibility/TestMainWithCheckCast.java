// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.forceproguardcompatibility;

public class TestMainWithCheckCast {

  public static void main(String[] args) throws Exception {
    String thisPackage = TestMainWithCheckCast.class.getPackage().getName();
    Object o = (TestClassWithDefaultConstructor)
        Class.forName(thisPackage + ".TestClassWithDefaultConstructor").newInstance();
    System.out.println("Instance of: " + o.getClass().getCanonicalName());
  }
}
