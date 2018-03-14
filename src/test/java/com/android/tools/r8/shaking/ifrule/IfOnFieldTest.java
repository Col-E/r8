// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.ifrule;

import static com.android.tools.r8.utils.DexInspectorMatchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.shaking.forceproguardcompatibility.ProguardCompatabilityTestBase;
import com.android.tools.r8.utils.DexInspector;
import com.android.tools.r8.utils.DexInspector.ClassSubject;
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
      MainUsesR.class, MainWithIf.class, MainWithInner.class);

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
  protected DexInspector runR8(
      List<Class> programClasses, String proguardConfig) throws Exception {
    return super.runR8(programClasses, adaptConfiguration(proguardConfig));
  }

  @Override
  protected DexInspector runProguard6(
      List<Class> programClasses, String proguardConfig) throws Exception {
    return super.runProguard6(programClasses, adaptConfiguration(proguardConfig));
  }

  private void verifyClassesPresent(
      DexInspector dexInspector, Class<?>... classesOfInterest) {
    for (Class klass : classesOfInterest) {
      ClassSubject c = dexInspector.clazz(klass);
      assertThat(c, isPresent());
    }
  }

  private void verifyClassesAbsent(
      DexInspector dexInspector, Class<?>... classesOfInterest) {
    for (Class klass : classesOfInterest) {
      ClassSubject c = dexInspector.clazz(klass);
      assertThat(c, not(isPresent()));
    }
  }

  @Test
  public void ifOnField() throws Exception {
    // TODO(b/73800755): not implemented yet.
    if (shrinker == Shrinker.R8) {
      return;
    }

    List<String> config = ImmutableList.of(
        "-keep class **.MainUsesR {",
        "  public static void main(java.lang.String[]);",
        "}",
        "-if class **.R {",
        "  public static int id1;",
        "}",
        "-keep class **.D1",
        "-if class **.R {",
        "  public static int id2;",
        "}",
        "-keep class **.D2"
    );

    DexInspector dexInspector = runShrinker(shrinker, CLASSES, config);
    verifyClassesAbsent(dexInspector,
        R1.class, R2.class, D.class, D2.class);
    verifyClassesPresent(dexInspector,
        R.class, D1.class);

    config = ImmutableList.of(
        "-keep class **.MainUsesR {",
        "  public static void main(java.lang.String[]);",
        "}",
        "-if class **.R {",
        "  public static int id?;",
        "}",
        "-keep class **.D<2>"
    );

    dexInspector = runShrinker(shrinker, CLASSES, config);
    verifyClassesAbsent(dexInspector,
        R1.class, R2.class, D.class, D2.class);
    verifyClassesPresent(dexInspector,
        R.class, D1.class);
  }

  @Test
  public void ifOnFieldWithCapture() throws Exception {
    // TODO(b/73800755): not implemented yet.
    if (shrinker == Shrinker.R8) {
      return;
    }

    List<String> config = ImmutableList.of(
        "-keep class **.MainWithIf {",
        "  public static void main(java.lang.String[]);",
        "}",
        "-if class **.R* {",
        "  public static int id1;",
        "  public static int id2;",
        "}",
        "-keep class **.D<2>"
    );

    DexInspector dexInspector = runShrinker(shrinker, CLASSES, config);
    verifyClassesAbsent(dexInspector,
        R.class, D.class, R1.class, D1.class);
    verifyClassesPresent(dexInspector,
        R2.class, D2.class);
  }

  @Test
  public void ifOnFieldWithInner() throws Exception {
    // TODO(b/73800755): not implemented yet.
    if (shrinker == Shrinker.R8) {
      return;
    }

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

    DexInspector dexInspector = runShrinker(shrinker, CLASSES, config);
    verifyClassesAbsent(dexInspector,
        R.class, D.class, R1.class, D1.class, R2.class, D2.class);
    verifyClassesPresent(dexInspector,
        MainWithInner.InnerR.class, MainWithInner.InnerD.class);
  }

  @Test
  public void ifOnFieldWithInner_outOfRange() throws Exception {
    // TODO(b/73800755): not implemented yet.
    if (shrinker == Shrinker.R8) {
      return;
    }

    List<String> config = ImmutableList.of(
        "-keep class **.MainWithInner {",
        "  public static void main(java.lang.String[]);",
        "}",
        "-if class **$*R",
        "-keep class <1>$<3>D"
    );

    try {
      runShrinker(shrinker, CLASSES, config);
      fail("Expect to see an error about wrong range of <n>.");
    } catch (Error e) {
      // "Invalid reference to wildcard (3, must lie between 1 and 2)"
      String message = e.getMessage();
      assertTrue(message.contains("Invalid"));
      assertTrue(message.contains("wildcard"));
      assertTrue(message.contains("3"));
    }
  }

}
