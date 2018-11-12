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
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class StringToStringTestMain {

  @NeverInline
  static String hideNPE(String s) {
    return s.toString();
  }

  public static void main(String[] args) {
    String x = "constant-x";
    System.out.println(x.toString());
    StringBuilder builder = new StringBuilder();
    builder.append("R");
    builder.append(8);
    System.out.println(builder.toString());
    try {
      System.out.println(hideNPE(null));
      fail("Expected NullPointerException");
    } catch (NullPointerException npe) {
      // Expected
    }
  }
}

@RunWith(Parameterized.class)
public class StringToStringTest extends TestBase {
  private final Backend backend;
  private static final List<Class<?>> CLASSES = ImmutableList.of(
      NeverInline.class,
      StringToStringTestMain.class
  );
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "constant-x",
      "R8"
  );
  private static final Class<?> MAIN = StringToStringTestMain.class;

  private static final String STRING_DESCRIPTOR = "Ljava/lang/String;";

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public StringToStringTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testJVMoutput() throws Exception {
    assumeTrue("Only run JVM reference once (for CF backend)", backend == Backend.CF);
    testForJvm().addTestClasspath().run(MAIN).assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private static boolean isStringToString(DexMethod method) {
    return method.getHolder().toDescriptorString().equals(STRING_DESCRIPTOR)
        && method.getArity() == 0
        && method.proto.returnType.toDescriptorString().equals(STRING_DESCRIPTOR)
        && method.name.toString().equals("toString");
  }

  private long countStringToString(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isStringToString(instructionSubject.getMethod());
      }
      return false;
    })).count();
  }

  private void test(TestRunResult result, int expectedStringToStringCount) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    long count = countStringToString(mainMethod);
    assertEquals(expectedStringToStringCount, count);
  }

  @Test
  @Ignore("b/119399513")
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", backend == Backend.DEX);

    TestRunResult result = testForD8()
        .debug()
        .addProgramClasses(CLASSES)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 1);

    result = testForD8()
        .release()
        .addProgramClasses(CLASSES)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 0);
  }

  @Test
  @Ignore("b/119399513")
  public void testR8() throws Exception {
    TestRunResult result = testForR8(backend)
        .addProgramClasses(CLASSES)
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-dontobfuscate")
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 0);
  }
}
