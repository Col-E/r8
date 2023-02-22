// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class LibraryExtendsProgramRefinedReceiverIsLibraryClass extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // Only test on CF, as the test use additional runtime classpath classes.
    return getTestParameters().withCfRuntimes().build();
  }

  // Regression test for b/145645482. Resolution issue related to lookupSingleVirtualTarget for
  // library extending program. The concrete issue hit in the Android Platform build this was in
  // a debug build, where the "debug write" instruction introduces an "alias" causing the
  // "receiver lower bound" to be unknown (null). With a receiver type of ProgramClass and a
  // "refined receiver type" of LibraryClass (lattice type of the debug write instruction) the
  // issue appeared.
  //
  // However, with a phi the same could happen in release mode. Again the "refined receiver type"
  // becomes LibraryClass (lattice type of the phi).

  public LibraryExtendsProgramRefinedReceiverIsLibraryClass(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void regression145645482DebugMode() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .addLibraryClasses(LibraryClass.class)
        .addProgramClasses(ProgramClass.class, ProgramTestRunnerWithoutPhi.class)
        .enableInliningAnnotations()
        .addKeepClassRules(ProgramClass.class)
        .addKeepMainRule(ProgramTestRunnerWithoutPhi.class)
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .debug()
        .compile()
        .assertAllWarningMessagesMatch(containsString("extends program class"))
        .addRunClasspathClasses(LibraryClass.class)
        .run(parameters.getRuntime(), ProgramTestRunnerWithoutPhi.class)
        .assertSuccessWithOutput(StringUtils.lines("SUCCESS"));
  }

  @Test
  public void regression145645482ReleaseMode() throws Exception {
    testForR8Compat(parameters.getBackend())
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .addLibraryClasses(LibraryClass.class)
        .addProgramClasses(ProgramClass.class, ProgramTestRunnerWithPhi.class)
        .enableInliningAnnotations()
        .addKeepClassRules(ProgramClass.class)
        .addKeepMainRule(ProgramTestRunnerWithPhi.class)
        .allowDiagnosticWarningMessages()
        .setMinApi(parameters)
        .compile()
        .assertAllWarningMessagesMatch(containsString("extends program class"))
        .addRunClasspathClasses(LibraryClass.class)
        .run(parameters.getRuntime(), ProgramTestRunnerWithPhi.class)
        .assertSuccessWithOutput(StringUtils.lines("SUCCESS"));
  }

  static class ProgramClass {
    public void addTestSuite(Class<?> suite) {
    }
  }

  static class LibraryClass extends ProgramClass {
    @Override
    public void addTestSuite(Class<?> suite) {
      super.addTestSuite(suite);
    }
  }

  static class ProgramTestRunnerWithoutPhi {
    @NeverInline
    public static ProgramClass test() {
      ProgramClass suite = new LibraryClass();
      suite.addTestSuite(ProgramTestRunnerWithoutPhi.class);
      return suite;
    }

    public static void main(String[] args) {
      ProgramTestRunnerWithoutPhi.test();
      System.out.println("SUCCESS");
    }
  }

  static class ProgramTestRunnerWithPhi {
    @NeverInline
    public static ProgramClass test(ProgramClass otherSuite) {
      ProgramClass suite = System.currentTimeMillis() > 0 ? new LibraryClass(): otherSuite;
      suite.addTestSuite(ProgramTestRunnerWithPhi.class);
      return suite;
    }

    public static void main(String[] args) {
      ProgramTestRunnerWithPhi.test(null);
      System.out.println("SUCCESS");
    }
  }
}
