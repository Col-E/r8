// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.switches;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminTestBase;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class SwitchRewritingJarTest extends JasminTestBase {

  private Backend backend;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public SwitchRewritingJarTest(Backend backend) {
    this.backend = backend;
  }

  static class Statistics {
    int ifCount = 0;
    int packedSwitchCount = 0;
    int sparseSwitchCount = 0;

    Statistics() {}

    Statistics(int ifCount, int packedSwitchCount, int sparseSwitchCount) {
      this.ifCount = ifCount;
      this.packedSwitchCount = packedSwitchCount;
      this.sparseSwitchCount = sparseSwitchCount;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      Statistics that = (Statistics) o;
      return ifCount == that.ifCount
          && packedSwitchCount == that.packedSwitchCount
          && sparseSwitchCount == that.sparseSwitchCount;
    }

    @Override
    public String toString() {
      return "Statistics{"
          + "ifCount="
          + ifCount
          + ", packedSwitchCount="
          + packedSwitchCount
          + ", sparseSwitchCount="
          + sparseSwitchCount
          + '}';
    }
  }

  private Statistics countInstructions(Iterator<InstructionSubject> iterator) {
    Statistics statistics = new Statistics();
    while (iterator.hasNext()) {
      InstructionSubject instruction = iterator.next();
      if (instruction.isIf()) {
        ++statistics.ifCount;
      } else if (instruction.isPackedSwitch()) {
        ++statistics.packedSwitchCount;
      } else if (instruction.isSparseSwitch()) {
        ++statistics.sparseSwitchCount;
      }
    }
    return statistics;
  }

  private MethodSubject getMethodSubject(
      AndroidApp app, String className, MethodSignature signature)
      throws IOException, ExecutionException {
    CodeInspector inspector = new CodeInspector(app);
    ClassSubject testClass = inspector.clazz(className);
    return testClass.method(signature);
  }

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

    AndroidApp app =
        ToolHelper.runR8(
            ToolHelper.prepareR8CommandBuilder(builder.build(), emptyConsumer(backend))
                .addLibraryFiles(runtimeJar(backend))
                .build());

    Iterator<InstructionSubject> iterator =
        getMethodSubject(app, "Test", new MethodSignature("test", "int", ImmutableList.of("int")))
            .iterateInstructions();
    Statistics stat = countInstructions(iterator);
    assertEquals(new Statistics(1, 0, 0), stat);
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

    AndroidApp app =
        ToolHelper.runR8(
            ToolHelper.prepareR8CommandBuilder(builder.build(), emptyConsumer(backend))
                .addLibraryFiles(runtimeJar(backend))
                .build());

    MethodSubject method =
        getMethodSubject(app, "Test", new MethodSignature("test", "int", ImmutableList.of("int")));
    Statistics stat = countInstructions(method.iterateInstructions());
    if (SwitchRewritingTest.twoCaseWillUsePackedSwitch(key1, key2)) {
      int expectedPackedSwitchCount = 1;
      int expectedSparseSwitchCount = 0;
      // TODO(b/113648554) Implement packed (table) switch support in the CF backend.
      if (backend == Backend.CF) {
        expectedSparseSwitchCount += expectedPackedSwitchCount;
        expectedPackedSwitchCount = 0;
      }
      assertEquals(new Statistics(0, expectedPackedSwitchCount, expectedSparseSwitchCount), stat);
    } else {
      assertEquals(new Statistics(2, 0, 0), stat);
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

    AndroidApp app =
        ToolHelper.runR8(
            ToolHelper.prepareR8CommandBuilder(builder.build(), emptyConsumer(backend))
                .addLibraryFiles(runtimeJar(backend))
                .build());

    MethodSignature signature = new MethodSignature("test", "int", ImmutableList.of("int"));

    Statistics stat =
        countInstructions(getMethodSubject(app, "Test", signature).iterateInstructions());

    int expectedPackedSwitchCount, expectedSparseSwitchCount;
    if (keyStep <= 2) {
      expectedPackedSwitchCount = 1;
      expectedSparseSwitchCount = 0;
    } else {
      expectedPackedSwitchCount = 0;
      expectedSparseSwitchCount = 1;
    }

    // TODO(b/113648554) Implement packed (table) switch support in the CF backend.
    if (backend == Backend.CF) {
      expectedSparseSwitchCount += expectedPackedSwitchCount;
      expectedPackedSwitchCount = 0;
    }

    assertEquals(new Statistics(0, expectedPackedSwitchCount, expectedSparseSwitchCount), stat);
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
    int totalKeys = backend == Backend.CF ? 4376 : 5503;
    runLargerSwitchJarTest(0, 1, totalKeys, null);
  }

  private void runConvertCasesToIf(
      List<Integer> keys,
      int defaultValue,
      int expectedIfs,
      int expectedPackedSwitches,
      int expectedSparseSwitches)
      throws Exception {
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
    AndroidApp app =
        ToolHelper.runR8(
            ToolHelper.prepareR8CommandBuilder(appBuilder.build(), emptyConsumer(backend))
                .addLibraryFiles(runtimeJar(backend))
                .build());

    CodeInspector inspector = new CodeInspector(app);
    Statistics stat =
        countInstructions(
            inspector
                .clazz("Test")
                .method("int", "test", ImmutableList.of("int"))
                .iterateInstructions());

    // TODO(b/113648554) Implement packed (table) switch support in the CF backend.
    if (backend == Backend.CF) {
      expectedSparseSwitches += expectedPackedSwitches;
      expectedPackedSwitches = 0;
    }

    assertEquals(new Statistics(expectedIfs, expectedPackedSwitches, expectedSparseSwitches), stat);

    // Run the code
    List<String> args = keys.stream().map(Object::toString).collect(Collectors.toList());
    args.add(Integer.toString(defaultValue));
    if (backend == Backend.DEX) {
      runOnArt(app, CheckSwitchInTestClass.class, args);
    } else {
      assert backend == Backend.CF;
      runOnJava(app, CheckSwitchInTestClass.class, args);
    }
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
