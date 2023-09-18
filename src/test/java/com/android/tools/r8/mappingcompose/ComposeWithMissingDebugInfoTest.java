// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.mappingcompose;

import static junit.framework.TestCase.assertEquals;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.FileUtils;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** This is a regression test for b/297970886 */
@RunWith(Parameterized.class)
public class ComposeWithMissingDebugInfoTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDexRuntimesAndAllApiLevels().build();
  }

  @Test
  public void testD8WithComposition() throws Exception {
    Path inputMap = temp.newFolder().toPath().resolve("input.map");
    FileUtils.writeTextFile(
        inputMap,
        "# {'id':'com.android.tools.r8.mapping','version':'2.2'}",
        typeName(Main.class) + " -> " + typeName(Main.class) + ":",
        "  12:12:int foo():12:12 -> a",
        "  112:112:int foo():112:112 -> a");
    StringBuilder mappingComposed = new StringBuilder();
    testForD8(parameters.getBackend())
        .addProgramClassFileData(
            transformer(Main.class)
                .setPredictiveLineNumbering(MethodPredicate.onName("a"), 12)
                .transform())
        .release()
        .setMinApi(parameters)
        .apply(
            b ->
                b.getBuilder()
                    .setProguardMapInputFile(inputMap)
                    .setProguardMapConsumer((string, handler) -> mappingComposed.append(string)))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("42");
    Set<String> foundMethods =
        ProguardMappingSupplier.builder()
            .setProguardMapProducer(ProguardMapProducer.fromString(mappingComposed.toString()))
            .setLoadAllDefinitions(true)
            .build()
            .createRetracer(new DiagnosticsHandler() {})
            .retraceMethod(
                Reference.method(
                    Reference.classFromTypeName(typeName(Main.class)),
                    "a",
                    new ArrayList<>(),
                    Reference.typeFromTypeName("int")))
            .stream()
            .map(method -> method.getRetracedMethod().getMethodName())
            .collect(Collectors.toSet());
    Set<String> expectedMethods = new HashSet<>();
    expectedMethods.add("foo");
    assertEquals(expectedMethods, foundMethods);
  }

  public static class Main {

    private int val;

    public static void main(String[] args) {
      System.out.println(new Main().a());
    }

    private int a() {
      val = 42;
      return val;
    }
  }
}
