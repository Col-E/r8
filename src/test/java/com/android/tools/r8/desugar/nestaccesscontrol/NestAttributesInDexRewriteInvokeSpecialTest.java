// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.utils.FileUtils.JAR_EXTENSION;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.google.common.base.Predicates.alwaysTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestAttributesInDexRewriteInvokeSpecialTest extends NestAttributesInDexTestBase {

  private static final Path JDK17_JAR =
      Paths.get(ToolHelper.TESTS_BUILD_DIR, "examplesJava11")
          .resolve("nesthostexample" + JAR_EXTENSION);
  private static final String MAIN = "nesthostexample.NestHierachy";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "m1", "m2", "m3", "s1", "s2", "m1", "m2", "m3", "s1", "s2", "s1", "s2", "s1", "s2");

  @Test
  public void testRuntime() throws Exception {
    assumeTrue(parameters.isCfRuntime() && isRuntimeWithNestSupport(parameters.asCfRuntime()));
    testForJvm(parameters)
        .addProgramFiles(JDK17_JAR)
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addProgramFiles(JDK17_JAR)
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  private void assertSingleInvokeSuper(MethodSubject method, Predicate<String> methodNameFilter) {
    long invokeCount =
        method
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .filter(
                instruction -> methodNameFilter.test(instruction.getMethod().getName().toString()))
            .count();
    long invokeSuperCount =
        method
            .streamInstructions()
            .filter(instruction -> instruction.asDexInstruction().isInvokeSuper())
            .filter(
                instruction -> methodNameFilter.test(instruction.getMethod().getName().toString()))
            .count();
    assertEquals(1, invokeCount);
    assertEquals(1, invokeSuperCount);
  }

  private void assertSingleInvokeDirect(MethodSubject method, Predicate<String> methodNameFilter) {
    long invokeCount =
        method
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .filter(
                instruction -> methodNameFilter.test(instruction.getMethod().getName().toString()))
            .count();
    long invokeSuperCount =
        method
            .streamInstructions()
            .filter(instruction -> instruction.asDexInstruction().isInvokeDirect())
            .filter(
                instruction -> methodNameFilter.test(instruction.getMethod().getName().toString()))
            .count();
    assertEquals(1, invokeCount);
    assertEquals(1, invokeSuperCount);
  }

  private void assertSingleInvokeVirtual(MethodSubject method, Predicate<String> methodNameFilter) {
    long invokeCount =
        method
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .filter(
                instruction -> methodNameFilter.test(instruction.getMethod().getName().toString()))
            .count();
    long invokeVirtualCount =
        method
            .streamInstructions()
            .filter(InstructionSubject::isInvokeVirtual)
            .filter(
                instruction -> methodNameFilter.test(instruction.getMethod().getName().toString()))
            .count();
    assertEquals(1, invokeCount);
    assertEquals(1, invokeVirtualCount);
  }

  private void assertSingleInvokeStatic(MethodSubject method, Predicate<String> methodNameFilter) {
    long invokeCount =
        method
            .streamInstructions()
            .filter(InstructionSubject::isInvoke)
            .filter(
                instruction -> methodNameFilter.test(instruction.getMethod().getName().toString()))
            .count();
    long invokeVirtualCount =
        method
            .streamInstructions()
            .filter(InstructionSubject::isInvokeStatic)
            .filter(
                instruction -> methodNameFilter.test(instruction.getMethod().getName().toString()))
            .count();
    assertEquals(1, invokeCount);
    assertEquals(1, invokeVirtualCount);
  }

  private void assertSingleInvokeSuper(MethodSubject method) {
    assertSingleInvokeSuper(method, alwaysTrue());
  }

  private void assertSingleInvokeDirect(MethodSubject method) {
    assertSingleInvokeDirect(method, alwaysTrue());
  }

  private void assertSingleInvokeStatic(MethodSubject method) {
    assertSingleInvokeStatic(method, alwaysTrue());
  }

  private void assertSingleInvokeDirect(MethodSubject method, String invokedMethodName) {
    assertSingleInvokeDirect(method, name -> name.equals(invokedMethodName));
  }

  private void assertSingleInvokeVirtual(MethodSubject method, String invokedMethodName) {
    assertSingleInvokeVirtual(method, name -> name.equals(invokedMethodName));
  }

  private void assertSingleInvokeStatic(MethodSubject method, String invokedMethodName) {
    assertSingleInvokeStatic(method, name -> name.equals(invokedMethodName));
  }

  @Test
  public void testD8DexWithNestSupport() throws Exception {
    parameters.assumeDexRuntime();
    assumeTrue(parameters.getApiLevel().getLevel() >= 34);
    // TODO(b/247047415): Update test when a DEX VM natively supporting nests is added.
    assertFalse(parameters.getApiLevel().getLevel() > 34);
    testForD8()
        .addProgramFiles(JDK17_JAR)
        .setMinApi(AndroidApiLevel.U)
        .addOptionsModification(options -> options.emitNestAnnotationsInDex = true)
        .compile()
        .inspect(
            inspector -> {
              ClassSubject innerSub = inspector.clazz("nesthostexample.NestHierachy$InnerSub");
              assertThat(innerSub, isPresent());
              // invokespecial on public super.
              assertSingleInvokeSuper(innerSub.uniqueMethodWithOriginalName("m1"));
              // invokespecial on private super.
              assertSingleInvokeDirect(innerSub.uniqueMethodWithOriginalName("m2"));
              // invokespecial on private super.
              assertSingleInvokeDirect(innerSub.uniqueMethodWithOriginalName("m3"));

              assertSingleInvokeStatic(innerSub.uniqueMethodWithOriginalName("s1"));
              assertSingleInvokeStatic(innerSub.uniqueMethodWithOriginalName("s2"));

              // invoke-virtual on public nest members, invoke-direct on private nest members
              ClassSubject outer = inspector.clazz("nesthostexample.NestHierachy");
              assertThat(outer, isPresent());
              MethodSubject callOnInnerSuper =
                  outer.uniqueMethodWithOriginalName("callOnInnerSuper");
              // invokevirtual on public in nest.
              assertSingleInvokeVirtual(callOnInnerSuper, "m1");
              // invokevirtual on private in nest.
              assertSingleInvokeDirect(callOnInnerSuper, "m2");
              // invokevirtual on private in nest.
              assertSingleInvokeDirect(callOnInnerSuper, "m3");

              assertSingleInvokeStatic(callOnInnerSuper, "s1");
              assertSingleInvokeStatic(callOnInnerSuper, "s2");

              MethodSubject callOnInnerSub = outer.uniqueMethodWithOriginalName("callOnInnerSub");
              // invokevirtual on public in nest.
              assertSingleInvokeVirtual(callOnInnerSub, "m1");
              // invokevirtual on public in nest.
              assertSingleInvokeVirtual(callOnInnerSub, "m2");
              // invokevirtual on private in nest.
              assertSingleInvokeDirect(callOnInnerSub, "m3");

              assertSingleInvokeStatic(callOnInnerSub, "s1");
              assertSingleInvokeStatic(callOnInnerSub, "s2");
            });
  }
}
