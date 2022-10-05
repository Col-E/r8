// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.inliner.conditionalsimpleinlining;

import static com.android.tools.r8.utils.codeinspector.CodeMatchers.invokesMethodWithName;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.notIf;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverSingleCallerInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SimpleIfNullOrNotNullInliningTest extends ConditionalSimpleInliningTestBase {

  private final Class<?> mainClass;

  @Parameters(name = "{2}, main: {1}, simple inlining constraints: {0}")
  public static List<Object[]> data() {
    return buildParameters(
        BooleanUtils.values(),
        ImmutableList.of(
            TestClassEligibleForSimpleInlining.class, TestClassIneligibleForSimpleInlining.class),
        TestBase.getTestParameters().withAllRuntimesAndApiLevels().build());
  }

  public SimpleIfNullOrNotNullInliningTest(
      boolean enableSimpleInliningConstraints, Class<?> mainClass, TestParameters parameters) {
    super(enableSimpleInliningConstraints, parameters);
    this.mainClass = mainClass;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(mainClass, TestMethods.class)
        .addKeepMainRule(mainClass)
        .apply(this::configure)
        .enableConstantArgumentAnnotations()
        .enableNeverSingleCallerInlineAnnotations()
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), mainClass)
        .assertSuccessWithOutput(getExpectedOutput());
  }

  private String getExpectedOutput() {
    if (mainClass == TestClassEligibleForSimpleInlining.class) {
      return "";
    }
    return StringUtils.times(StringUtils.lines("Hello world!"), 8);
  }

  private void inspect(CodeInspector inspector) {
    MethodSubject mainMethodSubject = inspector.clazz(mainClass).mainMethod();
    assertThat(mainMethodSubject, isPresent());
    assertThat(mainMethodSubject, not(invokesMethodWithName("print")));

    ClassSubject classSubject = inspector.clazz(TestMethods.class);
    assertThat(classSubject, notIf(isPresent(), shouldBeEligibleForSimpleInlining()));

    if (shouldBeEligibleForSimpleInlining()) {
      return;
    }

    assertThat(classSubject, isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("simpleIfNullTest"), isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("simpleIfBothNullTest"), isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("simpleIfNotNullTest"), isPresent());
    assertThat(classSubject.uniqueMethodWithOriginalName("simpleIfBothNotNullTest"), isPresent());
  }

  private boolean shouldBeEligibleForSimpleInlining() {
    return mainClass == TestClassEligibleForSimpleInlining.class && enableSimpleInliningConstraints;
  }

  static class TestClassEligibleForSimpleInlining {

    public static void main(String[] args) {
      Object notNull = new Object();
      TestMethods.simpleIfNullTest(null);
      TestMethods.simpleIfBothNullTest(null, null);
      TestMethods.simpleIfNotNullTest(notNull);
      TestMethods.simpleIfBothNotNullTest(notNull, notNull);
    }
  }

  static class TestClassIneligibleForSimpleInlining {

    public static void main(String[] args) {
      Object notNull = new Object();
      TestMethods.simpleIfNullTest(notNull);
      TestMethods.simpleIfBothNullTest(null, notNull);
      TestMethods.simpleIfBothNullTest(notNull, null);
      TestMethods.simpleIfBothNullTest(notNull, notNull);
      TestMethods.simpleIfNotNullTest(null);
      TestMethods.simpleIfBothNotNullTest(null, notNull);
      TestMethods.simpleIfBothNotNullTest(notNull, null);
      TestMethods.simpleIfBothNotNullTest(null, null);
    }
  }

  static class TestMethods {

    @KeepConstantArguments
    @NeverSingleCallerInline
    static void simpleIfNullTest(Object o) {
      if (o == null) {
        return;
      }
      System.out.print("H");
      System.out.print("e");
      System.out.print("l");
      System.out.print("l");
      System.out.print("o");
      System.out.print(" ");
      System.out.print("w");
      System.out.print("o");
      System.out.print("r");
      System.out.print("l");
      System.out.print("d");
      System.out.println("!");
    }

    @KeepConstantArguments
    @NeverSingleCallerInline
    static void simpleIfBothNullTest(Object o1, Object o2) {
      if (o1 == null && o2 == null) {
        return;
      }
      System.out.print("H");
      System.out.print("e");
      System.out.print("l");
      System.out.print("l");
      System.out.print("o");
      System.out.print(" ");
      System.out.print("w");
      System.out.print("o");
      System.out.print("r");
      System.out.print("l");
      System.out.print("d");
      System.out.println("!");
    }

    @KeepConstantArguments
    @NeverSingleCallerInline
    static void simpleIfNotNullTest(Object o) {
      if (o != null) {
        return;
      }
      System.out.print("H");
      System.out.print("e");
      System.out.print("l");
      System.out.print("l");
      System.out.print("o");
      System.out.print(" ");
      System.out.print("w");
      System.out.print("o");
      System.out.print("r");
      System.out.print("l");
      System.out.print("d");
      System.out.println("!");
    }

    @KeepConstantArguments
    @NeverSingleCallerInline
    static void simpleIfBothNotNullTest(Object o1, Object o2) {
      if (o1 != null && o2 != null) {
        return;
      }
      System.out.print("H");
      System.out.print("e");
      System.out.print("l");
      System.out.print("l");
      System.out.print("o");
      System.out.print(" ");
      System.out.print("w");
      System.out.print("o");
      System.out.print("r");
      System.out.print("l");
      System.out.print("d");
      System.out.println("!");
    }
  }
}
