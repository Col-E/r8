// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging.vertical;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class VerticalClassMergerSuperCallInStaticTest extends TestBase {

  private static final String[] EXPECTED = new String[] {"A.collect()", "Base.collect()"};

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public VerticalClassMergerSuperCallInStaticTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testRuntime() throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters)
        .addProgramClasses(Base.class, B.class, Main.class)
        .addProgramClassFileData(getAWithRewrittenInvokeSpecialToBase())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(Base.class, B.class, Main.class)
        .addProgramClassFileData(getAWithRewrittenInvokeSpecialToBase())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(inspector -> assertThat(inspector.clazz(A.class), not(isPresent())));
  }

  private byte[] getAWithRewrittenInvokeSpecialToBase() throws IOException {
    return transformer(A.class)
        .transformMethodInsnInMethod(
            "callSuper",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              continuation.visitMethodInsn(
                  INVOKESPECIAL,
                  DescriptorUtils.getBinaryNameFromJavaType(Base.class.getTypeName()),
                  name,
                  descriptor,
                  false);
            })
        .transform();
  }

  @NoVerticalClassMerging
  public static class Base {

    public void collect() {
      System.out.println("Base.collect()");
    }
  }

  public static class A extends Base {

    @Override
    @NeverInline
    public void collect() {
      System.out.println("A.collect()");
    }

    @NeverInline
    public static void callSuper(A a) {
      a.collect(); // Will be rewritten from invoke-virtual to invoke-special Base.collect();
    }
  }

  @NeverClassInline
  public static class B extends A {

    @NeverInline
    public void bar() {
      collect();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      B b = new B();
      b.bar();
      A.callSuper(b);
    }
  }
}
