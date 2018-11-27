// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.ForceInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject.JumboStringMode;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class StringValueOfTestMain {

  interface Itf {
    String getter();
  }

  @NeverInline
  static String hideNPE(String s) {
    return String.valueOf(s);
  }

  static class Foo implements Itf {
    @ForceInline
    @Override
    public String getter() {
      return String.valueOf(getClass().getName());
    }

    @NeverInline
    @Override
    public String toString() {
      return getter();
    }
  }

  public static void main(String[] args) {
    Foo foo = new Foo();
    System.out.println(foo.getter());
    // Trivial, it's String.
    String str = foo.toString();
    System.out.println(String.valueOf(str));
    if (str != null) {
      // With an explicit check, it's non-null String.
      System.out.println(String.valueOf(str));
    }
    // The instance itself is not of String type. Outputs are same, though.
    System.out.println(String.valueOf(foo));

    // Simply const-string "null"
    System.out.println(String.valueOf((Object) null));
    try {
      System.out.println(hideNPE(null));
    } catch (NullPointerException npe) {
      fail("Not expected: " + npe);
    }
  }
}

@RunWith(Parameterized.class)
public class StringValueOfTest extends TestBase {
  private final Backend backend;
  private static final List<Class<?>> CLASSES = ImmutableList.of(
      ForceInline.class,
      NeverInline.class,
      StringValueOfTestMain.class,
      StringValueOfTestMain.Itf.class,
      StringValueOfTestMain.Foo.class
  );
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "com.android.tools.r8.ir.optimize.string.StringValueOfTestMain$Foo",
      "com.android.tools.r8.ir.optimize.string.StringValueOfTestMain$Foo",
      "com.android.tools.r8.ir.optimize.string.StringValueOfTestMain$Foo",
      "com.android.tools.r8.ir.optimize.string.StringValueOfTestMain$Foo",
      "null",
      "null"
  );
  private static final Class<?> MAIN = StringValueOfTestMain.class;

  private static final String STRING_DESCRIPTOR = "Ljava/lang/String;";
  private static final String STRING_TYPE = "java.lang.String";

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public StringValueOfTest(Backend backend) {
    this.backend = backend;
  }


  @Test
  public void testJVMoutput() throws Exception {
    assumeTrue("Only run JVM reference once (for CF backend)", backend == Backend.CF);
    testForJvm().addTestClasspath().run(MAIN).assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private static boolean isStringValueOf(DexMethod method) {
    return method.getHolder().toDescriptorString().equals(STRING_DESCRIPTOR)
        && method.getArity() == 1
        && method.proto.returnType.toDescriptorString().equals(STRING_DESCRIPTOR)
        && method.name.toString().equals("valueOf");
  }

  private long countStringValueOf(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isStringValueOf(instructionSubject.getMethod());
      }
      return false;
    })).count();
  }

  private long countConstNullNumber(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(InstructionSubject::isConstNull)).count();
  }

  private long countNullStringNumber(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(instructionSubject ->
        instructionSubject.isConstString("null", JumboStringMode.ALLOW))).count();
  }

  private void test(
      TestRunResult result,
      int expectedStringValueOfCount,
      int expectedNullCount,
      int expectedNullStringCount)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    long count = countStringValueOf(mainMethod);
    assertEquals(expectedStringValueOfCount, count);
    count = countConstNullNumber(mainMethod);
    assertEquals(expectedNullCount, count);
    count = countNullStringNumber(mainMethod);
    assertEquals(expectedNullStringCount, count);

    MethodSubject hideNPE = mainClass.method(STRING_TYPE, "hideNPE", ImmutableList.of(STRING_TYPE));
    assertThat(hideNPE, isPresent());
    // Due to the nullable argument, valueOf should remain.
    assertEquals(1, countStringValueOf(hideNPE));
  }

  @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", backend == Backend.DEX);

    TestRunResult result = testForD8()
        .debug()
        .addProgramClasses(CLASSES)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 4, 1, 0);

    result = testForD8()
        .release()
        .addProgramClasses(CLASSES)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 3, 1, 1);
  }

  @Test
  public void testR8() throws Exception {
    TestRunResult result = testForR8(backend)
        .addProgramClasses(CLASSES)
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-dontobfuscate")
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 1, 1, 1);
  }
}
