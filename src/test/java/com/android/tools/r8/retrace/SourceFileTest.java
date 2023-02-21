// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.retrace.internal.RetraceUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.function.BiConsumer;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SourceFileTest extends TestBase {

  private final TestParameters parameters;
  private static final String FILE_NAME = "foobarbaz.java";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public SourceFileTest(TestParameters parameters) {
    this.parameters = parameters;
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
              FILE_NAME,
              inspector
                  .clazz(ClassWithCustomFileName.class)
                  .retraceUnique()
                  .getSourceFile()
                  .getSourceFile());
        }));
  }

  @Test
  public void testR8WithoutCustomFileName() throws Exception {
    runTest(
        true,
        ((stackTrace, inspector) -> {
          // Since the type has a mapping, the file is inferred from the class name.
          assertEquals("SourceFileTest.java", stackTrace.getStackTraceLines().get(0).fileName);
          RetraceClassElement retraceClassElement =
              inspector.clazz(ClassWithoutCustomFileName.class).retraceUnique();
          assertEquals(
              "SourceFileTest.java",
              RetraceUtils.inferSourceFile(
                  retraceClassElement.getRetracedClass().getTypeName(), "nofile.java", true));
        }));
  }

  private void runTest(boolean addDummyArg, BiConsumer<StackTrace, CodeInspector> consumer)
      throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(Main.class, ClassWithoutCustomFileName.class)
            .addProgramClassFileData(
                transformer(ClassWithCustomFileName.class).setSourceFile(FILE_NAME).transform())
            .addKeepClassRules(ClassWithoutCustomFileName.class)
            .enableInliningAnnotations()
            .addKeepMainRule(Main.class)
            .setMinApi(parameters)
            .addKeepAttributeSourceFile()
            .compile();
    R8TestRunResult runResult =
        addDummyArg
            ? compileResult.run(parameters.getRuntime(), Main.class, "foo")
            : compileResult.run(parameters.getRuntime(), Main.class);
    runResult.assertFailureWithErrorThatMatches(containsString("Hello World!"));
    StackTrace originalStackTrace = runResult.getOriginalStackTrace();
    StackTrace retracedStackTrace = originalStackTrace.retrace(runResult.proguardMap());
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
