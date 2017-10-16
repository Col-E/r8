// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package adaptclassstrings;

public class A {
  protected static final String ITSELF = "Ladaptclassstrings/A;";
  protected static final String OTHER = "adaptclassstrings.C";

  protected int f;

  A(int f) {
    this.f = f;
  }

  protected int foo() {
    return f * 9;
  }

  void bar() {
    System.out.println("adaptclassstrings.C");
    System.out.println("adaptclassstrings.D");
  }
}
