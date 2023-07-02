// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndNotRenamed;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NoAccessModification;
import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatibilityTestBase;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfOnClassTest extends ProguardCompatibilityTestBase {
  private static final List<Class<?>> CLASSES =
      ImmutableList.of(
          EmptyMainClassForIfOnClassTests.class,
          Precondition.class,
          DependentUser.class,
          Dependent.class,
          NoAccessModification.class);

  private final Shrinker shrinker;
  private final boolean keepPrecondition;

  public IfOnClassTest(Shrinker shrinker, boolean keepPrecondition) {
    this.shrinker = shrinker;
    this.keepPrecondition = keepPrecondition;
  }

  @Parameters(name = "shrinker: {0} precondition: {1}")
  public static Collection<Object[]> data() {
    return ImmutableList.of(
        new Object[] {Shrinker.PROGUARD6, true},
        new Object[] {Shrinker.PROGUARD6, false},
        new Object[] {Shrinker.R8, true},
        new Object[] {Shrinker.R8, false},
        new Object[] {Shrinker.R8_CF, true},
        new Object[] {Shrinker.R8_CF, false});
  }

  private String adaptConfiguration(String proguardConfig) {
    List<String> configWithPrecondition = new ArrayList<>();
    configWithPrecondition.add(
        keepMainProguardConfiguration(EmptyMainClassForIfOnClassTests.class));
    if (keepPrecondition) {
      configWithPrecondition.add("-keep class **.Precondition");
    }
    configWithPrecondition.add(proguardConfig);
    return String.join(System.lineSeparator(), configWithPrecondition);
  }

  @Override
  protected CodeInspector inspectR8Result(
      List<Class<?>> programClasses,
      String proguardConfig,
      Consumer<InternalOptions> configure,
      Backend backend)
      throws Exception {
    return super.inspectR8Result(
        programClasses, adaptConfiguration(proguardConfig), configure, backend);
  }

  @Override
  protected CodeInspector inspectProguard5Result(
      List<Class<?>> programClasses, String proguardConfig) throws Exception {
    return super.inspectProguard5Result(programClasses, adaptConfiguration(proguardConfig));
  }

  @Override
  protected CodeInspector inspectProguard6Result(
      List<Class<?>> programClasses, String proguardConfig) throws Exception {
    return super.inspectProguard6Result(programClasses, adaptConfiguration(proguardConfig));
  }

  @Test
  public void ifThenKeep_withoutNthWildcard() throws Exception {
    List<String> config = ImmutableList.of(
        "-if class **.Precondition",
        "-keep,allowobfuscation class **.*User",
        "-if class **.DependentUser",
        "-keep,allowobfuscation class **.Dependent"
    );

    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    if (!keepPrecondition) {
      // TODO(b/73708139): Proguard6 kept Dependent (w/o any members), which is not necessary.
      if (shrinker == Shrinker.PROGUARD6) {
        return;
      }
      assertEquals(1, codeInspector.allClasses().size());
      return;
    }

    ClassSubject clazz = codeInspector.clazz(DependentUser.class);
    assertThat(clazz, isPresentAndRenamed());
    // Members of DependentUser are not used anywhere.
    MethodSubject m = clazz.method("void", "callFoo", ImmutableList.of());
    assertThat(m, not(isPresent()));
    FieldSubject f = clazz.field("int", "canBeShrinked");
    assertThat(f, not(isPresent()));

    // Although DependentUser#callFoo is shrinked, Dependent is kept via -if.
    clazz = codeInspector.clazz(Dependent.class);
    assertThat(clazz, isPresentAndRenamed());
    // But, its members are gone.
    m = clazz.method("java.lang.String", "foo", ImmutableList.of());
    assertThat(m, not(isPresent()));
    f = clazz.field("int", "intField");
    assertThat(f, not(isPresent()));
  }

  @Test
  public void ifThenKeep_withNthWildcard() throws Exception {
    List<String> config = ImmutableList.of(
        "-if class **.Precondition",
        "-keep,allowobfuscation class **.*User",
        "-if class **.*User",
        "-keep,allowobfuscation class <1>.<2>"
    );

    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    if (!keepPrecondition) {
      // TODO(b/73708139): Proguard6 kept Dependent (w/o any members), which is not necessary.
      if (shrinker == Shrinker.PROGUARD6) {
        return;
      }
      assertEquals(1, codeInspector.allClasses().size());
      return;
    }

    ClassSubject clazz = codeInspector.clazz(DependentUser.class);
    assertThat(clazz, isPresentAndRenamed());
    // Members of DependentUser are not used anywhere.
    MethodSubject m = clazz.method("void", "callFoo", ImmutableList.of());
    assertThat(m, not(isPresent()));
    FieldSubject f = clazz.field("int", "canBeShrinked");
    assertThat(f, not(isPresent()));

    // Although DependentUser#callFoo is shrinked, Dependent is kept via -if.
    clazz = codeInspector.clazz(Dependent.class);
    assertThat(clazz, isPresentAndRenamed());
    // But, its members are gone.
    m = clazz.method("java.lang.String", "foo", ImmutableList.of());
    assertThat(m, not(isPresent()));
    f = clazz.field("int", "intField");
    assertThat(f, not(isPresent()));
  }

  @Test
  public void ifThenKeepClassesWithMembers() throws Exception {
    List<String> config =
        ImmutableList.of(
            "-if class **.Precondition",
            "-keepclasseswithmembers,allowobfuscation class **.*User {",
            "  static void callFoo(...);",
            "}",
            shrinker.isR8()
                ? "-noaccessmodification class * { @com.android.tools.r8.NoAccessModification *; }"
                : "");

    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    if (!keepPrecondition) {
      assertEquals(1, codeInspector.allClasses().size());
      return;
    }

    ClassSubject clazz = codeInspector.clazz(DependentUser.class);
    assertThat(clazz, isPresentAndRenamed());
    MethodSubject m = clazz.method("void", "callFoo", ImmutableList.of());
    assertThat(m, isPresentAndRenamed());
    FieldSubject f = clazz.field("int", "canBeShrinked");
    assertThat(f, not(isPresent()));

    // Dependent is kept due to DependentUser#callFoo, but renamed.
    clazz = codeInspector.clazz(Dependent.class);
    assertThat(clazz, isPresentAndRenamed());
    m = clazz.method("java.lang.String", "foo", ImmutableList.of());
    assertThat(m, isPresentAndRenamed());
    f = clazz.field("int", "intField");
    assertThat(f, isPresentAndRenamed());
  }

  @Test
  public void ifThenKeepClassMembers_withoutNthWildcard() throws Exception {
    List<String> config = ImmutableList.of(
        "-if class **.Precondition",
        "-keepclassmembers,allowobfuscation class **.*User {",
        "  static void callFoo(...);",
        "}",
        // If the member is kept, keep the enclosing class too.
        "-if class **.*User {",
        "  static void callFoo(...);",
        "}",
        "-keep,allowobfuscation class **.*User"
    );

    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    if (!keepPrecondition) {
      // TODO(b/73708139): Proguard6 kept DependentUser (w/o any members), which is not necessary.
      if (shrinker == Shrinker.PROGUARD6) {
        return;
      }
      assertEquals(1, codeInspector.allClasses().size());
      return;
    }

    if (shrinker.isR8()) {
      // The -keepclassmembers rule should not keep Dependent nor DependentUser since DependentUser
      // is never referenced.
      assertThat(codeInspector.clazz(EmptyMainClassForIfOnClassTests.class), isPresent());
      assertThat(codeInspector.clazz(Precondition.class), isPresent());
      assertEquals(2, codeInspector.allClasses().size());
      return;
    }

    ClassSubject clazz = codeInspector.clazz(DependentUser.class);
    assertThat(clazz, isPresentAndRenamed());
    MethodSubject m = clazz.method("void", "callFoo", ImmutableList.of());
    assertThat(m, isPresentAndRenamed());
    FieldSubject f = clazz.field("int", "canBeShrinked");
    assertThat(f, not(isPresent()));

    // Dependent is kept due to DependentUser#callFoo, but renamed.
    clazz = codeInspector.clazz(Dependent.class);
    assertThat(clazz, isPresentAndRenamed());
    m = clazz.method("java.lang.String", "foo", ImmutableList.of());
    assertThat(m, isPresentAndRenamed());
    f = clazz.field("int", "intField");
    assertThat(f, isPresentAndRenamed());
  }

  @Test
  public void ifThenKeepClassMembers_withNthWildcard() throws Exception {
    List<String> config = ImmutableList.of(
        "-if class **.Precondition",
        "-keepclassmembers,allowobfuscation class **.*User {",
        "  static void callFoo(...);",
        "}",
        // If the member is kept, keep the enclosing class too.
        "-if class **.*User {",
        "  static void callFoo(...);",
        "}",
        "-keep,allowobfuscation class <1>.<2>User"
    );

    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);
    if (!keepPrecondition) {
      // TODO(b/73708139): Proguard6 kept DependentUser (w/o any members), which is not necessary.
      if (shrinker == Shrinker.PROGUARD6) {
        return;
      }
      assertEquals(1, codeInspector.allClasses().size());
      return;
    }

    if (shrinker.isR8()) {
      // The -keepclassmembers rule should not keep Dependent nor DependentUser since DependentUser
      // is never referenced.
      assertThat(codeInspector.clazz(EmptyMainClassForIfOnClassTests.class), isPresent());
      assertThat(codeInspector.clazz(Precondition.class), isPresent());
      assertEquals(2, codeInspector.allClasses().size());
      return;
    }

    ClassSubject clazz = codeInspector.clazz(DependentUser.class);
    assertThat(clazz, isPresentAndRenamed());
    MethodSubject m = clazz.method("void", "callFoo", ImmutableList.of());
    assertThat(m, isPresentAndRenamed());
    FieldSubject f = clazz.field("int", "canBeShrinked");
    assertThat(f, not(isPresent()));

    // Dependent is kept due to DependentUser#callFoo, but renamed.
    clazz = codeInspector.clazz(Dependent.class);
    assertThat(clazz, isPresentAndRenamed());
    m = clazz.method("java.lang.String", "foo", ImmutableList.of());
    assertThat(m, isPresentAndRenamed());
    f = clazz.field("int", "intField");
    assertThat(f, isPresentAndRenamed());
  }

  @Test
  public void ifThenKeepNames() throws Exception {
    List<String> config =
        ImmutableList.of(
            // To keep DependentUser#callFoo, which in turn kept Dependent#<init> as well.
            // We're testing renaming of Dependent itself and members.
            "-keepclasseswithmembers,allowobfuscation class **.*User {",
            "  static void callFoo(...);",
            "}",
            "-if class **.Precondition",
            "-keepnames class **.Dependent",
            shrinker.isR8()
                ? "-noaccessmodification class * { @com.android.tools.r8.NoAccessModification *; }"
                : "");

    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);

    ClassSubject clazz = codeInspector.clazz(Dependent.class);
    // Only class name is not renamed, if triggered.
    assertThat(clazz, keepPrecondition ? isPresentAndNotRenamed() : isPresentAndRenamed());
    MethodSubject m = clazz.method("java.lang.String", "foo", ImmutableList.of());
    assertThat(m, isPresentAndRenamed());
    FieldSubject f = clazz.field("int", "intField");
    assertThat(f, isPresentAndRenamed());
  }

  @Test
  public void ifThenKeepClassesWithMemberNames() throws Exception {
    List<String> config =
        ImmutableList.of(
            // To keep DependentUser#callFoo, which in turn kept Dependent#<init> as well.
            // We're testing renaming of Dependent itself and members.
            "-keepclasseswithmembers,allowobfuscation class **.*User {",
            "  static void callFoo(...);",
            "}",
            "-if class **.Precondition",
            "-keepclasseswithmembernames class **.Dependent {",
            "  <methods>;",
            "}",
            shrinker.isR8()
                ? "-noaccessmodification class * { @com.android.tools.r8.NoAccessModification *; }"
                : "");

    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);

    ClassSubject clazz = codeInspector.clazz(Dependent.class);
    // Class name is not renamed, if triggered.
    assertThat(clazz, keepPrecondition ? isPresentAndNotRenamed() : isPresentAndRenamed());
    MethodSubject m = clazz.method("java.lang.String", "foo", ImmutableList.of());
    // Method name is not renamed either, if triggered.
    assertThat(m, keepPrecondition ? isPresentAndNotRenamed() : isPresentAndRenamed());
    FieldSubject f = clazz.field("int", "intField");
    assertThat(f, isPresentAndRenamed());
  }

  @Test
  public void ifThenKeepClassMemberNames() throws Exception {
    List<String> config =
        ImmutableList.of(
            // To keep DependentUser#callFoo, which in turn kept Dependent#<init> as well.
            // We're testing renaming of Dependent itself and members.
            "-keepclasseswithmembers,allowobfuscation class **.*User {",
            "  static void callFoo(...);",
            "}",
            "-if class **.Precondition",
            "-keepclassmembernames class **.Dependent {",
            "  <methods>;",
            "}",
            shrinker.isR8()
                ? "-noaccessmodification class * { @com.android.tools.r8.NoAccessModification *; }"
                : "");

    CodeInspector codeInspector = inspectAfterShrinking(shrinker, CLASSES, config);

    ClassSubject clazz = codeInspector.clazz(Dependent.class);
    assertThat(clazz, isPresentAndRenamed());
    MethodSubject m = clazz.method("java.lang.String", "foo", ImmutableList.of());
    // Only method name is not renamed, if triggered.
    assertThat(m, keepPrecondition ? isPresentAndNotRenamed() : isPresentAndRenamed());
    FieldSubject f = clazz.field("int", "intField");
    assertThat(f, isPresentAndRenamed());
  }
}
