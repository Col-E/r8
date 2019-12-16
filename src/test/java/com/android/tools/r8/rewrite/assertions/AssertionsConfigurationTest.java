// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.rewrite.assertions;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AssertionsConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.function.Function;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class AssertionsConfigurationTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public AssertionsConfigurationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void runD8Test(
      Function<AssertionsConfiguration.Builder, AssertionsConfiguration>
          assertionsConfigurationBuilder,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      List<String> outputLines)
      throws Exception {
    testForD8()
        .addProgramClasses(
            TestClass.class,
            com.android.tools.r8.rewrite.assertions.testclasses.Class1.class,
            com.android.tools.r8.rewrite.assertions.testclasses.Class2.class,
            com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class1.class,
            com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class2.class)
        .setMinApi(parameters.getApiLevel())
        .addAssertionsConfiguration(assertionsConfigurationBuilder)
        .compile()
        .inspect(inspector)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(StringUtils.lines(outputLines));
  }

  public void runR8Test(
      Function<AssertionsConfiguration.Builder, AssertionsConfiguration>
          assertionsConfigurationBuilder,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      List<String> outputLines)
      throws Exception {
    runR8Test(assertionsConfigurationBuilder, inspector, outputLines, false);
  }

  public void runR8Test(
      Function<AssertionsConfiguration.Builder, AssertionsConfiguration>
          assertionsConfigurationBuilder,
      ThrowingConsumer<CodeInspector, RuntimeException> inspector,
      List<String> outputLines,
      boolean enableJvmAssertions)
      throws Exception {

    testForR8(parameters.getBackend())
        .addProgramClasses(
            TestClass.class,
            com.android.tools.r8.rewrite.assertions.testclasses.Class1.class,
            com.android.tools.r8.rewrite.assertions.testclasses.Class2.class,
            com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class1.class,
            com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class2.class)
        .addKeepMainRule(TestClass.class)
        .addKeepClassAndMembersRules(
            com.android.tools.r8.rewrite.assertions.testclasses.Class1.class,
            com.android.tools.r8.rewrite.assertions.testclasses.Class2.class,
            com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class1.class,
            com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class2.class)
        .setMinApi(parameters.getApiLevel())
        .addAssertionsConfiguration(assertionsConfigurationBuilder)
        .compile()
        .inspect(inspector)
        .enableRuntimeAssertions(enableJvmAssertions)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(StringUtils.lines(outputLines));
  }

  private AssertionsConfiguration enableAllAssertions(AssertionsConfiguration.Builder builder) {
    return builder.enable().build();
  }

  private AssertionsConfiguration disableAllAssertions(AssertionsConfiguration.Builder builder) {
    return builder.disable().build();
  }

  private AssertionsConfiguration leaveAllAssertions(AssertionsConfiguration.Builder builder) {
    return builder.passthrough().build();
  }

  private List<String> allAssertionsExpectedLines() {
    return ImmutableList.of(
        "AssertionError in testclasses.Class1",
        "AssertionError in testclasses.Class2",
        "AssertionError in testclasses.subpackage.Class1",
        "AssertionError in testclasses.subpackage.Class2",
        "DONE");
  }

  private List<String> noAllAssertionsExpectedLines() {
    return ImmutableList.of("DONE");
  }

  private void checkAssertionCodeRemoved(CodeInspector inspector, Class<?> clazz) {
    ClassSubject subject = inspector.clazz(clazz);
    assertThat(subject, isPresent());
    // <clinit> is removed by R8 as it becomes empty.
    if (subject.uniqueMethodWithName("<clinit>").isPresent()) {
      assertFalse(
          subject
              .uniqueMethodWithName("<clinit>")
              .streamInstructions()
              .anyMatch(InstructionSubject::isStaticPut));
    }
    assertFalse(
        subject
            .uniqueMethodWithName("m")
            .streamInstructions()
            .anyMatch(InstructionSubject::isThrow));
  }

  private void checkAssertionCodeEnabled(CodeInspector inspector, Class<?> clazz) {
    ClassSubject subject = inspector.clazz(clazz);
    assertThat(subject, isPresent());
    // <clinit> is removed by R8.
    if (subject.uniqueMethodWithName("<clinit>").isPresent()) {
      assertFalse(
          subject
              .uniqueMethodWithName("<clinit>")
              .streamInstructions()
              .anyMatch(InstructionSubject::isStaticPut));
    }
    assertTrue(
        subject
            .uniqueMethodWithName("m")
            .streamInstructions()
            .anyMatch(InstructionSubject::isThrow));
  }

  private void checkAssertionCodeLeft(CodeInspector inspector, Class<?> clazz) {
    ClassSubject subject = inspector.clazz(clazz);
    assertThat(subject, isPresent());
    assertTrue(
        subject
            .uniqueMethodWithName("<clinit>")
            .streamInstructions()
            .anyMatch(InstructionSubject::isStaticPut));
    assertTrue(
        subject
            .uniqueMethodWithName("m")
            .streamInstructions()
            .anyMatch(InstructionSubject::isThrow));
  }

  private void checkAssertionCodeRemoved(CodeInspector inspector) {
    checkAssertionCodeRemoved(
        inspector, com.android.tools.r8.rewrite.assertions.testclasses.Class1.class);
    checkAssertionCodeRemoved(
        inspector, com.android.tools.r8.rewrite.assertions.testclasses.Class2.class);
    checkAssertionCodeRemoved(
        inspector, com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class1.class);
    checkAssertionCodeRemoved(
        inspector, com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class2.class);
  }

  private void checkAssertionCodeEnabled(CodeInspector inspector) {
    checkAssertionCodeEnabled(
        inspector, com.android.tools.r8.rewrite.assertions.testclasses.Class1.class);
    checkAssertionCodeEnabled(
        inspector, com.android.tools.r8.rewrite.assertions.testclasses.Class2.class);
    checkAssertionCodeEnabled(
        inspector, com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class1.class);
    checkAssertionCodeEnabled(
        inspector, com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class2.class);
  }

  private void checkAssertionCodeLeft(CodeInspector inspector) {
    checkAssertionCodeLeft(
        inspector, com.android.tools.r8.rewrite.assertions.testclasses.Class1.class);
    checkAssertionCodeLeft(
        inspector, com.android.tools.r8.rewrite.assertions.testclasses.Class2.class);
    checkAssertionCodeLeft(
        inspector, com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class1.class);
    checkAssertionCodeLeft(
        inspector, com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class2.class);
  }

  @Test
  public void testEnableAllAssertionsForDex() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    // Leaving assertions in or disabling them on Dalvik/Art means no assertions.
    runD8Test(
        this::leaveAllAssertions, this::checkAssertionCodeLeft, noAllAssertionsExpectedLines());
    runR8Test(
        this::leaveAllAssertions, this::checkAssertionCodeLeft, noAllAssertionsExpectedLines());
    runD8Test(
        this::disableAllAssertions,
        this::checkAssertionCodeRemoved,
        noAllAssertionsExpectedLines());
    runR8Test(
        this::disableAllAssertions,
        this::checkAssertionCodeRemoved,
        noAllAssertionsExpectedLines());
    // Compile time enabling assertions gives assertions on Dalvik/Art.
    runD8Test(
        this::enableAllAssertions, this::checkAssertionCodeEnabled, allAssertionsExpectedLines());
    runR8Test(
        this::enableAllAssertions, this::checkAssertionCodeEnabled, allAssertionsExpectedLines());
  }

  @Test
  public void testAssertionsForCf() throws Exception {
    Assume.assumeTrue(parameters.isCfRuntime());
    // Leaving assertion code means assertions are controlled by the -ea flag.
    runR8Test(
        this::leaveAllAssertions, this::checkAssertionCodeLeft, noAllAssertionsExpectedLines());
    runR8Test(
        this::leaveAllAssertions, this::checkAssertionCodeLeft, allAssertionsExpectedLines(), true);
    // Compile time enabling or disabling assertions means the -ea flag has no effect.
    runR8Test(
        this::enableAllAssertions, this::checkAssertionCodeEnabled, allAssertionsExpectedLines());
    runR8Test(
        this::enableAllAssertions,
        this::checkAssertionCodeEnabled,
        allAssertionsExpectedLines(),
        true);
    runR8Test(
        this::disableAllAssertions,
        this::checkAssertionCodeRemoved,
        noAllAssertionsExpectedLines());
    runR8Test(
        this::disableAllAssertions,
        this::checkAssertionCodeRemoved,
        noAllAssertionsExpectedLines(),
        true);
  }

  static class TestClass {
    public static void main(String[] args) {
      try {
        com.android.tools.r8.rewrite.assertions.testclasses.Class1.m();
      } catch (AssertionError e) {
        System.out.println("AssertionError in testclasses.Class1");
      }
      try {
        com.android.tools.r8.rewrite.assertions.testclasses.Class2.m();
      } catch (AssertionError e) {
        System.out.println("AssertionError in testclasses.Class2");
      }
      try {
        com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class1.m();
      } catch (AssertionError e) {
        System.out.println("AssertionError in testclasses.subpackage.Class1");
      }
      try {
        com.android.tools.r8.rewrite.assertions.testclasses.subpackage.Class2.m();
      } catch (AssertionError e) {
        System.out.println("AssertionError in testclasses.subpackage.Class2");
      }
      System.out.println("DONE");
    }
  }
}
