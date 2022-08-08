// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.negatedrules;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ProguardVersion;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.Subject;
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
    return getTestParameters().withDefaultCfRuntime().withDefaultDexRuntime().build();
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
              assertThat(inspector.clazz(C.class), not(isPresent()));
              assertThat(inspector.clazz(D.class), not(isPresent()));
              assertThat(inspector.clazz(FooBar.class), not(isPresent()));
              assertThat(inspector.clazz(BarBar.class), not(isPresent()));
            });
  }

  @Test
  public void testNegationR8Compat() throws Exception {
    testNegation(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testNegationPlainR8Full() throws Exception {
    testNegation(testForR8(parameters.getBackend()));
  }

  @Test
  public void testNegationProguard() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testNegation(testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"));
  }

  public void testNegation(TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    run(testBuilder.addKeepRules("-keep class !" + B.class.getTypeName() + " { *; }"))
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), isPresent());
              assertThat(inspector.clazz(B.class), not(isPresent()));
              assertThat(inspector.clazz(C.class), isPresent());
              assertThat(inspector.clazz(D.class), isPresent());
              assertThat(inspector.clazz(FooBar.class), isPresent());
              assertThat(inspector.clazz(BarBar.class), isPresent());
            });
  }

  @Test
  public void testExtendsR8Compat() throws Exception {
    testExtends(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testExtendsR8Full() throws Exception {
    testExtends(testForR8(parameters.getBackend()));
  }

  @Test
  public void testExtendsProguard() throws Exception {
    testExtends(testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"));
  }

  public void testExtends(TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    run(testBuilder.addKeepRules("-keep class ** extends " + A.class.getTypeName() + " { *; }"))
        .inspect(
            inspector -> {
              // A is only kept in full-mode because we are keeping two sub-types. For full-mode,
              // A could be removed. This is shown in the testNegatedExtends test.
              assertThat(inspector.clazz(A.class), isPresent());
              assertThat(inspector.clazz(B.class), isPresent());
              assertThat(inspector.clazz(C.class), not(isPresent()));
              assertThat(inspector.clazz(D.class), isPresent());
              assertThat(inspector.clazz(FooBar.class), not(isPresent()));
              assertThat(inspector.clazz(BarBar.class), not(isPresent()));
            });
  }

  @Test
  public void testNegatedExtendsR8Compat() throws Exception {
    testNegatedExtends(testForR8Compat(parameters.getBackend()), isPresent());
  }

  @Test
  public void testNegatedExtendsR8Full() throws Exception {
    testNegatedExtends(testForR8(parameters.getBackend()), not(isPresent()));
  }

  @Test
  public void testNegatedExtendsProguard() throws Exception {
    testNegatedExtends(
        testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"), isPresent());
  }

  public void testNegatedExtends(
      TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder, Matcher<Subject> aMatcher) throws Exception {
    // The negation binds closer than extends (at least for us).
    run(testBuilder.addKeepRules("-keep class !**B extends " + A.class.getTypeName() + " { *; }"))
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), aMatcher);
              assertThat(inspector.clazz(B.class), not(isPresent()));
              assertThat(inspector.clazz(C.class), not(isPresent()));
              assertThat(inspector.clazz(D.class), isPresent());
              assertThat(inspector.clazz(FooBar.class), not(isPresent()));
              assertThat(inspector.clazz(BarBar.class), not(isPresent()));
            });
  }

  @Test
  public void testNegatedWithStarsR8Compat() throws Exception {
    testNegatedWithStars(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testNegatedWithStarsR8Full() throws Exception {
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
              assertThat(inspector.clazz(C.class), isPresent());
              assertThat(inspector.clazz(D.class), isPresent());
              assertThat(inspector.clazz(FooBar.class), isPresent());
              assertThat(inspector.clazz(BarBar.class), isPresent());
            });
  }

  @Test
  public void testMultipleNegatedWithStarsR8Compat() throws Exception {
    testMultipleNegatedWithStars(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testMultipleNegatedWithStarsR8Full() throws Exception {
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
              assertThat(inspector.clazz(C.class), not(isPresent()));
              assertThat(inspector.clazz(D.class), isPresent());
              assertThat(inspector.clazz(FooBar.class), isPresent());
              assertThat(inspector.clazz(BarBar.class), isPresent());
            });
  }

  @Test
  public void testFooBarR8Compat() throws Exception {
    testFooBar(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testFooBarR8Full() throws Exception {
    testFooBar(testForR8(parameters.getBackend()));
  }

  @Test
  public void testFooBarProguard() throws Exception {
    testFooBar(testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"));
  }

  public void testFooBar(TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    run(testBuilder.addKeepRules("-keep class !" + FooBar.class.getTypeName() + ", **Bar { *; }"))
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(BarBar.class), isPresent());
              assertThat(inspector.clazz(FooBar.class), not(isPresent()));
              assertThat(inspector.clazz(A.class), not(isPresent()));
              assertThat(inspector.clazz(B.class), not(isPresent()));
              assertThat(inspector.clazz(C.class), not(isPresent()));
              assertThat(inspector.clazz(D.class), not(isPresent()));
            });
  }

  @Test
  public void testMultipleNegatedR8Compat() throws Exception {
    testMultipleNegated(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testMultipleNegatedR8Full() throws Exception {
    testMultipleNegated(testForR8(parameters.getBackend()));
  }

  @Test
  public void testMultipleNegatedProguard() throws Exception {
    testMultipleNegated(testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"));
  }

  public void testMultipleNegated(TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder) throws Exception {
    run(testBuilder.addKeepRules("-keep class !" + FooBar.class.getTypeName() + ", !**Bar { *; }"))
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(BarBar.class), not(isPresent()));
              assertThat(inspector.clazz(FooBar.class), not(isPresent()));
              assertThat(inspector.clazz(A.class), isPresent());
              assertThat(inspector.clazz(B.class), isPresent());
              assertThat(inspector.clazz(C.class), isPresent());
              assertThat(inspector.clazz(D.class), isPresent());
            });
  }

  @Test
  public void testNegatedWithPositiveR8Compat() throws Exception {
    testNegatedWithPositive(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testNegatedWithPositiveR8Full() throws Exception {
    testNegatedWithPositive(testForR8(parameters.getBackend()));
  }

  @Test
  public void testNegatedWithPositiveProguard() throws Exception {
    testNegatedWithPositive(testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"));
  }

  public void testNegatedWithPositive(TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder)
      throws Exception {
    run(testBuilder.addKeepRules("-keep class !**$Foo*,**Bar { *; }"))
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(BarBar.class), isPresent());
              assertThat(inspector.clazz(FooBar.class), not(isPresent()));
              assertThat(inspector.clazz(A.class), not(isPresent()));
              assertThat(inspector.clazz(B.class), not(isPresent()));
              assertThat(inspector.clazz(C.class), not(isPresent()));
              assertThat(inspector.clazz(D.class), not(isPresent()));
            });
  }

  @Test
  public void testPositiveWithNegatedR8Compat() throws Exception {
    testPositiveWithNegated(testForR8Compat(parameters.getBackend()));
  }

  @Test
  public void testPositiveWithNegatedR8Full() throws Exception {
    testPositiveWithNegated(testForR8(parameters.getBackend()));
  }

  @Test
  public void testPositiveWithNegatedProguard() throws Exception {
    testPositiveWithNegated(testForProguard(ProguardVersion.V7_0_0).addKeepRules("-dontwarn"));
  }

  public void testPositiveWithNegated(TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder)
      throws Exception {
    run(testBuilder.addKeepRules("-keep class **Bar,!**$Foo* { *; }"))
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(BarBar.class), isPresent());
              assertThat(inspector.clazz(FooBar.class), isPresent());
              assertThat(inspector.clazz(A.class), isPresent());
              assertThat(inspector.clazz(B.class), isPresent());
              assertThat(inspector.clazz(C.class), isPresent());
              assertThat(inspector.clazz(D.class), isPresent());
            });
  }

  private TestCompileResult<?, ?> run(TestShrinkerBuilder<?, ?, ?, ?, ?> testBuilder)
      throws Exception {
    return testBuilder
        .addProgramClasses(A.class, B.class, C.class, D.class, FooBar.class, BarBar.class)
        .setMinApi(AndroidApiLevel.B)
        .addDontObfuscate()
        .compile();
  }

  public static class A {}

  public static class B extends A {}

  public static class C {}

  public static class D extends A {}

  public static class FooBar {}

  public static class BarBar {}
}
