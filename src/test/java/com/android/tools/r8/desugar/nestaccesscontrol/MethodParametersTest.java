// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.TestRuntime.getCheckedInJdk11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.desugar.nestaccesscontrol.methodparameters.Outer;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.nio.file.Path;
import java.util.List;
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
            .setMinApi(parameters)
            .compile()
            .assertNoMessages()
            .inspect(this::verifyNoAnnotationsOnSyntheticConstructors)
            .writeToZip();

    Path nestDesugaredTwice =
        testForD8(Backend.CF)
            .addProgramFiles(nestDesugared)
            .setMinApi(parameters)
            .compile()
            .assertNoMessages()
            .inspect(this::verifyNoAnnotationsOnSyntheticConstructors)
            .writeToZip();

    Path programDesugared =
        testForD8(Backend.CF)
            .addClasspathClasses(Outer.class)
            .addInnerClasses(getClass())
            .setMinApi(parameters)
            .compile()
            .assertNoMessages()
            .writeToZip();

    testForD8(parameters.getBackend())
        .addProgramFiles(nestDesugaredTwice)
        .addProgramFiles(programDesugared)
        .setMinApi(parameters)
        .compile()
        .assertNoMessages()
        .inspect(this::verifyNoAnnotationsOnSyntheticConstructors)
        .run(parameters.getRuntime(), TestRunner.class)
        // Order of constructors is different on dex due to sorting.
        .applyIf(
            parameters.isCfRuntime(),
            result ->
                result.assertSuccessWithOutputLines(
                    "Outer$Inner-IA, 1",
                    "int, Outer$Inner-IA, 2",
                    "int, int, Outer$Inner-IA, 3",
                    "int, int, 2",
                    "int, 1",
                    "0"),
            result ->
                result.assertSuccessWithOutputLines(
                    "0",
                    "int, 1",
                    "int, int, 2",
                    "int, int, Outer$Inner-IA, 3",
                    "int, Outer$Inner-IA, 2",
                    "Outer$Inner-IA, 1"));
  }

  @Test
  public void testJavacBridges() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    verifyNoAnnotationsOnSyntheticConstructors(
        new CodeInspector(ToolHelper.getClassFileForTestClass(Outer.Inner.class)));
  }

  private void verifyNoAnnotationsOnSyntheticConstructors(CodeInspector inspector) {
    ClassSubject innerClassSubject = inspector.clazz(Outer.Inner.class);
    List<FoundMethodSubject> syntheticInitializers =
        innerClassSubject.allMethods(
            method -> method.isInstanceInitializer() && method.isSynthetic());
    assertEquals(3, syntheticInitializers.size());
    syntheticInitializers.forEach(
        syntheticInitializer ->
            assertTrue(
                syntheticInitializer.getProgramMethod().getDefinition().annotations().isEmpty()));
  }

  static class TestRunner {

    public static void main(String[] args) {
      Outer.callPrivateInnerConstructorZeroArgs();
    }
  }
}
