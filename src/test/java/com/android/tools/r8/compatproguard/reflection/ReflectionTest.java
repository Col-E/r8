// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.compatproguard.reflection;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresentAndRenamed;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.KeepConstantArguments;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.Method;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

class A {

  public void method0() {
    System.out.print(0);
  }

  public void method1(String s) {
    System.out.print(s);
  }

  public void method2(String s1, String s2) {
    System.out.print(s1 + s2);
  }

  public void method3(int i1, int i2) {
    System.out.print(i1 + i2);
  }
}

class MainTest {
  @SuppressWarnings("warning")
  public static void main(String[] args) throws Exception {
    A a = new A();

    Method m;
    m = A.class.getMethod("method0");
    m.invoke(a);
    // The call with in-exact argument to getMethod is intended and should stay.
    // The warning cannot be suppressed according to b/117198454.
    m = A.class.getMethod("method0", null);
    m.invoke(a);
    m = A.class.getMethod("method0", (Class<?>[]) null);
    m.invoke(a);
    m = A.class.getMethod("method0", (Class<?>[]) (Class<?>[]) null);
    m.invoke(a);
    m = A.class.getMethod("method0", (Class<?>[]) (Object[]) (Class<?>[]) null);
    m.invoke(a);
    m = A.class.getMethod("method1", String.class);
    m.invoke(a, "1");
    m = A.class.getMethod("method2", String.class, String.class);
    m.invoke(a, "2", "3");
    m = A.class.getDeclaredMethod("method0");
    m.invoke(a);
    m = A.class.getDeclaredMethod("method1", String.class);
    m.invoke(a, "1");
    m = A.class.getDeclaredMethod("method2", String.class, String.class);
    m.invoke(a, "2", "3");
    m = A.class.getDeclaredMethod("method3", int.class, int.class);
    m.invoke(a, 2, 2);

    try {
      m = A.class.getMethod("method0");
      m.invoke(a);
      m = A.class.getMethod("method1", String.class);
      m.invoke(a, "1");
      m = A.class.getMethod("method2", String.class, String.class);
      m.invoke(a, "2", "3");
      m = A.class.getDeclaredMethod("method0");
      m.invoke(a);
      m = A.class.getDeclaredMethod("method1", String.class);
      m.invoke(a, "1");
      m = A.class.getDeclaredMethod("method2", String.class, String.class);
      m.invoke(a, "2", "3");
      m = A.class.getDeclaredMethod("method3", int.class, int.class);
      m.invoke(a, 2, 2);
    } catch (Exception e) {
      System.out.println("Unexpected: " + e);
    }

    Class<?>[] argumentTypes;
    argumentTypes = new Class<?>[2];
    argumentTypes[1] = int.class;
    argumentTypes[0] = int.class;
    argumentTypes[0] = String.class;
    argumentTypes[1] = String.class;
    m = A.class.getDeclaredMethod("method2", argumentTypes);
    m.invoke(a, "2", "3");
    argumentTypes[0] = String.class;
    argumentTypes[1] = String.class;
    m = A.class.getDeclaredMethod("method2", argumentTypes);
    m.invoke(a, "4", "5");
    argumentTypes[0] = String.class;
    argumentTypes[1] = String.class;
    m = A.class.getDeclaredMethod("method2", (Class<?>[]) argumentTypes);
    m.invoke(a, "5", "4");
    argumentTypes[0] = String.class;
    argumentTypes[1] = String.class;
    m = A.class.getDeclaredMethod("method2", (Class<?>[]) (Object[]) argumentTypes);
    m.invoke(a, "5", "4");

    argumentTypes[1] = int.class;
    argumentTypes[0] = int.class;
    m = A.class.getDeclaredMethod("method3", argumentTypes);
    m.invoke(a, 3, 3);
    argumentTypes[1] = int.class;
    argumentTypes[0] = int.class;
    m = A.class.getDeclaredMethod("method3", argumentTypes);
    m.invoke(a, 3, 4);

    try {
      argumentTypes = new Class<?>[2];
      argumentTypes[1] = int.class;
      argumentTypes[0] = int.class;
      argumentTypes[0] = String.class;
      argumentTypes[1] = String.class;
      m = A.class.getDeclaredMethod("method2", argumentTypes);
      m.invoke(a, "2", "3");
      argumentTypes[0] = String.class;
      argumentTypes[1] = String.class;
      m = A.class.getDeclaredMethod("method2", argumentTypes);
      m.invoke(a, "4", "7");

      argumentTypes[1] = int.class;
      argumentTypes[0] = int.class;
      m = A.class.getDeclaredMethod("method3", argumentTypes);
      m.invoke(a, 3, 3);
      argumentTypes[1] = int.class;
      argumentTypes[0] = int.class;
      m = A.class.getDeclaredMethod("method3", argumentTypes);
      m.invoke(a, 3, 4);
    } catch (Exception e) {
      System.out.println("Unexpected: " + e);
    }
  }
}

