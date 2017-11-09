// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.keepclassmembers;

public class PureStaticClassWithoutDefaultConstructor {

  public static int a;
  public static int b;
  public static int c;

  public int i;
  public int j;

  private PureStaticClassWithoutDefaultConstructor(int i) {
    a = i;
  }

  public static int getA() {
    return a;
  }

  public static void setA(int value) {
    a = value;
  }

  public static int getB() {
    return b;
  }

  public static void setB(int value) {
    b = value;
  }

  public int getI() {
    return i;
  }

  public void setI(int value) {
    i = value;
  }

  public int getJ() {
    return j;
  }

  public void setJ(int value) {
    j = value;
  }
}
