// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.analysis.type.TypeLatticeElement.DOUBLE;
import static com.android.tools.r8.ir.analysis.type.TypeLatticeElement.FLOAT;
import static com.android.tools.r8.ir.analysis.type.TypeLatticeElement.INT;
import static com.android.tools.r8.ir.analysis.type.TypeLatticeElement.LONG;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.analysis.AnalysisTestBase;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.function.Consumer;
import org.junit.Test;

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
      return toString().toLowerCase() + "ConstraintOnTrivialPhiTest";
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

  public TypeConstraintOnTrivialPhiTest() throws Exception {
    super(buildApp(), "TestClass");
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
    return smaliBuilder.build();
  }

  @Test
  public void testIntConstraintOnTrivialPhi() throws Exception {
    buildAndCheckIR("intConstraintOnTrivialPhiTest", testInspector(INT));
  }

  @Test
  public void testFloatConstraintOnTrivialPhi() throws Exception {
    buildAndCheckIR("floatConstraintOnTrivialPhiTest", testInspector(FLOAT));
  }

  @Test
  public void testLongConstraintOnTrivialPhi() throws Exception {
    buildAndCheckIR("longConstraintOnTrivialPhiTest", testInspector(LONG));
  }

  @Test
  public void testDoubleConstraintOnTrivialPhi() throws Exception {
    buildAndCheckIR("doubleConstraintOnTrivialPhiTest", testInspector(DOUBLE));
  }

  private static Consumer<IRCode> testInspector(TypeLatticeElement expectedType) {
    return code -> {
      ConstNumber constNumberInstruction = getMatchingInstruction(code, Instruction::isConstNumber);
      assertEquals(expectedType, constNumberInstruction.outValue().getTypeLattice());
    };
  }
}