class MainNonConstArraySize {
  public static void main(String[] args) throws Exception {
    Method m;
    m = A.class.getMethod("method0");
    m.invoke(new A());

    nonConstArraySize(0);
  }

  @KeepConstantArguments
  @NeverInline
  static void nonConstArraySize(int argumentTypesSize) {
    try {
      A a = new A();

      Method m;
      Class<?>[] argumentTypes;
      argumentTypes = new Class<?>[argumentTypesSize];
      m = A.class.getDeclaredMethod("method0", argumentTypes);
      m.invoke(a);
    } catch (Exception e) {
      // Prepend the 0 output to the exception name to make it easier to compare the result with
      // the reference run.
      System.out.print("0" + e.getClass().getCanonicalName());
    }
  }
}

class MainPhiValue {
  public static void main(String[] args) throws Exception {
    Method m;
    m = A.class.getMethod("method0");
    m.invoke(new A());
    m = A.class.getMethod("method1", String.class);
    m.invoke(new A(), "1");

    try {
      arrayIsPhi(true);
      throw new Exception("Unexpected");
    } catch (NoSuchMethodException e) {
      // Expected.
    }

    try {
      elementIsPhi(true);
      throw new Exception("Unexpected");
    } catch (NoSuchMethodException e) {
      // Expected.
    }

    try {
      elementIsPhiButDeterministic(true, "");
      throw new Exception("Unexpected");
    } catch (NoSuchMethodException e) {
      // Expected.
    }
  }

  @NeverInline
  static void arrayIsPhi(boolean b) throws Exception {
    A a = new A();

    Method m;
    Class<?>[] argumentTypes;
    if (b) {
      argumentTypes = new Class<?>[1];
      argumentTypes[0] = String.class;
    } else {
      argumentTypes = new Class<?>[1];
      argumentTypes[0] = int.class;
    }
    m = A.class.getDeclaredMethod("method1", argumentTypes);
    m.invoke(a, "0");
  }

  @NeverInline
  static void elementIsPhi(boolean b) throws Exception {
    A a = new A();

    Class<?> x;
    x = b ? String.class : int.class;
    Method m;
    Class<?>[] argumentTypes = new Class<?>[1];
    argumentTypes[0] = x;
    m = A.class.getDeclaredMethod("method1", argumentTypes);
    m.invoke(a, "0");
  }

  @NeverInline
  static void elementIsPhiButDeterministic(boolean b, String arg) throws Exception {
    A a = new A();

    Class<?> x;
    x = b ? String.class : arg.getClass();
    Method m;
    Class<?>[] argumentTypes = new Class<?>[1];
    argumentTypes[0] = x;
    m = A.class.getDeclaredMethod("method1", argumentTypes);
    m.invoke(a, "0");
  }
}

class AllPrimitiveTypes {
  public void method(boolean b) {
    System.out.print(b);
  }

  public void method(byte b) {
    System.out.print(b);
  }

  public void method(char c) {
    System.out.print(c);
  }

  public void method(short s) {
    System.out.print(s);
  }

  public void method(int i) {
    System.out.print(i);
  }

  public void method(long l) {
    System.out.print(l);
  }

  public void method(float f) {
    System.out.print(f);
  }

  public void method(double d) {
    System.out.print(d);
  }
}

class MainAllPrimitiveTypes {

