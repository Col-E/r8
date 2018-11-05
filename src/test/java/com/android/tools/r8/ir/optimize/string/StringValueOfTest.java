// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

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
import org.junit.Before;
import org.junit.BeforeClass;
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

    @Override
    public String toString() {
      return getter();
    }
  }

  public static void main(String[] args) {
    Foo foo = new Foo();
    // valueOf inside getter() can be removed (if inlined).
    System.out.println(foo.getter());
    // Trivial, it's String.
    System.out.println(String.valueOf(foo.toString()));
    // But it's not. Outputs are same, though.
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
  private List<Class<?>> classes;
  private static String javaOutput;
  private static Class<?> main;

  private static final String STRING_DESCRIPTOR = "Ljava/lang/String;";

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public StringValueOfTest(Backend backend) {
    this.backend = backend;
  }

  @BeforeClass
  public static void buildExpectedJavaOutput() {
    javaOutput = StringUtils.lines(
        "com.android.tools.r8.ir.optimize.string.StringValueOfTestMain$Foo",
        "com.android.tools.r8.ir.optimize.string.StringValueOfTestMain$Foo",
        "com.android.tools.r8.ir.optimize.string.StringValueOfTestMain$Foo",
        "null",
        "null"
    );
    main = StringValueOfTestMain.class;
  }

  @Before
  public void setUp() throws Exception {
    classes = ImmutableList.of(
        ForceInline.class,
        NeverInline.class,
        StringValueOfTestMain.class,
        StringValueOfTestMain.Itf.class,
        StringValueOfTestMain.Foo.class);
    testForJvm().addTestClasspath().run(main).assertSuccessWithOutput(javaOutput);
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
    ClassSubject mainClass = codeInspector.clazz(main);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    long count = countStringValueOf(mainMethod);
    assertEquals(expectedStringValueOfCount, count);
    count = countConstNullNumber(mainMethod);
    assertEquals(expectedNullCount, count);
    count = countNullStringNumber(mainMethod);
    assertEquals(expectedNullStringCount, count);
  }

  @Test
  public void testD8() throws Exception {
    if (backend == Backend.CF) {
      return;
    }

    TestRunResult result = testForD8()
        .release()
        .addProgramClasses(classes)
        .run(main)
        .assertSuccessWithOutput(javaOutput);
    test(result, 1, 1, 1);

    result = testForD8()
        .debug()
        .addProgramClasses(classes)
        .run(main)
        .assertSuccessWithOutput(javaOutput);
    test(result, 3, 1, 0);
  }

  @Test
  public void testR8() throws Exception {
    TestRunResult result = testForR8(backend)
        .addProgramClasses(classes)
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addKeepMainRule(main)
        .addKeepRules("-dontobfuscate")
        .run(main)
        .assertSuccessWithOutput(javaOutput);
    test(result, 1, 1, 1);
  }
}
