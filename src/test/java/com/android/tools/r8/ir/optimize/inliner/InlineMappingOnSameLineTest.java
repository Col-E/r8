// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner;

import static com.android.tools.r8.naming.retrace.StackTrace.isSame;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.retrace.RetraceTestBase;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InlineMappingOnSameLineTest extends RetraceTestBase {

  @Parameters(name = "{0}, mode: {1}, compat: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withDexRuntimes().withAllApiLevels().build(),
        CompilationMode.values(),
        BooleanUtils.values());
  }

  public InlineMappingOnSameLineTest(
      TestParameters parameters, CompilationMode mode, boolean isCompat) {
    super(parameters, mode, isCompat);
  }

  @Test
  public void testR8() throws Exception {
    runTest(
        ImmutableList.of("-keepattributes SourceFile,LineNumberTable"),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) -> {
          assertThat(retracedStackTrace, isSame(getExpectedStackTrace()));
        });
  }

  @Override
  public void configure(R8TestBuilder<?> builder) {
    builder.enableInliningAnnotations();
  }

  @Override
  public Class<?> getMainClass() {
    return Main.class;
  }

  public static class Main {

    public static void main(String[] args) {
      foo(args.length);
    }

    @NeverInline
    public static void foo(int arg) {
      bar(arg);
      f(arg);
    }

    public static void bar(int arg) {
      f(arg);
      g(arg);
    }

    public static void f(int arg) {
      if (arg == 0) {
        throw new RuntimeException("In f()");
      }
    }

    public static void g(int arg) {
      if (arg == 1) {
        throw new RuntimeException("In g()");
      }
    }
  }
}