  public static void main(String[] args) throws Exception {
    AllPrimitiveTypes a = new AllPrimitiveTypes();

    Method m;
    Class<?>[] argumentTypes;
    argumentTypes = new Class<?>[1];
    argumentTypes[0] = boolean.class;
    m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, true);
    argumentTypes[0] = byte.class;
    m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, (byte) 0);
    argumentTypes[0] = char.class;
    m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, 'a');
    argumentTypes[0] = short.class;
    m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, (short) 1);
    argumentTypes[0] = int.class;
    m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, 2);
    argumentTypes[0] = long.class;
    m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, 3L);
    argumentTypes[0] = float.class;
    m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, 4.4f);
    argumentTypes[0] = double.class;
    m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, 5.5d);

    try {
      argumentTypes = new Class<?>[1];
      argumentTypes[0] = boolean.class;
      m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, true);
      argumentTypes[0] = byte.class;
      m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, (byte) 0);
      argumentTypes[0] = char.class;
      m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, 'a');
      argumentTypes[0] = short.class;
      m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, (short) 1);
      argumentTypes[0] = int.class;
      m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, 2);
      argumentTypes[0] = long.class;
      m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, 3L);
      argumentTypes[0] = float.class;
      m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, 4.4f);
      argumentTypes[0] = double.class;
      m = AllPrimitiveTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, 5.5d);
    } catch (Exception e) {
      System.out.println("Unexpected: " + e);
    }
  }
}

class AllBoxedTypes {
  public void method(Boolean b) {
    System.out.print(b);
  }

  public void method(Byte b) {
    System.out.print(b);
  }

  public void method(Character c) {
    System.out.print(c);
  }

  public void method(Short s) {
    System.out.print(s);
  }

  public void method(Integer i) {
    System.out.print(i);
  }

  public void method(Long l) {
    System.out.print(l);
  }

  public void method(Float f) {
    System.out.print(f);
  }

  public void method(Double d) {
    System.out.print(d);
  }
}

class MainAllBoxedTypes {

  public static void main(String[] args) throws Exception {
    AllBoxedTypes a = new AllBoxedTypes();

    Method m;
    Class<?>[] argumentTypes;
    argumentTypes = new Class<?>[1];
    argumentTypes[0] = Boolean.class;
    m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, true);
    argumentTypes[0] = Byte.class;
    m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, (byte) 0);
    argumentTypes[0] = Character.class;
    m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, 'a');
    argumentTypes[0] = Short.class;
    m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, (short) 1);
    argumentTypes[0] = Integer.class;
    m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, 2);
    argumentTypes[0] = Long.class;
    m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, 3L);
    argumentTypes[0] = Float.class;
    m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, 4.4f);
    argumentTypes[0] = Double.class;
    m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
    m.invoke(a, 5.5d);

    try {
      argumentTypes = new Class<?>[1];
      argumentTypes[0] = Boolean.class;
      m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, true);
      argumentTypes[0] = Byte.class;
      m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, (byte) 0);
      argumentTypes[0] = Character.class;
      m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, 'a');
      argumentTypes[0] = Short.class;
      m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, (short) 1);
      argumentTypes[0] = Integer.class;
      m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, 2);
      argumentTypes[0] = Long.class;
      m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, 3L);
      argumentTypes[0] = Float.class;
      m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, 4.4f);
      argumentTypes[0] = Double.class;
      m = AllBoxedTypes.class.getDeclaredMethod("method", argumentTypes);
      m.invoke(a, 5.5d);
    } catch (Exception e) {
      System.out.println("Unexpected: " + e);
    }
  }
}

@RunWith(Parameterized.class)
public class ReflectionTest extends TestBase {

