// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class ConflictingInterfaceSignaturesTest {

  public static void main(String[] args) {
    A a = new InterfaceImpl();
    a.foo();

    B b = new InterfaceImpl();
    b.foo();
  }

  public interface A {
    void foo();
  }

  public interface B {
    void foo();
  }

  public static final class InterfaceImpl implements A, B {

    @Override
    public void foo() {
      System.out.println("In foo on InterfaceImpl");
    }
  }
}
