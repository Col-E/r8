// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retraceproguard;

import static com.android.tools.r8.naming.retraceproguard.StackTrace.isSameExceptForFileName;
import static com.android.tools.r8.naming.retraceproguard.StackTrace.isSameExceptForFileNameAndLineNumber;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.BooleanUtils;
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
        getTestParameters()
            .withCfRuntimes()
            .withDexRuntimesStartingFromIncluding(Version.V5_1_1)
            .withAllApiLevels()
            .build(),
        CompilationMode.values(),
        BooleanUtils.values());
  }

  public InliningRetraceTest(TestParameters parameters, CompilationMode mode, boolean value) {
    super(parameters, mode, value);
  }

  @Override
  public Class<?> getMainClass() {
    return Main.class;
  }

  private int expectedActualStackTraceHeight() {
    int height = mode == CompilationMode.RELEASE ? 1 : 4;
    if (parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik()) {
      // Dalvik places a stack trace line in the bottom.
      height += 1;
    }
    return height;
  }

  @Test
  public void testSourceFileAndLineNumberTable() throws Exception {
    assumeTrue("b/288405478", mode.isRelease());
    runTest(
        ImmutableList.of("-keepattributes SourceFile,LineNumberTable"),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          // Even when SourceFile is present retrace replaces the file name in the stack trace.
          assertThat(retracedStackTrace, isSameExceptForFileName(expectedStackTrace));
          assertEquals(expectedActualStackTraceHeight(), actualStackTrace.size());
        });
  }

  @Test
  public void testLineNumberTableOnly() throws Exception {
    assumeTrue("b/288405478", mode.isRelease());
    assumeTrue(compat);
    assumeTrue(parameters.isDexRuntime());
    runTest(
        ImmutableList.of("-keepattributes LineNumberTable"),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          assertThat(retracedStackTrace, isSameExceptForFileName(expectedStackTrace));
          assertEquals(expectedActualStackTraceHeight(), actualStackTrace.size());
        });
  }

  @Test
  public void testNoLineNumberTable() throws Exception {
    assumeTrue("b/288405478", mode.isRelease());
    assumeTrue(compat);
    assumeTrue(parameters.isDexRuntime());
    runTest(
        ImmutableList.of(),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          assertThat(retracedStackTrace, isSameExceptForFileNameAndLineNumber(expectedStackTrace));
          assertEquals(expectedActualStackTraceHeight(), actualStackTrace.size());
        });
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
