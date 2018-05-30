// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.switches;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.code.Const;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.ConstHigh16;
import com.android.tools.r8.code.IfEq;
import com.android.tools.r8.code.IfEqz;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.PackedSwitch;
import com.android.tools.r8.code.SparseSwitch;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.smali.SmaliBuilder;
import com.android.tools.r8.smali.SmaliBuilder.MethodSignature;
import com.android.tools.r8.smali.SmaliTestBase;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.function.Consumer;
import org.junit.Test;

public class SwitchRewritingTest extends SmaliTestBase {

  static boolean twoCaseWillUsePackedSwitch(int key1, int key2) {
    assert key1 != key2;
    return Math.abs((long) key1 - (long) key2) == 1;
  }

  private boolean some16BitConst(Instruction instruction) {
    return instruction instanceof Const4
        || instruction instanceof ConstHigh16
        || instruction instanceof Const;
  }
  private void runSingleCaseDexTest(boolean packed, int key) {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);
    String switchInstruction;
    String switchData;
    if (packed) {
      switchInstruction = "packed-switch";
      switchData = StringUtils.join(
          "\n",
          "  :switch_data",
          "  .packed-switch " + key,
          "    :case_0",
          "  .end packed-switch");
    } else {
      switchInstruction = "sparse-switch";
      switchData = StringUtils.join(
          "\n",
          "  :switch_data",
          "  .sparse-switch",
          "    " + key + " -> :case_0",
          "  .end sparse-switch");
    }
    MethodSignature signature = builder.addStaticMethod(
        "int",
        DEFAULT_METHOD_NAME,
        ImmutableList.of("int"),
        0,
        "    " + switchInstruction + " p0, :switch_data",
        "    const/4 p0, 0x5",
        "    goto :return",
        "  :case_0",
        "    const/4 p0, 0x3",
        "  :return",
        "    return p0",
        switchData);

    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    const/4             v1, 0",
        "    invoke-static       { v1 }, LTest;->method(I)I",
        "    move-result         v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(I)V",
        "    return-void"
    );

    AndroidApp originalApplication = buildApplication(builder);
    AndroidApp processedApplication = processApplication(originalApplication);
    DexEncodedMethod method = getMethod(processedApplication, signature);
    DexCode code = method.getCode().asDexCode();

    if (key == 0) {
      assertEquals(5, code.instructions.length);
      assertTrue(code.instructions[0] instanceof IfEqz);
    } else {
      assertEquals(6, code.instructions.length);
      assertTrue(some16BitConst(code.instructions[0]));
      assertTrue(code.instructions[1] instanceof IfEq);
    }
  }

  @Test
  public void singleCaseDex() {
    for (boolean packed : new boolean[]{true, false}) {
      runSingleCaseDexTest(packed, Integer.MIN_VALUE);
      runSingleCaseDexTest(packed, -1);
      runSingleCaseDexTest(packed, 0);
      runSingleCaseDexTest(packed, 1);
      runSingleCaseDexTest(packed, Integer.MAX_VALUE);
    }
  }

  private void runTwoCaseSparseToPackedOrIfsDexTest(int key1, int key2) {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    MethodSignature signature = builder.addStaticMethod(
        "int",
        DEFAULT_METHOD_NAME,
        ImmutableList.of("int"),
        0,
        "    sparse-switch p0, :sparse_switch_data",
        "    const/4 v0, 0x5",
        "    goto :return",
        "  :case_1",
        "    const/4 v0, 0x3",
        "    goto :return",
        "  :case_2",
        "    const/4 v0, 0x4",
        "  :return",
        "    return v0",
        "  :sparse_switch_data",
        "  .sparse-switch",
        "    " + key1 + " -> :case_1",
        "    " + key2 + " -> :case_2",
        "  .end sparse-switch");

    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    const/4             v1, 0",
        "    invoke-static       { v1 }, LTest;->method(I)I",
        "    move-result         v1",
        "    invoke-virtual      { v0, v1 }, Ljava/io/PrintStream;->print(I)V",
        "    return-void"
    );

    AndroidApp originalApplication = buildApplication(builder);
    AndroidApp processedApplication = processApplication(originalApplication);
    DexEncodedMethod method = getMethod(processedApplication, signature);
    DexCode code = method.getCode().asDexCode();
    if (twoCaseWillUsePackedSwitch(key1, key2)) {
      assertTrue(code.instructions[0] instanceof PackedSwitch);
    } else {
      if (key1 == 0) {
        // Const instruction may be before if.
        assertTrue(code.instructions[0] instanceof IfEqz || code.instructions[1] instanceof IfEqz);
      } else {
        // Const instruction before if.
        assertTrue(code.instructions[1] instanceof IfEq);
      }
    }
  }

  @Test
  public void twoCaseSparseToPackedOrIfsDex() {
    for (int delta = 1; delta <= 3; delta++) {
      runTwoCaseSparseToPackedOrIfsDexTest(0, delta);
      runTwoCaseSparseToPackedOrIfsDexTest(-delta, 0);
      runTwoCaseSparseToPackedOrIfsDexTest(Integer.MIN_VALUE, Integer.MIN_VALUE + delta);
      runTwoCaseSparseToPackedOrIfsDexTest(Integer.MAX_VALUE - delta, Integer.MAX_VALUE);
    }
    runTwoCaseSparseToPackedOrIfsDexTest(-1, 1);
    runTwoCaseSparseToPackedOrIfsDexTest(-2, 1);
    runTwoCaseSparseToPackedOrIfsDexTest(-1, 2);
    runTwoCaseSparseToPackedOrIfsDexTest(Integer.MIN_VALUE, Integer.MAX_VALUE);
    runTwoCaseSparseToPackedOrIfsDexTest(Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
    runTwoCaseSparseToPackedOrIfsDexTest(Integer.MIN_VALUE, Integer.MAX_VALUE - 1);
  }

  private void runLargerSwitchDexTest(int firstKey, int keyStep, int totalKeys,
      Integer additionalLastKey) throws Exception {
    SmaliBuilder builder = new SmaliBuilder(DEFAULT_CLASS_NAME);

    StringBuilder switchSource = new StringBuilder();
    StringBuilder targetCode = new StringBuilder();
    for (int i = 0; i < totalKeys; i++) {
      String caseLabel = "case_" + i;
      switchSource.append("    " + (firstKey + i * keyStep) + " -> :" + caseLabel + "\n");
      targetCode.append("  :" + caseLabel + "\n");
      targetCode.append("    goto :return\n");
    }
    if (additionalLastKey != null) {
      String caseLabel = "case_" + totalKeys;
      switchSource.append("    " + additionalLastKey + " -> :" + caseLabel + "\n");
      targetCode.append("  :" + caseLabel + "\n");
      targetCode.append("    goto :return\n");
    }

    MethodSignature signature = builder.addStaticMethod(
        "void",
        DEFAULT_METHOD_NAME,
        ImmutableList.of("int"),
        0,
        "    sparse-switch p0, :sparse_switch_data",
        "    goto :return",
        targetCode.toString(),
        "  :return",
        "    return-void",
        "  :sparse_switch_data",
        "  .sparse-switch",
        switchSource.toString(),
        "  .end sparse-switch");

    builder.addMainMethod(
        2,
        "    sget-object         v0, Ljava/lang/System;->out:Ljava/io/PrintStream;",
        "    const/4             v1, 0",
        "    invoke-static       { v1 }, LTest;->method(I)V",
        "    return-void"
    );

    Consumer<InternalOptions> optionsConsumer = options -> {
      options.verbose = true;
      options.printTimes = true;
    };
    AndroidApp originalApplication = buildApplication(builder);
    AndroidApp processedApplication = processApplication(originalApplication, optionsConsumer);
    DexEncodedMethod method = getMethod(processedApplication, signature);
    DexCode code = method.getCode().asDexCode();
    if (keyStep <= 2) {
      assertTrue(code.instructions[0] instanceof PackedSwitch);
    } else {
      assertTrue(code.instructions[0] instanceof SparseSwitch);
    }
  }

  @Test
  public void twoMonsterSparseToPackedDex() throws Exception {
    runLargerSwitchDexTest(0, 1, 100, null);
    runLargerSwitchDexTest(0, 2, 100, null);
    runLargerSwitchDexTest(0, 3, 100, null);
    runLargerSwitchDexTest(100, 100, 100, null);
    runLargerSwitchDexTest(-10000, 100, 100, null);
    runLargerSwitchDexTest(-10000, 200, 100, 10000);
    runLargerSwitchDexTest(
        Integer.MIN_VALUE, (int) ((-(long)Integer.MIN_VALUE) / 16), 32, Integer.MAX_VALUE);

    // TODO(63090177): Currently this is commented out as R8 gets really slow for large switches.
    // runLargerSwitchDexTest(0, 1, Constants.U16BIT_MAX, null);
  }
}