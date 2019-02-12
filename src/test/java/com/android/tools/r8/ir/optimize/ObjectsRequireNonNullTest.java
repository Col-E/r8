// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.VmTestRunner;
import com.android.tools.r8.VmTestRunner.IgnoreIfVmOlderThan;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.Streams;
import java.util.Objects;
import org.junit.Test;
import org.junit.runner.RunWith;

class ObjectsRequireNonNullTestMain {

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
      unknownArg(null);
    } catch (NullPointerException npe) {
      System.out.println("Expected NPE");
    }
  }
}

@RunWith(VmTestRunner.class)
public class ObjectsRequireNonNullTest extends TestBase {
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "Foo::toString",
      "Foo::bar",
      "Foo::bar",
      "Expected NPE"
  );
  private static final Class<?> MAIN = ObjectsRequireNonNullTestMain.class;

  @Test
  public void testJvmOutput() throws Exception {
    testForJvm().addTestClasspath().run(MAIN).assertSuccessWithOutput(JAVA_OUTPUT);
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

  private void test(TestRunResult result, int expectedCount) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    long count = countObjectsRequireNonNull(mainMethod);
    assertEquals(expectedCount, count);

    MethodSubject unknownArg = mainClass.uniqueMethodWithName("unknownArg");
    assertThat(unknownArg, isPresent());
    // Due to the nullable argument, requireNonNull should remain.
    assertEquals(1, countObjectsRequireNonNull(unknownArg));
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V4_4_4)
  public void testD8() throws Exception {
    TestRunResult result = testForD8()
        .debug()
        .addProgramClassesAndInnerClasses(MAIN)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 2);

    result = testForD8()
        .release()
        .addProgramClassesAndInnerClasses(MAIN)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 0);
  }

  @Test
  @IgnoreIfVmOlderThan(Version.V4_4_4)
  public void testR8() throws Exception {
    // CF disables move result optimization.
    TestRunResult result = testForR8(Backend.DEX)
        .addProgramClassesAndInnerClasses(MAIN)
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 0);
  }
}
