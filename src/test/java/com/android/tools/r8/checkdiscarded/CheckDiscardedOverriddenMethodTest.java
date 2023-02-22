// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.checkdiscarded;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/**
 * This test is changed based on the discussion in b/139794417 to not include overridden members
 * which is compatible with -whyareyoukeeping.
 */
@RunWith(Parameterized.class)
public class CheckDiscardedOverriddenMethodTest extends TestBase {

  @Parameters(name = "{0} minification: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withDefaultDexRuntime().withMaximumApiLevel().build(),
        BooleanUtils.values());
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public boolean minification;

  private void test(Class<?> main, Class<?> targetClass) throws Exception {
    try {
      testForR8(parameters.getBackend())
          .addInnerClasses(CheckDiscardedOverriddenMethodTest.class)
          .addKeepMainRule(main)
          .addKeepRules(
              "-checkdiscard class **.*$" + targetClass.getSimpleName() + " { void gone(); }")
          .enableNeverClassInliningAnnotations()
          .enableInliningAnnotations()
          .enableNoVerticalClassMergingAnnotations()
          .minification(minification)
          .setMinApi(parameters)
          // Asserting that -checkdiscard is not giving any information out on an un-removed
          // sub-type member.
          .compileWithExpectedDiagnostics(diagnostics -> diagnostics.assertInfosCount(0));
    } catch (CompilationFailedException e) {
      String message = e.getCause().getMessage();
      assertThat(message, containsString("Discard checks failed."));
    }
  }

  @Test
  public void testExtends() throws Exception {
    test(TestMain1.class, Base.class);
  }

  @Test
  public void testImplements() throws Exception {
    test(TestMain2.class, Itf.class);
  }

  @NoVerticalClassMerging
  static class Base {
    @NeverInline
    void gone() {
      System.out.println("should be gone");
    }
  }

  @NeverClassInline
  static class Sub extends Base {
    @NeverInline
    @Override
    void gone() {
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
