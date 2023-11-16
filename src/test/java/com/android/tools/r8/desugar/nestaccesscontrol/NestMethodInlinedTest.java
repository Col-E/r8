// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.nestaccesscontrol;

import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.classesMatching;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.getExpectedResult;
import static com.android.tools.r8.desugar.nestaccesscontrol.NestAccessControlTestUtils.getMainClass;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class NestMethodInlinedTest extends TestBase {

  public NestMethodInlinedTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimesStartingFromIncluding(CfVm.JDK11)
        .withAllApiLevelsAlsoForCf()
        .build();
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramFiles(classesMatching("NestPvtMethodCallInlined"))
        .run(parameters.getRuntime(), getMainClass("pvtCallInlined"))
        .assertSuccessWithOutput(getExpectedResult("pvtCallInlined"));
  }

  @Test
  public void testPvtMethodCallInlined() throws Exception {
    parameters.assumeR8TestParameters();
    List<Path> toCompile = classesMatching("NestPvtMethodCallInlined");
    testForR8(parameters.getBackend())
        .addKeepMainRule(getMainClass("pvtCallInlined"))
        .addDontObfuscate()
        .addOptionsModification(
            options -> {
              options.enableClassInlining = false;
              options.getVerticalClassMergerOptions().disable();
            })
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .addProgramFiles(toCompile)
        .compile()
        .inspect(this::assertMethodsInlined)
        .inspect(NestAttributesUpdateTest::assertNestAttributesCorrect)
        .run(parameters.getRuntime(), getMainClass("pvtCallInlined"))
        .assertSuccessWithOutput(getExpectedResult("pvtCallInlined"));
  }

  private void assertMethodsInlined(CodeInspector inspector) {
    // Inlining through nest access.
    int nbDispatchInlining = 0;
    int nbNotInlinedPvtCall = 0;
    for (FoundClassSubject subj : inspector.allClasses()) {
      assertTrue(
          "nestPvtCallToInline should be inlined (from " + subj.getOriginalName() + ")",
          subj.allMethods().stream()
              .noneMatch(
                  method ->
                      method.toString().contains("nestPvtCallToInline")
                          || method.toString().contains("methodWithPvtCallToInline")));
      // Inlining nest access should transform virtual/ift invokes -> direct.
      MethodSubject methodSubject = subj.uniqueMethodWithOriginalName("dispatchInlining");
      if (methodSubject.isPresent()) {
        nbDispatchInlining++;
        assertTrue(
            methodSubject.streamInstructions().noneMatch(InstructionSubject::isInvokeVirtual));
      }
      methodSubject = subj.uniqueMethodWithOriginalName("notInlinedPvtCall");
      if (methodSubject.isPresent()) {
        nbNotInlinedPvtCall++;
      }
    }
    assertEquals("dispatchInlining methods should not be inlined", 2, nbDispatchInlining);
    assertEquals("notInlinedPvtCall methods should not be inlined", 3, nbNotInlinedPvtCall);
  }
}
