// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.desugar.backports.BooleanBackportTest;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import org.junit.Before;
import org.junit.Test;

/**
 * @deprecated New tests should follow the pattern of {@link BooleanBackportTest}.
 */
@Deprecated
public class BackportedMethodRewriterTest extends TestBase {
  static String expectedOutput = "";

  @Before
  public void testJvm() throws Exception {
    expectedOutput = testForJvm()
        .addTestClasspath()
        .run(TestMethods.class).getStdOut();
  }

  @Test
  public void testD8() throws Exception {
    testForD8()
        .addProgramClasses(TestMethods.class)
        .run(TestMethods.class)
        .assertSuccessWithOutput(expectedOutput);

    assertDesugaring(AndroidApiLevel.O, 17);
    assertDesugaring(AndroidApiLevel.N, 10);
    assertDesugaring(AndroidApiLevel.K, 2);
    assertDesugaring(AndroidApiLevel.J_MR2, 0);
  }

  private void assertDesugaring(AndroidApiLevel apiLevel, int expectedJavaInvokeStatics)
      throws Exception {
    D8TestCompileResult runResult = testForD8()
        .addProgramClasses(TestMethods.class)
        .setMinApi(apiLevel)
        .compile();

    MethodSubject mainMethod = runResult.inspector()
        .clazz(TestMethods.class)
        .mainMethod();
    assertThat(mainMethod, isPresent());

    List<InstructionSubject> javaInvokeStatics = mainMethod
        .streamInstructions()
        .filter(InstructionSubject::isInvokeStatic)
        .filter(is -> is.getMethod().holder.toDescriptorString().startsWith("Ljava/"))
        .collect(toList());

    int actualJavaInvokeStatics = javaInvokeStatics.size();
    assertEquals("Expected "
        + expectedJavaInvokeStatics
        + " invoke-static on java/*/<Type> but found "
        + actualJavaInvokeStatics
        + ": "
        + javaInvokeStatics, expectedJavaInvokeStatics, actualJavaInvokeStatics);
  }

  @Test
  public void testD8Merge() throws Exception {
    String jvmOutput = testForJvm()
        .addTestClasspath()
        .run(MergeRun.class).getStdOut();
    // See b/123242448
    Path zip1 = temp.newFile("first.zip").toPath();
    Path zip2 = temp.newFile("second.zip").toPath();

    testForD8()
        .setMinApi(AndroidApiLevel.L)
        .addProgramClasses(MergeRun.class, MergeInputB.class)
        .compile()
        .assertNoMessages()
        .writeToZip(zip1);
    testForD8()
        .setMinApi(AndroidApiLevel.L)
        .addProgramClasses(MergeInputA.class)
        .compile()
        .assertNoMessages()
        .writeToZip(zip2);
    testForD8()
        .addProgramFiles(zip1, zip2)
        .setMinApi(AndroidApiLevel.L)
        .compile()
        .assertNoMessages()
        .run(MergeRun.class)
        .assertSuccessWithOutput(jvmOutput);
  }


  static class MergeInputA {
    public void foo() {
      System.out.println(Integer.hashCode(42));
      System.out.println(Double.hashCode(42.0));
    }
  }

  static class MergeInputB {
    public void foo() {
      System.out.println(Integer.hashCode(43));
      System.out.println(Double.hashCode(43.0));
    }
  }

  static class MergeRun {
    public static void main(String[] args) {
      MergeInputA a = new MergeInputA();
      MergeInputB b = new MergeInputB();
      a.foo();
      b.foo();
    }
  }

  static class TestMethods {
    public static void main(String[] args) {
      int[] aInts = new int[]{42, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE};
      int[] bInts = new int[]{43, 1, -1, Integer.MAX_VALUE, Integer.MIN_VALUE};
      for (int aInt : aInts) {
        System.out.println(Integer.hashCode(aInt));
        System.out.println(Integer.toUnsignedLong(aInt));
        for (int bInt : bInts) {
          System.out.println(Integer.compare(aInt, bInt));
          System.out.println(Integer.max(aInt, bInt));
          System.out.println(Integer.min(aInt, bInt));
          System.out.println(Integer.sum(aInt, bInt));
          System.out.println(Integer.divideUnsigned(aInt, bInt));
          System.out.println(Integer.remainderUnsigned(aInt, bInt));
          System.out.println(Integer.compareUnsigned(aInt, bInt));
        }
      }

      long[] aLongs = new long[]{42L, 1L, -1L, Integer.MIN_VALUE, Integer.MAX_VALUE,
          Long.MAX_VALUE, Long.MIN_VALUE};
      long[] bLongs = new long[]{43L, 2L, -2L, Integer.MIN_VALUE, Integer.MAX_VALUE,
          Long.MAX_VALUE, Long.MIN_VALUE};
      for (long aLong : aLongs) {
        System.out.println(Long.hashCode(aLong));
        for (long bLong : bLongs) {
          System.out.println(Long.compare(aLong, bLong));
          System.out.println(Long.max(aLong, bLong));
          System.out.println(Long.min(aLong, bLong));
          System.out.println(Long.sum(aLong, bLong));
          System.out.println(Long.divideUnsigned(aLong, bLong));
          System.out.println(Long.remainderUnsigned(aLong, bLong));
          System.out.println(Long.compareUnsigned(aLong, bLong));
        }
      }
    }
  }
}
