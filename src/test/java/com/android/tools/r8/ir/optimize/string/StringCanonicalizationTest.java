// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestCompileResult;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.List;
import org.junit.Before;
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
  private final Backend backend;
  List<Class<?>> classes;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public StringCanonicalizationTest(Backend backend) {
    this.backend = backend;
  }

  @Before
  public void setUp() {
    classes = ImmutableList.of(
        NeverInline.class, MessageLoader.class,
        MessageLoader.StaticInternString.class,
        MessageLoader.StaticInternString2.class);
  }

  private void test(
      TestCompileResult result,
      int expectedConstStringCount0,
      int expectedConstStringCount1,
      int expectedConstStringCount2,
      int expectedInternCount,
      int expectedStringOpsCount) throws Exception {
    String main = MessageLoader.class.getCanonicalName();
    String javaOutput = runOnJava(MessageLoader.class);
    String vmOutput = runOnVM(result.app, main, backend);
    assertEquals(javaOutput, vmOutput);

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
  public void testD8() throws Exception {
    if (backend == Backend.CF) {
      return;
    }
    TestCompileResult result = testForD8()
        .release()
        .addProgramClasses(classes)
        .compile();
    test(result, 1, 1, 1, 1, 1);

    result = testForD8()
        .debug()
        .addProgramClasses(classes)
        .compile();
    test(result, 2, 1, 1, 1, 1);
  }

  @Test
  public void testR8() throws Exception {
    TestCompileResult result = testForR8(backend)
        .addProgramClasses(classes)
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addKeepMainRule(MessageLoader.class)
        .compile();
    if (backend == Backend.DEX) {
      test(result, 1, 1, 1, 1, 1);
    } else {
      // TODO(b/118235919): improve CF backend
      test(result, 8, 8, 1, 1, 2);
    }
  }

}
