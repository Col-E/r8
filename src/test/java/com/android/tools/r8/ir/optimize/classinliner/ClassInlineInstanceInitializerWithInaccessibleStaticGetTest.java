// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.classinliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ir.optimize.classinliner.testclasses.ClassInlineInstanceInitializerWithInaccessibleStaticGetTestClasses;
import com.android.tools.r8.ir.optimize.classinliner.testclasses.ClassInlineInstanceInitializerWithInaccessibleStaticGetTestClasses.CandidateBase;
import com.android.tools.r8.ir.optimize.classinliner.testclasses.ClassInlineInstanceInitializerWithInaccessibleStaticGetTestClasses.Environment;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ClassInlineInstanceInitializerWithInaccessibleStaticGetTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public ClassInlineInstanceInitializerWithInaccessibleStaticGetTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(
            ClassInlineInstanceInitializerWithInaccessibleStaticGetTest.class,
            ClassInlineInstanceInitializerWithInaccessibleStaticGetTestClasses.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .enableNoAccessModificationAnnotationsForMembers()
        .enableNoHorizontalClassMergingAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Hello world!");
  }

  private void inspect(CodeInspector inspector) {
    assertThat(inspector.clazz(Candidate.class), isPresent());
    assertThat(inspector.clazz(CandidateBase.class), isPresent());
  }

  static class TestClass {

    public static void main(String[] args) {
      Environment.setValue(true);
      System.out.print(new Candidate().get());
      Environment.setValue(false);
      System.out.println(new Candidate().get());
    }
  }

  static class Candidate extends CandidateBase {

    @NeverInline
    String get() {
      return f;
    }
  }
}
