// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.allOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.UnverifiableCfCodeDiagnostic;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class MissingInterfaceTest extends TestBase {

  private final TestParameters parameters;
  private final String[] EXPECTED = new String[] {"B112849320", "8"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  public MissingInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test_missingInterface() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClassForB112849320.class)
        .addDontWarn(GoingToBeMissed.class)
        .setMinApi(parameters)
        .addKeepMainRule(TestClassForB112849320.class)
        .addOptionsModification(options -> options.inlinerOptions().enableInlining = false)
        .addKeepPackageNamesRule(GoingToBeMissed.class.getPackage())
        .allowDiagnosticWarningMessages()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertWarningsMatch(
                    allOf(
                        diagnosticType(UnverifiableCfCodeDiagnostic.class),
                        diagnosticMessage(
                            containsString(
                                "Unverifiable code in `"
                                    + "void "
                                    + TestClassForB112849320.class.getTypeName()
                                    + ".main(java.lang.String[])`")))))
        .addRunClasspathFiles(
            buildOnDexRuntime(
                parameters,
                writeToJar(ImmutableList.of(ToolHelper.getClassAsBytes(GoingToBeMissed.class)))))
        .run(parameters.getRuntime(), TestClassForB112849320.class)
        .forCfRuntime(r -> r.assertSuccessWithOutputLines(EXPECTED))
        .otherwise(
            r -> {
              r.assertStdoutMatches(containsString("B112849320"));
              r.assertFailureWithErrorThatThrows(AbstractMethodError.class);
            });
  }


  @Test
  public void test_passingInterfaceAsLib() throws Exception {
    Path lib = writeToJar(ImmutableList.of(ToolHelper.getClassAsBytes(GoingToBeMissed.class)));
    testForR8(parameters.getBackend())
        .addProgramClasses(TestClassForB112849320.class)
        .addLibraryFiles(lib)
        .addDefaultRuntimeLibrary(parameters)
        .setMinApi(parameters)
        .addKeepMainRule(TestClassForB112849320.class)
        .addOptionsModification(options -> options.inlinerOptions().enableInlining = false)
        .addRunClasspathFiles(buildOnDexRuntime(parameters, lib))
        .run(parameters.getRuntime(), TestClassForB112849320.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  interface GoingToBeMissed {
    void onSomeEvent(long soLong);
  }

  public static class TestClassForB112849320 {
    GoingToBeMissed instance;

    void foo(GoingToBeMissed instance) {
      System.out.println("B112849320");
      this.instance = instance;
    }

    void bar() {
      instance.onSomeEvent(8L);
    }

    public static void main(String[] args) {
      TestClassForB112849320 self = new TestClassForB112849320();
      self.foo(
          l -> {
            if (l > 0) {
              System.out.println(l);
            }
          });
      self.bar();
    }
  }
}
