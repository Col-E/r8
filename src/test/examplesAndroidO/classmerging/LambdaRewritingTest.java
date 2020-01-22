// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package classmerging;

public class LambdaRewritingTest {

  public static void main(String[] args) {
    Interface obj = new InterfaceImpl();

    // Leads to an invoke-custom instruction that mentions the type of `obj` since it is captured.
    invoke(() -> obj.foo());

    FunctionImpl functionImpl = new FunctionImpl();
    if (System.currentTimeMillis() < 0) {
      System.out.println(functionImpl);
    }
  }

  @NeverInline
  private static void invoke(Function f) {
    f.accept();
  }

  // Cannot be merged as it has two subtypes: FunctionImpl and a lambda.
  public interface Function {

    void accept();
  }

  public static class FunctionImpl implements Function {

    @Override
    public void accept() {
      System.out.println("In FunctionImpl.accept()");
    }
  }

  // Will be merged into InterfaceImpl.
  public interface Interface {

    void foo();
  }

  public static class InterfaceImpl implements Interface {

    @Override
    public void foo() {
      System.out.println("In InterfaceImpl.foo()");
    }
  }
}
