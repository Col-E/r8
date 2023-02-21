// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper.DexVm.Version;
import com.android.tools.r8.references.Reference;
import org.hamcrest.Matcher;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class InvokeInterfaceOnClassTest extends TestBase {

  private final TestParameters parameters;

  @Parameterized.Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimes().withAllApiLevels().build();
  }

  public InvokeInterfaceOnClassTest(TestParameters parameters) {
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
    // Old runtimes are implemented to throw the wrong error, so NoSuchMethodError is expected.
    if (isDexVmOlderThanOrEqualTo(Version.V4_4_4)) {
      return containsString("NoSuchMethodError");
    }
    // For 5 and 6, the error is correct, but only as long as the class has a non-abstract method.
    // R8 will not trace the C1.f and C2.f as the resolution of I.f fails. The implementation
    // methods are removed and this again causes the runtime to throw the wrong error.
    if (isR8 && isDexVmOlderThanOrEqualTo(Version.V6_0_1)) {
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

  public abstract static class I {
    public abstract void f();
  }

  public static class C1 extends I {

    @Override
    public void f() {
      System.out.println("C1::f");
    }
  }

  public static class C2 extends I {

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
    String binaryNameForI = Reference.classFromClass(I.class).getBinaryName();
    return transformer(Main.class)
        .transformMethodInsnInMethod(
            "main",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (owner.equals(binaryNameForI) && name.equals("f")) {
                assertEquals(Opcodes.INVOKEVIRTUAL, opcode);
                assertFalse(isInterface);
                continuation.visitMethodInsn(
                    Opcodes.INVOKEINTERFACE, owner, name, descriptor, true);
              } else {
                continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }
}
