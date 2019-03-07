// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping.shared;

public class ProgramWithLibraryClasses {

  public static class LibraryClass {

    public static String LIB_MSG = "LibraryClass::foo";

    void foo() {
      System.out.println(LIB_MSG);
    }
  }

  public static class AnotherLibraryClass {

    public static String ANOTHERLIB_MSG = "AnotherLibraryClass::foo";

    void foo() {
      System.out.println(ANOTHERLIB_MSG);
    }
  }

  public static class ProgramClass extends LibraryClass {

    public static String PRG_MSG = "ProgramClass::bar";

    void bar() {
      System.out.println(PRG_MSG);
    }

    public static void main(String[] args) {
      new AnotherLibraryClass().foo();
      ProgramClass instance = new ProgramClass();
      instance.foo();
      instance.bar();
    }
  }
}
