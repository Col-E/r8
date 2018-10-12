// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.innerclassesenclosingmethod;

import static com.android.tools.r8.utils.codeinspector.Matchers.isMemberClass;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.innerclassesenclosingmethod.testclasses.Outer;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;

class Main {

  public static void main(String[] args) {
    Class<?>[] declaredClasses = Outer.class.getDeclaredClasses();
    if (declaredClasses.length == 0) {
      System.out.println("No declared classes");
    } else {
      for (int i = 0; i < declaredClasses.length; i++) {
        System.out.println("Declared class: " + declaredClasses[i].getName());
      }
    }
    if (Outer.Inner.class.getDeclaringClass() == null) {
      System.out.println("No declaring classes");
    } else {
      System.out.println("Declaring class: " + Outer.Inner.class.getDeclaringClass().getName());
    }
  }
}

public class KeepInnerClassesEnclosingMethodAnnotationsTest extends TestBase {
  private static class TestResult {

    public final AndroidApp app;
    public final CodeInspector inspector;
    public final ClassSubject outer;
    public final ClassSubject inner;

    TestResult(AndroidApp app) throws Throwable {
      this.app = app;
      this.inspector = new CodeInspector(app);
      this.outer = inspector.clazz(Outer.class);
      this.inner = inspector.clazz(Outer.Inner.class);
      assertThat(outer, isPresent());
      assertThat(inner, isPresent());
    }
  }

  private TestResult runTest(List<String> proguardConfiguration) throws Throwable {
    return new TestResult(
        ToolHelper.runR8(
            R8Command.builder()
                .addProgramFiles(ToolHelper.getClassFilesForTestPackage(Outer.class.getPackage()))
                .addClassProgramData(ToolHelper.getClassAsBytes(Main.class), Origin.unknown())
                .addProguardConfiguration(proguardConfiguration, Origin.unknown())
                .setProgramConsumer(emptyConsumer(Backend.DEX))
                .build()));
  }

  private void noInnerClassesEnclosingMethodInformation(TestResult result) throws Throwable {
    List<String> lines = StringUtils.splitLines(runOnArt(result.app, Main.class));
    assertEquals(2, lines.size());
    assertEquals("No declared classes", lines.get(0));
    assertEquals("No declaring classes", lines.get(1));
  }

  private void fullInnerClassesEnclosingMethodInformation(TestResult result) throws Throwable {
    List<String> lines = StringUtils.splitLines(runOnArt(result.app, Main.class));
    assertEquals(2, lines.size());
    assertEquals("Declared class: " + result.inner.getFinalName(), lines.get(0));
    assertEquals("Declaring class: " + result.outer.getFinalName(), lines.get(1));
  }

  @Test
  public void testKeepAll() throws Throwable {
    TestResult result =
        runTest(
            ImmutableList.of(
                "-keepattributes InnerClasses,EnclosingMethod",
                "-keep class **Outer*",
                keepMainProguardConfiguration(Main.class)));
    assertThat(result.outer, not(isRenamed()));
    assertThat(result.inner, not(isRenamed()));
    assertThat(result.inner, isMemberClass());
    fullInnerClassesEnclosingMethodInformation(result);
  }

  @Test
  public void testKeepAllWithoutAttributes() throws Throwable {
    TestResult result =
        runTest(
            ImmutableList.of("-keep class **Outer*", keepMainProguardConfiguration(Main.class)));
    assertThat(result.outer, not(isRenamed()));
    assertThat(result.inner, not(isRenamed()));
    assertThat(result.inner, not(isMemberClass()));
    noInnerClassesEnclosingMethodInformation(result);
  }

  @Test
  public void testKeepInner() throws Throwable {
    TestResult result =
        runTest(
            ImmutableList.of(
                "-keepattributes InnerClasses,EnclosingMethod",
                "-keep class **Outer$Inner",
                keepMainProguardConfiguration(Main.class)));
    assertThat(result.outer, isRenamed());
    assertThat(result.inner, not(isRenamed()));
    assertThat(result.inner, isMemberClass());
    fullInnerClassesEnclosingMethodInformation(result);
  }

  @Test
  public void testKeepOuter() throws Throwable {
    TestResult result =
        runTest(
            ImmutableList.of(
                "-keepattributes InnerClasses,EnclosingMethod",
                "-keep class **Outer",
                keepMainProguardConfiguration(Main.class)));
    assertThat(result.outer, not(isRenamed()));
    assertThat(result.inner, isRenamed());
    assertThat(result.inner, isMemberClass());
    fullInnerClassesEnclosingMethodInformation(result);
  }
}
