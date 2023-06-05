// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.errors.Unreachable;
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
public class TypeConstraintOnTrivialPhiTest extends AnalysisTestBase {

  private enum Config {
    INT,
    FLOAT,
    LONG,
    DOUBLE;

    public boolean isSingle() {
      return this == INT || this == FLOAT;
    }

    public String getTestName() {
      return StringUtils.toLowerCase(toString()) + "ConstraintOnTrivialPhiTest";
    }

    public String getConstInstruction() {
      return isSingle() ? "const/4 v0, 0x0" : "const-wide v0, 0x0";
    }

    public String getMoveInstruction() {
      return isSingle() ? "move v1, v0" : "move-wide v2, v0";
    }

    public String getInvokeStaticInstruction() {
      switch (this) {
        case INT:
          return "invoke-static {v1}, Ljava/lang/Integer;->toString(I)Ljava/lang/String;";
        case FLOAT:
          return "invoke-static {v1}, Ljava/lang/Float;->toString(F)Ljava/lang/String;";
        case LONG:
          return "invoke-static {v2, v3}, Ljava/lang/Long;->toString(J)Ljava/lang/String;";
        case DOUBLE:
          return "invoke-static {v2, v3}, Ljava/lang/Double;->toString(D)Ljava/lang/String;";
      }
      throw new Unreachable();
    }
  }

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public TypeConstraintOnTrivialPhiTest(TestParameters parameters) throws Exception {
    super(parameters, buildApp(), "TestClass");
  }

  public static AndroidApp buildApp() throws Exception {
    SmaliBuilder smaliBuilder = new SmaliBuilder("TestClass");
    for (Config config : Config.values()) {
      String code =
          StringUtils.lines(
              config.getConstInstruction(),
              ":label_1",
              "if-eqz p0, :label_2",
              config.getMoveInstruction(),
              config.getInvokeStaticInstruction(),
              ":label_2",
              "goto :label_1");
      smaliBuilder.addStaticMethod("void", config.getTestName(), ImmutableList.of("int"), 4, code);
    }
    return AndroidApp.builder()
        .addDexProgramData(smaliBuilder.compile(), Origin.unknown())
        .addLibraryFile(ToolHelper.getMostRecentAndroidJar())
        .build();
  }

  @Test
  public void testIntConstraintOnTrivialPhi() {
    buildAndCheckIR("intConstraintOnTrivialPhiTest", testInspector(TypeElement.getInt()));
  }

  @Test
  public void testFloatConstraintOnTrivialPhi() {
    buildAndCheckIR("floatConstraintOnTrivialPhiTest", testInspector(TypeElement.getFloat()));
  }

  @Test
  public void testLongConstraintOnTrivialPhi() {
    buildAndCheckIR("longConstraintOnTrivialPhiTest", testInspector(TypeElement.getLong()));
  }

  @Test
  public void testDoubleConstraintOnTrivialPhi() {
    buildAndCheckIR("doubleConstraintOnTrivialPhiTest", testInspector(TypeElement.getDouble()));
  }

  private static Consumer<IRCode> testInspector(TypeElement expectedType) {
    return code -> {
      ConstNumber constNumberInstruction = getMatchingInstruction(code, Instruction::isConstNumber);
      assertEquals(expectedType, constNumberInstruction.getOutType());
    };
  }
}
