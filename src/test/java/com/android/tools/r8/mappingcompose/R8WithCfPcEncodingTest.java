// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.mappingcompose;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.mappingcompose.testclasses.MainWithHelloWorld;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.utils.codeinspector.LineNumberTable;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.OptionalInt;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class R8WithCfPcEncodingTest extends TestBase {

  @Parameter() public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(MainWithHelloWorld.class)
        .setMinApi(parameters)
        .addKeepMainRule(MainWithHelloWorld.class)
        .addKeepAttributeLineNumberTable()
        .addOptionsModification(
            options -> options.getTestingOptions().usePcEncodingInCfForTesting = true)
        .run(parameters.getRuntime(), MainWithHelloWorld.class)
        .assertSuccessWithOutputLines("Hello World!")
        .inspect(
            inspector -> {
              MethodSubject subject = inspector.clazz(MainWithHelloWorld.class).mainMethod();
              assertThat(subject, isPresent());
              LineNumberTable lineNumberTable = subject.getLineNumberTable();
              Set<Integer> lines = new HashSet<>(lineNumberTable.getLines());
              assertEquals(ImmutableSet.of(1, 2, 3, 4), lines);
              Retracer retracer = inspector.getRetracer();
              MethodReference methodReference = subject.getFinalReference();
              Set<Integer> retracedLines = new HashSet<>();
              lines.forEach(
                  line -> {
                    retracer
                        .retraceFrame(
                            RetraceStackTraceContext.empty(), OptionalInt.of(line), methodReference)
                        .forEach(
                            element -> {
                              element.forEachRewritten(
                                  singleFrame -> {
                                    RetracedMethodReference retracedReference =
                                        singleFrame.getMethodReference();
                                    assertTrue(retracedReference.isKnown());
                                    assertEquals(
                                        methodReference,
                                        retracedReference.asKnown().getMethodReference());
                                    retracedLines.add(
                                        retracedReference.getOriginalPositionOrDefault(-1));
                                  });
                            });
                  });
              assertEquals(ImmutableSet.of(10), retracedLines);
            });
  }
}
