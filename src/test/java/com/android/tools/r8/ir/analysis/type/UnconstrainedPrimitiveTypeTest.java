// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.TypeLatticeElement.INT;
import static com.android.tools.r8.ir.analysis.type.TypeLatticeElement.LONG;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.function.Consumer;
import org.junit.Test;

public class UnconstrainedPrimitiveTypeTest extends TypeAnalysisTestBase {

  public UnconstrainedPrimitiveTypeTest() throws Exception {
    super(buildApp(), "TestClass");
  }

  private static AndroidApp buildApp() throws Exception {
    SmaliBuilder smaliBuilder = new SmaliBuilder("TestClass");

    {
      String code = StringUtils.lines("const/4 v0, 0x0", "return-void");
      smaliBuilder.addStaticMethod(
          "void", "unconstrainedSingleWithNoUsersTest", ImmutableList.of(), 1, code);
    }

    {
      String code =
          StringUtils.lines(
              "const/4 v0, 0x0",
              "const/4 v1, 0x1",
              "if-eq v0, v1, :cond_true",
              ":cond_true",
              "return-void");
      smaliBuilder.addStaticMethod(
          "void", "unconstrainedSingleWithIfUserTest", ImmutableList.of(), 2, code);
    }

    {
      String code =
          StringUtils.lines(
              "const/4 v0, 0x0", "if-eqz v0, :cond_true", ":cond_true", "return-void");
      smaliBuilder.addStaticMethod(
          "void", "unconstrainedSingleWithIfZeroUserTest", ImmutableList.of(), 1, code);
    }

    {
      String code = StringUtils.lines("const-wide v0, 0x0", "return-void");
      smaliBuilder.addStaticMethod(
          "void", "unconstrainedWideWithNoUsersTest", ImmutableList.of(), 2, code);
    }

    {
      String code =
          StringUtils.lines(
              "const-wide v0, 0x0",
              "const-wide v2, 0x1",
              "if-eq v0, v2, :cond_true",
              ":cond_true",
              "return-void");
      smaliBuilder.addStaticMethod(
          "void", "unconstrainedWideWithIfUserTest", ImmutableList.of(), 4, code);
    }

    return smaliBuilder.build();
  }

  @Test
  public void testUnconstrainedSingleWithNoUsers() throws Exception {
    buildAndCheckIR("unconstrainedSingleWithNoUsersTest", testInspector(INT, 1));
  }

  @Test
  public void testUnconstrainedSingleWithIfUser() throws Exception {
    buildAndCheckIR("unconstrainedSingleWithIfUserTest", testInspector(INT, 2));
  }

  @Test
  public void testUnconstrainedSingleWithIfZeroUser() throws Exception {
    buildAndCheckIR("unconstrainedSingleWithIfZeroUserTest", testInspector(INT, 1));
  }

  @Test
  public void testUnconstrainedWideWithNoUsers() throws Exception {
    buildAndCheckIR("unconstrainedWideWithNoUsersTest", testInspector(LONG, 1));
  }

  @Test
  public void testUnconstrainedWideWithIfUser() throws Exception {
    buildAndCheckIR("unconstrainedWideWithIfUserTest", testInspector(LONG, 2));
  }

  private static Consumer<IRCode> testInspector(
      TypeLatticeElement expectedType, int expectedNumberOfConstNumberInstructions) {
    return code -> {
      Iterable<Instruction> instructions = code::instructionIterator;
      for (Instruction instruction : instructions) {
        if (instruction.isConstNumber()) {
          ConstNumber constNumberInstruction = instruction.asConstNumber();
          assertEquals(expectedType, constNumberInstruction.outValue().getTypeLattice());
        }
      }

      assertEquals(
          expectedNumberOfConstNumberInstructions,
          Streams.stream(code.instructionIterator()).filter(Instruction::isConstNumber).count());
    };
  }
}
