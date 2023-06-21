// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.ifrule.verticalclassmerging;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class MergedTypeBaseTest extends TestBase {

  static class A {}

  static class B extends A {}

  // TODO(b/287891322): Allow vertical class merging even when C is made public.
  @NoAccessModification
  static class C {}

  // TODO(b/287891322): Allow vertical class merging even when I is made public.
  @NoAccessModification
  interface I {}

  interface J extends I {}

  // TODO(b/287891322): Allow vertical class merging even when K is made public.
  @NoAccessModification
  interface K {}

  static class Unused {}

  private final TestParameters parameters;
  final List<Class<?>> classes;
  final boolean enableVerticalClassMerging;

  public MergedTypeBaseTest(TestParameters parameters, boolean enableVerticalClassMerging) {
    this(parameters, enableVerticalClassMerging, ImmutableList.of());
  }

  public MergedTypeBaseTest(
      TestParameters parameters,
      boolean enableVerticalClassMerging,
      List<Class<?>> additionalClasses) {
    this.parameters = parameters;
    this.enableVerticalClassMerging = enableVerticalClassMerging;
    this.classes =
        ImmutableList.<Class<?>>builder()
            .add(A.class, B.class, C.class, I.class, J.class, K.class, Unused.class, getTestClass())
            .addAll(additionalClasses)
            .build();
  }

  @Parameters(name = "{0}, vertical class merging: {1}")
  public static Collection<Object[]> data() {
    // We don't run this on Proguard, as Proguard does not merge A into B.
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public void configure(R8FullTestBuilder builder) {
    builder
        .addOptionsModification(
            options -> options.enableVerticalClassMerging = enableVerticalClassMerging)
        .enableNoAccessModificationAnnotationsForClasses();
  }

  public abstract Class<?> getTestClass();

  public String getAdditionalKeepRules() {
    return "";
  }

  public abstract String getConditionForProguardIfRule();

  public abstract String getExpectedStdout();

  public void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(Unused.class), isPresent());

    // Verify that A and I are no longer present when vertical class merging is enabled.
    if (enableVerticalClassMerging) {
      assertThat(inspector.clazz(A.class), not(isPresent()));
      assertThat(inspector.clazz(I.class), not(isPresent()));
    }
  }

  @Test
  public void testIfRule() throws Exception {
    String expected = getExpectedStdout();
    assertEquals(expected, runOnJava(getTestClass()));

    testForR8(parameters.getBackend())
        .addProgramClasses(classes)
        .addKeepMainRule(getTestClass())
        .addKeepRules(
            getConditionForProguardIfRule(),
            "-keep class " + Unused.class.getTypeName(),
            getAdditionalKeepRules())
        .addDontObfuscate()
        .setMinApi(parameters)
        .apply(this::configure)
        .run(parameters.getRuntime(), getTestClass())
        .assertSuccessWithOutput(expected)
        .inspect(this::inspect);
  }
}
