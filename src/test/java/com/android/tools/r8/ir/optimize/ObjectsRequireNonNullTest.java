// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NeverPropagateValue;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.SingleTestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ObjectsRequireNonNullTest extends TestBase {
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "Foo::toString",
      "Foo::bar",
      "Foo::bar",
      "Expected NPE",
      "Expected NPE"
  );
  private static final Class<?> MAIN = ObjectsRequireNonNullTestMain.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters()
        .withCfRuntimes()
        // Objects#requireNonNull will be desugared VMs older than API level K.
        .withDexRuntimesStartingFromExcluding(Version.V4_4_4)
        .withApiLevelsStartingAtIncluding(AndroidApiLevel.K)
        .build();
  }

  private final TestParameters parameters;

  public ObjectsRequireNonNullTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvmOutput() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private static boolean isObjectsRequireNonNull(DexMethod method) {
    return method.toSourceString().equals(
        "java.lang.Object java.util.Objects.requireNonNull(java.lang.Object)");
  }

  private long countObjectsRequireNonNull(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isObjectsRequireNonNull(instructionSubject.getMethod());
      }
      return false;
    })).count();
  }

  private void test(
      SingleTestRunResult<?> result, int expectedCountInMain, int expectedCountInConsumer)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(expectedCountInMain, countObjectsRequireNonNull(mainMethod));

    MethodSubject unknownArg = mainClass.uniqueMethodWithOriginalName("unknownArg");
    assertThat(unknownArg, isPresent());
    // Due to the nullable argument, requireNonNull should remain.
    assertEquals(1, countObjectsRequireNonNull(unknownArg));

    MethodSubject uninit = mainClass.uniqueMethodWithOriginalName("consumeUninitialized");
    assertThat(uninit, isPresent());
    assertEquals(expectedCountInConsumer, countObjectsRequireNonNull(uninit));
    if (expectedCountInConsumer == 0) {
      assertEquals(
          0, Streams.stream(uninit.iterateInstructions(InstructionSubject::isInvoke)).count());
      assertEquals(
          1, Streams.stream(uninit.iterateInstructions(InstructionSubject::isThrow)).count());
    }
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.isDexRuntime());
    D8TestRunResult result =
        testForD8()
            .debug()
            .addProgramClassesAndInnerClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 2, 1);

    result =
        testForD8()
            .release()
            .addProgramClassesAndInnerClasses(MAIN)
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 0, 1);
  }

  @Test
  public void testR8() throws Exception {
    assumeTrue("CF disables move result optimization", parameters.isDexRuntime());
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(MAIN)
            .enableInliningAnnotations()
            .enableMemberValuePropagationAnnotations()
            .addKeepMainRule(MAIN)
            .addDontObfuscate()
            .setMinApi(parameters)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 0, 0);
  }

  static class ObjectsRequireNonNullTestMain {

    static class Uninitialized {
      void noWayToCall() {
        System.out.println("Uninitialized, hence no way to call this.");
      }
    }

    @NeverPropagateValue
    @NeverInline
    static void consumeUninitialized(Uninitialized arg) {
      Uninitialized nonNullArg = Objects.requireNonNull(arg);
      // Dead code.
      nonNullArg.noWayToCall();
    }

    static class Foo {
      @NeverInline
      void bar() {
        System.out.println("Foo::bar");
      }

      @NeverInline
      @Override
      public String toString() {
        return "Foo::toString";
      }
    }

    @NeverInline
    static void unknownArg(Foo foo) {
      // It's unclear the argument is definitely null or not null.
      Foo checked = Objects.requireNonNull(foo);
      checked.bar();
    }

    public static void main(String[] args) {
      Foo instance = new Foo();
      // Not removable in debug mode.
      Object nonNull = Objects.requireNonNull(instance);
      System.out.println(nonNull);
      // Removable because associated locals are changed while type casting.
      Foo checked = Objects.requireNonNull(instance);
      checked.bar();

      unknownArg(instance);
      try {
        Foo alwaysNull = System.currentTimeMillis() > 0 ? null : instance;
        unknownArg(alwaysNull);
        throw new AssertionError("Expected NullPointerException");
      } catch (NullPointerException npe) {
        System.out.println("Expected NPE");
      }

      try {
        consumeUninitialized(null);
        throw new AssertionError("Expected NullPointerException");
      } catch (NullPointerException npe) {
        System.out.println("Expected NPE");
      }
    }
  }
}
