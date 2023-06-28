// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.sealed;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static junit.framework.Assert.assertEquals;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedClassesExtendsVerticalMergeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  static final String EXPECTED = StringUtils.lines("Success!");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private void addTestClasses(TestBuilder<?, ?> builder) throws Exception {
    builder
        .addProgramClasses(TestClass.class, Sub1.class, Sub2.class, SubSub.class)
        .addProgramClassFileData(getTransformedClasses());
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Super.class);
    assertThat(clazz, isPresentAndRenamed());
    ClassSubject sub2 = inspector.clazz(Sub2.class);
    assertThat(sub2, isPresentAndRenamed());
    ClassSubject subSub = inspector.clazz(SubSub.class);
    assertThat(subSub, isPresentAndRenamed());
    // TODO(b/227160052): Should be both subSub.asTypeSubject() and sub2.asTypeSubject().
    assertEquals(
        parameters.isCfRuntime() ? ImmutableList.of(sub2.asTypeSubject()) : ImmutableList.of(),
        clazz.getFinalPermittedSubclassAttributes());
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .apply(this::addTestClasses)
        .setMinApi(parameters)
        .addKeepAttributePermittedSubclasses()
        .addKeepPermittedSubclasses(Super.class)
        .addKeepPermittedSubclasses(Sub2.class)
        .addKeepMainRule(TestClass.class)
        .addVerticallyMergedClassesInspector(
            inspector -> {
              inspector.assertMergedIntoSubtype(Sub1.class);
            })
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isDexRuntime(),
            r -> r.assertSuccessWithOutput(EXPECTED),
            parameters.isCfRuntime() && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17),
            r ->
                r.assertFailureWithErrorThatMatches(
                    containsString("cannot inherit from sealed class")),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  public byte[] getTransformedClasses() throws Exception {
    return transformer(Super.class)
        .setPermittedSubclasses(Super.class, Sub1.class, Sub2.class)
        .transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      new SubSub();
      System.out.println("Success!");
    }
  }

  abstract static class Super /* permits Sub1, Sub2 */ {}

  static class Sub1 extends Super {}

  static class Sub2 extends Super {}

  static class SubSub extends Sub1 {}
}
