// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.canonicalization;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.D8TestCompileResult;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.R8TestCompileResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class MessageLoader {
  private static int index = 0;

  @NeverInline
  static String buildSuffix(int i) {
    if (i % 2 == 0) {
      return "$" + i;
    }
    return "/" + i;
  }

  @NeverInline
  static String getSuffix() {
    if (index <= 8) {
      return "$" + index++;
    }
    throw new IndexOutOfBoundsException("index >= " + index);
  }

  public static void main(String[] args) {
    {
      // In debug mode, we need two: one for the below with the local
      // The other for literal constants that are canonicalized.
      // In release mode, all occurrences are canonicalized.
      String tag = "1st-tag";
      System.out.println("1st-tag" + buildSuffix(1));
      System.out.println(tag + buildSuffix(2));
      System.out.println(tag + buildSuffix(3));
      System.out.println(tag + buildSuffix(4));
      System.out.println(tag + buildSuffix(5));
      System.out.println("1st-tag" + buildSuffix(6));
      System.out.println(tag + buildSuffix(7));
      System.out.println(tag + buildSuffix(8));
    }
    {
      // All occurrences are canonicalized.
      System.out.println("2nd-tag" + buildSuffix(1));
      System.out.println("2nd-tag" + buildSuffix(2));
      System.out.println("2nd-tag" + buildSuffix(3));
      System.out.println("2nd-tag" + buildSuffix(4));
      System.out.println("2nd-tag" + buildSuffix(5));
      System.out.println("2nd-tag" + buildSuffix(6));
      System.out.println("2nd-tag" + buildSuffix(7));
      System.out.println("2nd-tag" + buildSuffix(8));
    }
    {
      // All occurrences are canonicalized.
      System.out.println("3rd-tag" + getSuffix());
      System.out.println("3rd-tag" + getSuffix());
      System.out.println("3rd-tag" + getSuffix());
      System.out.println("3rd-tag" + getSuffix());
      System.out.println("3rd-tag" + getSuffix());
      System.out.println("3rd-tag" + getSuffix());
      System.out.println("3rd-tag" + getSuffix());
      try {
        // Then we need one more in a catch handler.
        System.out.println("3rd-tag" + getSuffix());
      } catch (IndexOutOfBoundsException e) {
        // Intentionally empty.
      }
    }
    // Mimic part of Art596_app_imagesTest.
    {
      StringBuffer builder = new StringBuffer();
      builder.append("int");
      builder.append("er");
      builder.append("n");
      String tmp = builder.toString();
      String intern = tmp.intern();
      System.out.println(intern != tmp);
      System.out.println(intern == StaticInternString.INTERN);
      System.out.println(StaticInternString.getIntern() == StaticInternString2.getIntern());
    }
    // Mimic part of Art624_checker_stringops.
    {
      StringBuffer s = new StringBuffer();
      System.out.println(2 == s.append("x").append("x").length());
    }
  }

  static class StaticInternString {
    public final static String INTERN = "intern";
    @NeverInline
    public static String getIntern() {
      return INTERN;
    }
  }

  static class StaticInternString2 {
    public final static String INTERN = "intern";
    @NeverInline
    public static String getIntern() {
      return INTERN;
    }
  }
}

@RunWith(Parameterized.class)
public class StringCanonicalizationTest extends TestBase {
  private static final Class<?> MAIN = MessageLoader.class;
  private static final String EXPECTED = StringUtils.lines(
      "1st-tag/1",
      "1st-tag$2",
      "1st-tag/3",
      "1st-tag$4",
      "1st-tag/5",
      "1st-tag$6",
      "1st-tag/7",
      "1st-tag$8",
      "2nd-tag/1",
      "2nd-tag$2",
      "2nd-tag/3",
      "2nd-tag$4",
      "2nd-tag/5",
      "2nd-tag$6",
      "2nd-tag/7",
      "2nd-tag$8",
      "3rd-tag$0",
      "3rd-tag$1",
      "3rd-tag$2",
      "3rd-tag$3",
      "3rd-tag$4",
      "3rd-tag$5",
      "3rd-tag$6",
      "3rd-tag$7",
      "false",
      "true",
      "true",
      "true"
  );

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // CF should not canonicalize strings or lower them. See (r8g/30163) and (r8g/30320).
    return getTestParameters().withDexRuntimes().withAllApiLevels().build();
  }

  private final TestParameters parameters;

  public StringCanonicalizationTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private void test(
      TestCompileResult result,
      int expectedConstStringCount0,
      int expectedConstStringCount1,
      int expectedConstStringCount2,
      int expectedInternCount,
      int expectedStringOpsCount) throws Exception {
    String main = MessageLoader.class.getCanonicalName();
    result
        .run(parameters.getRuntime(), main)
        .assertSuccessWithOutput(EXPECTED);

    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(main);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    long count = Streams.stream(mainMethod.iterateInstructions(
        i -> i.isConstString("1st-tag", JumboStringMode.ALLOW))).count();
    assertEquals(expectedConstStringCount0, count);
    count = Streams.stream(mainMethod.iterateInstructions(
        i -> i.isConstString("2nd-tag", JumboStringMode.ALLOW))).count();
    assertEquals(expectedConstStringCount1, count);
    count = Streams.stream(mainMethod.iterateInstructions(
        i -> i.isConstString("3rd-tag", JumboStringMode.ALLOW))).count();
    assertEquals(expectedConstStringCount2, count);
    count = Streams.stream(mainMethod.iterateInstructions(
        i -> i.isConstString("intern", JumboStringMode.ALLOW))).count();
    assertEquals(expectedInternCount, count);
    count = Streams.stream(mainMethod.iterateInstructions(
        i -> i.isConstString("x", JumboStringMode.ALLOW))).count();
    assertEquals(expectedStringOpsCount, count);
  }

  @Test
  public void testR8Debug() throws Exception {
    D8TestCompileResult result =
        testForD8().debug().addProgramClassesAndInnerClasses(MAIN).setMinApi(parameters).compile();
    test(result, 2, 1, 1, 1, 1);
  }

  @Test
  public void testD8Release() throws Exception {
    D8TestCompileResult result =
        testForD8()
            .release()
            .addProgramClassesAndInnerClasses(MAIN)
            .setMinApi(parameters)
            .compile();
    test(result, 1, 1, 1, 1, 0);
  }

  @Test
  public void testR8() throws Exception {
    R8TestCompileResult result =
        testForR8(parameters.getBackend())
            .addProgramClassesAndInnerClasses(MAIN)
            .enableProguardTestOptions()
            .enableInliningAnnotations()
            .addKeepMainRule(MessageLoader.class)
            .setMinApi(parameters)
            .compile();
    test(result, 1, 1, 1, 1, 0);
  }
}
