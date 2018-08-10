// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.AndroidApp;
import org.junit.Test;

public class B112290098 extends TestBase {

  @Test
  public void test() throws Exception {
    String mainClass = TestClass.class.getName();
    AndroidApp input = readClasses(TestClass.class, C.class);
    AndroidApp output =
        compileWithR8(
            input,
            String.join(
                System.lineSeparator(),
                "-keep public class " + mainClass + " {",
                "  public static void main(...);",
                "}"));
    assertEquals(runOnArt(compileWithD8(input), mainClass), runOnArt(output, mainClass));
  }

  public static class TestClass {

    public static void main(String[] args) {
      // Instantiation that will be removed as a result of class inlining.
      new C();

      C obj = null;
      try {
        // After inlining this will lead to an iget instruction.
        obj.getField();
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException");
      }
    }
  }

  public static class C {

    // In the second round of tree shaking, C is no longer instantiated, but we should still
    // keep this field to avoid a NoSuchFieldError instead of a NullPointerException at the
    // obj.getField() invocation.
    public int field = 42;

    public int getField() {
      return field;
    }
  }
}
