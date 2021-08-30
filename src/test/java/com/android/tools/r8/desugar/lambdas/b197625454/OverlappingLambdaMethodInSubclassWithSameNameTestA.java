// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas.b197625454;

// Same base class name as the class it extends. See b/197625454 for details.
public class OverlappingLambdaMethodInSubclassWithSameNameTestA
    extends com.android.tools.r8.desugar.lambdas.b197625454.subpackage
        .OverlappingLambdaMethodInSubclassWithSameNameTestA {

  @Override
  public void myMethod() {
    super.myMethod();
    if (Math.random() < 0) { // always false
      Runnable local = () -> System.out.println("Subclass lambda: " + getString());
    }
    r.run();
  }

  @Override
  public String getString() {
    return "Hello!";
  }
}
