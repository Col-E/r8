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

    assertDesugaring(AndroidApiLevel.O, 65);
    assertDesugaring(AndroidApiLevel.N, 44);
    assertDesugaring(AndroidApiLevel.K, 19);
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
    // Defined as a static method on this class to avoid affecting invoke-static counts in main().
    private static <T> Comparator<T> reverse() {
      return Collections.reverseOrder();
    }

    // Defined as a static method on this class to avoid affecting invoke-static counts in main().
    private static int signum(int value) {
      return (int) Math.signum(value);
    }

    // Defined as a static method on this class to avoid affecting invoke-static counts in main().
    @SafeVarargs
    private static <T> List<T> listOf(T... values) {
      return Arrays.asList(values);
    }

    public static void main(String[] args) {
      byte[] aBytes = new byte[]{42, 1, -1, Byte.MAX_VALUE, Byte.MIN_VALUE};
      for (byte aByte : aBytes) {
        System.out.println(Byte.hashCode(aByte));
        System.out.println(Byte.toUnsignedInt(aByte));
        System.out.println(Byte.toUnsignedLong(aByte));
        for (byte bByte : aBytes) {
          // Normalize comparison to [-1, 1] since the values differ across versions but signs match
          System.out.println(signum(Byte.compare(aByte, bByte)));
        }
      }

      short[] aShorts = new short[]{42, 1, -1, Short.MAX_VALUE, Short.MIN_VALUE};
      for (short aShort : aShorts) {
        System.out.println(Short.hashCode(aShort));
        System.out.println(Short.toUnsignedInt(aShort));
        System.out.println(Short.toUnsignedLong(aShort));
        for (short bShort : aShorts) {
          // Normalize comparison to [-1, 1] since the values differ across versions but signs match
          System.out.println(signum(Short.compare(aShort, bShort)));
        }
      }

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

      double[] aDoubles = new double[]{42.0, 1.1, -1.1,  Double.MAX_VALUE, Double.MIN_NORMAL,
          Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
      double[] bDoubles = new double[]{43, 1.2, -1.3, Double.MAX_VALUE, Double.MIN_NORMAL,
          Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
      for (double aDouble : aDoubles) {
        System.out.println(Double.hashCode(aDouble));
        System.out.println(Double.isFinite(aDouble));
        for (double bDouble : bDoubles) {
          System.out.println(Double.max(aDouble, bDouble));
          System.out.println(Double.min(aDouble, bDouble));
          System.out.println(Double.sum(aDouble, bDouble));
        }
      }

      // Float.MAX_VALUE/MIN_VALUE printed values differs between jvm and art on some versions,
      // e.g., 1.17549435E-38 on jvm, 1.1754944E-38 on art
      float[] aFloats = new float[]{42.0f, 1.1f, -1.1f,  Float.MAX_VALUE, Float.MIN_NORMAL,
          Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
      float[] bFloats = new float[]{43, 1.2f, -1.3f, Float.MAX_VALUE, Float.MIN_NORMAL,
          Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY};
      for (float aFloat : aFloats) {
        System.out.println(Float.hashCode(aFloat));
        System.out.println(Float.isFinite(aFloat));
        for (float bFloat : bFloats) {
          // Print comparison, since Max/Min printed values differs between jvm and art
          System.out.println(Float.max(aFloat, bFloat) == aFloat);
          System.out.println(Float.min(aFloat, bFloat) == aFloat);
          // Compare to the calculated sum, again, Max/Min values may differ
          System.out.println(Float.sum(aFloat, bFloat) == (aFloat + bFloat));
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

      char[] aChars = new char[]{'s', 'u', 'p', Character.MAX_VALUE, Character.MIN_VALUE};
      for (char aChar : aChars) {
        System.out.println(Character.hashCode(aChar));
        for (char bChar : aChars) {
          System.out.println(Character.compare(aChar, bChar));
        }
      }

      System.out.println(String.join(", "));
      System.out.println(String.join(", ", "one", "two", "three"));
      System.out.println(String.join("", "one", "two", "three"));
      try {
        throw new AssertionError(String.join(null, "one", "two", "three"));
      } catch (NullPointerException expected) {
      }
      try {
        throw new AssertionError(String.join(", ", (CharSequence[]) null));
      } catch (NullPointerException expected) {
      }

      System.out.println(String.join(", ", listOf()));
      System.out.println(String.join(", ", listOf("one", "two", "three")));
      System.out.println(String.join("", listOf("one", "two", "three")));
      try {
        throw new AssertionError(String.join(null, listOf("one", "two", "three")));
      } catch (NullPointerException expected) {
      }
      try {
        throw new AssertionError(String.join(", ", (Iterable<CharSequence>) null));
      } catch (NullPointerException expected) {
      }

      System.out.println(Objects.compare("a", "b", reverse()));
      System.out.println(Objects.compare("b", "a", reverse()));
      System.out.println(Objects.compare("a", "a", reverse()));

      Object[] objects = {
          aBytes, new boolean[] { true, false }, aChars, aDoubles, aFloats, aInts, aLongs, "a", null
      };
      for (Object aObject : objects) {
        for (Object bObject : objects) {
          System.out.println(Objects.deepEquals(aObject, bObject));
          System.out.println(Objects.equals(aObject, bObject));
        }
      }

      // Use explicit boxing to avoid valueOf method calls which affect invoke-static counts.
      System.out.println(
          Objects.hash(new Integer(1), "2", new Double(3), new Float(4), new Long(5)));

      // Runtime conditionals to prevent any null analysis. Will always evaluate to false branch.
      Object maybeNullButNot = args.length == 1000 ? null : "non-null";
      Object maybeNullButIs = args.length == 2000 ? "non-null" : null;

      System.out.println(Objects.isNull(maybeNullButNot));
      System.out.println(Objects.isNull(maybeNullButIs));

      System.out.println(Objects.nonNull(maybeNullButNot));
      System.out.println(Objects.nonNull(maybeNullButIs));

      System.out.println(Objects.toString(maybeNullButNot));
      System.out.println(Objects.toString(maybeNullButIs));

      System.out.println(Objects.toString(maybeNullButNot, "null default"));
      System.out.println(Objects.toString(maybeNullButIs, "null default"));

      System.out.println(Objects.hashCode(maybeNullButNot));
      System.out.println(Objects.hashCode(maybeNullButIs));

      System.out.println(Objects.requireNonNull(maybeNullButNot, "unexpected"));
      try {
        throw new AssertionError(Objects.requireNonNull(maybeNullButIs, "expected"));
      } catch (NullPointerException e) {
        System.out.println(e.getMessage().contains("expected"));
      }
    }
  }
}
