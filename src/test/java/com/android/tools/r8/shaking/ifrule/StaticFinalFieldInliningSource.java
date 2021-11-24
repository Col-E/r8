// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

public class StaticFinalFieldInliningSource {

  public static final Object unusedField = "Unused Field";

  public static final Object nullObject = null;
  public static final Object constObject = "const";
  public static final String nullString = null;
  public static final String constString = "const";
  public static final int nonConstInt = System.nanoTime() < 0 ? 42 : 21;
  public static final int constInt = 42;
  public static final double constDouble = 42.5;

  public static Object getNullObject() {
    return nullObject;
  }

  public static Object getConstObject() {
    return constObject;
  }

  public static String getNullString() {
    return nullString;
  }

  public static String getConstString() {
    return constString;
  }

  public static int getNonConstInt() {
    return nonConstInt;
  }

  public static int getConstInt() {
    return constInt;
  }

  public static double getConstDouble() {
    return constDouble;
  }

  public static void main(String[] args) {
    System.out.println(getNullObject());
    System.out.println(getConstObject());
    System.out.println(getNullString());
    System.out.println(getConstString());
    System.out.println(getNonConstInt());
    System.out.println(getConstInt());
    System.out.println(getConstDouble());
  }
}
