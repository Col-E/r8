// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static com.android.tools.r8.utils.codeinspector.Matchers.containsLinePositions;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertEquals;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.naming.testclasses.ClassToBeMinified;
import com.android.tools.r8.naming.testclasses.Main;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.ProguardKeepAttributes;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.Matchers.LinePosition;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class RenameSourceFileRetraceTest extends TestBase {

  private static final String FILENAME_RENAME = "FOO";
  private static final String FILENAME_MAIN = "Main.java";
  private static final String FILENAME_CLASS_TO_BE_MINIFIED = "ClassToBeMinified.java";

  private final TestParameters parameters;
  private final boolean isCompat;
  private final boolean keepSourceFile;

  @Parameters(name = "{0}, is compat: {1}, keep source file attribute: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public RenameSourceFileRetraceTest(
      TestParameters parameters, Boolean isCompat, Boolean keepSourceFile) {
    this.parameters = parameters;
    this.isCompat = isCompat;
    this.keepSourceFile = keepSourceFile;
  }

  @Test
  public void testR8()
      throws ExecutionException, CompilationFailedException, IOException, NoSuchMethodException {
    R8TestBuilder<? extends R8TestBuilder<?>> r8TestBuilder =
        isCompat ? testForR8Compat(parameters.getBackend()) : testForR8(parameters.getBackend());
    if (keepSourceFile) {
      r8TestBuilder.addKeepAttributes(ProguardKeepAttributes.SOURCE_FILE);
    }
    String minifiedFileName =
        (keepSourceFile && isCompat) ? FILENAME_CLASS_TO_BE_MINIFIED : getDefaultExpectedName();
    String mainFileName = (keepSourceFile && isCompat) ? FILENAME_MAIN : getDefaultExpectedName();
    r8TestBuilder
        .addProgramClasses(ClassToBeMinified.class, Main.class)
        .addKeepAttributes(ProguardKeepAttributes.LINE_NUMBER_TABLE)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(containsString("ClassToBeMinified.foo()"))
        .inspectFailure(inspector -> inspectOutput(inspector, minifiedFileName, mainFileName))
        .inspectStackTrace(stackTrace -> inspectStackTrace(stackTrace, FILENAME_MAIN));
  }

  @Test
  public void testRenameSourceFileR8()
      throws ExecutionException, CompilationFailedException, IOException, NoSuchMethodException {
    R8TestBuilder<? extends R8TestBuilder<?>> r8TestBuilder =
        isCompat ? testForR8Compat(parameters.getBackend()) : testForR8(parameters.getBackend());
    if (keepSourceFile) {
      r8TestBuilder.addKeepAttributes(ProguardKeepAttributes.SOURCE_FILE);
    }
    String expectedName = getDefaultExpectedName(FILENAME_RENAME);
    r8TestBuilder
        .addProgramClasses(ClassToBeMinified.class, Main.class)
        .addKeepAttributes(ProguardKeepAttributes.LINE_NUMBER_TABLE)
        .addKeepRules("-renamesourcefileattribute " + FILENAME_RENAME)
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(containsString("ClassToBeMinified.foo()"))
        .inspectFailure(inspector -> inspectOutput(inspector, expectedName, expectedName))
        .inspectStackTrace(stackTrace -> inspectStackTrace(stackTrace, FILENAME_MAIN));
  }

  private String getDefaultExpectedName() {
    return "SourceFile";
  }

  private String getDefaultExpectedName(String name) {
    if (!keepSourceFile) {
      return getDefaultExpectedName();
    }
    return name;
  }

  private void inspectOutput(
      CodeInspector inspector, String classToBeMinifiedFilename, String mainClassFilename) {
    inspectSourceFileForClass(inspector, Main.class, mainClassFilename);
    inspectSourceFileForClass(inspector, ClassToBeMinified.class, classToBeMinifiedFilename);
  }

  private void inspectSourceFileForClass(CodeInspector inspector, Class<?> clazz, String expected) {
    ClassSubject classToBeMinifiedSubject = inspector.clazz(clazz);
    assertThat(classToBeMinifiedSubject, isPresent());
    DexClass dexClass = classToBeMinifiedSubject.getDexProgramClass();
    String actualString = dexClass.sourceFile == null ? null : dexClass.sourceFile.toString();
    assertEquals(expected, actualString);
  }

  private void inspectStackTrace(StackTrace stackTrace, String mainFileName)
      throws NoSuchMethodException {
    if (!keepSourceFile) {
      return;
    }
    assertEquals(2, stackTrace.getStackTraceLines().size());
    MethodReference classToBeMinifiedFoo =
        Reference.methodFromMethod(ClassToBeMinified.class.getDeclaredMethod("foo"));
    MethodReference mainMain =
        Reference.methodFromMethod(Main.class.getDeclaredMethod("main", String[].class));
    LinePosition expectedStack =
        LinePosition.stack(
            LinePosition.create(classToBeMinifiedFoo, 1, 13, FILENAME_CLASS_TO_BE_MINIFIED),
            LinePosition.create(mainMain, 1, 10, mainFileName));
    assertThat(stackTrace, containsLinePositions(expectedStack));
  }
}
