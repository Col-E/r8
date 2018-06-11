// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class TemplateMethodTest {

  public static void main(String[] args) {
    AbstractClass obj = new AbstractClassImpl();
    obj.foo();
  }

  private abstract static class AbstractClass {

    public void foo() {
      System.out.println("In foo on AbstractClass");
      bar();
    }

    protected abstract void bar();
  }

  public static final class AbstractClassImpl extends AbstractClass {

    @Override
    public void bar() {
      System.out.println("In bar on AbstractClassImpl");
    }
  }
}
