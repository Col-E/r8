// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.TestRuntime.getCheckedInJdk11;
import static org.hamcrest.CoreMatchers.anyOf;
import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.nestaccesscontrol.methodparameters.Outer;
import java.nio.file.Path;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MethodParametersTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // java.lang.reflect.Method.getParameters() supported from Android 8.1.
    return getTestParameters()
        .withDexRuntimesStartingFromIncluding(Version.V8_1_0)
        .withCfRuntimes()
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  public MethodParametersTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    // Compile with javac from JDK 11 to get a class file using nest access control.
    Path nestCompiledWithParameters =
        javac(getCheckedInJdk11())
            .addSourceFiles(ToolHelper.getSourceFileForTestClass(Outer.class))
            .addOptions("-parameters")
            .compile();

    Path nestDesugared =
        testForD8(Backend.CF)
            .addProgramFiles(nestCompiledWithParameters)
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();

    Path nestDesugaredTwice =
        testForD8(Backend.CF)
            .addProgramFiles(nestDesugared)
            .setMinApi(parameters.getApiLevel())
            .compile()
            // TODO(b/189743726): These warnings should not be there.
            .assertAtLeastOneInfoMessage()
            .assertAllInfoMessagesMatch(
                anyOf(
                    containsString("Invalid parameter counts in MethodParameter attributes"),
                    containsString("Methods with invalid MethodParameter attributes")))
            .writeToZip();

    Path programDesugared =
        testForD8(Backend.CF)
            .addClasspathClasses(Outer.class)
            .addInnerClasses(getClass())
            .setMinApi(parameters.getApiLevel())
            .compile()
            .writeToZip();

    testForD8(parameters.getBackend())
        .addProgramFiles(nestDesugaredTwice)
        .addProgramFiles(programDesugared)
        .setMinApi(parameters.getApiLevel())
        .compile()
        // TODO(b/189743726): These warnings should not be there.
        .assertAtLeastOneInfoMessage()
        .assertAllInfoMessagesMatch(
            anyOf(
                containsString("Invalid parameter counts in MethodParameter attributes"),
                containsString("Methods with invalid MethodParameter attributes")))
        .run(parameters.getRuntime(), TestRunner.class)
        // TODO(b/189743726): Should not fail at runtime.
        .assertFailureWithErrorThatMatches(
            containsString("Wrong number of parameters in MethodParameters attribute"));
  }

  static class TestRunner {

    public static void main(String[] args) {
      Outer.callPrivateInnerConstructorZeroArgs();
    }
  }
}
