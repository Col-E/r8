// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.code.invokedynamic;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.UnsupportedInvokeCustomDiagnostic;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.StringUtils;
import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

/**
 * Test that unrepresentable invoke-dynamic instructions are replaced by throwing instructions. See
 * b/174733673.
 */
@RunWith(Parameterized.class)
public class InvokeCustomRuntimeErrorTest extends TestBase {

  private static final String EXPECTED = StringUtils.lines("A::foo");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeCustomRuntimeErrorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean hasCompileSupport() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithInvokeCustomSupport());
  }

  @Test
  public void testReference() throws Throwable {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(I.class, A.class)
        .addProgramClassFileData(getTransformedTestClass())
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8CfNoDesugaring() throws Throwable {
    assumeTrue(parameters.isCfRuntime());
    // Explicitly test that no-desugaring will maintain a passthrough of the CF code.
    testForD8(parameters.getBackend())
        .addProgramClasses(I.class, A.class)
        .addProgramClassFileData(getTransformedTestClass())
        .setNoMinApi()
        .disableDesugaring()
        .compile()
        .assertNoMessages()
        .run(parameters.getRuntime(), TestClass.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8DexNoDesugaring() throws Throwable {
    assumeTrue(parameters.isDexRuntime() && parameters.getApiLevel().equals(AndroidApiLevel.B));
    // Explicitly test that no-desugaring will still strip instructions.
    testForD8(parameters.getBackend())
        .addProgramClasses(I.class, A.class)
        .addProgramClassFileData(getTransformedTestClass())
        .setMinApi(parameters)
        .disableDesugaring()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics
                    .assertAllWarningsMatch(diagnosticType(UnsupportedInvokeCustomDiagnostic.class))
                    .assertOnlyWarnings())
        .run(parameters.getRuntime(), TestClass.class)
        .assertFailureWithErrorThatThrows(RuntimeException.class)
        .assertStderrMatches(containsString("invoke-dynamic"));
  }

  @Test
  public void testD8() throws Throwable {
    // For CF compilations we desugar to API level B, thus it should always fail.
    AndroidApiLevel minApi =
        parameters.isDexRuntime() ? parameters.getApiLevel() : AndroidApiLevel.B;
    boolean expectedSuccess = parameters.isDexRuntime() && hasCompileSupport();
    testForD8(parameters.getBackend())
        .addProgramClasses(I.class, A.class)
        .addProgramClassFileData(getTransformedTestClass())
        .setMinApi(minApi)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              if (expectedSuccess) {
                diagnostics.assertNoMessages();
              } else {
                diagnostics
                    .assertAllWarningsMatch(diagnosticType(UnsupportedInvokeCustomDiagnostic.class))
                    .assertOnlyWarnings();
              }
            })
        .run(parameters.getRuntime(), TestClass.class)
        .applyIf(
            expectedSuccess,
            r -> r.assertSuccessWithOutput(EXPECTED),
            r ->
                r.assertFailureWithErrorThatThrows(RuntimeException.class)
                    .assertStderrMatches(containsString("invoke-dynamic")));
  }

  private byte[] getTransformedTestClass() throws Exception {
    ClassReference aClass = Reference.classFromClass(A.class);
    MethodReference iFoo = Reference.methodFromMethod(I.class.getDeclaredMethod("foo"));
    MethodReference bsm =
        Reference.methodFromMethod(
            TestClass.class.getDeclaredMethod(
                "bsmCreateCallSite",
                Lookup.class,
                String.class,
                MethodType.class,
                MethodHandle.class));
    return transformer(TestClass.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              if (name.equals("replaced")) {
                visitor.visitInvokeDynamicInsn(
                    iFoo.getMethodName(),
                    "(" + aClass.getDescriptor() + ")V",
                    new Handle(
                        Opcodes.H_INVOKESTATIC,
                        bsm.getHolderClass().getBinaryName(),
                        bsm.getMethodName(),
                        bsm.getMethodDescriptor(),
                        false),
                    new Handle(
                        Opcodes.H_INVOKEVIRTUAL,
                        aClass.getBinaryName(),
                        iFoo.getMethodName(),
                        iFoo.getMethodDescriptor(),
                        false));
              } else {
                visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  public interface I {
    void foo();
  }

  public static class A implements I {

    @Override
    public void foo() {
      System.out.println("A::foo");
    }
  }

  static class TestClass {

    public static CallSite bsmCreateCallSite(
        MethodHandles.Lookup caller, String name, MethodType type, MethodHandle handle) {
      return new ConstantCallSite(handle);
    }

    public static void replaced(Object o) {
      throw new RuntimeException("unreachable!");
    }

    public static void main(String[] args) {
      replaced(new A());
    }
  }
}
