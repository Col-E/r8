// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.invokespecial;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticMessage;
import static com.android.tools.r8.utils.DescriptorUtils.getBinaryNameFromJavaType;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.StringUtils;
import java.io.IOException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class InvokeSpecialToVirtualMethodTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("Bar::foo", "Foo::foo", "Foo::foo", "Foo::foo");

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public InvokeSpecialToVirtualMethodTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testJvm() throws Exception {
    assumeTrue(parameters.isCfRuntime());
    testForJvm()
        .addProgramClasses(Base.class, Bar.class, TestClass.class)
        .addProgramClassFileData(getFooTransform())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }


  @Test(expected = CompilationFailedException.class)
  public void testD8() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClasses(Base.class, Bar.class, TestClass.class)
        .addProgramClassFileData(getFooTransform())
        .setMinApi(parameters.getApiLevel())
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertOnlyErrors()
                    .assertErrorsMatch(
                        diagnosticMessage(containsString("unsupported use of invokespecial"))));
  }

  @Test(expected = CompilationFailedException.class)
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(Base.class, Bar.class, TestClass.class)
        .addProgramClassFileData(getFooTransform())
        .setMinApi(parameters.getApiLevel())
        .addKeepMainRule(TestClass.class)
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertOnlyErrors()
                    .assertErrorsMatch(
                        diagnosticMessage(containsString("unsupported use of invokespecial"))));
  }

  @Test
  public void testDX() throws Exception {
    assumeTrue(parameters.isDexRuntime());
    testForDX()
        .addProgramClasses(Base.class, Bar.class, TestClass.class)
        .addProgramClassFileData(getFooTransform())
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatMatches(containsString(getExpectedOutput()));
  }

  private String getExpectedOutput() {
    if (parameters.getRuntime().asDex().getVm().getVersion().isOlderThanOrEqual(Version.V4_4_4)) {
      return "VFY: unable to resolve direct method";
    }
    return "was expected to be of type direct but instead was found to be of type virtual";
  }

  private byte[] getFooTransform() throws IOException {
    return transformer(Foo.class)
        .transformMethodInsnInMethod(
            "foo",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (Opcodes.INVOKESPECIAL == opcode) {
                assertEquals(getBinaryNameFromJavaType(Base.class.getName()), owner);
                assertEquals("foo", name);
                visitor.visitMethodInsn(
                    opcode,
                    getBinaryNameFromJavaType(Foo.class.getName()),
                    name,
                    descriptor,
                    isInterface);
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  static class Base {
    // Base method is never hit.
    public void foo(int i) {
      System.out.println("Base::foo");
    }
  }

  static class Foo extends Base {

    public void foo(int i) {
      if (i > 0) {
        System.out.println("Foo::foo");
        // Will be converted to invoke-special Foo.foo.
        super.foo(i - 1);
      }
    }
  }

  static class Bar extends Foo {

    // Subclass override is only hit initially.
    @Override
    public void foo(int i) {
      System.out.println("Bar::foo");
      super.foo(3);
    }
  }

  static class TestClass {

    public static void main(String[] args) {
      new Bar().foo(0);
    }
  }
}
