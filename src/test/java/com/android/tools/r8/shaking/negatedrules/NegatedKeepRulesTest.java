// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.negatedrules;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NegatedKeepRulesTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntime(CfVm.JDK9).withDexRuntime(Version.DEFAULT).build();
  }

  public NegatedKeepRulesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testPlainR8Compat() throws Exception {
    testPlain(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testPlainR8Full() throws Exception {
    testPlain(testForR8(parameters.getBackend()));
  }

  @Test
  public void testPlainProguard() throws Exception {
    testPlain(testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"));
  }

  public void testPlain(TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    run(testBuilder.addKeepRules("-keep class " + A.class.getTypeName() + " { *; }"))
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), isPresent());
              assertThat(inspector.clazz(B.class), not(isPresent()));
            });
  }

  @Test
  public void testNegationR8Compat() throws Exception {
    // TODO(b/174824047): Should not emit info regarding unused keep rule.
    // TODO(b/174824047): The class A should be present.
    testNegation(
        testForR8Compat(parameters.getBackend()).allowUnusedProguardConfigurationRules(),
        not(isPresent()));
  }

  @Test
  public void testNegationPlainR8Full() throws Exception {
    // TODO(b/174824047): Should not emit info regarding unused keep rule.
    // TODO(b/174824047): The class A should be present.
    testNegation(
        testForR8(parameters.getBackend()).allowUnusedProguardConfigurationRules(),
        not(isPresent()));
  }

  @Test
  public void testNegationProguard() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testNegation(testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"), isPresent());
  }

  public void testNegation(
      TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder, Matcher<? super ClassSubject> matcher)
      throws Exception {
    run(testBuilder.addKeepRules("-keep class !" + B.class.getTypeName() + " { *; }"))
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), matcher);
              assertThat(inspector.clazz(B.class), not(isPresent()));
            });
  }

  @Test
  public void testExtendsR8Compat() throws Exception {
    testExtends(testForR8Compat(parameters.getBackend()), isPresent());
  }

  @Test
  public void testExtendsR8Full() throws Exception {
    testExtends(testForR8(parameters.getBackend()), not(isPresent()));
  }

  @Test
  public void testExtendsProguard() throws Exception {
    testExtends(testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"), isPresent());
  }

  public void testExtends(
      TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder, Matcher<? super ClassSubject> matcher)
      throws Exception {
    run(testBuilder.addKeepRules("-keep class ** extends " + A.class.getTypeName() + " { *; }"))
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), matcher);
              assertThat(inspector.clazz(B.class), isPresentAndNotRenamed());
            });
  }

  @Test
  public void testNegatedExtendsR8Compat() throws Exception {
    // TODO(b/174824047): Should not emit info regarding unused keep rule.
    testNegatedExtends(
        testForR8Compat(parameters.getBackend()).allowUnusedProguardConfigurationRules());
  }

  @Test
  public void testNegatedExtendsR8Full() throws Exception {
    // TODO(b/174824047): Should not emit info regarding unused keep rule.
    testNegatedExtends(testForR8(parameters.getBackend()).allowUnusedProguardConfigurationRules());
  }

  @Test
  public void testNegatedExtendsProguard() throws Exception {
    testNegatedExtends(testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"));
  }

  public void testNegatedExtends(TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    run(testBuilder
            .addKeepRules("-keep class !** extends " + A.class.getTypeName() + " { *; }")
            .addKeepClassRules(C.class)) // <-- kept to have an output
        .inspect(
            inspector -> {
              // TODO(b/174824047): These seems to be incorrect behavior, since one could expect A
              //  and C to be kept and not B, since (A extends A) = (C extends A) = false and the
              //  negation would become true.
              assertThat(inspector.clazz(A.class), not(isPresent()));
              assertThat(inspector.clazz(B.class), not(isPresent()));
              assertThat(inspector.clazz(C.class), isPresent());
            });
  }

  @Test
  public void testNegatedWithStarsR8Compat() throws Exception {
    // TODO(b/174824047): Should not emit info regarding unused keep rule.
    testNegatedWithStars(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testNegatedWithStarsR8Full() throws Exception {
    // TODO(b/174824047): Should not emit info regarding unused keep rule.
    testNegatedWithStars(testForR8(parameters.getBackend()));
  }

  @Test
  public void testNegatedWithStarsProguard() throws Exception {
    testNegatedWithStars(testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"));
  }

  public void testNegatedWithStars(TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder)
      throws Exception {
    run(testBuilder.addKeepRules("-keep class !" + B.class.getTypeName() + ", ** { *; }"))
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), isPresent());
              assertThat(inspector.clazz(B.class), not(isPresent()));
            });
  }

  @Test
  public void testMultipleNegatedWithStarsR8Compat() throws Exception {
    // TODO(b/174824047): Should not emit info regarding unused keep rule.
    testMultipleNegatedWithStars(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testMultipleNegatedWithStarsR8Full() throws Exception {
    // TODO(b/174824047): Should not emit info regarding unused keep rule.
    testMultipleNegatedWithStars(testForR8(parameters.getBackend()));
  }

  @Test
  public void testMultipleWithStarsProguard() throws Exception {
    testMultipleNegatedWithStars(testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"));
  }

  public void testMultipleNegatedWithStars(TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder)
      throws Exception {
    run(testBuilder.addKeepRules(
            "-keep class !" + B.class.getTypeName() + ",!" + C.class.getTypeName() + ", ** { *; }"))
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), isPresent());
              assertThat(inspector.clazz(B.class), not(isPresent()));
            });
  }

  private TestCompileResult<?, ?> run(TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder)
      throws Exception {
    return testBuilder
        .addProgramClasses(A.class, B.class, C.class)
        .setMinApi(AndroidApiLevel.B)
        .noMinification()
        .compile();
  }

  public static class A {}

  public static class B extends A {

    public static String test() {
      return "Hello World!";
    }
  }

  public static class C {}
}
