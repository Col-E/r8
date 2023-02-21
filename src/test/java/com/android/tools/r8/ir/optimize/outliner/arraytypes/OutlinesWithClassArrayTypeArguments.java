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

@RunWith(Parameterized.class)
public class OutlinesWithClassArrayTypeArguments extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public OutlinesWithClassArrayTypeArguments(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void validateOutlining(CodeInspector inspector) {
    ClassSubject outlineClass =
        inspector.clazz(SyntheticItemsTestUtils.syntheticOutlineClass(TestClass.class, 0));
    MethodSubject outline0Method =
        outlineClass.method(
            "void",
            SyntheticItemsTestUtils.syntheticMethodName(),
            ImmutableList.of(
                TestClass.class.getTypeName() + "[]", TestClass.class.getTypeName() + "[]"));
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
    String expectedOutput = StringUtils.lines("1", "1", "1", "1");
    testForR8(parameters.getBackend())
        .enableInliningAnnotations()
        .addInnerClasses(OutlinesWithClassArrayTypeArguments.class)
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
    public static void useArray(TestClass[] array) {
      System.out.println(array[0].method());
    }

    @NeverInline
    public int method() {
      return 1;
    }

    @NeverInline
    public static void method1(TestClass[] array) {
      // These two invokes are expected to be outlined.
      useArray(array);
      useArray(array);
    }

    @NeverInline
    public static void method2(TestClass[] array) {
      // These two invokes are expected to be outlined.
      useArray(array);
      useArray(array);
    }

    public static void main(String[] args) {
      TestClass[] array1 = new TestClass[1];
      array1[0] = new TestClass();
      method1(array1);
      method2(array1);
    }
  }
}
