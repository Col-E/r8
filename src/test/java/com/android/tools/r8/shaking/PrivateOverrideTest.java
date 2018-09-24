// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.ByteDataView;
import com.android.tools.r8.ClassFileConsumer.ArchiveConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.ToolHelper.ProcessResult;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.jasmin.JasminBuilder.ClassBuilder;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class PrivateOverrideTest extends TestBase {

  private static Class<?> main = PrivateOverrideTestClass.class;
  private static Class<?> A = PrivateOverrideTestClass.A.class;
  private static Class<?> B = PrivateOverrideTestClass.B.class;

  private final Backend backend;

  @Rule public ExpectedException thrown = ExpectedException.none();

  @Parameterized.Parameters(name = "Backend: {0}")
  public static Collection<Backend> data() {
    return Arrays.asList(Backend.values());
  }

  public PrivateOverrideTest(Backend backend) {
    this.backend = backend;
  }

  @Test
  public void test() throws Exception {
    // Construct B such that it inherits from A and shadows method A.m() with a private method.
    JasminBuilder jasminBuilder = new JasminBuilder();
    ClassBuilder classBuilder = jasminBuilder.addClass(B.getName(), A.getName());
    classBuilder.addDefaultConstructor();
    classBuilder.addPrivateVirtualMethod(
        "m",
        ImmutableList.of(),
        "V",
        ".limit stack 2",
        ".limit locals 1",
        "getstatic java/lang/System/out Ljava/io/PrintStream;",
        "ldc \"In B.m()\"",
        "invokevirtual java/io/PrintStream/println(Ljava/lang/String;)V",
        "return");

    AndroidApp input =
        AndroidApp.builder()
            .addProgramFiles(ToolHelper.getClassFileForTestClass(main))
            .addProgramFiles(ToolHelper.getClassFileForTestClass(A))
            .addClassProgramData(jasminBuilder.buildClasses())
            .build();

    // Run the program using java.
    Path referenceJar = temp.getRoot().toPath().resolve("input.jar");
    ArchiveConsumer inputConsumer = new ArchiveConsumer(referenceJar);
    for (Class<?> clazz : ImmutableList.of(main, A)) {
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

    // TODO(b/116093710): Fix tree shaking.
    thrown.expect(AssertionError.class);
    thrown.expectMessage("java.lang.AbstractMethodError");
    thrown.expectMessage("com.android.tools.r8.shaking.PrivateOverrideTestClass$A.m()");

    // Run the program on Art after is has been compiled with R8.
    AndroidApp compiled =
        compileWithR8(
            input,
            keepMainProguardConfiguration(main),
            options -> {
              options.enableMinification = false;
              options.enableVerticalClassMerging = false;
            },
            backend);
    assertEquals(referenceResult.stdout, runOnVM(compiled, main, backend));

    // TODO(b/116093710): Assert that B.m() is removed by tree pruner.
  }
}

class PrivateOverrideTestClass {

  public static void main(String[] args) {
    A b = new B();
    b.m(); // Since B.m() is made private with Jasmin, this should print "In A.m()".
  }

  static class A {

    public void m() {
      System.out.println("In A.m()");
    }
  }

  static class B extends A {

    // Will be made private with Jasmin. Therefore ends up being dead code.
    public void m() {
      System.out.println("In B.m()");
    }
  }
}
