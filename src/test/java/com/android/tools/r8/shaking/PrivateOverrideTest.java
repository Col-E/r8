// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isRenamed;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.nio.file.Path;
import java.util.Collection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PrivateOverrideTest extends TestBase {

  private static Class<?> main = PrivateOverrideTestClass.class;
  private static Class<?> A = PrivateOverrideTestClass.A.class;
  private static Class<?> B = PrivateOverrideTestClass.B.class;
  private static Class<?> C = PrivateOverrideTestClass.C.class;

  private final Backend backend;
  private final boolean enableClassInlining;
  private final boolean enableVerticalClassMerging;

  @Parameterized.Parameters(name = "Backend: {0}, class inlining: {1}, vertical class merging: {2}")
  public static Collection<Object[]> data() {
    ImmutableList.Builder<Object[]> builder = ImmutableList.builder();
    for (Backend backend : Backend.values()) {
      // TODO(b/116441307): Class inliner does not preserve IllegalAccessError/IncompatibleClass-
      // ChangeError.
      for (boolean enableClassInlining : ImmutableList.of(false)) {
        for (boolean enableVerticalClassMerging : ImmutableList.of(true, false)) {
          builder.add(new Object[] {backend, enableClassInlining, enableVerticalClassMerging});
        }
      }
    }
    return builder.build();
  }

  public PrivateOverrideTest(
      Backend backend, boolean enableClassInlining, boolean enableVerticalClassMerging) {
    this.backend = backend;
    this.enableClassInlining = enableClassInlining;
    this.enableVerticalClassMerging = enableVerticalClassMerging;
  }

  @Test
  public void test() throws Exception {
    // Construct B such that it inherits from A and shadows method A.m() with a private method.
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass(B.getName(), A.getName());
    classBuilder.addDefaultConstructor();
    for (String methodName : ImmutableList.of("m1", "m2")) {
      classBuilder.addPrivateVirtualMethod(
          methodName, ImmutableList.of(), "V", jasminCodeForPrinting("In B." + methodName + "()"));
    }
    for (String methodName : ImmutableList.of("m3", "m4")) {
      classBuilder.addStaticMethod(
          methodName, ImmutableList.of(), "V", jasminCodeForPrinting("In B." + methodName + "()"));
    }

    AndroidApp input =
        AndroidApp.builder()
            .addProgramFiles(
                ToolHelper.getClassFileForTestClass(main),
                ToolHelper.getClassFileForTestClass(A),
                ToolHelper.getClassFileForTestClass(C))
            .addClassProgramData(jasminBuilder.buildClasses())
            .build();

    // Run the program using java.
    Path referenceJar = temp.getRoot().toPath().resolve("input.jar");
    ArchiveConsumer inputConsumer = new ArchiveConsumer(referenceJar);
    for (Class<?> clazz : ImmutableList.of(main, A, C)) {
      inputConsumer.accept(
          ByteDataView.of(ToolHelper.getClassAsBytes(clazz)),
          DescriptorUtils.javaTypeToDescriptor(clazz.getName()),
          null);
    }
    inputConsumer.accept(
        ByteDataView.of(jasminBuilder.buildClasses().get(0)),
        DescriptorUtils.javaTypeToDescriptor(B.getName()),
        null);
    inputConsumer.finished(null);

    ProcessResult referenceResult = ToolHelper.runJava(referenceJar, main.getName());
    assertEquals(referenceResult.exitCode, 0);

    // Run the program on Art after is has been compiled with R8.
    AndroidApp compiled =
        compileWithR8(
            input,
            keepMainProguardConfiguration(main),
            options -> {
              options.enableClassInlining = enableClassInlining;
              options.enableVerticalClassMerging = enableVerticalClassMerging;
              options.testing.validInliningReasons = ImmutableSet.of(Reason.FORCE);
            },
            backend);
    assertEquals(referenceResult.stdout, runOnVM(compiled, main, backend));

    // Check that B is present and that it doesn't contain the unused private method m2.
    if (!enableClassInlining && !enableVerticalClassMerging) {
      CodeInspector inspector = new CodeInspector(compiled);
      ClassSubject classSubject = inspector.clazz(B.getName());
      assertThat(classSubject, isRenamed());
      assertThat(classSubject.method("void", "m1", ImmutableList.of()), isPresent());
      assertThat(classSubject.method("void", "m2", ImmutableList.of()), not(isPresent()));
      assertThat(classSubject.method("void", "m3", ImmutableList.of()), isPresent());
      assertThat(classSubject.method("void", "m4", ImmutableList.of()), not(isPresent()));
    }
  }

  private static String[] jasminCodeForPrinting(String message) {
    return new String[] {
      ".limit stack 2",
      ".limit locals 1",
      "getstatic java/lang/System/out Ljava/io/PrintStream;",
      "ldc \"" + message + "\"",
      "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
      "return"
    };
  }
}

class PrivateOverrideTestClass {

  public static void main(String[] args) {
    A a = new B();
    a.m1();
    a.m2();
    a.m3();
    a.m4();

    a = new C();
    a.m1();
    a.m2();
    a.m3();
    a.m4();

    B b = new B();
    try {
      b.m1();
    } catch (IllegalAccessError exception) {
      System.out.println("Caught IllegalAccessError when calling B.m1()");
    }
    try {
      b.m3();
    } catch (IncompatibleClassChangeError exception) {
      System.out.println("Caught IncompatibleClassChangeError when calling B.m3()");
    }

    try {
      b = new C();
      b.m1();
    } catch (IllegalAccessError exception) {
      System.out.println("Caught IllegalAccessError when calling B.m1()");
    }
    try {
      b = new C();
      b.m3();
    } catch (IncompatibleClassChangeError exception) {
      System.out.println("Caught IncompatibleClassChangeError when calling B.m3()");
    }

    C c = new C();
    c.m1();
    c.m3();
  }

  static class A {

    public void m1() {
      System.out.println("In A.m1()");
    }

    public void m2() {
      System.out.println("In A.m2()");
    }

    public void m3() {
      System.out.println("In A.m3()");
    }

    public void m4() {
      System.out.println("In A.m4()");
    }
  }

  static class B extends A {

    // Will be made private with Jasmin. This method is targeted and can therefore not be removed.
    public void m1() {
      System.out.println("In B.m1()");
    }

    // Will be made private with Jasmin. Ends up being dead code because the method is never called.
    public void m2() {
      System.out.println("In B.m2()");
    }

    // Will be made static with Jasmin. This method is targeted and can therefore not be removed.
    public void m3() {
      System.out.println("In B.m3()");
    }

    // Will be made static with Jasmin. Ends up being dead code because the method is never called.
    public void m4() {
      System.out.println("In B.m4()");
    }
  }

  static class C extends B {

    public void m1() {
      System.out.println("In C.m1()");
    }

    public void m3() {
      System.out.println("In C.m3()");
    }
  }
}
