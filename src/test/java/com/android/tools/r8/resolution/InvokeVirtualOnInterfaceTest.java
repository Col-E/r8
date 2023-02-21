// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;
import static org.objectweb.asm.Opcodes.INVOKEVIRTUAL;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.utils.DescriptorUtils;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class InvokeVirtualOnInterfaceTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public InvokeVirtualOnInterfaceTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testReference() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(I.class, C1.class, C2.class)
        .addProgramClassFileData(transformMain())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(getExpectedFailureMatcher(false));
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(I.class, C1.class, C2.class)
        .addProgramClassFileData(transformMain())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .addOptionsModification(options -> options.testing.allowInvokeErrors = true)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(getExpectedFailureMatcher(true));
  }

  private Matcher<String> getExpectedFailureMatcher(boolean isR8) {
    // Old runtimes fail verification outright.
    if (isDexVmOlderThanOrEqualTo(Version.V4_4_4)) {
      return containsString("VerifyError");
    }
    // For 5, 6 and 7, the error is correct, but only if the class has a non-abstract method.
    // R8 will not trace the C1.f and C2.f as the resolution of I.f fails. The implementation
    // methods are removed and this again causes the runtime to throw the wrong error.
    if (isR8 && isDexVmOlderThanOrEqualTo(Version.V7_0_0)) {
      return containsString("NoSuchMethodError");
    }
    return containsString("IncompatibleClassChangeError");
  }

  private boolean isDexVmOlderThanOrEqualTo(Version version) {
    return parameters.getRuntime().isDex()
        && parameters
        .getRuntime()
        .asDex()
        .getVm()
        .getVersion()
        .isOlderThanOrEqual(version);
  }

  public interface I {
    void f();
  }

  public static class C1 implements I {

    @Override
    public void f() {
      System.out.println("C1::f");
    }
  }

  public static class C2 implements I {

    @Override
    public void f() {
      System.out.println("C2::f");
    }
  }

  static class Main {

    public static void main(String[] args) {
      I i = args.length % 2 == 0 ? new C1() : new C2();
      i.f();
    }
  }

  private static byte[] transformMain() throws Exception {
    String binaryNameForI = DescriptorUtils.getBinaryNameFromJavaType(I.class.getTypeName());
    return transformer(Main.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (owner.equals(binaryNameForI) && name.equals("f")) {
                assertEquals(INVOKEINTERFACE, opcode);
                assertTrue(isInterface);
                continuation.visitMethodInsn(INVOKEVIRTUAL, owner, name, descriptor, false);
              } else {
                continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }
}
