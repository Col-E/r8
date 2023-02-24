// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.VerticallyMergedClassesInspector;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergerSuperToLibraryTest extends TestBase {

  private static final String[] EXPECTED = new String[] {"B::foo", "Lib::foo"};

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public VerticalClassMergerSuperToLibraryTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(B.class, Main.class)
        .addProgramClassFileData(getAWithRewrittenInvokeSpecialToBase())
        .addLibraryClasses(LibParent.class)
        .addLibraryClassFileData(
            transformer(LibWithFoo.class).setClassDescriptor(descriptor(Lib.class)).transform())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testD8() throws Exception {
    parameters.assumeDexRuntime();
    testForD8()
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(Lib.class, LibParent.class)
        .addProgramClasses(B.class, Main.class)
        .addProgramClassFileData(getAWithRewrittenInvokeSpecialToBase())
        .setMinApi(parameters)
        .compile()
        .addBootClasspathFiles(
            buildOnDexRuntime(
                parameters,
                transformer(LibParent.class).transform(),
                transformer(LibWithFoo.class)
                    .setClassDescriptor(descriptor(Lib.class))
                    .transform()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addDefaultRuntimeLibrary(parameters)
        .addLibraryClasses(Lib.class, LibParent.class)
        .addProgramClasses(B.class, Main.class)
        .addProgramClassFileData(getAWithRewrittenInvokeSpecialToBase())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .setMinApi(parameters)
        .addVerticallyMergedClassesInspector(
            VerticallyMergedClassesInspector::assertNoClassesMerged)
        .compile()
        .addBootClasspathFiles(
            buildOnDexRuntime(
                parameters,
                transformer(LibParent.class).transform(),
                transformer(LibWithFoo.class)
                    .setClassDescriptor(descriptor(Lib.class))
                    .transform()))
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(inspector -> assertThat(inspector.clazz(A.class), isPresent()));
  }

  private byte[] getAWithRewrittenInvokeSpecialToBase() throws Exception {
    return transformer(A.class)
        .transformMethodInsnInMethod(
            "callSuper",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (name.equals("foo")) {
                continuation.visitMethodInsn(
                    INVOKESPECIAL,
                    DescriptorUtils.getBinaryNameFromJavaType(A.class.getTypeName()),
                    name,
                    descriptor,
                    false);
              } else {
                continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  public static class LibParent {

    public void foo() {
      System.out.println("LibParent::foo");
    }
  }

  public static class Lib extends LibParent {}

  /* Will be Lib when passed in at boot classpath */
  public static class LibWithFoo extends LibParent {

    @Override
    public void foo() {
      System.out.println("Lib::foo");
    }
  }

  public static class A extends Lib {

    @NeverInline
    public static void callSuper(A a) {
      a.foo(); // Will be rewritten from invoke-virtual to invoke-special A.foo();
    }
  }

  @NeverClassInline
  public static class B extends A {

    @Override
    public void foo() {
      System.out.println("B::foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      B b = new B();
      b.foo();
      A.callSuper(b);
    }
  }
}
