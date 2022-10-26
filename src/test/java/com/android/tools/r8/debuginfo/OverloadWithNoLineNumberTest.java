// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.debuginfo;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.debuginfo.testclasses.SimpleCallChainClassWithOverloads;
import com.android.tools.r8.retrace.ProguardMapProducer;
import com.android.tools.r8.retrace.ProguardMappingSupplier;
import com.android.tools.r8.retrace.Retrace;
import com.android.tools.r8.retrace.RetraceCommand;
import com.android.tools.r8.transformers.ClassFileTransformer.MethodPredicate;
import com.android.tools.r8.utils.StringUtils;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class OverloadWithNoLineNumberTest extends TestBase {

  private final String SOURCE_FILE_NAME = "SimpleCallChainClassWithOverloads.java";

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClassFileData(
                transformer(SimpleCallChainClassWithOverloads.class)
                    .removeLineNumberTable(MethodPredicate.onName("test"))
                    .transform())
            .setMinApi(parameters.getApiLevel())
            .addKeepMainRule(SimpleCallChainClassWithOverloads.class)
            .addKeepClassAndMembersRules(SimpleCallChainClassWithOverloads.class)
            .addKeepAttributeLineNumberTable()
            .run(parameters.getRuntime(), SimpleCallChainClassWithOverloads.class)
            .assertFailureWithErrorThatThrows(RuntimeException.class);
    Retrace.run(
        RetraceCommand.builder()
            .setMappingSupplier(
                ProguardMappingSupplier.builder()
                    .setProguardMapProducer(ProguardMapProducer.fromString(result.proguardMap()))
                    .build())
            .setStackTrace(
                result.getOriginalStackTrace().getStackTraceLines().stream()
                    .map(line -> line.originalLine)
                    .collect(Collectors.toList()))
            .setRetracedStackTraceConsumer(
                retraced -> {
                  String className = typeName(SimpleCallChainClassWithOverloads.class);
                  assertEquals(
                      StringUtils.joinLines(
                          "\tat " + className + ".void test(long)(" + SOURCE_FILE_NAME + ":0)",
                          "\tat " + className + ".void test()(" + SOURCE_FILE_NAME + ":0)",
                          "\tat "
                              + className
                              + ".void main(java.lang.String[])("
                              + SOURCE_FILE_NAME
                              + ":10)"),
                      StringUtils.joinLines(retraced));
                })
            .setVerbose(true)
            .build());
  }
}
