// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.methodhandles;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticType;
import static com.android.tools.r8.references.Reference.classFromClass;
import static com.android.tools.r8.references.Reference.methodFromMethod;
import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.errors.UnsupportedFeatureDiagnostic;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.StringUtils;
import java.lang.invoke.MethodHandle;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Handle;
import org.objectweb.asm.Opcodes;

/**
 * Test that unrepresentable MethodHandle invokes are replaced by throwing instructions. See
 * b/174733673.
 */
@RunWith(Parameterized.class)
public class InvokeMethodHandleRuntimeErrorTest extends TestBase {

  private static final String EXPECTED = StringUtils.lines("I.target");

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeMethodHandleRuntimeErrorTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  private boolean hasCompileSupport() {
    return parameters.getApiLevel().isGreaterThanOrEqualTo(apiLevelWithConstMethodHandleSupport());
  }

  @Test
  public void testReference() throws Throwable {
    parameters.assumeJvmTestParameters();
    testForJvm(parameters)
        .addProgramClasses(Main.class, I.class, Super.class)
        .addProgramClassFileData(getInvokeCustomTransform())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutput(EXPECTED);
  }

  @Test
  public void testD8() throws Throwable {
    assumeTrue(parameters.isDexRuntime());
    testForD8()
        .addProgramClasses(Main.class, I.class, Super.class)
        .addProgramClassFileData(getInvokeCustomTransform())
        .setMinApi(parameters)
        .compileWithExpectedDiagnostics(
            diagnostics -> {
              if (hasCompileSupport()) {
                diagnostics.assertNoMessages();
              } else {
                diagnostics
                    .assertAllWarningsMatch(diagnosticType(UnsupportedFeatureDiagnostic.class))
                    .assertOnlyWarnings();
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .applyIf(
            hasCompileSupport(),
            r -> r.assertSuccessWithOutput(EXPECTED),
            r ->
                r.assertFailureWithErrorThatThrows(RuntimeException.class)
                    .assertStderrMatches(containsString("const-method-handle")));
  }

  private static byte[] getInvokeCustomTransform() throws Throwable {
    ClassReference symbolicHolder = classFromClass(InvokeCustom.class);
    MethodReference method = methodFromMethod(InvokeCustom.class.getMethod("target"));
    return transformer(InvokeCustom.class)
        .transformMethodInsnInMethod(
            "test",
            (opcode, owner, name, descriptor, isInterface, visitor) -> {
              // Replace null argument by a const method handle.
              visitor.visitInsn(Opcodes.POP);
              visitor.visitLdcInsn(
                  new Handle(
                      Opcodes.H_INVOKEVIRTUAL,
                      symbolicHolder.getBinaryName(),
                      method.getMethodName(),
                      method.getMethodDescriptor(),
                      false));
              visitor.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
            })
        .transform();
  }

  interface I {
    default void target() {
      System.out.println("I.target");
    }
  }

  static class Super implements I {}

  static class InvokeCustom extends Super {

    public static void doInvoke(MethodHandle handle) throws Throwable {
      handle.invoke(new InvokeCustom());
    }

    public static void test() throws Throwable {
      doInvoke(null /* will be const method handle */);
    }
  }

  static class Main {

    public static void main(String[] args) throws Throwable {
      InvokeCustom.test();
    }
  }
}
