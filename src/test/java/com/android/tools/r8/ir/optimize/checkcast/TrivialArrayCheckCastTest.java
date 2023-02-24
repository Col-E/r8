// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.checkcast;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

/** Regression test for b/123269162. */
@RunWith(Parameterized.class)
public class TrivialArrayCheckCastTest extends TestBase {

  private static final String EXPECTED_OUTPUT =
      StringUtils.lines(
          "Caught NullPointerException", "Caught NullPointerException",
          "Caught NullPointerException", "Caught NullPointerException",
          "Caught NullPointerException", "Caught NullPointerException",
          "Caught NullPointerException", "Caught NullPointerException",
          "Caught NullPointerException", "Caught NullPointerException");

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  @Test
  public void testR8() throws Exception {
    InternalOptions options = new InternalOptions();
    options.setMinApiLevel(AndroidApiLevel.I_MR1);
    assert options.canHaveArtCheckCastVerifierBug();

    testForR8(parameters.getBackend())
        .addInnerClasses(TrivialArrayCheckCastTest.class)
        .addKeepMainRule(TestClass.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED_OUTPUT);
  }

  static class TestClass {

    public static void main(String[] args) {
      testBooleanArray();
      testByteArray();
      testCharArray();
      testDoubleArray();
      testFloatArray();
      testFloatArrayNested();
      testIntArray();
      testLongArray();
      testObjectArray();
      testShortArray();
    }

    @NeverInline
    private static void testBooleanArray() {
      boolean[] array = (boolean[]) null;
      try {
        boolean value = array[42];
        System.out.println("Read value: " + value);
      } catch (ArrayIndexOutOfBoundsException e) {
        System.out.println("Caught ArrayIndexOutOfBoundsException");
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException");
      }
    }

    @NeverInline
    private static void testByteArray() {
      byte[] array = (byte[]) null;
      try {
        byte value = array[42];
        System.out.println("Read value: " + value);
      } catch (ArrayIndexOutOfBoundsException e) {
        System.out.println("Caught ArrayIndexOutOfBoundsException");
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException");
      }
    }

    @NeverInline
    private static void testCharArray() {
      char[] array = (char[]) null;
      try {
        char value = array[42];
        System.out.println("Read value: " + value);
      } catch (ArrayIndexOutOfBoundsException e) {
        System.out.println("Caught ArrayIndexOutOfBoundsException");
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException");
      }
    }

    @NeverInline
    private static void testDoubleArray() {
      double[] array = (double[]) null;
      try {
        double value = array[42];
        System.out.println("Read value: " + value);
      } catch (ArrayIndexOutOfBoundsException e) {
        System.out.println("Caught ArrayIndexOutOfBoundsException");
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException");
      }
    }

    @NeverInline
    private static void testFloatArray() {
      float[] array = (float[]) null;
      try {
        float value = array[42];
        System.out.println("Read value: " + value);
      } catch (ArrayIndexOutOfBoundsException e) {
        System.out.println("Caught ArrayIndexOutOfBoundsException");
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException");
      }
    }

    @NeverInline
    private static void testFloatArrayNested() {
      float[][] nestedArray = (float[][]) null;
      try {
        float[] array = nestedArray[42];
        float value = array[42];
        System.out.println("Read value: " + value);
      } catch (ArrayIndexOutOfBoundsException e) {
        System.out.println("Caught ArrayIndexOutOfBoundsException");
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException");
      }
    }

    @NeverInline
    private static void testIntArray() {
      int[] array = (int[]) null;
      try {
        int value = array[42];
        System.out.println("Read value: " + value);
      } catch (ArrayIndexOutOfBoundsException e) {
        System.out.println("Caught ArrayIndexOutOfBoundsException");
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException");
      }
    }

    @NeverInline
    private static void testLongArray() {
      long[] array = (long[]) null;
      try {
        long value = array[42];
        System.out.println("Read value: " + value);
      } catch (ArrayIndexOutOfBoundsException e) {
        System.out.println("Caught ArrayIndexOutOfBoundsException");
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException");
      }
    }

    @NeverInline
    private static void testObjectArray() {
      Object[] array = (Object[]) null;
      try {
        Object value = array[42];
        System.out.println("Read value: " + value);
      } catch (ArrayIndexOutOfBoundsException e) {
        System.out.println("Caught ArrayIndexOutOfBoundsException");
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException");
      }
    }

    @NeverInline
    private static void testShortArray() {
      short[] array = (short[]) null;
      try {
        short value = array[42];
        System.out.println("Read value: " + value);
      } catch (ArrayIndexOutOfBoundsException e) {
        System.out.println("Caught ArrayIndexOutOfBoundsException");
      } catch (NullPointerException e) {
        System.out.println("Caught NullPointerException");
      }
    }
  }
}
