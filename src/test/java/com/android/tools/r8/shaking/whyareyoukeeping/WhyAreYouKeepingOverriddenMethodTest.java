// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.whyareyoukeeping;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverMerge;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.WhyAreYouKeepingConsumer;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

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
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    testForR8(Backend.DEX)
        .addInnerClasses(WhyAreYouKeepingOverriddenMethodTest.class)
        .addKeepMainRule(main)
        .addKeepRules(
            "-whyareyoukeeping class **.*$" + targetClass.getSimpleName() + " { void gone(); }")
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableMergeAnnotations()
        .minification(minification)
        .setMinApi(parameters.getRuntime())
        // Redirect the compilers stdout to intercept the '-whyareyoukeeping' output
        .redirectStdOut(new PrintStream(baos))
        .compile();
    String output = new String(baos.toByteArray(), StandardCharsets.UTF_8);
    assertThat(output, containsString(expectedMessage(main, targetClass, subClass)));
  }

  private void testViaConsumer(
      Class<?> main, Class<?> targetClass, Class<?> subClass) throws Exception {
    WhyAreYouKeepingConsumer graphConsumer = new WhyAreYouKeepingConsumer(null);
    testForR8(Backend.DEX)
        .addInnerClasses(WhyAreYouKeepingOverriddenMethodTest.class)
        .addKeepMainRule(main)
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .enableMergeAnnotations()
        .minification(minification)
        .setMinApi(parameters.getRuntime())
        .setKeptGraphConsumer(graphConsumer)
        .compile();

    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream printStream = new PrintStream(baos);
    graphConsumer.printWhyAreYouKeeping(
        Reference.methodFromMethod(targetClass.getMethod("gone")), printStream);
    graphConsumer.printWhyAreYouKeeping(
        Reference.methodFromMethod(subClass.getMethod("gone")), printStream);
    String output = new String(baos.toByteArray(), StandardCharsets.UTF_8);
    assertThat(output, containsString(expectedMessage(main, targetClass, subClass)));
  }

  private static final String MAIN_SIG = "main(java.lang.String[])";

  private String expectedMessage(Class<?> main, Class<?> targetClass, Class<?> subClass) {
    return StringUtils.lines(
        "Nothing is keeping void " + targetClass.getTypeName() + ".gone()",
        "void " + subClass.getTypeName() + ".gone()",
        "|- is invoked from:",
        "|  void " + main.getTypeName() + "." + MAIN_SIG,
        "|- is referenced in keep rule:",
        "|  -keep class " + main.getTypeName() + " { public static void " + MAIN_SIG + "; }");
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

  @NeverMerge
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

  @NeverMerge
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
