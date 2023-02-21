// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.CLASSES_PATH;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.CLASS_NAMES;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.JAR;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.getExpectedResult;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.getMainClass;
import static com.android.tools.r8.utils.FileUtils.CLASS_EXTENSION;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.core.StringContains.containsString;
import static org.hamcrest.core.StringEndsWith.endsWith;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import java.nio.file.Path;
import java.util.List;
import org.hamcrest.Matcher;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestOnProgramAndClassPathTest extends TestBase {

  public NestOnProgramAndClassPathTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  @Test
  public void testD8MethodBridgesPresent() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    // 1 inner class.
    D8TestCompileResult singleInner =
        compileClassesWithD8ProgramClassesMatching(
            containsString("BasicNestHostWithInnerClassMethods$BasicNestedClass"));
    singleInner.inspect(inspector -> assertThisNumberOfBridges(inspector, 2));
    // Outer class.
    D8TestCompileResult host =
        compileClassesWithD8ProgramClassesMatching(endsWith("BasicNestHostWithInnerClassMethods"));
    host.inspect(inspector -> assertThisNumberOfBridges(inspector, 2));
    // 2 inner classes.
    D8TestCompileResult multipleInner =
        compileClassesWithD8ProgramClassesMatching(
            containsString("NestHostExample$StaticNestMemberInner"));
    multipleInner.inspect(inspector -> assertThisNumberOfBridges(inspector, 5));
  }

  @Test
  public void testD8ConstructorBridgesPresent() throws Exception {
    Assume.assumeTrue(parameters.isDexRuntime());
    D8TestCompileResult inner =
        compileClassesWithD8ProgramClassesMatching(
            containsString("BasicNestHostWithInnerClassConstructors$BasicNestedClass"));
    inner.inspect(
        inspector -> {
          assertThisNumberOfBridges(inspector, 3);
          assertNestConstructor(inspector);
        });
    D8TestCompileResult host =
        compileClassesWithD8ProgramClassesMatching(
            endsWith("BasicNestHostWithInnerClassConstructors"));
    host.inspect(
        inspector -> {
          assertThisNumberOfBridges(inspector, 1);
          assertNestConstructor(inspector);
        });
  }

  @Test
  public void testD8ConstructorNestMergeCorrect() throws Exception {
    // Multiple Nest Constructor classes have to be merged here.
    Assume.assumeTrue(parameters.isDexRuntime());
    D8TestCompileResult inner =
        compileClassesWithD8ProgramClassesMatching(
            containsString("BasicNestHostWithInnerClassConstructors$BasicNestedClass"));
    D8TestCompileResult host =
        compileClassesWithD8ProgramClassesMatching(
            endsWith("BasicNestHostWithInnerClassConstructors"));
    testForD8()
        .addProgramFiles(inner.writeToZip(), host.writeToZip())
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), getMainClass("constructors"))
        .assertSuccessWithOutput(getExpectedResult("constructors"));
  }

  private D8TestCompileResult compileClassesWithD8ProgramClassesMatching(Matcher<String> matcher)
      throws Exception {
    List<Path> matchingClasses =
        CLASS_NAMES.stream()
            .filter(matcher::matches)
            .map(name -> CLASSES_PATH.resolve(name + CLASS_EXTENSION))
            .collect(toList());
    return testForD8()
        .setMinApi(parameters)
        .addProgramFiles(matchingClasses)
        .addClasspathFiles(JAR)
        .compile();
  }

  private static void assertNestConstructor(CodeInspector inspector) {
    assertTrue(inspector.allClasses().stream().anyMatch(FoundClassSubject::isSynthetic));
  }

  private static void assertThisNumberOfBridges(CodeInspector inspector, int numBridges) {
    for (FoundClassSubject clazz : inspector.allClasses()) {
      if (!clazz.isSynthetic()) {
        assertEquals(numBridges, clazz.allMethods(FoundMethodSubject::isSynthetic).size());
      }
    }
  }
}
