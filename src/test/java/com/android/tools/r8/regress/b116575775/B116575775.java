// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.regress.b116575775;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;

public class B116575775 extends TestBase {

  @Test
  public void test() throws Exception {
    CodeInspector inspector =
        new CodeInspector(
            compileWithR8(
                readClasses(B116575775Test.class),
                keepMainProguardConfiguration(B116575775Test.class)));
    // Ensure toBeInlined is inlined, and only main remains.
    inspector
        .clazz(B116575775Test.class)
        .forAllMethods(m -> assertEquals(m.getOriginalName(), "main"));
  }
}

class B116575775Test {

  public static void toBeInlined() throws ClassCastException {
    try {
      new Object();
    } catch (IllegalArgumentException | ClassCastException e) {
      System.out.println(e);
    }
  }

  public static void main(String[] args) {
    try {
      toBeInlined();
    } catch (IllegalArgumentException | ClassCastException e) {
      System.out.println(e);
    }
  }
}
