// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.librarymethodoverride;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverMerge;
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
        .enableClassInliningAnnotations()
        .enableMergeAnnotations()
        .setMinApi(parameters.getApiLevel())
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

      // TODO(b/142772856): None of the non-escaping classes should have a toString() method. It is
      //  a requirement that the instance initializers are considered trivial for this to work,
      //  though, even when they have a side effect (as long as the receiver does not escape via the
      //  side effecting instruction).
      if (nonEscapingClass == DoesNotEscapeWithSubThatDoesNotOverrideSub.class) {
        assertThat(classSubject.uniqueMethodWithName("toString"), not(isPresent()));
      } else {
        assertThat(classSubject.uniqueMethodWithName("toString"), isPresent());
      }
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

  @NeverMerge
  static class EscapesIndirectly {

    @Override
    public String toString() {
      return "EscapesIndirectly";
    }
  }

  static class EscapesIndirectlySub extends EscapesIndirectly {}

  @NeverMerge
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
  static class DoesNotEscape {

    // TODO(b/142772856): Should be classified as a trivial instance initializer although it has a
    //  side effect.
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
  static class DoesNotEscapeWithSubThatDoesNotOverride {

    // TODO(b/142772856): Should be classified as a trivial instance initializer although it has a
    //  side effect.
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

    // TODO(b/142772856): Should be classified as a trivial instance initializer although it has a
    //  side effect.
    DoesNotEscapeWithSubThatDoesNotOverrideSub() {
      // Side effect to ensure that the constructor is not removed from main().
      System.out.print("");
    }
  }

  @NeverClassInline
  static class DoesNotEscapeWithSubThatOverrides {

    // TODO(b/142772856): Should be classified as a trivial instance initializer although it has a
    //  side effect.
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

    // TODO(b/142772856): Should be classified as a trivial instance initializer although it has a
    //  side effect.
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
  static class DoesNotEscapeWithSubThatOverridesAndEscapes {

    // TODO(b/142772856): Should be classified as a trivial instance initializer although it has a
    //  side effect.
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

    // TODO(b/142772856): Should be classified as a trivial instance initializer although it has a
    //  side effect.
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
