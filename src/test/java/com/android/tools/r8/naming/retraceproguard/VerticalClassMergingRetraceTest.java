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
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.naming.retraceproguard.StackTrace.StackTraceLine;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergingRetraceTest extends RetraceTestBase {
  private Set<StackTraceLine> haveSeenLines = new HashSet<>();

  @Parameters(name = "{0}, mode: {1}, compat: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withCfRuntimes()
            // Runtimes prior to 8 will emit stack trace lines as:
            // Exception in thread "main" java.lang.NullPointerException: throw with null exception
            // 	at com.android.tools.r8.naming.retraceproguard.a.b(SourceFile)
            // PG do not support retracing if no line number is specified.
            .withDexRuntimesStartingFromIncluding(Version.V8_1_0)
            .withAllApiLevels()
            .build(),
        CompilationMode.values(),
        BooleanUtils.values());
  }

  public VerticalClassMergingRetraceTest(
      TestParameters parameters, CompilationMode mode, boolean compat) {
    super(parameters, mode, compat);
  }

  @Override
  public void configure(R8TestBuilder builder) {
    builder.enableInliningAnnotations();
  }

  @Override
  public Collection<Class<?>> getClasses() {
    return ImmutableList.of(getMainClass(), ResourceWrapper.class, TintResources.class);
  }

  @Override
  public Class<?> getMainClass() {
    return MainApp.class;
  }

  private int expectedActualStackTraceHeight() {
    // In RELEASE mode, a synthetic bridge will be added by vertical class merger.
    int height = mode == CompilationMode.RELEASE ? 3 : 2;
    if (parameters.isDexRuntime() && parameters.getDexRuntimeVersion().isDalvik()) {
      // Dalvik places a stack trace line in the bottom.
      height += 1;
    }
    return height;
  }

  private boolean filterSynthesizedMethodWhenLineNumberAvailable(
      StackTraceLine retracedStackTraceLine) {
    return retracedStackTraceLine.lineNumber > 0;
  }

  private boolean filterSynthesizedMethod(StackTraceLine retracedStackTraceLine) {
    return haveSeenLines.add(retracedStackTraceLine)
        && (retracedStackTraceLine.className.contains("ResourceWrapper")
            || retracedStackTraceLine.className.contains("MainApp"));
  }

  @Test
  public void testSourceFileAndLineNumberTable() throws Exception {
    runTest(
        ImmutableList.of("-keepattributes SourceFile,LineNumberTable"),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          // Even when SourceFile is present retrace replaces the file name in the stack trace.
          StackTrace reprocessedStackTrace =
              mode == CompilationMode.DEBUG
                  ? retracedStackTrace
                  : retracedStackTrace.filter(this::filterSynthesizedMethodWhenLineNumberAvailable);
          assertThat(
              reprocessedStackTrace.filter(this::isNotDalvikNativeStartMethod),
              isSameExceptForFileName(
                  expectedStackTrace.filter(this::isNotDalvikNativeStartMethod)));
          assertEquals(expectedActualStackTraceHeight(), actualStackTrace.size());
        });
  }

  @Test
  public void testLineNumberTableOnly() throws Exception {
    assumeTrue(compat);
    assumeTrue(parameters.isDexRuntime());
    runTest(
        ImmutableList.of("-keepattributes LineNumberTable"),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          StackTrace reprocessedStackTrace =
              mode == CompilationMode.DEBUG
                  ? retracedStackTrace
                  : retracedStackTrace.filter(this::filterSynthesizedMethodWhenLineNumberAvailable);
          assertThat(
              reprocessedStackTrace.filter(this::isNotDalvikNativeStartMethod),
              isSameExceptForFileName(
                  expectedStackTrace.filter(this::isNotDalvikNativeStartMethod)));
          assertEquals(expectedActualStackTraceHeight(), actualStackTrace.size());
        });
  }

  @Test
  public void testNoLineNumberTable() throws Exception {
    assumeTrue(compat);
    assumeTrue(parameters.isDexRuntime());
    haveSeenLines.clear();
    runTest(
        ImmutableList.of(),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          StackTrace reprocessedStackTrace =
              mode == CompilationMode.DEBUG
                  ? retracedStackTrace
                  : retracedStackTrace.filter(this::filterSynthesizedMethod);
          assertThat(
              reprocessedStackTrace.filter(this::isNotDalvikNativeStartMethod),
              isSameExceptForFileNameAndLineNumber(
                  expectedStackTrace.filter(this::isNotDalvikNativeStartMethod)));
          assertEquals(expectedActualStackTraceHeight(), actualStackTrace.size());
        });
  }
}

class ResourceWrapper {
  // Will be merged down, and represented as:
  //     java.lang.String ...ResourceWrapper.foo() -> a
  @NeverInline
  String foo() {
    throw null;
  }
}

class TintResources extends ResourceWrapper {}

class MainApp {
  public static void main(String[] args) {
    TintResources t = new TintResources();
    System.out.println(t.foo());
  }
}
