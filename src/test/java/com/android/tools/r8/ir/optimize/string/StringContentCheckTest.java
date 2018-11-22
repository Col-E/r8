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

class StringContentCheckTestMain {

  @NeverInline
  static boolean argCouldBeNull(String arg) {
    return "CONST".contains(arg)
        && "prefix".startsWith(arg)
        && "suffix".endsWith(arg)
        && "CONST".equals(arg)
        && "CONST".equalsIgnoreCase(arg)
        && "CONST".contentEquals(arg);
  }

  public static void main(String[] args) {
    {
      String s1 = "prefix-CONST-suffix";
      System.out.println(s1.contains("CONST"));
      System.out.println(s1.startsWith("prefix"));
      System.out.println(s1.endsWith("suffix"));
      System.out.println(s1.equals("prefix-CONST-suffix"));
      System.out.println(s1.equalsIgnoreCase("PREFIX-const-SUFFIX"));
      System.out.println(s1.contentEquals("prefix-CONST-suffix"));
      System.out.println(s1.contentEquals(new StringBuffer("prefix-CONST-suffix")));
    }

    {
      // Not compile-time constant string
      String s2 = args.length > 8 ? "prefix-CONST-suffix" : "PREFIX-const-SUFFIX";
      System.out.println(s2.contains("CONST"));
      System.out.println(s2.startsWith("prefix"));
      System.out.println(s2.endsWith("suffix"));
      System.out.println(s2.equals("prefix-CONST-suffix"));
      System.out.println(s2.equalsIgnoreCase("pre-con-suf"));
      System.out.println(s2.contentEquals("prefix-CONST-suffix"));
      System.out.println(s2.contentEquals(new StringBuffer("prefix-CONST-suffix")));
    }

    {
      System.out.println(argCouldBeNull("prefix-CONST-suffix"));
      try {
        argCouldBeNull(null);
        fail("Should raise NullPointerException");
      } catch (NullPointerException npe) {
        // expected
      }
    }
  }
}

@RunWith(Parameterized.class)
public class StringContentCheckTest extends TestBase {
  private final Backend backend;
  private static final List<Class<?>> CLASSES = ImmutableList.of(
      NeverInline.class,
      StringContentCheckTestMain.class
  );
  private static final String JAVA_OUTPUT = StringUtils.lines(
      // s1, contains
      "true",
      // s1, startsWith
      "true",
      // s1, endsWith
      "true",
      // s1, equals
      "true",
      // s1, equalsIgnoreCase
      "true",
      // s1, contentEquals(CharSequence)
      "true",
      // s1, contentEquals(StringBuffer)
      "true",
      // s2, contains
      "false",
      // s2, startsWith
      "false",
      // s2, endsWith
      "false",
      // s2, equals
      "false",
      // s2, equalsIgnoreCase
      "false",
      // s2, contentEquals(CharSequence)
      "false",
      // s2, contentEquals(StringBuffer)
      "false",
      // argCouldBeNull
      "false"
  );
  private static final Class<?> MAIN = StringContentCheckTestMain.class;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public StringContentCheckTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testJVMoutput() throws Exception {
    assumeTrue("Only run JVM reference once (for CF backend)", backend == Backend.CF);
    testForJvm().addTestClasspath().run(MAIN).assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private static boolean isStringContentChecker(DexMethod method) {
    return method.getHolder().toDescriptorString().equals("Ljava/lang/String;")
        && method.getArity() == 1
        && method.proto.returnType.isBooleanType()
        && (method.name.toString().equals("contains")
            || method.name.toString().equals("startsWith")
            || method.name.toString().equals("endsWith")
            || method.name.toString().equals("equals")
            || method.name.toString().equals("equalsIgnoreCase")
            || method.name.toString().equals("contentEquals"));
  }

  private long countStringContentChecker(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isStringContentChecker(instructionSubject.getMethod());
      }
      return false;
    })).count();
  }

  private void test(TestRunResult result, int expectedStringContentCheckerCount) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    long count = countStringContentChecker(mainMethod);
    assertEquals(expectedStringContentCheckerCount, count);

    MethodSubject argCouldBeNull = mainClass.method(
        "boolean", "argCouldBeNull", ImmutableList.of("java.lang.String"));
    assertThat(argCouldBeNull, isPresent());
    // Because of nullable argument, all checkers should remain.
    assertEquals(6, countStringContentChecker(argCouldBeNull));
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", backend == Backend.DEX);

    TestRunResult result = testForD8()
        .debug()
        .addProgramClasses(CLASSES)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 14);

    result = testForD8()
        .release()
        .addProgramClasses(CLASSES)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 8);
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
    test(result, 8);
  }
}
