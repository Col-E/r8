// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileName;
import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileNameAndLineNumber;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugarLambdaRetraceTest extends RetraceTestBase {

  @Parameters(name = "{0}, mode: {1}, compat: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        CompilationMode.values(),
        BooleanUtils.values());
  }

  public DesugarLambdaRetraceTest(TestParameters parameters, CompilationMode mode, boolean compat) {
    super(parameters, mode, compat);
  }

  @Override
  public Collection<Class<?>> getClasses() {
    return ImmutableList.of(getMainClass(), ConsumerDesugarLambdaRetraceTest.class);
  }

  @Override
  public Class<?> getMainClass() {
    return MainDesugarLambdaRetraceTest.class;
  }

  private int expectedActualStackTraceHeight() {
    // In DEX release the entire lambda is inlined.
    if (parameters.isDexRuntime()) {
      return mode == CompilationMode.RELEASE ? 1 : 6;
    }
    // In CF release it is not and in debug there is no lambda desugaring thus the shorter stack.
    return mode == CompilationMode.RELEASE ? 2 : 4;
  }

  private void checkIsSame(StackTrace actualStackTrace, StackTrace retracedStackTrace) {
    // Even when SourceFile is present retrace replaces the file name in the stack trace.
    assertThat(retracedStackTrace, isSame(getExpectedStackTrace()));
    assertEquals(expectedActualStackTraceHeight(), actualStackTrace.getStackTraceLines().size());
  }

  private void checkIsSameExceptForFileName(
      StackTrace actualStackTrace, StackTrace retracedStackTrace) {
    // Even when SourceFile is present retrace replaces the file name in the stack trace.
    assertThat(retracedStackTrace, isSameExceptForFileName(getExpectedStackTrace()));
    assertEquals(expectedActualStackTraceHeight(), actualStackTrace.getStackTraceLines().size());
  }

  private void checkIsSameExceptForFileNameAndLineNumber(
      StackTrace actualStackTrace, StackTrace retracedStackTrace) {
    // Even when SourceFile is present retrace replaces the file name in the stack trace.
    assertThat(retracedStackTrace, isSameExceptForFileNameAndLineNumber(getExpectedStackTrace()));
    assertEquals(expectedActualStackTraceHeight(), actualStackTrace.getStackTraceLines().size());
  }

  @Test
  public void testSourceFileAndLineNumberTable() throws Exception {
    runTest(ImmutableList.of("-keepattributes SourceFile,LineNumberTable"), this::checkIsSame);
  }

  @Test
  public void testLineNumberTableOnly() throws Exception {
    assumeTrue(compat);
    assumeTrue(parameters.isDexRuntime());
    runTest(
        ImmutableList.of("-keepattributes LineNumberTable"), this::checkIsSameExceptForFileName);
  }

  @Test
  public void testNoLineNumberTable() throws Exception {
    assumeTrue(compat);
    assumeTrue(parameters.isDexRuntime());
    runTest(ImmutableList.of(), this::checkIsSameExceptForFileNameAndLineNumber);
  }
}

// Custom consumer functional interface, as java.util.function.Consumer is not present on Android.
interface ConsumerDesugarLambdaRetraceTest<T> {
  void accept(T value);
}

class MainDesugarLambdaRetraceTest {

  public static void method2(long j) {
    System.out.println("In method2");
    if (j == 1) throw null;
  }

  public static void method1(String s, ConsumerDesugarLambdaRetraceTest<String> consumer) {
    System.out.println("In method1");
    for (int i = 0; i < 10; i++) {
      consumer.accept(s);
    }
  }

  public static void main(String[] args) {
    System.out.println("In main");
    method1("1", s -> method2(Long.parseLong(s)));
  }
}
