// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods.methodparameters;

public interface I {

  default void zeroArgsDefault() {
    System.out.println(new Object() {}.getClass().getEnclosingMethod().getParameters().length);
  }

  default void oneArgDefault(int a) {
    System.out.println(new Object() {}.getClass().getEnclosingMethod().getParameters().length);
  }

  default void twoArgDefault(int a, int b) {
    System.out.println(new Object() {}.getClass().getEnclosingMethod().getParameters().length);
  }

  static void zeroArgStatic() {
    System.out.println(new Object() {}.getClass().getEnclosingMethod().getParameters().length);
  }

  static void oneArgStatic(int a) {
    System.out.println(new Object() {}.getClass().getEnclosingMethod().getParameters().length);
  }

  static void twoArgsStatic(int a, int b) {
    System.out.println(new Object() {}.getClass().getEnclosingMethod().getParameters().length);
  }
}
