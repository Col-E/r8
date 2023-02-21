// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.argumentpropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
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
public class UpwardsInterfacePropagationToLibraryOrClasspathMethodTest extends TestBase {

  private enum LibraryOrClasspath {
    LIBRARY,
    CLASSPATH;

    private boolean isLibrary() {
      return this == LIBRARY;
    }
  }

  @Parameter(0)
  public TestParameters parameters;

  @Parameter(1)
  public LibraryOrClasspath libraryOrClasspath;

  @Parameters(name = "{0} {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        LibraryOrClasspath.values());
  }

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines("LibraryClass::libraryMethod(false)", "ProgramClass2::libraryMethod(true)");
  private static final List<Class<?>> LIBRARY_CLASSES = ImmutableList.of(LibraryClass.class);
  private static final List<Class<?>> PROGRAM_CLASSES =
      ImmutableList.of(
          ProgramClass.class,
          Delegate.class,
          Delegater.class,
          AnotherProgramClass.class,
          AnotherDelegate.class,
          AnotherDelegator.class,
          TestClass.class);

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
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.S))
        .apply(
            b -> {
              if (libraryOrClasspath.isLibrary()) {
                b.addLibraryClasses(LIBRARY_CLASSES);
              } else {
                b.addClasspathClasses(LIBRARY_CLASSES);
              }
            })
        .addProgramClasses(PROGRAM_CLASSES)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .enableNoVerticalClassMergingAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableInliningAnnotations()
        .addHorizontallyMergedClassesInspector(
            HorizontallyMergedClassesInspector::assertNoClassesMerged)
        .compile()
        .addRunClasspathClasses(LibraryClass.class)
        .run(parameters.getRuntime(), TestClass.class)
        .inspect(
            inspector -> {
              assertThat(
                  inspector.clazz(Delegate.class).method("void", "libraryMethod", "boolean"),
                  isPresent());
              // Check that boolean argument to libraryMethod was removed for AnotherProgramClass.
              inspector
                  .clazz(AnotherProgramClass.class)
                  .forAllMethods(
                      method -> assertEquals(method.getFinalSignature().toDescriptor(), "()V"));
            })
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
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
  @NoHorizontalClassMerging
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

  @NoVerticalClassMerging
  @NoHorizontalClassMerging
  public interface AnotherDelegate {
    void libraryMethod(boolean visible);
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class AnotherProgramClass implements AnotherDelegate {
    AnotherDelegator delegater;

    public AnotherProgramClass() {
      delegater = new AnotherDelegator(this);
    }

    @NeverInline
    public void libraryMethod(boolean visible) {
      System.out.println("ProgramClass2::libraryMethod(" + visible + ")");
    }

    @NeverInline
    public void m() {
      delegater.m();
    }
  }

  @NoVerticalClassMerging
  @NeverClassInline
  public static class AnotherDelegator {
    AnotherDelegate delegate;

    AnotherDelegator(AnotherDelegate delegate) {
      this.delegate = delegate;
    }

    public void m() {
      delegate.libraryMethod(true);
    }
  }

  public static class TestClass {

    public static void main(String[] args) {
      new ProgramClass().m();
      new AnotherProgramClass().m();
    }
  }
}
