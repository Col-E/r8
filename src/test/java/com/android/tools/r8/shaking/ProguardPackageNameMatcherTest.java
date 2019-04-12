// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.graph.DexItemFactory;
import org.junit.Test;

public class ProguardPackageNameMatcherTest {
  private DexItemFactory dexItemFactory = new DexItemFactory();

  private boolean matches(ProguardPackageNameList matcher, String packageName) {
    return matcher.matches(dexItemFactory.createType("L" + packageName.replace('.', '/') + "/A;"));
  }

  @Test
  public void testSimple() {
    ProguardPackageNameList matcher =
        ProguardPackageNameList.builder()
            .addPackageName(false, new ProguardPackageMatcher("com.example"))
            .build();
    assertTrue(matches(matcher, "com.example"));
    assertFalse(matches(matcher, "com.exampl"));
    assertFalse(matches(matcher, "com.example.a"));
  }

  @Test
  public void testSingleEnd() {
    ProguardPackageNameList matcher =
        ProguardPackageNameList.builder()
            .addPackageName(false, new ProguardPackageMatcher("com.example*"))
            .build();
    assertTrue(matches(matcher, "com.example"));
    assertTrue(matches(matcher, "com.example1"));
    assertTrue(matches(matcher, "com.example2"));
    assertFalse(matches(matcher, "com.exampl"));
    assertFalse(matches(matcher, "com.example.a"));
  }

  @Test
  public void testSingleBeginning() {
    ProguardPackageNameList matcher =
        ProguardPackageNameList.builder()
            .addPackageName(false, new ProguardPackageMatcher("*.example"))
            .build();
    assertTrue(matches(matcher, "com.example"));
    assertTrue(matches(matcher, "org.example"));
    assertFalse(matches(matcher, "com.example1"));
    assertFalse(matches(matcher, "org.example1"));
    assertFalse(matches(matcher, "com.exampl"));
    assertFalse(matches(matcher, "com.example.a"));
  }

  @Test
  public void testDoubleEnd() {
    ProguardPackageNameList matcher =
        ProguardPackageNameList.builder()
            .addPackageName(false, new ProguardPackageMatcher("com.example**"))
            .build();
    assertTrue(matches(matcher, "com.example"));
    assertTrue(matches(matcher, "com.example1"));
    assertTrue(matches(matcher, "com.example2"));
    assertTrue(matches(matcher, "com.example.a"));
    assertTrue(matches(matcher, "com.example.a.a"));
    assertFalse(matches(matcher, "com.exampl"));
  }

  @Test
  public void testDoubleBeginning() {
    ProguardPackageNameList matcher =
        ProguardPackageNameList.builder()
            .addPackageName(false, new ProguardPackageMatcher("**example"))
            .build();
    assertTrue(matches(matcher, "com.example"));
    assertTrue(matches(matcher, "org.example"));
    assertTrue(matches(matcher, "com.a.example"));
    assertTrue(matches(matcher, "com.a.a.example"));
    assertTrue(matches(matcher, "comexample"));
    assertFalse(matches(matcher, "com.example1"));
  }

  @Test
  public void testQuestionMark() {
    ProguardPackageNameList matcher =
        ProguardPackageNameList.builder()
            .addPackageName(false, new ProguardPackageMatcher("com.e?ample"))
            .build();
    assertTrue(matches(matcher, "com.example"));
    assertTrue(matches(matcher, "com.eyample"));
    assertFalse(matches(matcher, "com.example1"));
    assertFalse(matches(matcher, "com.example.a"));
  }

  @Test
  public void testList() {
    ProguardPackageNameList matcher =
        ProguardPackageNameList.builder()
            .addPackageName(false, new ProguardPackageMatcher("com.example"))
            .addPackageName(false, new ProguardPackageMatcher("org.example"))
            .build();
    assertTrue(matches(matcher, "com.example"));
    assertTrue(matches(matcher, "org.example"));
    assertFalse(matches(matcher, "com.example1"));
    assertFalse(matches(matcher, "com.example.a"));
    assertFalse(matches(matcher, "org.example1"));
    assertFalse(matches(matcher, "org.example.a"));
  }

  @Test
  public void testListNegation() {
    ProguardPackageNameList matcher =
        ProguardPackageNameList.builder()
            .addPackageName(true, new ProguardPackageMatcher("!org.example"))
            .addPackageName(false, new ProguardPackageMatcher("*.example"))
            .build();
    assertTrue(matches(matcher, "com.example"));
    assertTrue(matches(matcher, "org.example"));
  }

  @Test
  public void testListNegationNotMatched() {
    ProguardPackageNameList matcher =
        ProguardPackageNameList.builder()
            .addPackageName(false, new ProguardPackageMatcher("*.example"))
            .addPackageName(true, new ProguardPackageMatcher("!org.example"))
            .build();
    assertTrue(matches(matcher, "com.example"));
    // Negations only stops attempts on subsequent names.
    assertTrue(matches(matcher, "org.example"));
  }

  @Test
  public void testNegateAll() {
    ProguardPackageNameList matcher =
        ProguardPackageNameList.builder()
            .addPackageName(true, new ProguardPackageMatcher("!**"))
            .build();
    assertFalse(matches(matcher, "com"));
    assertFalse(matches(matcher, "com.example"));
    assertFalse(matches(matcher, "com.example.a"));
  }
}
