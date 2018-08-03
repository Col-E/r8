// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package adaptresourcefilenames;

public class B extends A {

  private Inner inner = new Inner();

  public static class Inner {

    public void method() {
      System.out.println("In Inner.method()");
    }
  }

  public void method() {
    System.out.println("In B.method()");
    super.method();
    inner.method();
  }
}
