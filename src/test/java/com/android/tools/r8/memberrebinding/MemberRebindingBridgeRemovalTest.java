// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.memberrebinding;

import static com.android.tools.r8.utils.codeinspector.Matchers.isBridge;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isSynthetic;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.memberrebinding.testclasses.MemberRebindingBridgeRemovalTestClasses;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class MemberRebindingBridgeRemovalTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public MemberRebindingBridgeRemovalTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(
            MemberRebindingBridgeRemovalTest.class, MemberRebindingBridgeRemovalTestClasses.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNoAccessModificationAnnotationsForClasses()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(StringUtils.lines("Hello world!"));
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject classSubject = inspector.clazz(MemberRebindingBridgeRemovalTestClasses.B.class);
    assertThat(classSubject, isPresent());

    for (FoundMethodSubject methodSubject : classSubject.allMethods()) {
      assertThat(methodSubject, not(isBridge()));
      assertThat(methodSubject, not(isSynthetic()));
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new MemberRebindingBridgeRemovalTestClasses.B().m();
    }
  }
}