  private static final String MAIN_ALL_BOXED_TYPES_EXPECTED_OUTPUT =
      "true0a1234.45.5true0a1234.45.5";
  private static final String MAIN_ALL_PRIMITIVE_TYPES_EXPECTED_OUTPUT =
      "true0a1234.45.5true0a1234.45.5";
  private static final String MAIN_NON_CONST_ARRAY_SIZE_EXPECTED_OUTPUT = "00";
  private static final String MAIN_TEST_EXPECTED_OUTPUT = "00000123012340123012342345545467234767";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testJvmMainTest() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MainTest.class)
        .assertSuccessWithOutput(MAIN_TEST_EXPECTED_OUTPUT);
  }

  @Test
  public void testR8MainTest() throws Exception {
    Class<?> mainClass = MainTest.class;
    testForR8(parameters.getBackend())
        .addProgramClasses(mainClass, A.class)
        .addKeepMainRule(mainClass)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector -> {
              assertThat(
                  inspector.clazz(A.class).method("void", "method0", ImmutableList.of()),
                  isPresentAndRenamed());
              assertThat(
                  inspector
                      .clazz(A.class)
                      .method("void", "method1", ImmutableList.of("java.lang.String")),
                  isPresentAndRenamed());
              assertThat(
                  inspector
                      .clazz(A.class)
                      .method(
                          "void",
                          "method2",
                          ImmutableList.of("java.lang.String", "java.lang.String")),
                  isPresentAndRenamed());
            })
        .run(parameters.getRuntime(), mainClass)
        .assertSuccessWithOutput(MAIN_TEST_EXPECTED_OUTPUT);
  }

  @Test
  public void testJvmMainNonConstArraySize() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MainNonConstArraySize.class)
        .assertSuccessWithOutput(MAIN_NON_CONST_ARRAY_SIZE_EXPECTED_OUTPUT);
  }

  @Test
  public void testR8MainNonConstArraySize() throws Exception {
    Class<?> mainClass = MainNonConstArraySize.class;
    testForR8(parameters.getBackend())
        .addProgramClasses(mainClass, A.class)
        .addKeepMainRule(mainClass)
        .enableConstantArgumentAnnotations()
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector ->
                assertThat(
                    inspector.clazz(A.class).method("void", "method0", ImmutableList.of()),
                    isPresentAndRenamed()))
        .run(parameters.getRuntime(), mainClass)
        // The reference run on the Java VM will succeed, whereas the run on the R8 output will fail
        // as in this test we fail to recognize the reflective call. To compare the output of the
        // successful reference run append "java.lang.NoSuchMethodException" to it.
        .assertSuccessWithOutput(
            MAIN_NON_CONST_ARRAY_SIZE_EXPECTED_OUTPUT + "java.lang.NoSuchMethodException");
  }

  @Test
  public void testJvmPhiValue() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MainPhiValue.class)
        .assertFailureWithErrorThatThrows(Exception.class)
        .assertStdoutMatches(equalTo("010"));
  }

  @Test
  public void testR8PhiValue() throws Exception {
    Class<?> mainClass = MainPhiValue.class;
    testForR8(parameters.getBackend())
        .addProgramClasses(mainClass, A.class)
        .addKeepMainRule(mainClass)
        .enableInliningAnnotations()
        .setMinApi(parameters)
        .compile()
        .run(parameters.getRuntime(), mainClass)
        .assertSuccessWithOutput("01");
  }

  @Test
  public void testJvmAllPrimitiveTypes() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MainAllPrimitiveTypes.class)
        .assertSuccessWithOutput(MAIN_ALL_PRIMITIVE_TYPES_EXPECTED_OUTPUT);
  }

  @Test
  public void testR8AllPrimitiveTypes() throws Exception {
    Class<?> mainClass = MainAllPrimitiveTypes.class;
    testForR8(parameters.getBackend())
        .addProgramClasses(mainClass, AllPrimitiveTypes.class)
        .addKeepMainRule(mainClass)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector ->
                inspector
                    .clazz(AllPrimitiveTypes.class)
                    .forAllMethods(
                        m -> {
                          if (!m.isInstanceInitializer()) {
                            assertThat(m, isPresentAndRenamed());
                          }
                        }))
        .run(parameters.getRuntime(), mainClass)
        .assertSuccessWithOutput(MAIN_ALL_PRIMITIVE_TYPES_EXPECTED_OUTPUT);
  }

  @Test
  public void testJvmAllBoxedTypes() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MainAllBoxedTypes.class)
        .assertSuccessWithOutput(MAIN_ALL_BOXED_TYPES_EXPECTED_OUTPUT);
  }

  @Test
  public void testR8AllBoxedTypes() throws Exception {
    Class<?> mainClass = MainAllBoxedTypes.class;
    testForR8(parameters.getBackend())
        .addProgramClasses(mainClass, AllBoxedTypes.class)
        .addKeepMainRule(mainClass)
        .setMinApi(parameters)
        .compile()
        .inspect(
            inspector ->
                inspector
                    .clazz(AllBoxedTypes.class)
                    .forAllMethods(
                        m -> {
                          if (!m.isInstanceInitializer()) {
                            assertThat(m, isPresentAndRenamed());
                          }
                        }))
        .run(parameters.getRuntime(), mainClass)
        .assertSuccessWithOutput(MAIN_ALL_BOXED_TYPES_EXPECTED_OUTPUT);
  }
}
