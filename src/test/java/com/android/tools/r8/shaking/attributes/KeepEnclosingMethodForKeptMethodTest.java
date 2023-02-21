// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking.attributes;

import com.android.tools.r8.R8TestBuilder;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeepEnclosingMethodForKeptMethodTest extends TestBase {

  private final TestParameters parameters;

  private final String[] EXPECTED = {
    "null",
    "class com.android.tools.r8.shaking.attributes.KeepEnclosingMethodForKeptMethodTest"
        + "$KeptClass",
    "public static com.android.tools.r8.shaking.attributes.KeepEnclosingMethodForKeptMethodTest$I "
        + "com.android.tools.r8.shaking.attributes.KeepEnclosingMethodForKeptMethodTest$KeptClass.enclosingFromKeptMethod()",
    "public"
        + " com.android.tools.r8.shaking.attributes.KeepEnclosingMethodForKeptMethodTest$KeptClass()"
  };

  private final String[] EXPECTED_FULL = {"null", "null", "null", "null"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public KeepEnclosingMethodForKeptMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws Exception {
    testForRuntime(parameters)
        .addInnerClasses(KeptClass.class)
        .addProgramClassFileData(
            transformer(I.class).removeInnerClasses().transform(),
            transformer(KeptClass.class).removeInnerClasses().transform())
        .run(parameters.getRuntime(), KeptClass.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8Full() throws Exception {
    runTest(testForR8(parameters.getBackend())).assertSuccessWithOutputLines(EXPECTED_FULL);
  }

  @Test
  public void testR8Compat() throws Exception {
    runTest(testForR8Compat(parameters.getBackend())).assertSuccessWithOutputLines(EXPECTED);
  }

  private R8TestRunResult runTest(R8TestBuilder<?> testBuilder) throws Exception {
    return testBuilder
        .addInnerClasses(KeptClass.class)
        .addProgramClassFileData(
            transformer(I.class).removeInnerClasses().transform(),
            transformer(KeptClass.class).removeInnerClasses().transform())
        .addKeepClassRules(I.class)
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .addKeepMainRule(KeptClass.class)
        .addKeepClassAndMembersRules(KeptClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), KeptClass.class);
  }

  public interface I {

    void foo();
  }

  public static class KeptClass {

    static I staticField =
        new I() {
          @Override
          public void foo() {
            System.out.println(this.getClass().getEnclosingConstructor());
            System.out.println(this.getClass().getEnclosingClass());
          }
        };

    private I instanceField;

    public KeptClass() {
      instanceField =
          new I() {
            @Override
            public void foo() {
              System.out.println(this.getClass().getEnclosingConstructor());
            }
          };
    }

    public static void main(String[] args) {
      staticField.foo();
      enclosingFromKeptMethod().foo();
      new KeptClass().instanceField.foo();
    }

    public static I enclosingFromKeptMethod() {
      return new I() {
        @Override
        public void foo() {
          System.out.println(this.getClass().getEnclosingMethod());
        }
      };
    }
  }
}
