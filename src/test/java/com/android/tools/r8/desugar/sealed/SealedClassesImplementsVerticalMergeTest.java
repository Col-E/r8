// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.sealed;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static junit.framework.Assert.assertEquals;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NoUnusedInterfaceRemoval;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.android.tools.r8.utils.codeinspector.Matchers;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedClassesImplementsVerticalMergeTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  static final String EXPECTED = StringUtils.lines("Success!");

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  private void addTestClasses(TestBuilder<?, ?> builder) throws Exception {
    builder
        .addProgramClasses(TestClass.class, Super.class, Sub1.class, Sub2.class, SubSub.class)
        .addProgramClassFileData(getTransformedClasses());
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject iface1 = inspector.clazz(Iface1.class);
    assertThat(iface1, isPresentAndRenamed());
    ClassSubject iface2 = inspector.clazz(Iface2.class);
    assertThat(iface2, isPresentAndRenamed());
    ClassSubject ifaceUnrelated = inspector.clazz(IfaceUnrelated.class);
    assertThat(ifaceUnrelated, isPresentAndRenamed());
    ClassSubject sub2 = inspector.clazz(Sub2.class);
    assertThat(sub2, isPresentAndRenamed());
    ClassSubject subSub = inspector.clazz(SubSub.class);
    assertThat(subSub, Matchers.isPresentAndRenamed());
    for (ClassSubject clazz : ImmutableList.of(iface1, iface2, ifaceUnrelated)) {
      assertEquals(
          parameters.isCfRuntime()
              ? ImmutableList.of(subSub.asTypeSubject(), sub2.asTypeSubject())
              : ImmutableList.of(),
          clazz.getFinalPermittedSubclassAttributes());
    }
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .apply(this::addTestClasses)
        .setMinApi(parameters)
        .addKeepAttributePermittedSubclasses()
        .addKeepPermittedSubclasses(
            Super.class, Iface1.class, Iface2.class, Sub2.class, IfaceUnrelated.class)
        .addKeepMainRule(TestClass.class)
        .addVerticallyMergedClassesInspector(
            inspector -> {
              inspector.assertMergedIntoSubtype(Sub1.class);
            })
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .enableNoUnusedInterfaceRemovalAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isDexRuntime() || parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertSuccessWithOutput(EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  public List<byte[]> getTransformedClasses() throws Exception {
    return ImmutableList.of(
        transformer(Iface1.class)
            .setPermittedSubclasses(Iface1.class, Sub1.class, Sub2.class)
            .transform(),
        transformer(Iface2.class)
            .setPermittedSubclasses(Iface2.class, Sub1.class, Sub2.class)
            .transform(),
        transformer(IfaceUnrelated.class)
            .setPermittedSubclasses(IfaceUnrelated.class, Sub1.class, Sub2.class)
            .transform());
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new SubSub());
    }
  }

  @NoUnusedInterfaceRemoval
  interface Iface1 /* permits Sub1, Sub2 */ {}

  @NoUnusedInterfaceRemoval
  interface Iface2 /* permits Sub1, Sub2 */ {}

  @NoUnusedInterfaceRemoval
  interface IfaceUnrelated /* permits Sub1, Sub2 */ {}

  abstract static class Super {}

  static class Sub1 extends Super implements Iface1, Iface2 {}

  static class Sub2 extends Super implements Iface1 {}

  static class SubSub extends Sub1 {

    @Override
    public String toString() {
      return "Success!";
    }
  }
}
