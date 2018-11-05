// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ForceInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.List;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class StringLengthTestMain {

  @ForceInline
  static String simpleInlinable() {
    return "Shared";
  }

  @NeverInline
  static int npe() {
    String n = null;
    // Cannot be computed at compile time.
    return n.length();
  }

  public static void main(String[] args) {
    String s1 = "GONE";
    // Can be computed at compile time: constCount++
    System.out.println(s1.length());

    String s2 = simpleInlinable();
    // Depends on inlining: constCount++
    System.out.println(s2.length());
    String s3 = simpleInlinable();
    System.out.println(s3);

    String s4 = "Another_shared";
    // Can be computed at compile time: constCount++
    System.out.println(s4.length());
    System.out.println(s4);

    String s5 = "\uD800\uDC00";  // U+10000
    // Can be computed at compile time: constCount++
    System.out.println(s5.length());
    // Even reusable: should not increase any counts.
    System.out.println(s5.codePointCount(0, s5.length()));
    System.out.println(s5);

    // Make sure this is not optimized in DEBUG mode.
    int l = "ABC".length();
    System.out.println(l);

    try {
      npe();
    } catch (NullPointerException npe) {
      // expected
    }
  }
}

@RunWith(Parameterized.class)
public class StringLengthTest extends TestBase {
  private final Backend backend;
  private List<Class<?>> classes;
  private static String javaOutput;
  private static Class<?> main;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public StringLengthTest(Backend backend) {
    this.backend = backend;
  }

  @BeforeClass
  public static void buildExpectedJavaOutput() {
    javaOutput = StringUtils.lines(
        "4",
        "6",
        "Shared",
        "14",
        "Another_shared",
        "2",
        "1",
        "𐀀",
        "3"
    );
    main = StringLengthTestMain.class;
  }

  @Before
  public void setUp() throws Exception {
    classes = ImmutableList.of(ForceInline.class, NeverInline.class, StringLengthTestMain.class);
    testForJvm().addTestClasspath().run(main).assertSuccessWithOutput(javaOutput);
  }

  private static boolean isStringLength(DexMethod method) {
    return method.getHolder().toDescriptorString().equals("Ljava/lang/String;")
        && method.getArity() == 0
        && method.proto.returnType.isIntType()
        && method.name.toString().equals("length");
  }

  private long countStringLength(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isStringLength(instructionSubject.getMethod());
      }
      return false;
    })).count();
  }

  private long countNonZeroConstNumber(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(InstructionSubject::isConstNumber)).count()
        - Streams.stream(method.iterateInstructions(instr -> instr.isConstNumber(0))).count();
  }

  private void test(
      TestRunResult result, int expectedStringLengthCount, int expectedConstNumberCount)
      throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(main);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    long count = countStringLength(mainMethod);
    assertEquals(expectedStringLengthCount, count);
    count = countNonZeroConstNumber(mainMethod);
    assertEquals(expectedConstNumberCount, count);
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
    test(result, 1, 4);

    result = testForD8()
        .debug()
        .addProgramClasses(classes)
        .run(main)
        .assertSuccessWithOutput(javaOutput);
    test(result, 6, 0);
  }

  @Test
  public void testR8() throws Exception {
    TestRunResult result = testForR8(backend)
        .addProgramClasses(classes)
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addKeepMainRule(main)
        .run(main)
        .assertSuccessWithOutput(javaOutput);
    // TODO we could remove const counting if it needs to be changed too frequently, since
    // the string length count is what we're interested in.
    test(result, 0, backend == Backend.DEX ? 5 : 6);
  }
}
