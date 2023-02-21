// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageWithPackagePrivateLibraryOverrideTest extends RepackageTestBase {

  private final String[] EXPECTED = new String[] {"Program::foo"};

  public RepackageWithPackagePrivateLibraryOverrideTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addLibraryClasses(Library.class)
        .addProgramClasses(Program.class, Main.class)
        .addRunClasspathFiles(buildOnDexRuntime(parameters, Library.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryClasses(Library.class)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(Program.class, Main.class)
        .apply(this::configureRepackaging)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .enableNeverClassInliningAnnotations()
        .compile()
        .addRunClasspathFiles(buildOnDexRuntime(parameters, Library.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    assertThat(Program.class, isNotRepackaged(inspector));
  }

  public static class Library {

    void foo() {
      System.out.println("Library::foo");
    }

    public void callFoo() {
      foo();
    }
  }

  @NeverClassInline
  public static class Program extends Library {

    @Override
    void foo() {
      System.out.println("Program::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new Program().callFoo();
    }
  }
}
