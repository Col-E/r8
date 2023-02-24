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
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InliningRetraceTest extends RetraceTestBase {

  @Parameters(name = "{0}, mode: {1}, compat: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        CompilationMode.values(),
        BooleanUtils.values());
  }

  public InliningRetraceTest(TestParameters parameters, CompilationMode mode, boolean compat) {
    super(parameters, mode, compat);
  }

  @Override
  public Class<?> getMainClass() {
    return Main.class;
  }

  private int expectedActualStackTraceHeight() {
    return mode == CompilationMode.RELEASE ? 1 : 4;
  }

  @Test
  public void testSourceFileAndLineNumberTable() throws Exception {
    runTest(
        ImmutableList.of("-keepattributes SourceFile,LineNumberTable"),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          assertThat(retracedStackTrace, isSame(getExpectedStackTrace()));
          assertEquals(
              expectedActualStackTraceHeight(), actualStackTrace.getStackTraceLines().size());
        });
  }

  @Test
  public void testLineNumberTableOnly() throws Exception {
    assumeTrue(compat);
    assumeTrue(parameters.isDexRuntime());
    runTest(
        ImmutableList.of("-keepattributes LineNumberTable"),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          assertThat(retracedStackTrace, isSameExceptForFileName(getExpectedStackTrace()));
          assertEquals(
              expectedActualStackTraceHeight(), actualStackTrace.getStackTraceLines().size());
        });
  }

  @Test
  public void testNoLineNumberTable() throws Exception {
    assumeTrue(compat);
    assumeTrue(parameters.isDexRuntime());
    runTest(
        ImmutableList.of(),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          assertThat(
              retracedStackTrace, isSameExceptForFileNameAndLineNumber(getExpectedStackTrace()));
          assertEquals(
              expectedActualStackTraceHeight(), actualStackTrace.getStackTraceLines().size());
        });
  }

  @Override
  public void inspect(CodeInspector inspector) {
    if (mode == CompilationMode.RELEASE) {
      assertEquals(compat ? 2 : 1, inspector.clazz(Main.class).allMethods().size());
    }
  }
}

class Main {

  public static void method3(long j) {
    System.out.println("In method3");
    throw null;
  }

  public static void method2(int j) {
    System.out.println("In method2");
    for (int i = 0; i < 10; i++) {
      method3((long) j);
    }
  }

  public static void method1(String s) {
    System.out.println("In method1");
    for (int i = 0; i < 10; i++) {
      method2(Integer.parseInt(s));
    }
  }

  public static void main(String[] args) {
    System.out.println("In main");
    method1("1");
  }
}
