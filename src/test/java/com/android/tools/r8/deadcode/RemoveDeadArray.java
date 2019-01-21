// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.deadcode;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.code.Aput;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RemoveDeadArray extends TestBase {

  public static class TestClassWithCatch {
    static {
      try {
        int[] foobar = new int[]{42, 42, 42, 42};
      } catch (Exception ex) {
        System.out.println("foobar");
      }
    }
  }

  public static class TestClass {
    private static int[] foobar = new int[]{42, 42, 42, 42};
    public static void main(java.lang.String[] args) {
      ShouldGoAway[] willBeRemoved = new ShouldGoAway[4];
      ShouldGoAway[] willAlsoBeRemoved = new ShouldGoAway[0];
      System.out.println("foobar");
    }

    public static class ShouldGoAway { }
  }

  private Backend backend;

  public RemoveDeadArray(Backend backend) {
    this.backend = backend;
  }

  @Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    // Todo(ricow): enable unused array removal for cf backend.
    return new Backend[]{Backend.DEX};
  }


  @Test
  public void testDeadArraysRemoved() throws Exception {
    R8TestCompileResult result =
        testForR8(backend)
            .addProgramClasses(TestClass.class, TestClass.ShouldGoAway.class)
            .addKeepMainRule(TestClass.class)
            .compile();
    CodeInspector inspector = result.inspector();
    assertFalse(inspector.clazz(TestClass.class).clinit().isPresent());

    MethodSubject main = inspector.clazz(TestClass.class).mainMethod();
    main.streamInstructions().noneMatch(instructionSubject -> instructionSubject.isNewArray());
    assertFalse(main.getMethod().getCode().asDexCode().toString().contains("NewArray"));
    runOnArt(result.app, TestClass.class.getName());
 }

  @Test
  public void testNotRemoveStaticCatch() throws Exception {
    R8TestCompileResult result =
        testForR8(backend)
            .addProgramClasses(TestClassWithCatch.class)
            .addKeepAllClassesRule()
            .compile();
    CodeInspector inspector = result.inspector();
    MethodSubject clinit = inspector.clazz(TestClassWithCatch.class).clinit();
    assertTrue(clinit.isPresent());
    // Ensure that our optimization does not hit, we should still have 4 Aput instructions.
    long aPutCount = Arrays.stream(clinit.getMethod().getCode().asDexCode().instructions)
        .filter(instruction -> instruction instanceof  Aput)
        .count();
    assertEquals(4, aPutCount);
  }
}
