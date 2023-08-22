// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debug.classinit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AlwaysInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Iterator;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InitClassPopCfTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  private final TestParameters parameters;

  public InitClassPopCfTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testInitClass() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .enableInliningAnnotations()
        .enableAlwaysInliningAnnotations()
        .addOptionsModification(
            internalOptions -> internalOptions.testing.irModifier = this::modifyIR)
        .addKeepRules("-keepattributes LineNumberTable")
        .addKeepRules("-keep class * { static java.lang.Object f; }")
        .addKeepMainRule(Main.class)
        .compile()
        .inspect(InitClassPopCfTest::verifyInitClassFollowedByPopInCf);
  }

  private void modifyIR(IRCode irCode, AppView<?> appView) {
    InstructionIterator instructionIterator = irCode.instructionIterator();
    Position pos = null;
    while (instructionIterator.hasNext()) {
      Instruction next = instructionIterator.next();
      // Modify the position so code can be shared except for initClass and invoke-static that
      // will be transformed into initClass.
      if (!next.isInitClass()
          && !(next.isInvokeStatic()
              && next.asInvokeStatic().getInvokedMethod().getName().toString().equals("nop"))) {
        if (pos == null) {
          pos = next.getPosition();
        }
        next.forceOverwritePosition(pos);
      }
    }
  }

  private static void verifyInitClassFollowedByPopInCf(CodeInspector i) {
    MethodSubject main = i.clazz(Main.class).mainMethod();
    Iterator<InstructionSubject> iterator = main.iterateInstructions();
    int index = 0;
    while (iterator.hasNext()) {
      InstructionSubject next = iterator.next();
      // All static gets on non System are init class.
      if (next.isStaticGet()
          && !next.getField().getHolderType().toString().equals("java.lang.System")) {
        assertTrue(iterator.next().isPop());
        index++;
      }
    }
    assertEquals(4, index);
  }

  public static class A {
    static {
      System.out.println("clinit A");
    }

    static Object f;

    @AlwaysInline
    static void nop() {}
  }

  public static class Main {

    public static void main(String[] args) {
      int i = System.currentTimeMillis() > 0 ? 5 : 3;
      if (System.currentTimeMillis() > 0) {
        System.out.println("0");
        A.nop();
        print((((i << 2) + 1 << 3) + 4) << 5);
      } else if (System.currentTimeMillis() > 1) {
        System.out.println("1");
        A.nop();
        print((((i << 2) + 1 << 3) + 4) << 5);
      } else if (System.currentTimeMillis() > 2) {
        System.out.println("2");
        A.nop();
        print((((i << 2) + 1 << 3) + 4) << 5);
      } else {
        System.out.println("3");
        A.nop();
        print((((i << 2) + 1 << 3) + 4) << 5);
      }
      System.out.println(i);
    }

    @NeverInline
    private static void print(int k) {
      System.out.println(k);
    }
  }
}
