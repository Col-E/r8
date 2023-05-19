// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.librarymethodoverride;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LibraryMethodOverrideTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public LibraryMethodOverrideTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(LibraryMethodOverrideTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(options -> options.enableTreeShakingOfLibraryMethodOverrides = true)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyOutput)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines(
            "EscapesDirectly",
            "EscapesIndirectly",
            "EscapesIndirectlyWithOverrideSub",
            "DoesNotEscapeWithSubThatOverridesAndEscapesSub");
  }

  private void verifyOutput(CodeInspector inspector) {
    List<Class<?>> nonEscapingClasses =
        ImmutableList.of(
            DoesNotEscape.class,
            DoesNotEscapeWithSubThatDoesNotOverride.class,
            DoesNotEscapeWithSubThatDoesNotOverrideSub.class,
            DoesNotEscapeWithSubThatOverrides.class,
            DoesNotEscapeWithSubThatOverridesSub.class,
            DoesNotEscapeWithSubThatOverridesAndEscapes.class);
    for (Class<?> nonEscapingClass : nonEscapingClasses) {
      ClassSubject classSubject = inspector.clazz(nonEscapingClass);
      assertThat(classSubject, isPresent());
      assertThat(classSubject.uniqueMethodWithOriginalName("toString"), not(isPresent()));
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      System.out.println(new EscapesDirectly());
      System.out.println(new EscapesIndirectlySub());
      System.out.println(new EscapesIndirectlyWithOverrideSub());
      System.out.println(new DoesNotEscapeWithSubThatOverridesAndEscapesSub());

      new DoesNotEscape();
      new DoesNotEscapeWithSubThatDoesNotOverride();
      new DoesNotEscapeWithSubThatDoesNotOverrideSub();
      new DoesNotEscapeWithSubThatOverrides();
      new DoesNotEscapeWithSubThatOverridesSub();
      new DoesNotEscapeWithSubThatOverridesAndEscapes();
    }
  }

  static class EscapesDirectly {

    @Override
    public String toString() {
      return "EscapesDirectly";
    }
  }

  @NoVerticalClassMerging
  static class EscapesIndirectly {

    @Override
    public String toString() {
      return "EscapesIndirectly";
    }
  }

  static class EscapesIndirectlySub extends EscapesIndirectly {}

  @NoVerticalClassMerging
  static class EscapesIndirectlyWithOverride {

    @Override
    public String toString() {
      return "EscapesIndirectlyWithOverride";
    }
  }

  static class EscapesIndirectlyWithOverrideSub extends EscapesIndirectlyWithOverride {

    @Override
    public String toString() {
      return "EscapesIndirectlyWithOverrideSub";
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class DoesNotEscape {

    @NeverInline
    DoesNotEscape() {
      // Side effect to ensure that the constructor is not removed from main().
      System.out.print("");
    }

    @Override
    public String toString() {
      return "DoesNotEscape";
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class DoesNotEscapeWithSubThatDoesNotOverride {

    DoesNotEscapeWithSubThatDoesNotOverride() {
      // Side effect to ensure that the constructor is not removed from main().
      System.out.print("");
    }

    @Override
    public String toString() {
      return "DoesNotEscapeWithSubThatDoesNotOverride";
    }
  }

  @NeverClassInline
  static class DoesNotEscapeWithSubThatDoesNotOverrideSub
      extends DoesNotEscapeWithSubThatDoesNotOverride {

    DoesNotEscapeWithSubThatDoesNotOverrideSub() {
      // Side effect to ensure that the constructor is not removed from main().
      System.out.print("");
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  static class DoesNotEscapeWithSubThatOverrides {

    DoesNotEscapeWithSubThatOverrides() {
      // Side effect to ensure that the constructor is not removed from main().
      System.out.print("");
    }

    @Override
    public String toString() {
      return "DoesNotEscapeWithSubThatOverrides";
    }
  }

  @NeverClassInline
  static class DoesNotEscapeWithSubThatOverridesSub extends DoesNotEscapeWithSubThatOverrides {

    DoesNotEscapeWithSubThatOverridesSub() {
      // Side effect to ensure that the constructor is not removed from main().
      System.out.print("");
    }

    @Override
    public String toString() {
      return "DoesNotEscapeWithSubThatOverridesSub";
    }
  }

  // Note that this class and its subclass is equivalent to DoesNotEscapeWithSubThatOverrides and
  // DoesNotEscapeWithSubThatOverridesSub, respectively. The difference is that the class DoesNot-
  // EscapeWithSubThatOverridesAndEscapesSub is escaping from main(), unlike DoesNotEscapeWithSub-
  // ThatOverridesSub.
  @NeverClassInline
  @NoHorizontalClassMerging
  static class DoesNotEscapeWithSubThatOverridesAndEscapes {

    DoesNotEscapeWithSubThatOverridesAndEscapes() {
      // Side effect to ensure that the constructor is not removed from main().
      System.out.print("");
    }

    @Override
    public String toString() {
      return "DoesNotEscapeWithSubThatOverridesAndEscapes";
    }
  }

  @NeverClassInline
  static class DoesNotEscapeWithSubThatOverridesAndEscapesSub
      extends DoesNotEscapeWithSubThatOverridesAndEscapes {

    DoesNotEscapeWithSubThatOverridesAndEscapesSub() {
      // Side effect to ensure that the constructor is not removed from main().
      System.out.print("");
    }

    @Override
    public String toString() {
      return "DoesNotEscapeWithSubThatOverridesAndEscapesSub";
    }
  }
}
