// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugar.twr;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class CheckTwrOutputForJavaCompilersTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("no file", "no file");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withCfRuntimes().build();
  }

  public CheckTwrOutputForJavaCompilersTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  /**
   * Test to document that the special $closeResource synthesis is only being done in JDK9.
   *
   * <p>If this ever fails we will need to verify if the same AutoClosable issues might occur and
   * amend the tests in the source-sets specific to that JDK version.
   *
   * <p>This test is not parameterized over DEX VMs as it primarily to check the javac behavior. The
   * testing of the JDK9 code and its desugaring on the DEX VM and API level matrix is thus confined
   * to just the JDK9 input defined in the source-set tests for JDK9.
   */
  @Test
  public void test() throws Exception {
    Path javacOut =
        javac(parameters.getRuntime().asCf())
            .addSourceFiles(ToolHelper.getSourceFileForTestClass(TwrTestSource.class))
            .compile();
    testForJvm(parameters)
        .addProgramFiles(javacOut)
        .run(parameters.getRuntime(), TwrTestSource.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkUseOfCloseResource);
    testForD8(parameters.getBackend())
        .setMinApi(AndroidApiLevel.B)
        .addProgramFiles(javacOut)
        .run(parameters.getRuntime(), TwrTestSource.class)
        .assertSuccessWithOutput(EXPECTED)
        .inspect(this::checkNoUseOfCloseResource);
  }

  private void checkUseOfCloseResource(CodeInspector inspector) {
    Set<String> methods =
        inspector.clazz(TwrTestSource.class).allMethods().stream()
            .map(m -> m.getMethod().getName().toString())
            .collect(Collectors.toSet());
    Set<InstructionSubject> callsToCloseResource = getCallsToCloseResource(inspector);
    if (parameters.isCfRuntime(CfVm.JDK9)) {
      assertEquals(ImmutableSet.of("<init>", "main", "$closeResource"), methods);
      assertEquals(4, callsToCloseResource.size());
    } else {
      assertEquals(ImmutableSet.of("<init>", "main"), methods);
      assertEquals(Collections.emptySet(), callsToCloseResource);
    }
  }

  private void checkNoUseOfCloseResource(CodeInspector inspector) {
    Set<InstructionSubject> callsToCloseResource = getCallsToCloseResource(inspector);
    assertEquals(Collections.emptySet(), callsToCloseResource);
  }

  private Set<InstructionSubject> getCallsToCloseResource(CodeInspector inspector) {
    Set<InstructionSubject> callsToCloseResource = new HashSet<>();
    inspector.forAllClasses(
        c ->
            c.forAllMethods(
                m ->
                    callsToCloseResource.addAll(
                        m.streamInstructions()
                            .filter(InstructionSubject::isInvoke)
                            .filter(i -> i.getMethod().qualifiedName().contains("$closeResource"))
                            .collect(Collectors.toSet()))));
    return callsToCloseResource;
  }
}
