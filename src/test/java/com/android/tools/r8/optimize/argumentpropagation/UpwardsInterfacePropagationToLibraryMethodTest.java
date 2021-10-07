// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

// This is a regression test for b/202074964.
@RunWith(Parameterized.class)
public class UpwardsInterfacePropagationToLibraryMethodTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build());
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("LibraryClass::libraryMethod(false)");
  private static final List<Class<?>> LIBRARY_CLASSES = ImmutableList.of(LibraryClass.class);
  private static final List<Class<?>> PROGRAM_CLASSES =
      ImmutableList.of(ProgramClass.class, Delegate.class, Delegater.class, TestClass.class);

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(LIBRARY_CLASSES)
        .addProgramClasses(PROGRAM_CLASSES)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.S))
        .addLibraryClasses(LIBRARY_CLASSES)
        .addProgramClasses(PROGRAM_CLASSES)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters.getApiLevel())
        .enableNoVerticalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .compile()
        .addRunClasspathClasses(LibraryClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        // TODO(b/202074964): This should not fail.
        .assertFailureWithErrorThatThrows(AbstractMethodError.class);
  }

  public static class LibraryClass {
    public void libraryMethod(boolean visible) {
      System.out.println("LibraryClass::libraryMethod(" + visible + ")");
    }
  }

  @NoVerticalClassMerging
  public interface Delegate {
    void libraryMethod(boolean visible);
  }

  @NeverClassInline
  public static class ProgramClass extends LibraryClass implements Delegate {
    Delegater delegater;

    public ProgramClass() {
      delegater = new Delegater(this);
    }

    @NeverInline
    public void m() {
      delegater.m();
    }
  }

  @NoVerticalClassMerging
  @NeverClassInline
  public static class Delegater {
    Delegate delegate;

    Delegater(Delegate delegate) {
      this.delegate = delegate;
    }

    public void m() {
      delegate.libraryMethod(false);
    }
  }

  public static class TestClass {

    public static void main(String[] args) {
      new ProgramClass().m();
    }
  }
}
