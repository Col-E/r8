// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.D8TestRunResult;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class StringIsEmptyTestMain {

  @NeverInline
  static boolean wrapper(String arg) {
    // Cannot be computed at compile time.
    return arg.isEmpty();
  }

  public static void main(String[] args) {
    String s1 = "non-empty";
    System.out.println(s1.isEmpty());
    String s2 = "";
    System.out.println(s2.isEmpty());
    System.out.println((s1 + s2).isEmpty());
    System.out.println(wrapper("non-null"));
    try {
      wrapper(null);
      fail("Should raise NullPointerException");
    } catch (NullPointerException npe) {
      // expected
    }
  }
}

@RunWith(Parameterized.class)
public class StringIsEmptyTest extends TestBase {
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "false",
      "true",
      "false",
      "false"
  );
  private static final Class<?> MAIN = StringIsEmptyTestMain.class;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().build();
  }

  private final TestParameters parameters;

  public StringIsEmptyTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJVMOutput() throws Exception {
    assumeTrue(
        "Only run JVM reference once (for CF backend)",
        parameters.getBackend() == Backend.CF);
    testForJvm()
        .addTestClasspath()
        .run(parameters.getRuntime(), MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private static boolean isStringIsEmpty(DexMethod method) {
    return method.holder.toDescriptorString().equals("Ljava/lang/String;")
        && method.getArity() == 0
        && method.proto.returnType.isBooleanType()
        && method.name.toString().equals("isEmpty");
  }

  private long countStringIsEmpty(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isStringIsEmpty(instructionSubject.getMethod());
      }
      return false;
    })).count();
  }

  private void test(TestRunResult result, int expectedStringIsEmptyCount) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    long count = countStringIsEmpty(mainMethod);
    assertEquals(expectedStringIsEmptyCount, count);

    MethodSubject wrapper = mainClass.method(
        "boolean", "wrapper", ImmutableList.of("java.lang.String"));
    assertThat(wrapper, isPresent());
    // Because of nullable, non-constant argument, isEmpty() should remain.
    assertEquals(1, countStringIsEmpty(wrapper));
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", parameters.getBackend() == Backend.DEX);

    D8TestRunResult result =
        testForD8()
            .debug()
            .addProgramClasses(MAIN)
            .apply(parameters::setMinApiForRuntime)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 3);

    result =
        testForD8()
            .release()
            .addProgramClasses(MAIN)
            .apply(parameters::setMinApiForRuntime)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 1);
  }

  @Test
  public void testR8() throws Exception {
    R8TestRunResult result =
        testForR8(parameters.getBackend())
            .addProgramClasses(MAIN)
            .enableProguardTestOptions()
            .enableInliningAnnotations()
            .addKeepMainRule(MAIN)
            .apply(parameters::setMinApiForRuntime)
            .run(parameters.getRuntime(), MAIN)
            .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 1);
  }
}
