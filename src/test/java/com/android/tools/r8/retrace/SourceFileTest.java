// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SourceFileTest extends TestBase {

  private final TestParameters parameters;
  private final boolean useRegularExpression;
  private static final String FILE_NAME = "foobarbaz.java";

  @Parameters(name = "{0}, useRegularExpression: {1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  public SourceFileTest(TestParameters parameters, boolean useRegularExpression) {
    this.parameters = parameters;
    this.useRegularExpression = useRegularExpression;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(Main.class, ClassWithoutCustomFileName.class)
        .addProgramClassFileData(
            transformer(ClassWithCustomFileName.class).setSourceFile(FILE_NAME).transform())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(containsString("Hello World!"))
        .inspectStackTrace(
            stackTrace -> {
              assertEquals(FILE_NAME, stackTrace.getStackTraceLines().get(0).fileName);
            });
  }

  @Test
  public void testR8WithCustomFileName() throws Exception {
    runTest(
        false,
        ((stackTrace, inspector) -> {
          assertEquals(FILE_NAME, stackTrace.getStackTraceLines().get(0).fileName);
          assertEquals(
              1,
              inspector
                  .clazz(ClassWithCustomFileName.class)
                  .getNaming()
                  .getAdditionalMappings()
                  .size());
        }));
  }

  @Test
  public void testR8WithoutCustomFileName() throws Exception {
    runTest(
        true,
        ((stackTrace, inspector) -> {
          assertEquals("SourceFileTest.java", stackTrace.getStackTraceLines().get(0).fileName);
          assertEquals(
              0,
              inspector
                  .clazz(ClassWithoutCustomFileName.class)
                  .getNaming()
                  .getAdditionalMappings()
                  .size());
        }));
  }

  private void runTest(boolean addDummyArg, BiConsumer<StackTrace, CodeInspector> consumer)
      throws Exception {
    R8FullTestBuilder r8FullTestBuilder =
        testForR8(parameters.getBackend())
            .addProgramClasses(Main.class, ClassWithoutCustomFileName.class)
            .addProgramClassFileData(
                transformer(ClassWithCustomFileName.class).setSourceFile(FILE_NAME).transform())
            .addKeepClassRules(ClassWithoutCustomFileName.class)
            .enableInliningAnnotations()
            .addKeepMainRule(Main.class)
            .setMinApi(parameters.getApiLevel())
            .addKeepAttributeSourceFile();
    R8TestRunResult runResult =
        addDummyArg
            ? r8FullTestBuilder.run(parameters.getRuntime(), Main.class, "foo")
            : r8FullTestBuilder.run(parameters.getRuntime(), Main.class);
    runResult.assertFailureWithErrorThatMatches(containsString("Hello World!"));
    StackTrace originalStackTrace = runResult.getOriginalStackTrace();
    StackTrace retracedStackTrace =
        originalStackTrace.retrace(
            runResult.proguardMap(),
            useRegularExpression ? Retrace.DEFAULT_REGULAR_EXPRESSION : null);
    runResult.inspectFailure(inspector -> consumer.accept(retracedStackTrace, inspector));
  }

  public static class ClassWithoutCustomFileName {

    @NeverInline
    public static void foo() {
      throw new RuntimeException("Hello World!");
    }
  }

  public static class ClassWithCustomFileName {

    @NeverInline
    public static void foo() {
      throw new RuntimeException("Hello World!");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      if (args.length == 0) {
        ClassWithCustomFileName.foo();
      } else {
        ClassWithoutCustomFileName.foo();
      }
    }
  }
}
