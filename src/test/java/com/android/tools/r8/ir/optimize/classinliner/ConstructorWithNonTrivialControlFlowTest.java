// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.apimodel.ApiModelingTestHelper;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ConstructorWithNonTrivialControlFlowTest extends TestBase {

  private final boolean enableClassInlining;
  private final TestParameters parameters;

  @Parameters(name = "{1}, enable class inlining: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(), getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public ConstructorWithNonTrivialControlFlowTest(
      boolean enableClassInlining, TestParameters parameters) {
    this.enableClassInlining = enableClassInlining;
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(ConstructorWithNonTrivialControlFlowTest.class)
        .addKeepMainRule(TestClass.class)
        .apply(ApiModelingTestHelper::enableApiCallerIdentification)
        .addOptionsModification(options -> options.enableClassInlining = enableClassInlining)
        .enableInliningAnnotations()
        .enableMemberValuePropagationAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::verifyClassInliningRemovesCandidate)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccess();
  }

  private void verifyClassInliningRemovesCandidate(CodeInspector inspector) {
    ClassSubject candidateClassSubject = inspector.clazz(Candidate.class);
    if (enableClassInlining
        && (parameters.isDexRuntime()
            && parameters.getApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.G))) {
      assertThat(candidateClassSubject, not(isPresent()));
    } else {
      assertThat(candidateClassSubject, isPresent());
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new Candidate(args, fourtyTwo());
    }

    @NeverInline
    @NeverPropagateValue
    static int fourtyTwo() {
      return 42;
    }
  }

  static class Candidate {

    Object x;
    int y;

    @NeverInline
    Candidate(Object x, int y) {
      if (x == null || x.toString().isEmpty()) {
        throw new RuntimeException(
            "Argument `x` must be non-null and `x.toString()` must be non-empty");
      }
      if (y != 42) {
        throw new RuntimeException("Argument `y` must be 42");
      }
      this.x = x;
      this.y = y;
    }
  }
}
