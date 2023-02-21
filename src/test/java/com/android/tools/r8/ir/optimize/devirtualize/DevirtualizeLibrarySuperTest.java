// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.devirtualize;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithHolderAndName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class DevirtualizeLibrarySuperTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    boolean hasNewLibraryHierarchyOnClassPath =
        parameters.isCfRuntime()
            || parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.M);
    testForR8(parameters.getBackend())
        .addLibraryClasses(Library.class, LibraryOverride.class, LibraryBoundary.class)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(Main.class)
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .compile()
        .inspect(
            inspector -> {
              MethodSubject fooMethod =
                  inspector.clazz(Main.class).uniqueMethodWithOriginalName("foo");
              assertThat(fooMethod, isPresent());
              assertThat(
                  fooMethod,
                  not(invokesMethodWithHolderAndName(LibraryOverride.class.getTypeName(), "foo")));
            })
        .applyIf(
            hasNewLibraryHierarchyOnClassPath,
            b ->
                b.addRunClasspathClasses(
                    Library.class, LibraryOverride.class, LibraryBoundary.class),
            b ->
                b.addRunClasspathClassFileData(
                    transformer(Library.class).transform(),
                    transformer(LibraryBoundary.class)
                        .replaceClassDescriptorInMethodInstructions(
                            descriptor(LibraryOverride.class), descriptor(Library.class))
                        .setSuper(descriptor(Library.class))
                        .transform()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLinesIf(hasNewLibraryHierarchyOnClassPath, "LibraryOverride::foo")
        .assertSuccessWithOutputLinesIf(!hasNewLibraryHierarchyOnClassPath, "Library::foo");
  }

  public static class Library {

    public void foo() {
      System.out.println("Library::foo");
    }
  }

  // This class will is inserted in the hierarchy from api 23.
  public static class LibraryOverride extends Library {

    @Override
    public void foo() {
      System.out.println("LibraryOverride::foo");
    }
  }

  public static class LibraryBoundary extends LibraryOverride {}

  public static class Main extends LibraryBoundary {

    @NeverInline
    @Override
    public void foo() {
      super.foo();
    }

    public static void main(String[] args) {
      new Main().foo();
    }
  }
}
