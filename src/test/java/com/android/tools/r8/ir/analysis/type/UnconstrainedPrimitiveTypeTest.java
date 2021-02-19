// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ir.analysis.AnalysisTestBase;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class UnconstrainedPrimitiveTypeTest extends AnalysisTestBase {

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public UnconstrainedPrimitiveTypeTest(TestParameters parameters) throws Exception {
    super(parameters, buildApp(), "TestClass");
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

    return AndroidApp.builder()
        .addDexProgramData(smaliBuilder.compile(), Origin.unknown())
        .addLibraryFile(ToolHelper.getMostRecentAndroidJar())
        .build();
  }

  @Test
  public void testUnconstrainedSingleWithNoUsers() {
    buildAndCheckIR("unconstrainedSingleWithNoUsersTest", testInspector(TypeElement.getInt(), 1));
  }

  @Test
  public void testUnconstrainedSingleWithIfUser() {
    buildAndCheckIR("unconstrainedSingleWithIfUserTest", testInspector(TypeElement.getInt(), 2));
  }

  @Test
  public void testUnconstrainedSingleWithIfZeroUser() {
    buildAndCheckIR(
        "unconstrainedSingleWithIfZeroUserTest", testInspector(IntTypeElement.getInt(), 1));
  }

  @Test
  public void testUnconstrainedWideWithNoUsers() {
    buildAndCheckIR("unconstrainedWideWithNoUsersTest", testInspector(TypeElement.getLong(), 1));
  }

  @Test
  public void testUnconstrainedWideWithIfUser() {
    buildAndCheckIR("unconstrainedWideWithIfUserTest", testInspector(TypeElement.getLong(), 2));
  }

  private static Consumer<IRCode> testInspector(
      TypeElement expectedType, int expectedNumberOfConstNumberInstructions) {
    return code -> {
      for (Instruction instruction : code.instructions()) {
        if (instruction.isConstNumber()) {
          ConstNumber constNumberInstruction = instruction.asConstNumber();
          assertEquals(expectedType, constNumberInstruction.getOutType());
        }
      }

      assertEquals(
          expectedNumberOfConstNumberInstructions,
          code.streamInstructions().filter(Instruction::isConstNumber).count());
    };
  }
}
