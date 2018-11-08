// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.reflection;

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
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Streams;
import java.util.List;
import java.util.concurrent.Callable;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

class GetClassTestMain implements Callable<Class<?>> {
  static class Base {}
  static class Sub extends Base {}
  static class EffectivelyFinal {}

  static class Reflection implements Callable<Class<?>> {
    @ForceInline
    @Override
    public Class<?> call() {
      return getClass();
    }
  }

  @NeverInline
  static Class<?> getMainClass(GetClassTestMain instance) {
    // Nullable argument. Should not be rewritten to const-class to preserve NPE.
    return instance.getClass();
  }

  @NeverInline
  @Override
  public Class<?> call() {
    // Non-null `this` pointer.
    return getClass();
  }

  public static void main(String[] args) {
    {
      Base base = new Base();
      // Not applicable in debug mode.
      System.out.println(base.getClass());
      // Can be rewritten to const-class always.
      System.out.println(new Base().getClass());
    }

    {
      Base sub = new Sub();
      // Not applicable in debug mode.
      System.out.println(sub.getClass());
    }

    {
      Base[] subs = new Sub[1];
      // Not applicable in debug mode.
      System.out.println(subs.getClass());
    }

    {
      EffectivelyFinal ef = new EffectivelyFinal();
      // Not applicable in debug mode.
      System.out.println(ef.getClass());
    }

    try {
      // To not be recognized as un-instantiated class.
      GetClassTestMain instance = new GetClassTestMain();
      System.out.println(instance.call());
      System.out.println(getMainClass(instance));

      System.out.println(getMainClass(null));
      fail("Should preserve NPE.");
    } catch (NullPointerException e) {
      // Expected
    }

    {
      Reflection r = new Reflection();
      // Not applicable in debug mode.
      System.out.println(r.getClass());
      try {
        // Can be rewritten to const-class after inlining.
        System.out.println(r.call());
      } catch (Throwable e) {
        fail("Not expected any exceptions.");
      }
    }
  }
}

@RunWith(Parameterized.class)
public class GetClassTest extends TestBase {
  private final Backend backend;
  private static final List<Class<?>> CLASSES = ImmutableList.of(
      ForceInline.class,
      NeverInline.class,
      GetClassTestMain.class,
      GetClassTestMain.Base.class,
      GetClassTestMain.Sub.class,
      GetClassTestMain.EffectivelyFinal.class,
      GetClassTestMain.Reflection.class
  );
  private static final String JAVA_OUTPUT = StringUtils.lines(
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain$Base",
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain$Base",
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain$Sub",
      "class [Lcom.android.tools.r8.ir.optimize.reflection.GetClassTestMain$Sub;",
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain$EffectivelyFinal",
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain",
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain",
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain$Reflection",
      "class com.android.tools.r8.ir.optimize.reflection.GetClassTestMain$Reflection"
  );
  private static final Class<?> MAIN = GetClassTestMain.class;

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Backend[] data() {
    return Backend.values();
  }

  public GetClassTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void testJVMoutput() throws Exception {
    assumeTrue("Only run JVM reference once (for CF backend)", backend == Backend.CF);
    testForJvm().addTestClasspath().run(MAIN).assertSuccessWithOutput(JAVA_OUTPUT);
  }

  private static boolean isGetClass(DexMethod method) {
    return method.getArity() == 0
        && method.proto.returnType.toDescriptorString().equals("Ljava/lang/Class;")
        && method.name.toString().equals("getClass");
  }

  private long countGetClass(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(instructionSubject -> {
      if (instructionSubject.isInvoke()) {
        return isGetClass(instructionSubject.getMethod());
      }
      return false;
    })).count();
  }

  private long countConstClass(MethodSubject method) {
    return Streams.stream(method.iterateInstructions(InstructionSubject::isConstClass)).count();
  }

  private void test(
      TestRunResult result,
      int expectedGetClassCount,
      int expectedConstClassCount,
      int expectedGetClassCountForCall,
      int expectedConstClassCountForCall) throws Exception {
    CodeInspector codeInspector = result.inspector();
    ClassSubject mainClass = codeInspector.clazz(MAIN);
    MethodSubject mainMethod = mainClass.mainMethod();
    assertThat(mainMethod, isPresent());
    assertEquals(expectedGetClassCount, countGetClass(mainMethod));
    assertEquals(expectedConstClassCount, countConstClass(mainMethod));

    MethodSubject getMainClass = mainClass.method(
        "java.lang.Class", "getMainClass", ImmutableList.of(MAIN.getCanonicalName()));
    assertThat(getMainClass, isPresent());
    // Because of nullable argument, getClass() should reMAIN.
    assertEquals(1, countGetClass(getMainClass));
    assertEquals(0, countConstClass(getMainClass));

    MethodSubject call = mainClass.method("java.lang.Class", "call", ImmutableList.of());
    assertThat(call, isPresent());
    // Because of local, only R8 release mode can rewrite getClass() to const-class.
    assertEquals(expectedGetClassCountForCall, countGetClass(call));
    assertEquals(expectedConstClassCountForCall, countConstClass(call));
  }

    @Test
  public void testD8() throws Exception {
    assumeTrue("Only run D8 for Dex backend", backend == Backend.DEX);

    // D8 debug.
    TestRunResult result = testForD8()
        .debug()
        .addProgramClasses(CLASSES)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 6, 0, 1, 0);

    // D8 release.
    result = testForD8()
        .release()
        .addProgramClasses(CLASSES)
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 6, 0, 1, 0);
  }

  @Test
  public void testR8() throws Exception {
    // R8 debug, no minification.
    TestRunResult result = testForR8(backend)
        .debug()
        .addProgramClasses(CLASSES)
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-dontobfuscate")
        .run(MAIN);
    test(result, 5, 1, 1, 0);

    // R8 release, no minification.
    result = testForR8(backend)
        .addProgramClasses(CLASSES)
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        .addKeepRules("-dontobfuscate")
        .run(MAIN)
        .assertSuccessWithOutput(JAVA_OUTPUT);
    test(result, 0, 7, 0, 1);

    // R8 release, minification.
    result = testForR8(backend)
        .addProgramClasses(CLASSES)
        .enableProguardTestOptions()
        .enableInliningAnnotations()
        .addKeepMainRule(MAIN)
        // We are not checking output because it can't be matched due to minification. Just run.
        .run(MAIN);
    test(result, 0, 7, 0, 1);
  }

}
