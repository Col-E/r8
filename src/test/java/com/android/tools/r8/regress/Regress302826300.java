// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class Regress302826300 extends TestBase {

  static final String EXPECTED = "foobar";

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public Regress302826300(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    D8TestRunResult run =
        testForD8(parameters.getBackend())
            // We simply pass LibraryBaseClass as program, but could also compile it separately and
            // pass as bootclasspath when running.
            .addProgramClasses(
                Foo.class, Bar.class, ProgramClassExtendsLibrary.class, LibraryBaseClass.class)
            // LibraryClass is passed when compiling - but not passed when running
            .addLibraryClasses(LibraryClass.class)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), Foo.class);
    if (parameters.getRuntime().asDex().getVersion().isDalvik()) {
      run.assertFailureWithErrorThatMatches(containsString("rejecting opcode 0x6e"));
    } else {
      run.assertSuccessWithOutputLines(EXPECTED);
    }
  }

  public static class Foo {
    public static void main(String[] args) {
      new Bar().x("foobar");
    }
  }

  public static class Bar {
    public void x(ProgramClassExtendsLibrary value) {
      // To trigger the hard verification error it is important that the invoke virtual is to
      // a class actually known at runtime - but not the ProgramClassExtendsLibrary which we
      // don't know so it only triggers a soft verification error.
      ((LibraryBaseClass) value).z();
    }

    public void x(String s) {
      System.out.println(s);
    }
  }

  public static class ProgramClassExtendsLibrary extends LibraryClass {}

  public static class LibraryClass extends LibraryBaseClass {}

  public static class LibraryBaseClass {
    public void z() {
      System.out.println("z()");
    }
  }
}
