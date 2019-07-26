// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.ir.optimize.string.StringBuilderOptimizerAnalysisTest.checkBuilderState;
import static com.android.tools.r8.ir.optimize.string.StringBuilderOptimizerAnalysisTest.checkOptimizerStates;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.analysis.AnalysisTestBase;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.string.StringBuilderOptimizer.BuilderState;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StringBuilderOptimizerAnalysisSmaliTest extends AnalysisTestBase {
  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public StringBuilderOptimizerAnalysisSmaliTest(TestParameters parameters) throws Exception {
    super(parameters, buildApp(), "TestClass");
  }

  private static AndroidApp buildApp() throws Exception {
    SmaliBuilder smaliBuilder = new SmaliBuilder("TestClass");

    {
      // IR of StringConcatenationTestClass#phiAtInit at DVM older than or equal to 5.1.1
      String code =
          StringUtils.lines(
              "invoke-static {}, Ljava/lang/System;->currentTimeMillis()J",
              "move-result-wide v0",
              "const-wide/16 v2, 0",
              "cmp-long v4, v0, v2",
              // new-instance is hoisted, but <init> calls are still at each branch
              "new-instance v0, Ljava/lang/StringBuilder;",
              "if-lez v4, :cond",
              "const-string v1, \"Hello\"",
              "invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V",
              "goto :merge",
              ":cond",
              "const-string v1, \"Hi\"",
              "invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V",
              "goto :merge",
              ":merge",
              "const-string v1, \", R8\"",
              "invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append"
                  + "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
              "sget-object v2, Ljava/lang/System;->out:Ljava/io/PrintStream;",
              "invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;",
              "move-result-object v1",
              "invoke-virtual {v2, v1}, Ljava/io/Stream;->println(Ljava/lang/String;)V",
              "return-void");
      smaliBuilder.addStaticMethod(
          "void", "phiAtInit_5_1_1", ImmutableList.of(), 5, code);
    }

    {
      // Compiled StringConcatenationTestClass#phiAtInit and modified two new-instance instructions
      // are flown to the same <init> call.
      String code =
          StringUtils.lines(
              "invoke-static {}, Ljava/lang/System;->currentTimeMillis()J",
              "move-result-wide v0",
              "const-wide/16 v2, 0",
              "cmp-long v4, v0, v2",
              "if-lez v4, :cond",
              "new-instance v0, Ljava/lang/StringBuilder;",
              "const-string v1, \"Hello\"",
              "goto :merge",
              ":cond",
              "new-instance v0, Ljava/lang/StringBuilder;",
              "const-string v1, \"Hi\"",
              "goto :merge",
              ":merge",
              // Two separate new-instance instructions are flown to the same <init>.
              "invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V",
              "const-string v1, \", R8\"",
              "invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append"
                  + "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
              "sget-object v2, Ljava/lang/System;->out:Ljava/io/PrintStream;",
              "invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;",
              "move-result-object v1",
              "invoke-virtual {v2, v1}, Ljava/io/Stream;->println(Ljava/lang/String;)V",
              "return-void");
      smaliBuilder.addStaticMethod(
          "void", "phiWithDifferentNewInstance", ImmutableList.of(), 5, code);
    }

    {
      // IR of StringConcatenationTestClass#phiAtInit at DVM/ART newer than 5.1.1
      String code =
          StringUtils.lines(
              "invoke-static {}, Ljava/lang/System;->currentTimeMillis()J",
              "move-result-wide v0",
              "const-wide/16 v2, 0",
              "cmp-long v4, v0, v2",
              // new-instance and <init> are in each branch.
              "if-lez v4, :cond",
              "new-instance v0, Ljava/lang/StringBuilder;",
              "const-string v1, \"Hello\"",
              "invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V",
              "goto :merge",
              ":cond",
              "new-instance v0, Ljava/lang/StringBuilder;",
              "const-string v1, \"Hi\"",
              "invoke-direct {v0, v1}, Ljava/lang/StringBuilder;-><init>(Ljava/lang/String;)V",
              "goto :merge",
              ":merge",
              "const-string v1, \", R8\"",
              "invoke-virtual {v0, v1}, Ljava/lang/StringBuilder;->append"
                  + "(Ljava/lang/String;)Ljava/lang/StringBuilder;",
              "sget-object v2, Ljava/lang/System;->out:Ljava/io/PrintStream;",
              "invoke-virtual {v0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;",
              "move-result-object v1",
              "invoke-virtual {v2, v1}, Ljava/io/Stream;->println(Ljava/lang/String;)V",
              "return-void");
      smaliBuilder.addStaticMethod(
          "void", "phiAtInit", ImmutableList.of(), 5, code);
    }

    return smaliBuilder.build();
  }

  @Test
  public void testPhiAtInit_5_1_1() throws Exception {
    buildAndCheckIR(
        "phiAtInit_5_1_1",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(1, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, null, true);
          }
          assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testPhiWithDifferentNewInstance() throws Exception {
    buildAndCheckIR(
        "phiWithDifferentNewInstance",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(2, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, null, false);
          }
          assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
        }));
  }

  @Test
  public void testPhiAtInit() throws Exception {
    buildAndCheckIR(
        "phiAtInit",
        checkOptimizerStates(appView, optimizer -> {
          assertEquals(2, optimizer.analysis.builderStates.size());
          for (Value builder : optimizer.analysis.builderStates.keySet()) {
            Map<Instruction, BuilderState> perBuilderState =
                optimizer.analysis.builderStates.get(builder);
            checkBuilderState(optimizer, perBuilderState, null, false);
          }
          assertEquals(0, optimizer.analysis.simplifiedBuilders.size());
        }));
  }
}
