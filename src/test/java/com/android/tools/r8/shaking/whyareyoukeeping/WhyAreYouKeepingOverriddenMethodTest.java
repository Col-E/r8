// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.whyareyoukeeping;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test is changed based on the discussion in b/139794417 to not include overridden members
 * which is compatible with the proguard implementation.
 */
@RunWith(Parameterized.class)
public class WhyAreYouKeepingOverriddenMethodTest extends TestBase {
  @Parameters(name = "{0} minification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(getTestParameters().withNoneRuntime().build(), BooleanUtils.values());
  }

  private final TestParameters parameters;
  private final boolean minification;

  public WhyAreYouKeepingOverriddenMethodTest(TestParameters parameters, boolean minification) {
    this.parameters = parameters;
    this.minification = minification;
  }

  private void testViaConfig(Class<?> main, Class<?> targetClass, Class<?> subClass)
      throws Exception {
    testForR8(Backend.DEX)
        .addInnerClasses(WhyAreYouKeepingOverriddenMethodTest.class)
        .addKeepMainRule(main)
        .addKeepRules(
            "-whyareyoukeeping class **.*$" + targetClass.getSimpleName() + " { void gone(); }")
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .minification(minification)
        .setMinApi(AndroidApiLevel.B)
        // Redirect the compilers stdout to intercept the '-whyareyoukeeping' output
        .collectStdout()
        .compile()
        .assertStdoutThatMatches(containsString(expectedMessage(targetClass)))
        .assertStdoutThatMatches(not(containsString(expectedNotContainingMessage(subClass))));
  }

  private void testViaConsumer(
      Class<?> main, Class<?> targetClass, Class<?> subClass) throws Exception {
    WhyAreYouKeepingConsumer graphConsumer = new WhyAreYouKeepingConsumer(null);
    testForR8(Backend.DEX)
        .addInnerClasses(WhyAreYouKeepingOverriddenMethodTest.class)
        .addKeepMainRule(main)
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .minification(minification)
        .setMinApi(AndroidApiLevel.B)
        .setKeptGraphConsumer(graphConsumer)
        .compile();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(baos);
    graphConsumer.printWhyAreYouKeeping(
        Reference.methodFromMethod(targetClass.getMethod("gone")), printStream);
    String output = new String(baos.toByteArray(), StandardCharsets.UTF_8);
    assertThat(output, containsString(expectedMessage(targetClass)));
    assertThat(output, not(containsString(expectedNotContainingMessage(subClass))));
  }

  private String expectedMessage(Class<?> targetClass) {
    return "Nothing is keeping void " + targetClass.getTypeName() + ".gone()";
  }

  private String expectedNotContainingMessage(Class<?> subClass) {
    return "void " + subClass.getTypeName() + ".gone()";
  }

  @Test
  public void testExtends_config() throws Exception {
    testViaConfig(TestMain1.class, Base.class, Sub.class);
  }

  @Test
  public void testExtends_consumer() throws Exception {
    testViaConsumer(TestMain1.class, Base.class, Sub.class);
  }

  @Test
  public void testImplements_config() throws Exception {
    testViaConfig(TestMain2.class, Itf.class, Impl.class);
  }

  @Test
  public void testImplements_consumer() throws Exception {
    testViaConsumer(TestMain2.class, Itf.class, Impl.class);
  }

  @NoVerticalClassMerging
  static class Base {
    @NeverInline
    public void gone() {
      System.out.println("should be gone");
    }
  }

  @NeverClassInline
  static class Sub extends Base {
    @NeverInline
    @Override
    public void gone() {
      System.out.println("used");
    }
  }

  static class TestMain1 {
    public static void main(String... args) {
      new Sub().gone();
    }
  }

  @NoVerticalClassMerging
  interface Itf {
    void gone();
  }

  @NeverClassInline
  static class Impl implements Itf {
    @NeverInline
    @Override
    public void gone() {
      System.out.println("used");
    }
  }

  static class TestMain2 {
    public static void main(String... args) {
      new Impl().gone();
    }
  }
}
