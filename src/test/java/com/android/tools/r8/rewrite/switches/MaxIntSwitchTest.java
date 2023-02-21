// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.switches;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MaxIntSwitchTest extends TestBase {

  private final TestParameters parameters;
  private final CompilationMode mode;

  @Parameterized.Parameters(name = "{0}, mode = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(), CompilationMode.values());
  }

  // See b/177790310 for details.
  public MaxIntSwitchTest(TestParameters parameters, CompilationMode mode) {
    this.parameters = parameters;
    this.mode = mode;
  }

  private void checkSwitch(InstructionSubject instruction, boolean hasMaxIntKey) {
    assertEquals(hasMaxIntKey, instruction.asSwitch().getKeys().contains(0x7fffffff));
  }

  private void checkSwitch(MethodSubject method, boolean hasMaxIntKey) {
    assertTrue(method.streamInstructions().anyMatch(InstructionSubject::isSwitch));
    method
        .streamInstructions()
        .filter(InstructionSubject::isSwitch)
        .forEach(instruction -> checkSwitch(instruction, hasMaxIntKey));
  }

  private void checkStringSwitch(MethodSubject method, boolean hasMaxIntKey) {
    // String switch will have two switches.
    List<InstructionSubject> switchInstructions =
        method
            .streamInstructions()
            .filter(InstructionSubject::isSwitch)
            .collect(Collectors.toList());
    assertEquals(2, switchInstructions.size());
    checkSwitch(switchInstructions.get(0), hasMaxIntKey);
  }

  public void checkSwitchKeys(CodeInspector inspector) {
    checkSwitch(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("f"),
        parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K));
    checkSwitch(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("g"),
        parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K));
    // Debug mode will not rewrite switch statements except when the MAX_INT key is present.
    assertEquals(
        inspector
            .clazz(TestClass.class)
            .uniqueMethodWithOriginalName("h")
            .streamInstructions()
            .filter(InstructionSubject::isSwitch)
            .count(),
        BooleanUtils.intValue(
            mode == CompilationMode.DEBUG
                && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K)));

    checkStringSwitch(
        inspector.clazz(TestClass.class).uniqueMethodWithOriginalName("s"),
        parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.K));
  }

  @Test
  public void testD8() throws Exception {
    testForD8(parameters.getBackend())
        .addInnerClasses(this.getClass())
        .setMinApi(parameters)
        .setMode(mode)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::checkSwitchKeys)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(this.getClass())
        .addKeepMainRule(TestClass.class)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .setMinApi(parameters)
        .setMode(mode)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(this::checkSwitchKeys)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          Integer.toString(0x7ffffffc),
          Integer.toString(0x7ffffffd),
          Integer.toString(0x7ffffffe),
          Integer.toString(0x7fffffff),
          "good",
          "show",
          "!",
          "?");

  static class TestClass {
    @KeepConstantArguments
    @NeverInline
    @NeverPropagateValue
    public static void f(int i) {
      switch (i) {
        case 0x7ffffffd:
          System.out.println("a");
          break;
        case 0x7ffffffe:
          System.out.println("b");
          break;
        case 0x7fffffff:
          System.out.println("good");
          break;
        default:
          throw new AssertionError();
      }
    }

    @KeepConstantArguments
    @NeverInline
    @NeverPropagateValue
    public static void g(int i) {
      switch (i) {
        case 0x7ffffffc:
          System.out.println("a");
          break;
        case 0x7ffffffd:
          System.out.println("b");
          break;
        case 0x7ffffffe:
        case 0x7fffffff:
          System.out.println("show");
          break;
        default:
          throw new AssertionError();
      }
    }

    @KeepConstantArguments
    @NeverInline
    @NeverPropagateValue
    public static void h(int i) {
      switch (i) {
        case 0x7fffffff:
          System.out.println("!");
          break;
        default:
          throw new AssertionError();
      }
    }

    @KeepConstantArguments
    @NeverInline
    @NeverPropagateValue
    public static void s(String s) {
      switch (s) {
        case "DAEFIDU":
          System.out.println("a");
          break;
        case "DAEFIDV":
          System.out.println("b");
          break;
        case "DAEFIDW":
          System.out.println("c");
          break;
        case "DAEFIDX":
          System.out.println("?");
          break;
        default:
          throw new AssertionError();
      }
    }

    public static void main(String[] args) {
      System.out.println("DAEFIDU".hashCode());
      System.out.println("DAEFIDV".hashCode());
      System.out.println("DAEFIDW".hashCode());
      System.out.println("DAEFIDX".hashCode());
      f(0x7fffffff);
      g(0x7fffffff);
      h(0x7fffffff);
      s("DAEFIDX");
    }
  }
}
