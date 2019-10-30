// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.outliner;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.InternalOptions.OutlineOptions;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class OutlinesWithNonNullTest extends TestBase {
  private static final String JVM_OUTPUT = StringUtils.lines(
      "42",
      "arg",
      "42",
      "arg"
  );

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  public OutlinesWithNonNullTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testNonNullOnOneSide() throws Exception {
    testForR8(parameters.getBackend())
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .addProgramClasses(TestArg.class, TestClassWithNonNullOnOneSide.class)
        .addKeepMainRule(TestClassWithNonNullOnOneSide.class)
        .setMinApi(parameters.getRuntime())
        .allowAccessModification()
        .noMinification()
        .addOptionsModification(
            options -> {
              if (parameters.isCfRuntime()) {
                assert !options.outline.enabled;
                options.outline.enabled = true;
              }
              options.outline.threshold = 2;
              options.outline.minSize = 2;
            })
        .compile()
        .inspect(inspector -> validateOutlining(inspector, TestClassWithNonNullOnOneSide.class))
        .run(parameters.getRuntime(), TestClassWithNonNullOnOneSide.class)
        .assertSuccessWithOutput(JVM_OUTPUT);
  }

  @Test
  public void testNonNullOnBothSides() throws Exception {
    testForR8(parameters.getBackend())
        .enableClassInliningAnnotations()
        .enableInliningAnnotations()
        .addProgramClasses(TestArg.class, TestClassWithNonNullOnBothSides.class)
        .addKeepMainRule(TestClassWithNonNullOnBothSides.class)
        .setMinApi(parameters.getRuntime())
        .allowAccessModification()
        .noMinification()
        .addOptionsModification(
            options -> {
              if (parameters.isCfRuntime()) {
                assert !options.outline.enabled;
                options.outline.enabled = true;
              }
              options.outline.threshold = 2;
              options.outline.minSize = 2;
            })
        .compile()
        .inspect(inspector -> validateOutlining(inspector, TestClassWithNonNullOnBothSides.class))
        .run(parameters.getRuntime(), TestClassWithNonNullOnBothSides.class)
        .assertSuccessWithOutput(JVM_OUTPUT);
  }

  private void validateOutlining(CodeInspector inspector, Class<?> main) {
    ClassSubject outlineClass = inspector.clazz(OutlineOptions.CLASS_NAME);
    assertThat(outlineClass, isPresent());
    MethodSubject outlineMethod = outlineClass.uniqueMethodWithName("outline0");
    assertThat(outlineMethod, isPresent());

    ClassSubject argClass = inspector.clazz(TestArg.class);
    assertThat(argClass, isPresent());
    MethodSubject printHash = argClass.uniqueMethodWithName("printHash");
    assertThat(printHash, isPresent());
    MethodSubject printArg= argClass.uniqueMethodWithName("printArg");
    assertThat(printArg, isPresent());

    ClassSubject classSubject = inspector.clazz(main);
    assertThat(classSubject, isPresent());
    MethodSubject method1 = classSubject.uniqueMethodWithName("method1");
    assertThat(method1, isPresent());
    assertThat(method1, CodeMatchers.invokesMethod(outlineMethod));
    assertThat(method1, not(CodeMatchers.invokesMethod(printHash)));
    assertThat(method1, not(CodeMatchers.invokesMethod(printArg)));
    MethodSubject method2 = classSubject.uniqueMethodWithName("method2");
    assertThat(method2, isPresent());
    assertThat(method2, CodeMatchers.invokesMethod(outlineMethod));
    assertThat(method2, not(CodeMatchers.invokesMethod(printHash)));
    assertThat(method2, not(CodeMatchers.invokesMethod(printArg)));
  }

  @NeverClassInline
  public static class TestArg {
    @Override
    public int hashCode() {
      return 42;
    }

    @Override
    public String toString() {
      return "arg";
    }

    @NeverInline
    static void printHash(Object arg) {
      if (arg == null) {
        throw new NullPointerException();
      }
      System.out.println(arg.hashCode());
      // This method guarantees that, at the normal exit, argument is not null.
    }

    @NeverInline
    static void printArg(Object arg) {
      System.out.println(arg);
    }
  }

  static class TestClassWithNonNullOnOneSide {
    @NeverInline
    static void method1(Object arg) {
      TestArg.printHash(arg);
      // We will have non-null aliasing here.
      TestArg.printArg(arg);
    }

    @NeverInline
    static void method2(Object arg) {
      if (arg != null) {
        // We will have non-null aliasing here.
        TestArg.printHash(arg);
        TestArg.printArg(arg);
      }
    }

    public static void main(String... args) {
      TestArg arg = new TestArg();
      method1(arg);
      method2(arg);
    }
  }

  static class TestClassWithNonNullOnBothSides {
    @NeverInline
    static void method1(Object arg) {
      TestArg.printHash(arg);
      // We will have non-null aliasing here.
      TestArg.printArg(arg);
    }

    @NeverInline
    static void method2(Object arg) {
      TestArg.printHash(arg);
      // We will have non-null aliasing here.
      TestArg.printArg(arg);
    }

    public static void main(String... args) {
      TestArg arg = new TestArg();
      method1(arg);
      method2(arg);
    }
  }
}
