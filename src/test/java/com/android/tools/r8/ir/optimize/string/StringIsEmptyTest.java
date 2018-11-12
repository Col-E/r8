// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.List;
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
  private final Backend backend;
  private static final List<Class<?>> CLASSES = ImmutableList.of(
      NeverInline.class,
      StringIsEmptyTestMain.class
  );
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "false",
      "true",
      "false",
      "false"
  );
  private static final Class<?> MAIN = StringIsEmptyTestMain.class;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public StringIsEmptyTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testJVMoutput() throws Exception {
    assumeTrue("Only run JVM reference once (for CF backend)", backend == Backend.CF);
    testForJvm().addTestClasspath().run(MAIN).assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private static boolean isStringIsEmpty(DexMethod method) {
    return method.getHolder().toDescriptorString().equals("Ljava/lang/String;")
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
    assumeTrue("Only run D8 for Dex backend", backend == Backend.DEX);

    TestRunResult result = testForD8()
        .debug()
        .addProgramClasses(CLASSES)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 3);

    result = testForD8()
        .release()
        .addProgramClasses(CLASSES)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 1);
  }

  @Test
  public void testR8() throws Exception {
    TestRunResult result = testForR8(backend)
        .addProgramClasses(CLASSES)
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 1);
  }
}
