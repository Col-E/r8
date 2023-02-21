// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.rewrite.arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationMode;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ArrayLengthRewriteTest extends TestBase {
  @Parameters(name = "{0}, debug = {1}")
  public static Iterable<?> data() {
    return buildParameters(
        getTestParameters().withAllRuntimesAndApiLevels().build(), BooleanUtils.values());
  }

  private final TestParameters parameters;
  private final boolean debugMode;

  public ArrayLengthRewriteTest(TestParameters parameters, boolean debugMode) {
    this.parameters = parameters;
    this.debugMode = debugMode;
  }

  private static final String[] expectedOutput = {
      "boolean 1",
      "byte 2",
      "char 3",
      "double 4",
      "float 5",
      "int 6",
      "long 7",
      "short 8",
      "class java.lang.String 1",
      "class java.lang.Object 2",
      "class java.lang.String 3",
      "NPE",
      "class java.lang.Object 2",
      "boolean 1",
      "class java.lang.String 0",
      "class java.lang.String 1",
      "class java.lang.String 2",
      "class java.lang.String 1",
      "class java.lang.String 1",
  };

  @Test public void d8() throws Exception {
    assumeTrue(parameters.isDexRuntime());

    testForD8()
        .setMinApi(parameters)
        .setMode(debugMode ? CompilationMode.DEBUG : CompilationMode.RELEASE)
        .addProgramClasses(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(expectedOutput)
        .inspect(i -> inspect(i, true));
  }

  @Test public void r8() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .setMode(debugMode ? CompilationMode.DEBUG : CompilationMode.RELEASE)
        .addProgramClasses(Main.class)
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(expectedOutput)
        .inspect(i -> inspect(i, false));
  }

  private void inspect(CodeInspector inspector, boolean d8) {
    ClassSubject mainClass = inspector.clazz(Main.class);
    assertTrue(mainClass.isPresent());

    MethodSubject primitives = mainClass.uniqueMethodWithOriginalName("primitives");
    assertArrayLengthCallCount(primitives, debugMode ? 8 : 0);

    MethodSubject nonNullReferences = mainClass.uniqueMethodWithOriginalName("nonNullReferences");
    assertArrayLengthCallCount(nonNullReferences, debugMode ? 3 : 0);

    // No assertion on nullReference() because it's seen as always throwing an NPE and
    // the array-length instruction is removed. The output check validates behavior.

    MethodSubject argument = mainClass.uniqueMethodWithOriginalName("argument");
    assertArrayLengthCallCount(argument, 1);

    MethodSubject phi = mainClass.uniqueMethodWithOriginalName("phi");
    assertArrayLengthCallCount(phi, 1);

    // TODO(139489070): these should be rewritten and result in 0 array-length bytecodes
    MethodSubject staticConstants = mainClass.uniqueMethodWithOriginalName("staticConstants");
    assertArrayLengthCallCount(staticConstants, (d8 || debugMode) ? 3 : 0);

    MethodSubject staticNonConstants = mainClass.uniqueMethodWithOriginalName("staticNonConstants");
    assertArrayLengthCallCount(staticNonConstants, (d8 || debugMode) ? 2 : 0);
  }

  private static void assertArrayLengthCallCount(MethodSubject subject, int expected) {
    assertTrue(subject.isPresent());
    long actual = subject.streamInstructions()
        .filter(InstructionSubject::isArrayLength)
        .count();
    assertEquals(expected, actual);
  }

  public static final class Main {
    public static void main(String[] args) {
      primitives();
      nonNullReferences();
      nullReferences();
      argument("one", "two");
      phi();
      staticConstants();
      staticNonConstants();
    }

    @NeverInline
    private static void primitives() {
      boolean[] booleanArray = new boolean[1];
      byte[] byteArray = new byte[2];
      char[] charArray = new char[3];
      double[] doubleArray = new double[4];
      float[] floatArray = new float[5];
      int[] intArray = new int[6];
      long[] longArray = new long[7];
      short[] shortArray = new short[8];

      System.out.println(booleanArray.getClass().getComponentType() + " " + booleanArray.length);
      System.out.println(byteArray.getClass().getComponentType() + " " + byteArray.length);
      System.out.println(charArray.getClass().getComponentType() + " " + charArray.length);
      System.out.println(doubleArray.getClass().getComponentType() + " " + doubleArray.length);
      System.out.println(floatArray.getClass().getComponentType() + " " + floatArray.length);
      System.out.println(intArray.getClass().getComponentType() + " " + intArray.length);
      System.out.println(longArray.getClass().getComponentType() + " " + longArray.length);
      System.out.println(shortArray.getClass().getComponentType() + " " + shortArray.length);
    }

    @NeverInline
    private static void nonNullReferences() {
      String[] stringArray = new String[1];
      Object[] objectArray = new Object[2];
      Object[] stringAsObjectArray = new String[3];

      System.out.println(stringArray.getClass().getComponentType() + " " + stringArray.length);
      System.out.println(objectArray.getClass().getComponentType() + " " + objectArray.length);
      System.out.println(
          stringAsObjectArray.getClass().getComponentType() + " " + stringAsObjectArray.length);
    }

    @NeverInline
    private static void nullReferences() {
      String[] nullArray = null;
      try {
        System.out.println(nullArray.length);
      } catch (NullPointerException expected) {
        System.out.println("NPE");
      }
    }

    @NeverInline
    private static void argument(Object... input) {
      System.out.println(input.getClass().getComponentType() + " " + input.length);
    }

    @NeverInline
    private static void phi() {
      boolean[] booleanArray;
      if (System.nanoTime() > 0) {
        booleanArray = new boolean[1];
      } else {
        booleanArray = new boolean[2];
      }
      System.out.println(booleanArray.getClass().getComponentType() + " " + booleanArray.length);
    }

    private static final String[] zero = {};
    private static final String[] one = { "one" };
    private static final String[] two = { "one", "two" };

    @NeverInline
    private static void staticConstants() {
      System.out.println(zero.getClass().getComponentType() + " " + zero.length);
      System.out.println(one.getClass().getComponentType() + " " + one.length);
      System.out.println(two.getClass().getComponentType() + " " + two.length);
    }

    private static String[] mutable = { "one" };
    private static final String[] runtimeInit = {"two"};

    @NeverInline
    private static void staticNonConstants() {
      System.out.println(mutable.getClass().getComponentType() + " " + mutable.length);
      System.out.println(runtimeInit.getClass().getComponentType() + " " + runtimeInit.length);
    }
  }
}
