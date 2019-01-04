// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.keptgraph;

import com.android.tools.r8.Keep;

@Keep
public class KeptByTwoRulesTest {

  public static void foo() {
    System.out.println("called foo");
  }

  public static void main(String[] args) {
    foo();
  }
}
