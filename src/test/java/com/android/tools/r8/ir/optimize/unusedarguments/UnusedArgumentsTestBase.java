// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.unusedarguments;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class UnusedArgumentsTestBase extends TestBase {

  protected final TestParameters parameters;
  protected final boolean minification;

  public UnusedArgumentsTestBase(TestParameters parameters, boolean minification) {
    this.parameters = parameters;
    this.minification = minification;
  }

  @Parameters(name = "{0}, minification:{1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        TestParameters.builder().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public void configure(R8FullTestBuilder builder) {
    builder.enableInliningAnnotations();
  }

  public abstract Class<?> getTestClass();

  public Collection<Class<?>> getAdditionalClasses() {
    return ImmutableList.of();
  }

  public abstract String getExpectedResult();

  public void inspectTestClass(ClassSubject clazz) {}

  public void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(getTestClass());
    assertThat(clazz, isPresent());
    inspectTestClass(clazz);
  }

  @Test
  public void testReference() throws Exception {
    assumeFalse(minification);
    testForRuntime(parameters)
        .addProgramClasses(getTestClass())
        .addProgramClasses(getAdditionalClasses())
        .run(parameters.getRuntime(), getTestClass())
        .assertSuccessWithOutput(getExpectedResult());
  }

  @Test
  public void testR8() throws Throwable {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClasses(getTestClass())
        .addProgramClasses(getAdditionalClasses())
        .addKeepMainRule(getTestClass())
        .minification(minification)
        .addOptionsModification(options -> options.enableSideEffectAnalysis = false)
        .apply(this::configure)
        .run(parameters.getRuntime(), getTestClass())
        .inspect(this::inspect)
        .assertSuccessWithOutput(getExpectedResult());
  }
}
