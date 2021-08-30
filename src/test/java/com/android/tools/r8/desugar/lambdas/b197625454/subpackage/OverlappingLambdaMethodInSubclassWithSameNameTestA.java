// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.lambdas.b197625454.subpackage;

import com.android.tools.r8.desugar.lambdas.b197625454.OverlappingLambdaMethodInSubclassWithSameNameTestB;

public abstract class OverlappingLambdaMethodInSubclassWithSameNameTestA {

  public Runnable r;

  public void myMethod() {
    r = () -> System.out.println("Superclass lambda: " + getString());
  }

  public abstract String getString();

  public static void main(String[] args) {
    new com.android.tools.r8.desugar.lambdas.b197625454
            .OverlappingLambdaMethodInSubclassWithSameNameTestA()
        .myMethod();
    new OverlappingLambdaMethodInSubclassWithSameNameTestB().myMethod();
  }
}
