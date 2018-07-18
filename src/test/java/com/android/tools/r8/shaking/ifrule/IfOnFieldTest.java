// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatabilityTestBase;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class IfOnFieldTest extends ProguardCompatabilityTestBase {
  private final static List<Class> CLASSES = ImmutableList.of(
      D.class, D1.class, D2.class,
      R.class, R1.class, R2.class,
      MainWithInner.InnerR.class, MainWithInner.InnerD.class,
      I.class, Impl.class,
      MainUsesR.class, MainWithIf.class, MainWithInner.class, MainUsesImpl.class);

  private final Shrinker shrinker;

  public IfOnFieldTest(Shrinker shrinker) {
    this.shrinker = shrinker;
  }

  @Parameters(name = "shrinker: {0}")
  public static Collection<Object> data() {
    return ImmutableList.of(Shrinker.PROGUARD6, Shrinker.R8);
  }

  private String adaptConfiguration(String proguardConfig) {
    List<String> configWithPrecondition = new ArrayList<>();
    configWithPrecondition.add(proguardConfig);
    configWithPrecondition.add("-dontobfuscate");
    return String.join(System.lineSeparator(), configWithPrecondition);
  }

  @Override
  protected CodeInspector runR8(
      List<Class> programClasses, String proguardConfig) throws Exception {
    return super.runR8(programClasses, adaptConfiguration(proguardConfig));
  }

  @Override
  protected CodeInspector runProguard6(
      List<Class> programClasses, String proguardConfig) throws Exception {
    return super.runProguard6(programClasses, adaptConfiguration(proguardConfig));
  }

  @Test
  public void ifOnField_withoutNthWildcard() throws Exception {
    List<String> config = ImmutableList.of(
        "-keep class **.MainUsesR {",
        "  public static void main(java.lang.String[]);",
        "}",
        // R.id1 -> D1
        "-if class **.R {",
        "  public static int id1;",
        "}",
        "-keep class **.D1",
        // R.id2 -> D2
        "-if class **.R {",
        "  public static int id2;",
        "}",
        "-keep class **.D2",
        // R.id1 && R.id2 -> D
        "-if class **.R {",
        "  public static int id1;",
        "  public static int id2;",
        "}",
        "-keep class **.D"
    );

    CodeInspector codeInspector = runShrinker(shrinker, CLASSES, config);
    verifyClassesAbsent(codeInspector,
        R1.class, R2.class, D.class, D2.class);
    verifyClassesPresent(codeInspector,
        R.class, D1.class);
  }

  @Test
  public void ifOnField_withNthWildcard() throws Exception {
    List<String> config = ImmutableList.of(
        "-keep class **.MainUsesR {",
        "  public static void main(java.lang.String[]);",
        "}",
        "-if class **.R {",
        "  public static int id?;",
        "}",
        "-keep class **.D<2>"
    );

    CodeInspector codeInspector = runShrinker(shrinker, CLASSES, config);
    verifyClassesAbsent(codeInspector,
        R1.class, R2.class, D.class, D2.class);
    verifyClassesPresent(codeInspector,
        R.class, D1.class);
  }

  @Test
  public void ifOnFieldWithCapture_withoutNthWildcard() throws Exception {
    List<String> config = ImmutableList.of(
        "-keep class **.MainWithIf {",
        "  public static void main(java.lang.String[]);",
        "}",
        "-if class **.R1 {",
        "  public static int id*;",
        "}",
        "-keep class **.D1",
        "-if class **.R2 {",
        "  public static int id*;",
        "}",
        "-keep class **.D2"
    );

    CodeInspector codeInspector = runShrinker(shrinker, CLASSES, config);
    verifyClassesAbsent(codeInspector,
        R.class, D.class, R1.class, D1.class);
    verifyClassesPresent(codeInspector,
        R2.class, D2.class);
  }

  @Test
  public void ifOnFieldWithCapture_withNthWildcard() throws Exception {
    List<String> config = ImmutableList.of(
        "-keep class **.MainWithIf {",
        "  public static void main(java.lang.String[]);",
        "}",
        "-if class **.R* {",
        "  public static int id*;",
        "}",
        "-keep class **.D<2>"
    );

    CodeInspector codeInspector = runShrinker(shrinker, CLASSES, config);
    verifyClassesAbsent(codeInspector,
        R.class, D.class, R1.class, D1.class);
    verifyClassesPresent(codeInspector,
        R2.class, D2.class);
  }

  @Test
  public void ifOnFieldWithInner_withoutNthWildcard() throws Exception {
    List<String> config = ImmutableList.of(
        "-keep class **.MainWithInner {",
        "  public static void main(java.lang.String[]);",
        "}",
        "-if class **$*R {",
        "  public static int id1;",
        "  public static int id2;",
        "}",
        "-keep class **$*D"
    );

    CodeInspector codeInspector = runShrinker(shrinker, CLASSES, config);
    verifyClassesAbsent(codeInspector,
        R.class, D.class, R1.class, D1.class, R2.class, D2.class);
    verifyClassesPresent(codeInspector,
        MainWithInner.InnerR.class, MainWithInner.InnerD.class);
  }

  @Test
  public void ifOnFieldWithInner_withNthWildcard() throws Exception {
    List<String> config = ImmutableList.of(
        "-keep class **.MainWithInner {",
        "  public static void main(java.lang.String[]);",
        "}",
        "-if class **$*R {",
        "  public static int id1;",
        "  public static int id2;",
        "}",
        "-keep class <1>$<2>D"
    );

    CodeInspector codeInspector = runShrinker(shrinker, CLASSES, config);
    verifyClassesAbsent(codeInspector,
        R.class, D.class, R1.class, D1.class, R2.class, D2.class);
    verifyClassesPresent(codeInspector,
        MainWithInner.InnerR.class, MainWithInner.InnerD.class);
  }

  @Test
  public void ifOnFieldInImplementer_withoutNthWildcard() throws Exception {
    List<String> config =
        ImmutableList.of(
            "-keep class **.MainUsesImpl {",
            "  public static void main(java.lang.String[]);",
            "}",
            "-keep class **.I", // Prevent I from being merged into Impl.
            "-if class ** implements **.I {",
            "  private <fields>;",
            "}",
            "-keep class **.D1",
            "-if class ** implements **.I {",
            "  public <fields>;",
            "}",
            "-keep class **.D2");

    CodeInspector codeInspector = runShrinker(shrinker, CLASSES, config);
    verifyClassesAbsent(codeInspector, D2.class);
    verifyClassesPresent(codeInspector,
        I.class, Impl.class, D1.class);
  }

  @Test
  public void ifOnFieldInImplementer_withNthWildcard() throws Exception {
    List<String> config =
        ImmutableList.of(
            "-keep class **.MainUsesImpl {",
            "  public static void main(java.lang.String[]);",
            "}",
            "-keep class **.I", // Prevent I from being merged into Impl.
            "-if class ** implements **.I {",
            "  private <fields>;",
            "}",
            "-keep class <2>.D1",
            "-if class ** implements **.I {",
            "  public <fields>;",
            "}",
            "-keep class <2>.D2");

    CodeInspector codeInspector = runShrinker(shrinker, CLASSES, config);
    verifyClassesAbsent(codeInspector, D2.class);
    verifyClassesPresent(codeInspector,
        I.class, Impl.class, D1.class);
  }

}
