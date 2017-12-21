// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.switches;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.code.IfEq;
import com.android.tools.r8.code.IfEqz;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.PackedSwitch;
import com.android.tools.r8.code.SparseSwitch;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.MethodSubject;
import com.android.tools.r8.utils.StringUtils;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class SwitchRewritingJarTest extends JasminTestBase {

  private void runSingleCaseJarTest(boolean packed, int key) throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    String switchCode;
    if (packed) {
      switchCode = StringUtils.join(
          "\n",
          "    tableswitch " + key,
          "      case_0",
          "      default : case_default");
    } else {
      switchCode = StringUtils.join(
          "\n",
          "    lookupswitch",
          "      " + key + " : case_0",
          "      default : case_default");
    }

    clazz.addStaticMethod("test", ImmutableList.of("I"), "I",
        "    .limit stack 1",
        "    .limit locals 1",
        "    iload 0",
        switchCode,
        "  case_0:",
        "    iconst_3",
        "    goto return_",
        "  case_default:",
        "    ldc 5",
        "  return_:",
        "    ireturn");

    clazz.addMainMethod(
        "    .limit stack 2",
        "    .limit locals 1",
        "    getstatic java/lang/System/out Ljava/io/PrintStream;",
        "    ldc 2",
        "    invokestatic Test/test(I)I",
        "    invokevirtual java/io/PrintStream/print(I)V",
        "    return");

    AndroidApp app = builder.build();
    app = ToolHelper.runR8(app);

    MethodSignature signature = new MethodSignature("test", "int", ImmutableList.of("int"));
    DexEncodedMethod method = getMethod(app, "Test", signature);
    DexCode code = method.getCode().asDexCode();
    if (key == 0) {
      assertEquals(5, code.instructions.length);
      assertTrue(code.instructions[0] instanceof IfEqz);
    } else {
      assertEquals(6, code.instructions.length);
      assertTrue(code.instructions[1] instanceof IfEq);
    }
  }

  @Test
  public void singleCaseJar() throws Exception {
    for (boolean packed : new boolean[]{true, false}) {
      runSingleCaseJarTest(packed, Integer.MIN_VALUE);
      runSingleCaseJarTest(packed, -1);
      runSingleCaseJarTest(packed, 0);
      runSingleCaseJarTest(packed, 1);
      runSingleCaseJarTest(packed, Integer.MAX_VALUE);
    }
  }

  private void runTwoCaseSparseToPackedJarTest(int key1, int key2) throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    clazz.addStaticMethod("test", ImmutableList.of("I"), "I",
        "    .limit stack 1",
        "    .limit locals 1",
        "    iload 0",
        "    lookupswitch",
        "      " + key1 + " : case_1",
        "      " + key2 + " : case_2",
        "      default : case_default",
        "  case_1:",
        "    iconst_3",
        "    goto return_",
        "  case_2:",
        "    iconst_4",
        "    goto return_",
        "  case_default:",
        "    iconst_5",
        "  return_:",
        "    ireturn");

    clazz.addMainMethod(
        "    .limit stack 2",
        "    .limit locals 1",
        "    getstatic java/lang/System/out Ljava/io/PrintStream;",
        "    ldc 2",
        "    invokestatic Test/test(I)I",
        "    invokevirtual java/io/PrintStream/print(I)V",
        "    return");

    AndroidApp app = compileWithR8(builder);

    MethodSignature signature = new MethodSignature("test", "int", ImmutableList.of("int"));
    DexEncodedMethod method = getMethod(app, "Test", signature);
    DexCode code = method.getCode().asDexCode();
    if (SwitchRewritingTest.twoCaseWillUsePackedSwitch(key1, key2)) {
      assertTrue(code.instructions[0] instanceof PackedSwitch);
    } else {
      if (key1 == 0) {
        assertTrue(code.instructions[0] instanceof IfEqz);
      } else {
        // Const instruction before if.
        assertTrue(code.instructions[1] instanceof IfEq);
      }
    }
  }

  @Test
  public void twoCaseSparseToPackedJar() throws Exception {
    for (int delta = 1; delta <= 3; delta++) {
      runTwoCaseSparseToPackedJarTest(0, delta);
      runTwoCaseSparseToPackedJarTest(-delta, 0);
      runTwoCaseSparseToPackedJarTest(Integer.MIN_VALUE, Integer.MIN_VALUE + delta);
      runTwoCaseSparseToPackedJarTest(Integer.MAX_VALUE - delta, Integer.MAX_VALUE);
    }
    runTwoCaseSparseToPackedJarTest(-1, 1);
    runTwoCaseSparseToPackedJarTest(-2, 1);
    runTwoCaseSparseToPackedJarTest(-1, 2);
    runTwoCaseSparseToPackedJarTest(Integer.MIN_VALUE, Integer.MAX_VALUE);
    runTwoCaseSparseToPackedJarTest(Integer.MIN_VALUE + 1, Integer.MAX_VALUE);
    runTwoCaseSparseToPackedJarTest(Integer.MIN_VALUE, Integer.MAX_VALUE - 1);
  }

  private void runLargerSwitchJarTest(int firstKey, int keyStep, int totalKeys,
      Integer additionalLastKey) throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    StringBuilder switchSource = new StringBuilder();
    StringBuilder targetCode = new StringBuilder();
    for (int i = 0; i < totalKeys; i++) {
      String caseLabel = "case_" + i;
      switchSource.append("      " + (firstKey + i * keyStep) + " : " + caseLabel + "\n");
      targetCode.append("  " + caseLabel + ":\n");
      targetCode.append("    ldc " + i + "\n");
      targetCode.append("    goto return_\n");
    }
    if (additionalLastKey != null) {
      String caseLabel = "case_" + totalKeys;
      switchSource.append("      " + additionalLastKey + " : " + caseLabel + "\n");
      targetCode.append("  " + caseLabel + ":\n");
      targetCode.append("    ldc " + totalKeys + "\n");
      targetCode.append("    goto return_\n");
    }

    clazz.addStaticMethod("test", ImmutableList.of("I"), "I",
        "    .limit stack 1",
        "    .limit locals 1",
        "    iload 0",
        "  lookupswitch",
        switchSource.toString(),
        "      default : case_default",
        targetCode.toString(),
        "  case_default:",
        "    iconst_5",
        "  return_:",
        "    ireturn");

    clazz.addMainMethod(
        "    .limit stack 2",
        "    .limit locals 1",
        "    getstatic java/lang/System/out Ljava/io/PrintStream;",
        "    ldc 2",
        "    invokestatic Test/test(I)I",
        "    invokevirtual java/io/PrintStream/print(I)V",
        "    return");

    AndroidApp app = compileWithR8(builder);

    MethodSignature signature = new MethodSignature("test", "int", ImmutableList.of("int"));
    DexEncodedMethod method = getMethod(app, "Test", signature);
    DexCode code = method.getCode().asDexCode();
    int packedSwitchCount = 0;
    int sparseSwitchCount = 0;
    for (Instruction instruction : code.instructions) {
      if (instruction instanceof PackedSwitch) {
        packedSwitchCount++;
      }
      if (instruction instanceof SparseSwitch) {
        sparseSwitchCount++;
      }
    }
    if (keyStep <= 2) {
      assertEquals(1, packedSwitchCount);
      assertEquals(0, sparseSwitchCount);
    } else {
      assertEquals(0, packedSwitchCount);
      assertEquals(1, sparseSwitchCount);
    }
  }

  @Test
  public void largerSwitchJar() throws Exception {
    runLargerSwitchJarTest(0, 1, 100, null);
    runLargerSwitchJarTest(0, 2, 100, null);
    runLargerSwitchJarTest(0, 3, 100, null);
    runLargerSwitchJarTest(100, 100, 100, null);
    runLargerSwitchJarTest(-10000, 100, 100, null);
    runLargerSwitchJarTest(-10000, 200, 100, 10000);
    runLargerSwitchJarTest(
        Integer.MIN_VALUE, (int) ((-(long) Integer.MIN_VALUE) / 16), 32, Integer.MAX_VALUE);

    // This is the maximal value possible with Jasmin with the generated code above. It depends on
    // the source, so making smaller source can raise this limit. However we never get close to the
    // class file max.
    runLargerSwitchJarTest(0, 1, 5503, null);
  }

  private void runConvertCasesToIf(List<Integer> keys, int defaultValue, int expectedIfs,
      int expectedPackedSwitches, int expectedSparceSwitches) throws Exception {
    JasminBuilder builder = new JasminBuilder();
    JasminBuilder.ClassBuilder clazz = builder.addClass("Test");

    StringBuilder x = new StringBuilder();
    StringBuilder y = new StringBuilder();
    for (Integer key : keys) {
      x.append(key).append(" : case_").append(key).append("\n");
      y.append("case_").append(key).append(":\n");
      y.append("    ldc ").append(key).append("\n");
      y.append("    goto return_\n");
    }

    clazz.addStaticMethod("test", ImmutableList.of("I"), "I",
        "    .limit stack 1",
        "    .limit locals 1",
        "    iload_0",
        "    lookupswitch",
        x.toString(),
        "      default : case_default",
        y.toString(),
        "  case_default:",
        "    ldc " + defaultValue,
        "  return_:",
        "    ireturn");

    // Add the Jasmin class and a class from Java source with the main method.
    AndroidApp.Builder appBuilder = AndroidApp.builder();
    appBuilder.addClassProgramData(builder.buildClasses());
    appBuilder.addProgramFiles(ToolHelper.getClassFileForTestClass(CheckSwitchInTestClass.class));
    AndroidApp app = compileWithR8(appBuilder.build());

    DexInspector inspector = new DexInspector(app);
    MethodSubject method = inspector.clazz("Test").method("int", "test", ImmutableList.of("int"));
    DexCode code = method.getMethod().getCode().asDexCode();

    int packedSwitches = 0;
    int sparseSwitches = 0;
    int ifs = 0;
    for (Instruction instruction : code.instructions) {
      if (instruction instanceof PackedSwitch) {
        packedSwitches++;
      }
      if (instruction instanceof SparseSwitch) {
        sparseSwitches++;
      }
      if (instruction instanceof IfEq || instruction instanceof IfEqz) {
        ifs++;
      }
    }

    assertEquals(expectedPackedSwitches, packedSwitches);
    assertEquals(expectedSparceSwitches, sparseSwitches);
    assertEquals(expectedIfs, ifs);

    // Run the code
    List<String> args = keys.stream().map(Object::toString).collect(Collectors.toList());
    args.add(Integer.toString(defaultValue));
    runOnArt(app, CheckSwitchInTestClass.class, args);
  }

  @Test
  public void convertCasesToIf() throws Exception {
    // Switches that are completely converted to ifs.
    runConvertCasesToIf(ImmutableList.of(0, 1000), -100, 2, 0, 0);
    runConvertCasesToIf(ImmutableList.of(0, 1000, 2000), -100, 3, 0, 0);
    runConvertCasesToIf(ImmutableList.of(0, 1000, 2000, 3000), -100, 4, 0, 0);

    // Switches that are completely converted to ifs and one switch.
    runConvertCasesToIf(ImmutableList.of(0, 1000, 1001, 1002, 1003, 1004), -100, 1, 1, 0);
    runConvertCasesToIf(ImmutableList.of(1000, 1001, 1002, 1003, 1004, 2000), -100, 1, 1, 0);
    runConvertCasesToIf(ImmutableList.of(
        Integer.MIN_VALUE, 1000, 1001, 1002, 1003, 1004), -100, 1, 1, 0);
    runConvertCasesToIf(ImmutableList.of(
        1000, 1001, 1002, 1003, 1004, Integer.MAX_VALUE), -100, 1, 1, 0);
    runConvertCasesToIf(ImmutableList.of(0, 1000, 1001, 1002, 1003, 1004, 2000), -100, 2, 1, 0);
    runConvertCasesToIf(ImmutableList.of(
        Integer.MIN_VALUE, 1000, 1001, 1002, 1003, 1004, Integer.MAX_VALUE), -100, 2, 1, 0);

    // Switches that are completely converted to ifs and two switches.
    runConvertCasesToIf(ImmutableList.of(
        0, 1, 2, 3, 4, 1000, 1001, 1002, 1003, 1004), -100, 0, 2, 0);
    runConvertCasesToIf(ImmutableList.of(
        -1000, 0, 1, 2, 3, 4, 1000, 1001, 1002, 1003, 1004), -100, 1, 2, 0);
    runConvertCasesToIf(ImmutableList.of(
        -1000, 0, 1, 2, 3, 4, 1000, 1001, 1002, 1003, 1004, 2000), -100, 2, 2, 0);

    // Switches that are completely converted two switches (one sparse and one packed).
    runConvertCasesToIf(ImmutableList.of(
        -1000, -900, -800, -700, -600, -500, -400, -300,
        1000, 1001, 1002, 1003, 1004,
        2000, 2100, 2200, 2300, 2400, 2500), -100, 0, 1, 1);
  }
}
