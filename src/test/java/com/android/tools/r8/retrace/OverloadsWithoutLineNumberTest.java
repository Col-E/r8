// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.Box;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class OverloadsWithoutLineNumberTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult run =
        testForR8(parameters.getBackend())
            .addProgramClasses(Main.class)
            .addProgramClassFileData(
                transformer(ClassWithOverload.class)
                    .removeLineNumberTable(MethodPredicate.all())
                    .transform())
            .addKeepAttributeLineNumberTable()
            .setMinApi(parameters)
            .addKeepRules(
                "-keepclassmembers class " + typeName(ClassWithOverload.class) + " { *; }")
            .addKeepClassAndMembersRules(Main.class)
            .run(parameters.getRuntime(), Main.class)
            .assertFailureWithErrorThatMatches(containsString("FOO"));

    Box<List<String>> box = new Box<>();
    List<String> originalStackTrace =
        run.getOriginalStackTrace().getStackTraceLines().stream()
            .map(x -> x.originalLine)
            .collect(Collectors.toList());
    RetraceCommand.Builder builder =
        RetraceCommand.builder()
            .setMappingSupplier(
                ProguardMappingSupplier.builder()
                    .setProguardMapProducer(ProguardMapProducer.fromString(run.proguardMap()))
                    .build())
            .setStackTrace(originalStackTrace)
            .setRetracedStackTraceConsumer(box::set)
            .setVerbose(true);
    Retrace.run(builder.build());
    assertEquals(
        "\tat "
            + typeName(ClassWithOverload.class)
            + ".void test(int)(OverloadsWithoutLineNumberTest.java:0)",
        box.get().get(1));
  }

  public static class ClassWithOverload {

    public static void test() {
      Main.throwError(1);
    }

    public static void test(int i) {
      Main.throwError(i);
    }
  }

  public static class Main {

    public static void throwError(int i) {
      if (i == 0) {
        throw new RuntimeException("FOO");
      }
    }

    public static void main(String[] args) {
      ClassWithOverload.test(args.length);
    }
  }
}
