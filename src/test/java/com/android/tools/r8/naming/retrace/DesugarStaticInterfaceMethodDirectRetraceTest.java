// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming.retrace;

import static com.android.tools.r8.naming.retrace.StackTrace.isSameExceptForFileName;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DesugarStaticInterfaceMethodDirectRetraceTest extends RetraceTestBase {

  @Parameters(name = "{0}, mode: {1}, compat: {2}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        CompilationMode.values(),
        BooleanUtils.values());
  }

  public DesugarStaticInterfaceMethodDirectRetraceTest(
      TestParameters parameters, CompilationMode mode, boolean compat) {
    super(parameters, mode, compat);
  }

  @Override
  public Collection<Class<?>> getClasses() {
    return ImmutableList.of(getMainClass(), InterfaceWithStaticMethod.class);
  }

  @Override
  public Class<?> getMainClass() {
    return MainDesugarStaticInterfaceMethodRetraceTest.class;
  }

  @Override
  public void configure(R8TestBuilder<?> builder) {
    builder.enableInliningAnnotations();
  }

  @Test
  public void testSourceFileAndLineNumberTable() throws Exception {
    runTest(
        ImmutableList.of("-keepattributes SourceFile,LineNumberTable"),
        (StackTrace actualStackTrace, StackTrace retracedStackTrace) ->
            assertThat(retracedStackTrace, isSameExceptForFileName(getExpectedStackTrace())));
  }
}

interface InterfaceWithStaticMethod {

  @NeverInline
  static void staticMethod() {
    throw null;
  }
}

class MainDesugarStaticInterfaceMethodRetraceTest {

  public static void main(String[] args) {
    InterfaceWithStaticMethod.staticMethod();
  }
}
