// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.outliner.classtypes;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoMethodStaticizing;
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
public class B134462736 extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public B134462736(TestParameters parameters) {
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
                StringBuilder.class.getTypeName(),
                String.class.getTypeName(),
                String.class.getTypeName(),
                TestClass.class.getTypeName(),
                String.class.getTypeName()));
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
    String expectedOutput = StringUtils.lines("12 null", "12 null");
    testForR8(parameters.getBackend())
        .enableInliningAnnotations()
        .addInnerClasses(B134462736.class)
        .addKeepMainRule(TestClass.class)
        .enableConstantArgumentAnnotations()
        .enableNoMethodStaticizingAnnotations()
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

    @KeepConstantArguments
    @NeverInline
    @NoMethodStaticizing
    public void consumer(String arg1, String arg2) {
      System.out.println(arg1 + " " + arg2);
    }

    @KeepConstantArguments
    @NeverInline
    public void method1(StringBuilder builder, String arg1, String arg2) {
      builder.append(arg1);
      builder.append(arg2);
      consumer(builder.toString(), null);
    }

    @KeepConstantArguments
    @NeverInline
    public void method2(StringBuilder builder, String arg1, String arg2) {
      builder.append(arg1);
      builder.append(arg2);
      consumer(builder.toString(), null);
    }

    public static void main(String[] args) {
      TestClass instance = new TestClass();
      instance.method1(new StringBuilder(), "1", "2");
      instance.method2(new StringBuilder(), "1", "2");
    }
  }
}