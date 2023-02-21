// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.repackage;

import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class RepackageWithPackagePrivateLibraryTypeTest extends RepackageTestBase {

  private final String[] EXPECTED =
      new String[] {
        "class com.android.tools.r8.repackage.RepackageWithPackagePrivateLibraryTypeTest$Library",
        "ProgramSub::foo",
        "ProgramSub2::foo"
      };

  public RepackageWithPackagePrivateLibraryTypeTest(
      String flattenPackageHierarchyOrRepackageClasses, TestParameters parameters) {
    super(flattenPackageHierarchyOrRepackageClasses, parameters);
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addLibraryClasses(Library.class, LibraryI.class)
        .addProgramClasses(Program.class, ProgramSub.class, ProgramSub2.class, Main.class)
        .addRunClasspathFiles(buildOnDexRuntime(parameters, Library.class, LibraryI.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addLibraryClasses(Library.class, LibraryI.class)
        .addDefaultRuntimeLibrary(parameters)
        .addProgramClasses(Program.class, ProgramSub.class, ProgramSub2.class, Main.class)
        .apply(this::configureRepackaging)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .addRunClasspathFiles(buildOnDexRuntime(parameters, Library.class, LibraryI.class))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(this::inspect);
  }

  private void inspect(CodeInspector inspector) {
    assertThat(Program.class, isNotRepackaged(inspector));
    assertThat(ProgramSub.class, isNotRepackaged(inspector));
  }

  static class Library {}

  interface LibraryI {}

  public static class Program {

    @NeverInline
    public static void bar() {
      System.out.println(Library.class.toString());
    }
  }

  @NeverClassInline
  public static class ProgramSub extends Library {

    @NeverInline
    public void foo() {
      System.out.println("ProgramSub::foo");
    }
  }

  @NeverClassInline
  public static class ProgramSub2 implements LibraryI {

    @NeverInline
    public void foo() {
      System.out.println("ProgramSub2::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      Program.bar();
      new ProgramSub().foo();
      new ProgramSub2().foo();
    }
  }
}
