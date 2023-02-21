// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverReprocessMethod;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class FieldWriteBeforeFieldReadTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public FieldWriteBeforeFieldReadTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(FieldWriteBeforeFieldReadTest.class)
        .addKeepMainRule(TestClass.class)
        .addOptionsModification(
            options -> {
              options.testing.waveModifier =
                  (waves) -> {
                    Function<String, Predicate<ProgramMethodSet>> wavePredicate =
                        methodName ->
                            wave ->
                                wave.stream()
                                    .anyMatch(
                                        method -> method.toSourceString().contains(methodName));
                    int readFieldsWaveIndex =
                        IterableUtils.firstIndexMatching(waves, wavePredicate.apply("readFields"));
                    assertTrue(readFieldsWaveIndex >= 0);
                    int writeFieldsWaveIndex =
                        IterableUtils.firstIndexMatching(waves, wavePredicate.apply("writeFields"));
                    assertTrue(writeFieldsWaveIndex >= 0);
                    assertTrue(writeFieldsWaveIndex < readFieldsWaveIndex);
                  };
            })
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNeverReprocessMethodAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::inspect)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutputLines("Live!");
  }

  private void inspect(CodeInspector inspector) {
    ClassSubject testClassSubject = inspector.clazz(TestClass.class);
    assertThat(testClassSubject, isPresent());
    assertThat(testClassSubject.uniqueMethodWithOriginalName("live"), isPresent());
    assertThat(testClassSubject.uniqueMethodWithOriginalName("dead"), not(isPresent()));
  }

  static class TestClass {

    static boolean alwaysFalse;

    public static void main(String[] args) {
      A obj = new A();
      writeFields(obj);
      readFields(obj);
    }

    @NeverInline
    static void writeFields(A obj) {
      alwaysFalse = false;
      obj.alwaysFalse = false;
      increaseDistanceToNearestLeaf();
    }

    static void increaseDistanceToNearestLeaf() {}

    @NeverInline
    @NeverReprocessMethod
    static void readFields(A obj) {
      if (alwaysFalse || obj.alwaysFalse) {
        dead();
      } else {
        live();
      }
    }

    @NeverInline
    static void live() {
      System.out.println("Live!");
    }

    @NeverInline
    static void dead() {
      System.out.println("Dead!");
    }
  }

  @NeverClassInline
  static class A {

    boolean alwaysFalse;
  }
}
