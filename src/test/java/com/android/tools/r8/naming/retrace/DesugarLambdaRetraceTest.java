// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileName;
import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileNameAndLineNumber;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.naming.retrace.StackTrace.StackTraceLine;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugarLambdaRetraceTest extends RetraceTestBase {

  @Parameters(name = "Backend: {0}, mode: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(Backend.values(), CompilationMode.values());
  }

  public DesugarLambdaRetraceTest(Backend backend, CompilationMode mode) {
    super(backend, mode);
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
    // In debug mode the expected stack trace height differs since there is no lambda desugaring
    // for CF.
    return mode == CompilationMode.RELEASE ? 2 : (backend == Backend.CF ? 4 : 5);
  }

  private boolean isSynthesizedLambdaFrame(StackTraceLine line) {
    return line.className.contains("-$$Lambda$");
  }

  private void checkLambdaFrame(StackTrace retracedStackTrace) {
    StackTrace lambdaFrames = retracedStackTrace.filter(this::isSynthesizedLambdaFrame);
    assertEquals(1, lambdaFrames.size());
    if (lambdaFrames.get(0).hasLineNumber()) {
      assertEquals(mode == CompilationMode.RELEASE ? 0 : 2, lambdaFrames.get(0).lineNumber);
    }
    // Proguard retrace will take the class name until the first $ to construct the file
    // name, so for "-$$Lambda$...", the file name becomes "-.java".
    assertEquals("-.java", lambdaFrames.get(0).fileName);
  }

  private void checkIsSameExceptForFileName(
      StackTrace actualStackTrace, StackTrace retracedStackTrace) {
    // Even when SourceFile is present retrace replaces the file name in the stack trace.
    if (backend == Backend.CF) {
      // TODO(122440196): Additional code to locate issue.
      if (!isSameExceptForFileName(expectedStackTrace).matches(retracedStackTrace)) {
        System.out.println("Expected original:");
        System.out.println(expectedStackTrace.getOriginalStderr());
        System.out.println("Actual original:");
        System.out.println(retracedStackTrace.getOriginalStderr());
        System.out.println("Parsed original:");
        System.out.println(expectedStackTrace);
        System.out.println("Parsed retraced:");
        System.out.println(retracedStackTrace);
      }
      assertThat(retracedStackTrace, isSameExceptForFileName(expectedStackTrace));
    } else {
      // TODO(122440196): Additional code to locate issue.
      if (!isSameExceptForFileName(expectedStackTrace)
          .matches(retracedStackTrace.filter(line -> !isSynthesizedLambdaFrame(line)))) {
        System.out.println("Expected original:");
        System.out.println(expectedStackTrace.getOriginalStderr());
        System.out.println("Actual original:");
        System.out.println(retracedStackTrace.getOriginalStderr());
        System.out.println("Parsed original:");
        System.out.println(expectedStackTrace);
        System.out.println("Parsed retraced:");
        System.out.println(retracedStackTrace);
      }
      // With the frame from the lambda class filtered out the stack trace is the same.
      assertThat(
          retracedStackTrace.filter(line -> !isSynthesizedLambdaFrame(line)),
          isSameExceptForFileName(expectedStackTrace));
      // Check the frame from the lambda class.
      checkLambdaFrame(retracedStackTrace);
    }
    assertEquals(expectedActualStackTraceHeight(), actualStackTrace.size());
  }

  private void checkIsSameExceptForFileNameAndLineNumber(
      StackTrace actualStackTrace, StackTrace retracedStackTrace) {
    // Even when SourceFile is present retrace replaces the file name in the stack trace.
    if (backend == Backend.CF) {
      // TODO(122440196): Additional code to locate issue.
      if (!isSameExceptForFileNameAndLineNumber(expectedStackTrace).matches(retracedStackTrace)) {
        System.out.println("Expected original:");
        System.out.println(expectedStackTrace.getOriginalStderr());
        System.out.println("Actual original:");
        System.out.println(retracedStackTrace.getOriginalStderr());
        System.out.println("Parsed original:");
        System.out.println(expectedStackTrace);
        System.out.println("Parsed retraced:");
        System.out.println(retracedStackTrace);
      }
      assertThat(retracedStackTrace, isSameExceptForFileNameAndLineNumber(expectedStackTrace));
    } else {
      // TODO(122440196): Additional code to locate issue.
      if (!isSameExceptForFileNameAndLineNumber(expectedStackTrace)
          .matches(retracedStackTrace.filter(line -> !isSynthesizedLambdaFrame(line)))) {
        System.out.println("Expected original:");
        System.out.println(expectedStackTrace.getOriginalStderr());
        System.out.println("Actual original:");
        System.out.println(retracedStackTrace.getOriginalStderr());
        System.out.println("Parsed original:");
        System.out.println(expectedStackTrace);
        System.out.println("Parsed retraced:");
        System.out.println(retracedStackTrace);
      }
      // With the frame from the lambda class filtered out the stack trace is the same.
      assertThat(
          retracedStackTrace.filter(line -> !isSynthesizedLambdaFrame(line)),
          isSameExceptForFileNameAndLineNumber(expectedStackTrace));
      // Check the frame from the lambda class.
      checkLambdaFrame(retracedStackTrace);
    }
    assertEquals(expectedActualStackTraceHeight(), actualStackTrace.size());
  }

  @Test
  public void testSourceFileAndLineNumberTable() throws Exception {
    runTest(
        ImmutableList.of("-keepattributes SourceFile,LineNumberTable"),
        this::checkIsSameExceptForFileName);
  }

  @Test
  public void testLineNumberTableOnly() throws Exception {
    runTest(
        ImmutableList.of("-keepattributes LineNumberTable"),
        this::checkIsSameExceptForFileName);
  }

  @Test
  public void testNoLineNumberTable() throws Exception {
    runTest(
        ImmutableList.of(),
        this::checkIsSameExceptForFileNameAndLineNumber);
  }
}

// Custom consumer functional interface, as java.util.function.Consumer is not present on Android.
@FunctionalInterface
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
