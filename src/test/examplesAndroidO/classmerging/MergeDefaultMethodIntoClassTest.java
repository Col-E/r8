// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class MergeDefaultMethodIntoClassTest {

  public static void main(String[] args) {
    // Note: Important that the static type of [obj] is A, such that the call to f becomes an
    // invoke-interface instruction and not invoke-virtual instruction.
    A obj = new B();
    obj.f();
  }

  public interface A {
    default void f() {
      System.out.println("In A.f");
    }
  }

  public static class B implements A {}
}
