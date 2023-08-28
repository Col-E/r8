// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.naming.retrace.StackTrace;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.HorizontallyMergedClassesInspector;
import java.util.List;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class OutlineMappingInformationTest extends TestBase {

  private final TestParameters parameters;
  private final boolean throwInFirstOutline;
  private final boolean throwOnFirstCall;

  @Parameterized.Parameters(name = "{0}, throwInFirstOutline: {1}, throwOnFirstCall: {2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public OutlineMappingInformationTest(
      TestParameters parameters, boolean throwInFirstOutline, boolean throwOnFirstCall) {
    this.parameters = parameters;
    this.throwInFirstOutline = throwInFirstOutline;
    this.throwOnFirstCall = throwOnFirstCall;
  }

  StackTrace expectedStackTrace;

  @Before
  public void setup() throws Exception {
    expectedStackTrace =
        testForRuntime(parameters)
            .addProgramClasses(TestClass.class, TestClass2.class, Greeter.class)
            .run(
                parameters.getRuntime(),
                TestClass.class,
                throwInFirstOutline ? "0" : "1",
                throwOnFirstCall ? "0" : "1")
            .assertFailureWithErrorThatThrows(ArrayStoreException.class)
            .getStackTrace();
  }

  @Test
  public void test() throws Exception {
    R8TestCompileResult compileResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(TestClass.class, TestClass2.class, Greeter.class)
            .addKeepMainRule(TestClass.class)
            .addOptionsModification(
                options -> {
                  options.outline.threshold = 2;
                  options.outline.minSize = 2;
                })
            .addKeepAttributeLineNumberTable()
            .addKeepAttributeSourceFile()
            .enableNoHorizontalClassMergingAnnotations()
            .noHorizontalClassMergingOfSynthetics()
            .addHorizontallyMergedClassesInspector(
                HorizontallyMergedClassesInspector::assertNoClassesMerged)
            .enableInliningAnnotations()
            .setMinApi(parameters)
            .compile();
    compileResult
        .run(
            parameters.getRuntime(),
            TestClass.class,
            throwInFirstOutline ? "0" : "1",
            throwOnFirstCall ? "0" : "1")
        .assertFailureWithErrorThatThrows(ArrayStoreException.class)
        .inspectStackTrace(
            (stackTrace, inspector) -> {
              // Two outlines are created, one for
              //   Greeter.throwExceptionFirst();
              //   Greeter.throwExceptionSecond();
              // and one for
              //   new ArrayStoreException("Foo")
              assertEquals(5, inspector.allClasses().size());
              assertEquals(expectedStackTrace, stackTrace);
            });
    String proguardMap = compileResult.getProguardMap();

    if (parameters.isDexRuntime()) {
      // TODO(b/263357015, b/293630963): Outline information is not reset for new default events.
      assertEquals(6, StringUtils.occurrences(proguardMap, "com.android.tools.r8.outlineCallsite"));
    } else {
      assertEquals(4, StringUtils.occurrences(proguardMap, "com.android.tools.r8.outlineCallsite"));
    }
  }

  @NoHorizontalClassMerging
  static class TestClass {

    public static boolean shouldThrowInGreeter;
    public static boolean throwOnFirst;

    public static void main(String... args) {
      shouldThrowInGreeter = args[0].equals("0");
      throwOnFirst = args[1].equals("0");
      greet();
      shouldThrowInGreeter = true;
      TestClass2.greet();
    }

    @NeverInline
    static void greet() {
      Greeter.throwExceptionFirst();
      Greeter.throwExceptionSecond();
    }
  }

  @NoHorizontalClassMerging
  static class TestClass2 {

    @NeverInline
    static void greet() {
      // Keep on same line
      Greeter.throwExceptionFirst(); Greeter.throwExceptionSecond();
    }
  }

  @NoHorizontalClassMerging
  public static class Greeter {

    @NeverInline
    public static void throwExceptionFirst() {
      if (TestClass.shouldThrowInGreeter && TestClass.throwOnFirst) {
        throw new ArrayStoreException("Foo");
      }
    }

    @NeverInline
    public static void throwExceptionSecond() {
      if (TestClass.shouldThrowInGreeter && !TestClass.throwOnFirst) {
        throw new ArrayStoreException("Foo");
      }
    }
  }
}
