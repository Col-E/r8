// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.sealed;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.DesugarTestConfiguration;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestBuilder;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedClassesExtendsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean keepPermittedSubclassesAttribute;

  @Parameter(2)
  public boolean repackage;

  static final String EXPECTED = StringUtils.lines("Success!");

  @Parameters(name = "{0}, keepPermittedSubclasses = {1}, repackage = {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  private void addTestClasses(TestBuilder<?, ?> builder) throws Exception {
    builder
        .addProgramClasses(TestClass.class, Sub1.class, Sub2.class)
        .addProgramClassFileData(getTransformedClasses());
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    assumeTrue(keepPermittedSubclassesAttribute);
    assumeTrue(parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17));
    testForJvm(parameters)
        .apply(this::addTestClasses)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testDesugaring() throws Exception {
    assumeTrue(keepPermittedSubclassesAttribute);
    testForDesugaring(parameters)
        .apply(this::addTestClasses)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            c ->
                DesugarTestConfiguration.isNotJavac(c)
                    || parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertSuccessWithOutput(EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(Super.class);
    assertThat(clazz, isPresentAndRenamed());
    ClassSubject sub1 = inspector.clazz(Sub1.class);
    ClassSubject sub2 = inspector.clazz(Sub2.class);
    assertThat(sub1, isPresentAndRenamed());
    assertThat(sub2, isPresentAndRenamed());
    if (repackage) {
      assertEquals(-1, sub1.getFinalName().indexOf('.'));
    } else {
      assertTrue(sub1.getFinalName().startsWith(getClass().getPackage().getName()));
    }
    assertEquals(
        parameters.isCfRuntime() && keepPermittedSubclassesAttribute
            ? ImmutableList.of(sub1.asTypeSubject(), sub2.asTypeSubject())
            : ImmutableList.of(),
        clazz.getFinalPermittedSubclassAttributes());
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .apply(this::addTestClasses)
        .setMinApi(parameters)
        .applyIf(
            keepPermittedSubclassesAttribute,
            TestShrinkerBuilder::addKeepAttributePermittedSubclasses)
        // Keep the sealed class to ensure the PermittedSubclasses attribute stays live.
        .addKeepPermittedSubclasses(Super.class, Sub1.class, Sub2.class)
        .addKeepMainRule(TestClass.class)
        .applyIf(repackage, b -> b.addKeepRules("-repackageclasses"))
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            !parameters.isCfRuntime() || parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertSuccessWithOutput(EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  public byte[] getTransformedClasses() throws Exception {
    return transformer(Super.class)
        .setPermittedSubclasses(Super.class, Sub1.class, Sub2.class)
        .transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      new Sub1();
      new Sub2();
      System.out.println("Success!");
    }
  }

  public abstract static class Super /* permits Sub1, Sub2 */ {}

  public static class Sub1 extends Super {}

  public static class Sub2 extends Super {}
}
