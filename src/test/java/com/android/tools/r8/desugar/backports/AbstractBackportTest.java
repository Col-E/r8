// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.backports;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.util.List;
import java.util.NavigableMap;
import java.util.TreeMap;
import org.junit.Assert;
import org.junit.Test;

abstract class AbstractBackportTest extends TestBase {
  private final TestParameters parameters;
  private final Class<?> targetClass;
  private final Class<?> testClass;
  private final NavigableMap<AndroidApiLevel, Integer> invokeStaticCounts = new TreeMap<>();

  AbstractBackportTest(TestParameters parameters, Class<?> targetClass,
      Class<?> testClass) {
    this.parameters = parameters;
    this.targetClass = targetClass;
    this.testClass = testClass;

    // Assume all method calls will be rewritten on the lowest API level.
    invokeStaticCounts.put(AndroidApiLevel.B, 0);
  }

  void registerTarget(AndroidApiLevel apiLevel, int invokeStaticCount) {
    invokeStaticCounts.put(apiLevel, invokeStaticCount);
  }

  @Test
  public void desugaring() throws Exception {
    testForD8()
        .addProgramClasses(testClass, MiniAssert.class)
        .setMinApi(parameters.getRuntime().asDex().getMinApiLevel())
        .compile()
        .run(parameters.getRuntime(), testClass)
        .assertSuccess()
        .inspect(this::assertDesugaring);
  }

  private void assertDesugaring(CodeInspector inspector) {
    ClassSubject testSubject = inspector.clazz(testClass);
    assertThat(testSubject, isPresent());

    MethodSubject mainMethod = testSubject.mainMethod();
    assertThat(mainMethod, isPresent());

    List<InstructionSubject> javaInvokeStatics = mainMethod
        .streamInstructions()
        .filter(InstructionSubject::isInvoke)
        .filter(is -> is.getMethod().holder.toSourceString().equals(targetClass.getName()))
        .collect(toList());

    AndroidApiLevel apiLevel = parameters.getRuntime().asDex().getMinApiLevel();
    long expectedTargetInvokes = invokeStaticCounts.ceilingEntry(apiLevel).getValue();
    long actualTargetInvokes = javaInvokeStatics.size();
    assertEquals("Expected "
        + expectedTargetInvokes
        + " invokes on "
        + targetClass.getName()
        + " but found "
        + actualTargetInvokes
        + ": "
        + javaInvokeStatics, expectedTargetInvokes, actualTargetInvokes);
  }

  /** JUnit {@link Assert} isn't available in the VM runtime. This is a mini mirror of its API. */
  static abstract class MiniAssert {
    static void assertTrue(boolean value) {
      assertEquals(true, value);
    }

    static void assertFalse(boolean value) {
      assertEquals(false, value);
    }

    static void assertEquals(boolean expected, boolean actual) {
      if (expected != actual) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
      }
    }

    static void assertEquals(int expected, int actual) {
      if (expected != actual) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
      }
    }

    static void assertEquals(long expected, long actual) {
      if (expected != actual) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
      }
    }

    static void assertEquals(float expected, float actual) {
      if (Float.compare(expected, actual) != 0) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
      }
    }

    static void assertEquals(double expected, double actual) {
      if (Double.compare(expected, actual) != 0) {
        throw new AssertionError("Expected <" + expected + "> but was <" + actual + '>');
      }
    }
  }
}
