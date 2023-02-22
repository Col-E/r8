// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner.arraytypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class OutlinesWithPrimitiveArrayTypeArguments extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  private void validateOutlining(CodeInspector inspector) {
    ClassSubject outlineClass =
        inspector.clazz(SyntheticItemsTestUtils.syntheticOutlineClass(TestClass.class, 0));
    MethodSubject outline0Method =
        outlineClass.method(
            "void",
            SyntheticItemsTestUtils.syntheticMethodName(),
            ImmutableList.of("int[]", "int[]"));
    assertThat(outline0Method, isPresent());
    ClassSubject classSubject = inspector.clazz(TestClass.class);
    assertThat(
        classSubject.uniqueMethodWithOriginalName("method1"),
        CodeMatchers.invokesMethod(outline0Method));
    assertThat(
        classSubject.uniqueMethodWithOriginalName("method2"),
        CodeMatchers.invokesMethod(outline0Method));
  }

  @Test
  public void test() throws Exception {
    String expectedOutput = StringUtils.lines("1", "1", "2", "2");
    testForR8(parameters.getBackend())
        .enableInliningAnnotations()
        .addInnerClasses(OutlinesWithPrimitiveArrayTypeArguments.class)
        .addKeepMainRule(TestClass.class)
        .setMinApi(parameters)
        .addDontObfuscate()
        .addOptionsModification(
            options -> {
              options.outline.threshold = 2;
              options.outline.minSize = 2;
            })
        .compile()
        .inspect(this::validateOutlining)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(expectedOutput);
  }

  public static class TestClass {
    @NeverInline
    public static void printArrayLength(int[] intArray) {
      System.out.println(intArray.length);
    }

    @NeverInline
    public static void method1(int[] intArray) {
      // These two invokes are expected to be outlined.
      printArrayLength(intArray);
      printArrayLength(intArray);
    }

    @NeverInline
    public static void method2(int[] intArray) {
      // These two invokes are expected to be outlined.
      printArrayLength(intArray);
      printArrayLength(intArray);
    }

    public static void main(String[] args) {
      method1(new int[1]);
      method2(new int[2]);
    }
  }
}
