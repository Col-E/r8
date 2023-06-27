// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.sealed;

import static junit.framework.Assert.assertEquals;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeFalse;
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
import com.android.tools.r8.utils.codeinspector.Matchers;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SealedClassesIllegalSubclassTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean keepPermittedSubclassesAttribute;
  static final Matcher<String> EXPECTED = containsString("cannot inherit from sealed class");
  static final String EXPECTED_WITHOUT_PERMITTED_SUBCLASSES_ATTRIBUTE =
      StringUtils.lines("Success!");

  @Parameters(name = "{0}, keepPermittedSubclasses = {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  private void addTestClasses(TestBuilder<?, ?> builder) throws Exception {
    builder
        .addProgramClasses(TestClass.class, Sub1.class, Sub2.class, Sub3.class)
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
        .assertFailureWithErrorThatMatches(EXPECTED);
  }

  @Test
  public void testDesugaring() throws Exception {
    assumeTrue(keepPermittedSubclassesAttribute);
    testForDesugaring(parameters)
        .apply(this::addTestClasses)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            DesugarTestConfiguration::isNotJavac,
            r -> r.assertSuccessWithOutput(EXPECTED_WITHOUT_PERMITTED_SUBCLASSES_ATTRIBUTE),
            c -> parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK17),
            r -> r.assertFailureWithErrorThatMatches(EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject clazz = inspector.clazz(C.class);
    assertThat(clazz, Matchers.isPresentAndRenamed());
    if (!parameters.isCfRuntime()) {
      return;
    }
    assertEquals(
        keepPermittedSubclassesAttribute ? 2 : 0,
        clazz.getFinalPermittedSubclassAttributes().size());
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    assumeFalse(parameters.isDexRuntime() && keepPermittedSubclassesAttribute);
    testForR8(parameters.getBackend())
        .apply(this::addTestClasses)
        .setMinApi(parameters)
        .applyIf(
            keepPermittedSubclassesAttribute,
            TestShrinkerBuilder::addKeepAttributePermittedSubclasses)
        // Keep the sealed class to ensure the PermittedSubclasses attribute stays live.
        .addKeepPermittedSubclasses(C.class)
        // Keep subclasses as the PermittedSubclasses attribute is not rewritten.
        .addKeepRules("-keep class * extends " + C.class.getTypeName())
        .addKeepMainRule(TestClass.class)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            parameters.isDexRuntime()
                || (!keepPermittedSubclassesAttribute
                    && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17)),
            r -> r.assertSuccessWithOutput(EXPECTED_WITHOUT_PERMITTED_SUBCLASSES_ATTRIBUTE),
            parameters.isCfRuntime()
                && parameters.asCfRuntime().isNewerThanOrEqual(CfVm.JDK17)
                && keepPermittedSubclassesAttribute,
            r -> r.assertFailureWithErrorThatMatches(EXPECTED),
            r -> r.assertFailureWithErrorThatThrows(UnsupportedClassVersionError.class));
  }

  public byte[] getTransformedClasses() throws Exception {
    return transformer(C.class).setPermittedSubclasses(C.class, Sub1.class, Sub2.class).transform();
  }

  static class TestClass {

    public static void main(String[] args) {
      new Sub1();
      new Sub2();
      new Sub3();
      System.out.println("Success!");
    }
  }

  abstract static class C /* permits Sub1, Sub2 */ {}

  static class Sub1 extends C {}

  static class Sub2 extends C {}

  static class Sub3 extends C {}
}
