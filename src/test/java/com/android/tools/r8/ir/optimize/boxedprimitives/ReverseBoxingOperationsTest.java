// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.boxedprimitives;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ReverseBoxingOperationsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testUnboxingRemoved() throws Exception {
    testForR8(parameters.getBackend())
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(this::assertBoxOperationsRemoved)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(getExpectedResult());
  }

  private void assertBoxOperationsRemoved(CodeInspector codeInspector) {
    DexItemFactory factory = codeInspector.getFactory();

    Set<DexMethod> unboxMethods = Sets.newIdentityHashSet();
    Set<DexMethod> boxMethods = Sets.newIdentityHashSet();
    for (DexType primitiveType : factory.unboxPrimitiveMethod.keySet()) {
      unboxMethods.add(factory.getUnboxPrimitiveMethod(primitiveType));
      boxMethods.add(factory.getBoxPrimitiveMethod(primitiveType));
    }

    // All box operations should have been removed, except for the unbox methods that act as null
    // checks, which may or may not be replaced by null-checks depending if they are reprocessed.
    assertEquals(
        24,
        codeInspector
            .clazz(Main.class)
            .allMethods(m -> !m.getOriginalName().equals("main"))
            .size());
    codeInspector
        .clazz(Main.class)
        .allMethods(m -> !m.getOriginalName().equals("main"))
        .forEach(
            m ->
                assertTrue(
                    m.streamInstructions()
                        .noneMatch(i -> i.isInvoke() && boxMethods.contains(i.getMethod()))));
    codeInspector
        .clazz(Main.class)
        .allMethods(
            m ->
                !m.getOriginalName().equals("main")
                    && (m.getOriginalName().contains("Unbox")
                        || m.getOriginalName().contains("NonNull")))
        .forEach(
            m ->
                assertTrue(
                    m.streamInstructions()
                        .noneMatch(i -> i.isInvoke() && unboxMethods.contains(i.getMethod()))));
  }

  private String getExpectedResult() {
    String[] resultItems = {"1", "1", "1.0", "1.0", "1", "1", "c", "true"};
    String[] resultItems2 = {"2", "2", "2.0", "2.0", "2", "2", "e", "false"};
    List<String> result = new ArrayList<>();
    for (int i = 0; i < resultItems.length; i++) {
      String item = resultItems[i];
      String item2 = resultItems2[i];
      result.add(">" + item);
      result.add(">" + item);
      result.add(">npe failure");
      result.add(">" + item);
      result.add(">" + item2);
    }
    return StringUtils.lines(result);
  }

  public static class Main {

    public static void main(String[] args) {
      int i = System.currentTimeMillis() > 0 ? 1 : 0;
      System.out.println(intUnboxTest(i));
      System.out.println(intTest(i));
      try {
        System.out.println(intTest(null));
      } catch (NullPointerException npe7) {
        System.out.println("npe failure");
      }
      System.out.println(intTestNonNull(i));
      System.out.println(intTestNonNull(i + 1));

      long l = System.currentTimeMillis() > 0 ? 1L : 0L;
      System.out.println(longUnboxTest(l));
      System.out.println(longTest(l));
      try {
        System.out.println(longTest(null));
      } catch (NullPointerException npe6) {
        System.out.println("npe failure");
      }
      System.out.println(longTestNonNull(l));
      System.out.println(longTestNonNull(l + 1));

      double d = System.currentTimeMillis() > 0 ? 1.0 : 0.0;
      System.out.println(doubleUnboxTest(d));
      System.out.println(doubleTest(d));
      try {
        System.out.println(doubleTest(null));
      } catch (NullPointerException npe5) {
        System.out.println("npe failure");
      }
      System.out.println(doubleTestNonNull(d));
      System.out.println(doubleTestNonNull(d + 1));

      float f = System.currentTimeMillis() > 0 ? 1.0f : 0.0f;
      System.out.println(floatUnboxTest(f));
      System.out.println(floatTest(f));
      try {
        System.out.println(floatTest(null));
      } catch (NullPointerException npe4) {
        System.out.println("npe failure");
      }
      System.out.println(floatTestNonNull(f));
      System.out.println(floatTestNonNull(f + 1));

      byte b = (byte) (System.currentTimeMillis() > 0 ? 1 : 0);
      System.out.println(byteUnboxTest(b));
      System.out.println(byteTest(b));
      try {
        System.out.println(byteTest(null));
      } catch (NullPointerException npe3) {
        System.out.println("npe failure");
      }
      System.out.println(byteTestNonNull(b));
      System.out.println(byteTestNonNull((byte) (b + 1)));

      short s = (short) (System.currentTimeMillis() > 0 ? 1 : 0);
      System.out.println(shortUnboxTest(s));
      System.out.println(shortTest(s));
      try {
        System.out.println(shortTest(null));
      } catch (NullPointerException npe2) {
        System.out.println("npe failure");
      }
      System.out.println(shortTestNonNull(s));
      System.out.println(shortTestNonNull((short) (s + 1)));

      char c = System.currentTimeMillis() > 0 ? 'c' : 'd';
      System.out.println(charUnboxTest(c));
      System.out.println(charTest(c));
      try {
        System.out.println(charTest(null));
      } catch (NullPointerException npe1) {
        System.out.println("npe failure");
      }
      System.out.println(charTestNonNull(c));
      System.out.println(charTestNonNull('e'));

      boolean bool = System.currentTimeMillis() > 0;
      System.out.println(booleanUnboxTest(bool));
      System.out.println(booleanTest(bool));
      try {
        System.out.println(booleanTest(null));
      } catch (NullPointerException npe) {
        System.out.println("npe failure");
      }
      System.out.println(booleanTestNonNull(bool));
      System.out.println(booleanTestNonNull(false));
    }

    @NeverInline
    public static int intUnboxTest(int i) {
      System.out.print(">");
      return Integer.valueOf(i).intValue();
    }

    @NeverInline
    public static Integer intTest(Integer i) {
      System.out.print(">");
      return Integer.valueOf(i.intValue());
    }

    @NeverInline
    public static Integer intTestNonNull(Integer i) {
      System.out.print(">");
      return Integer.valueOf(i.intValue());
    }

    @NeverInline
    public static double doubleUnboxTest(double d) {
      System.out.print(">");
      return Double.valueOf(d).doubleValue();
    }

    @NeverInline
    public static Double doubleTest(Double d) {
      System.out.print(">");
      return Double.valueOf(d.doubleValue());
    }

    @NeverInline
    public static Double doubleTestNonNull(Double d) {
      System.out.print(">");
      return Double.valueOf(d.doubleValue());
    }

    @NeverInline
    public static long longUnboxTest(long l) {
      System.out.print(">");
      return Long.valueOf(l).longValue();
    }

    @NeverInline
    public static Long longTest(Long l) {
      System.out.print(">");
      return Long.valueOf(l.longValue());
    }

    @NeverInline
    public static Long longTestNonNull(Long l) {
      System.out.print(">");
      return Long.valueOf(l.longValue());
    }

    @NeverInline
    public static float floatUnboxTest(float f) {
      System.out.print(">");
      return Float.valueOf(f).floatValue();
    }

    @NeverInline
    public static Float floatTest(Float f) {
      System.out.print(">");
      return Float.valueOf(f.floatValue());
    }

    @NeverInline
    public static Float floatTestNonNull(Float f) {
      System.out.print(">");
      return Float.valueOf(f.floatValue());
    }

    @NeverInline
    public static short shortUnboxTest(short s) {
      System.out.print(">");
      return Short.valueOf(s).shortValue();
    }

    @NeverInline
    public static Short shortTest(Short s) {
      System.out.print(">");
      return Short.valueOf(s.shortValue());
    }

    @NeverInline
    public static Short shortTestNonNull(Short s) {
      System.out.print(">");
      return Short.valueOf(s.shortValue());
    }

    @NeverInline
    public static char charUnboxTest(char c) {
      System.out.print(">");
      return Character.valueOf(c).charValue();
    }

    @NeverInline
    public static Character charTest(Character c) {
      System.out.print(">");
      return Character.valueOf(c.charValue());
    }

    @NeverInline
    public static Character charTestNonNull(Character c) {
      System.out.print(">");
      return Character.valueOf(c.charValue());
    }

    @NeverInline
    public static byte byteUnboxTest(byte b) {
      System.out.print(">");
      return Byte.valueOf(b).byteValue();
    }

    @NeverInline
    public static Byte byteTest(Byte b) {
      System.out.print(">");
      return Byte.valueOf(b.byteValue());
    }

    @NeverInline
    public static Byte byteTestNonNull(Byte b) {
      System.out.print(">");
      return Byte.valueOf(b.byteValue());
    }

    @NeverInline
    public static boolean booleanUnboxTest(boolean b) {
      System.out.print(">");
      return Boolean.valueOf(b).booleanValue();
    }

    @NeverInline
    public static Boolean booleanTest(Boolean b) {
      System.out.print(">");
      return Boolean.valueOf(b.booleanValue());
    }

    @NeverInline
    public static Boolean booleanTestNonNull(Boolean b) {
      System.out.print(">");
      return Boolean.valueOf(b.booleanValue());
    }
  }
}
