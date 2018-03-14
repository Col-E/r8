// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package dexsplitsample;

public class Class4 {
  public static void main(String[] args) {
    new Class4().createLambda();
    System.out.println("Class4");
  }

  private void useLambda(LambdaInterface toInvokeOn) {
    toInvokeOn.foo(42);
  }

  private void createLambda() {
    useLambda((a) -> { return a + 2;});
  }

  interface LambdaInterface {
    int foo(int a);
  }
}
