// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.methodhandles;

import static org.hamcrest.CoreMatchers.containsString;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.StringUtils;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

/**
 * This test is a refactoring of the InvokePolymorphic test code run by the various AndroidOTest
 * runners.
 */
@RunWith(Parameterized.class)
public class InvokePolymorphicTypesTest extends TestBase {

  static final String EXPECTED =
      StringUtils.lines(
          "N-1-string",
          "2-a--1-1.1-2.24-12345678-N-1-string",
          "false",
          "h",
          "56",
          "72",
          "2147483689",
          "0.56",
          "100.0",
          "hello",
          "goodbye",
          "true");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build();
  }

  public InvokePolymorphicTypesTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testD8() throws Exception {
    boolean hasCompileSupport =
        parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithInvokePolymorphicSupport());
    boolean hasRuntimeSupport =
        parameters.isCfRuntime()
            || parameters.asDexRuntime().getVersion().isNewerThanOrEqual(Version.V8_1_0);
    testForD8(parameters.getBackend())
        .addProgramClasses(Data.class, TestClass.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            hasCompileSupport,
            r -> r.assertSuccessWithOutput(EXPECTED),
            hasRuntimeSupport,
            r ->
                r.assertSuccess()
                    .assertStderrMatches(
                        containsString(
                            "Instruction is unrepresentable in DEX V35: invoke-polymorphic")),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    boolean hasCompileSupport =
        parameters.isCfRuntime()
            || parameters
                .getApiLevel()
                .isGreaterThanOrEqualTo(apiLevelWithInvokePolymorphicSupport());
    boolean hasRuntimeSupport =
        parameters.isCfRuntime()
            || parameters.asDexRuntime().getVersion().isNewerThanOrEqual(Version.V8_1_0);
    testForR8(parameters.getBackend())
        .addProgramClasses(Data.class, TestClass.class)
        .setMinApi(parameters)
        .applyIf(
            !hasCompileSupport,
            b ->
                b.addDontWarn(
                    MethodType.class,
                    MethodHandle.class,
                    MethodHandles.class,
                    MethodHandles.Lookup.class))
        .addKeepMainRule(TestClass.class)
        .addKeepMethodRules(
            Reference.methodFromMethod(Data.class.getDeclaredConstructor()),
            Reference.methodFromMethod(
                TestClass.class.getDeclaredMethod(
                    "buildString", Integer.class, int.class, String.class)),
            Reference.methodFromMethod(
                TestClass.class.getDeclaredMethod(
                    "buildString",
                    byte.class,
                    char.class,
                    short.class,
                    float.class,
                    double.class,
                    long.class,
                    Integer.class,
                    int.class,
                    String.class)),
            Reference.methodFromMethod(
                TestClass.class.getDeclaredMethod(
                    "testWithAllTypes",
                    boolean.class,
                    char.class,
                    short.class,
                    int.class,
                    long.class,
                    float.class,
                    double.class,
                    String.class,
                    Object.class)))
        .allowDiagnosticMessages()
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            hasCompileSupport,
            r -> r.assertSuccessWithOutput(EXPECTED),
            hasRuntimeSupport,
            r ->
                r.assertSuccess()
                    .assertStderrMatches(
                        containsString(
                            "Instruction is unrepresentable in DEX V35: invoke-polymorphic")),
            r -> r.assertFailureWithErrorThatThrows(NoClassDefFoundError.class));
  }

  static class Data {}

  static class TestClass {

    public String buildString(Integer i1, int i2, String s) {
      return (i1 == null ? "N" : "!N") + "-" + i2 + "-" + s;
    }

    public void testInvokePolymorphic() {
      MethodType mt = MethodType.methodType(String.class, Integer.class, int.class, String.class);
      MethodHandles.Lookup lk = MethodHandles.lookup();

      try {
        MethodHandle mh = lk.findVirtual(getClass(), "buildString", mt);
        System.out.println(mh.invoke(this, null, 1, "string"));
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }

    public String buildString(
        byte b, char c, short s, float f, double d, long l, Integer i1, int i2, String str) {
      return b
          + "-"
          + c
          + "-"
          + s
          + "-"
          + f
          + "-"
          + d
          + "-"
          + l
          + "-"
          + (i1 == null ? "N" : "!N")
          + "-"
          + i2
          + "-"
          + str;
    }

    public void testInvokePolymorphicRange() {
      MethodType mt =
          MethodType.methodType(
              String.class,
              byte.class,
              char.class,
              short.class,
              float.class,
              double.class,
              long.class,
              Integer.class,
              int.class,
              String.class);
      MethodHandles.Lookup lk = MethodHandles.lookup();

      try {
        MethodHandle mh = lk.findVirtual(getClass(), "buildString", mt);
        System.out.println(
            mh.invoke(
                this, (byte) 2, 'a', (short) 0xFFFF, 1.1f, 2.24d, 12345678L, null, 1, "string"));
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }

    public static void testWithAllTypes(
        boolean z, char a, short b, int c, long d, float e, double f, String g, Object h) {
      System.out.println(z);
      System.out.println(a);
      System.out.println(b);
      System.out.println(c);
      System.out.println(d);
      System.out.println(e);
      System.out.println(f);
      System.out.println(g);
      System.out.println(h);
    }

    public void testInvokePolymorphicWithAllTypes() {
      try {
        MethodHandle mth =
            MethodHandles.lookup()
                .findStatic(
                    TestClass.class,
                    "testWithAllTypes",
                    MethodType.methodType(
                        void.class,
                        boolean.class,
                        char.class,
                        short.class,
                        int.class,
                        long.class,
                        float.class,
                        double.class,
                        String.class,
                        Object.class));
        mth.invokeExact(
            false,
            'h',
            (short) 56,
            72,
            Integer.MAX_VALUE + 42l,
            0.56f,
            100.0d,
            "hello",
            (Object) "goodbye");
      } catch (Throwable t) {
        t.printStackTrace();
      }
    }

    public MethodHandle testInvokePolymorphicWithConstructor() {
      MethodHandle mh = null;
      MethodType mt = MethodType.methodType(void.class);
      MethodHandles.Lookup lk = MethodHandles.lookup();

      try {
        mh = lk.findConstructor(Data.class, mt);
        System.out.println(mh.invoke().getClass() == Data.class);
      } catch (Throwable t) {
        t.printStackTrace();
      }

      return mh;
    }

    public static void main(String[] args) {
      TestClass invokePolymorphic = new TestClass();
      invokePolymorphic.testInvokePolymorphic();
      invokePolymorphic.testInvokePolymorphicRange();
      invokePolymorphic.testInvokePolymorphicWithAllTypes();
      invokePolymorphic.testInvokePolymorphicWithConstructor();
    }
  }
}
