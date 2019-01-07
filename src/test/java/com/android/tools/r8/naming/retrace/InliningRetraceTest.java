// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileName;
import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileNameAndLineNumber;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.ForceInline;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InliningRetraceTest extends RetraceTestBase {

  @Parameters(name = "Backend: {0}, mode: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(Backend.values(), CompilationMode.values());
  }

  public InliningRetraceTest(Backend backend, CompilationMode mode) {
    super(backend, mode);
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
          // Even when SourceFile is present retrace replaces the file name in the stack trace.
          assertThat(retracedStackTrace, isSameExceptForFileName(expectedStackTrace));
          assertEquals(expectedActualStackTraceHeight(), actualStackTrace.size());
        });
  }

  @Test
  public void testLineNumberTableOnly() throws Exception {
    runTest(
        ImmutableList.of("-keepattributes LineNumberTable"),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          assertThat(retracedStackTrace, isSameExceptForFileName(expectedStackTrace));
          assertEquals(expectedActualStackTraceHeight(), actualStackTrace.size());
        });
  }

  @Test
  public void testNoLineNumberTable() throws Exception {
    runTest(
        ImmutableList.of(),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          assertThat(retracedStackTrace, isSameExceptForFileNameAndLineNumber(expectedStackTrace));
          assertEquals(expectedActualStackTraceHeight(), actualStackTrace.size());
        });
  }
}

class Main {

  @ForceInline
  public static void method3(long j) {
    System.out.println("In method3");
    throw null;
  }

  @ForceInline
  public static void method2(int j) {
    System.out.println("In method2");
    for (int i = 0; i < 10; i++) {
      method3((long) j);
    }
  }

  @ForceInline
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
