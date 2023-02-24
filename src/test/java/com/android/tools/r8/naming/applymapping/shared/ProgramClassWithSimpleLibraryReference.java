// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.applymapping.shared;

import com.android.tools.r8.naming.applymapping.shared.InnerLibraryClass.LibraryClass;

public class ProgramClassWithSimpleLibraryReference {

  public static class SubLibraryClass extends LibraryClass {

    @Override
    public void foo() {
      System.out.println("SubLibraryClass.foo()");
      super.foo();
    }
  }

  public static void main(String[] args) {
    new SubLibraryClass().foo();
  }
}
