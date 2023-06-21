// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.inliner;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import org.junit.Test;

class Foobar {
  public static void main(String[] args) {
    System.out.println("Value: " + (new Bar()).returnPlusConstant(42));
  }
}

class Bar {
  public int returnPlusConstant(int value) {
    return value + 42;
  }
}

public class InliningOptimize extends TestBase {

  @Test
  public void test() throws Exception {
    testForR8(Backend.DEX)
        .addProgramClasses(Bar.class, Foobar.class)
        .addKeepRules("-keep,allowoptimization class ** {\n" + "*;\n" + "}")
        .addOptionsModification(
            options -> {
              options.testing.enableLir();
              options.inlinerOptions().simpleInliningInstructionLimit = 5;
            })
        .compile()
        .inspect(
            inspector -> {
              inspector
                  .clazz(Foobar.class)
                  .mainMethod()
                  .iterateInstructions(InstructionSubject::isInvoke)
                  .forEachRemaining(
                      invoke -> {
                        assertFalse(
                            invoke.getMethod().name.toString().contains("returnPlusConstant"));
                      });
            })
        .run(Foobar.class)
        .assertSuccessWithOutputLines("Value: 84");
  }
}
