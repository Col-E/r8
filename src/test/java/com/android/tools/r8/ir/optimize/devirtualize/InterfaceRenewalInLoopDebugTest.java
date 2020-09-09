// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.devirtualize;

import com.android.tools.r8.NoVerticalClassMerging;
import java.util.ArrayList;
import java.util.List;

interface TestInterface {
  void foo();
}

@NoVerticalClassMerging
class OneUniqueImplementer implements TestInterface {
  String boo;

  OneUniqueImplementer(String boo) {
    this.boo = boo;
  }

  @Override
  public void foo() {
    System.out.println("boo?! " + boo);
  }
}

class InterfaceRenewalInLoopDebugTest {

  static void booRunner(String[] boos) {
    List<TestInterface> l = new ArrayList<>();
    for (int i = 0; i < boos.length; i++) {
      l.add(new OneUniqueImplementer(boos[i]));
    }
    TestInterface local = new OneUniqueImplementer("Initial");
    for (int i = 0; i < l.size(); i++) {
      local.foo();

      local = l.get(i);
      local.foo();

      if (i > 0) {
        local = l.get(i-1);
      }
    }
  }

  public static void main(String[] args) {
    String[] boos = {"a", "b", "c"};
    booRunner(boos);
  }

}
