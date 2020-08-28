// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.desugaring.interfacemethods;

import static org.junit.Assert.assertFalse;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class StaticInterfaceMethodReferenceTest extends TestBase {

  private final TestParameters parameters;
  private final boolean isInterface;

  @Parameterized.Parameters(name = "{0}, itf:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevelsAlsoForCf().build(),
        BooleanUtils.values());
  }

  public StaticInterfaceMethodReferenceTest(TestParameters parameters, boolean isInterface) {
    this.parameters = parameters;
    this.isInterface = isInterface;
  }

  @Test
  public void test() throws Exception {
    TestRunResult<?> result =
        testForDesugaring(parameters, o -> o.testing.allowInvokeErrors = true)
            .addProgramClasses(TestClass.class)
            .addProgramClassFileData(getTarget(isInterface))
            .run(parameters.getRuntime(), TestClass.class);
    checkResult(result);
  }

  @Test
  public void testTargetMissing() throws Exception {
    TestRunResult<?> result =
        testForDesugaring(parameters, o -> o.testing.allowInvokeErrors = true)
            .addProgramClasses(TestClass.class)
            .addRunClasspathFiles(buildOnDexRuntime(parameters, getTarget(isInterface)))
            .run(parameters.getRuntime(), TestClass.class);
    // Missing target will cause the call to remain as Target::foo rather than Target$-CC::foo.
    // TODO(b/166726895): Support static interface invoke as no knowledge of Target is needed.
    if (isInterface
        && parameters.isDexRuntime()
        && parameters.getApiLevel().isLessThan(apiLevelWithStaticInterfaceMethodsSupport())) {
      result.assertFailureWithErrorThatThrows(
          parameters
                  .getRuntime()
                  .asDex()
                  .getVm()
                  .getVersion()
                  .isOlderThanOrEqual(DexVm.Version.V4_4_4)
              // On <= 4.4.4 a verify error happens due to the static invoke on an interface.
              ? VerifyError.class
              : NoSuchMethodError.class);
      return;
    }
    checkResult(result);
  }

  private void checkResult(TestRunResult<?> result) {
    // If the reference is of correct type, or running on JDK8 which allows mismatch the
    // output is the expected print.
    if (isInterface
        || (parameters.isCfRuntime() && parameters.getRuntime().asCf().getVm() == CfVm.JDK8)) {
      result.assertSuccessWithOutputLines("Target::foo");
      return;
    }

    // DEX runtimes do not check method reference types so the code will run.
    // TODO(b/166732606): Compile to an ICCE? What about the "missing" target case?
    assertFalse(isInterface);
    if (parameters.isDexRuntime()) {
      result.assertSuccessWithOutputLines("Target::foo");
      return;
    }

    // Otherwise the run should result in an ICCE.
    result.assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  private static byte[] getTarget(boolean isInterface) throws Exception {
    return transformer(Target.class)
        .setAccessFlags(
            classAccessFlags -> {
              if (isInterface) {
                assert !classAccessFlags.isSuper();
                assert classAccessFlags.isAbstract();
                assert classAccessFlags.isInterface();
              } else {
                classAccessFlags.unsetAbstract();
                classAccessFlags.unsetInterface();
              }
            })
        .transform();
  }

  interface Target {

    static void foo() {
      System.out.println("Target::foo");
    }
  }

  static class TestClass {

    static void call(Runnable fn) {
      fn.run();
    }

    public static void main(String[] args) {
      call(Target::foo);
    }
  }
}
