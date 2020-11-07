// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.interfacetargets;

import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRuntime.CfVm;
import com.android.tools.r8.TestRuntime.DexRuntime;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.transformers.ClassTransformer;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.hamcrest.CoreMatchers;
import org.hamcrest.Matcher;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class InvokeInterfaceClInitTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeInterfaceClInitTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.useRuntimeAsNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(A.class, B.class)
                .addClassProgramData(transformI(), transformMain())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(I.class, "<clinit>", appInfo.dexItemFactory());
    DexProgramClass context =
        appView.definitionForProgramType(buildType(Main.class, appInfo.dexItemFactory()));
    Assert.assertThrows(
        AssertionError.class,
        () ->
            appInfo
                .resolveMethodOnInterface(method)
                .lookupVirtualDispatchTargets(context, appInfo));
  }

  private Matcher<String> getExpected() {
    if (parameters.getRuntime().isCf()) {
      Matcher<String> expected = containsString("java.lang.VerifyError");
      // JDK 9 and 11 output VerifyError or ClassFormatError non-deterministically.
      if (parameters.getRuntime().asCf().isNewerThanOrEqual(CfVm.JDK9)) {
        expected = CoreMatchers.anyOf(expected, containsString("java.lang.ClassFormatError"));
      }
      return expected;
    }
    assert parameters.getRuntime().isDex();
    DexRuntime dexRuntime = parameters.getRuntime().asDex();
    if (dexRuntime.getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_TARGET)) {
      return containsString("NoSuchMethodError");
    }
    return containsString("java.lang.VerifyError");
  }

  @Test
  public void testRuntimeClInit()
      throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters)
        .addProgramClasses(A.class, B.class)
        .addProgramClassFileData(transformMain(), transformI())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(getExpected());
  }

  @Test
  public void testR8ClInit() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, B.class)
        .addProgramClassFileData(transformMain(), transformI())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters.getApiLevel())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatMatches(getExpected());
  }

  private byte[] transformI() throws IOException {
    return transformer(I.class)
        .addClassTransformer(
            new ClassTransformer() {
              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                return super.visitMethod(
                    access | Constants.ACC_STATIC, "<clinit>", descriptor, signature, exceptions);
              }
            })
        .transform();
  }

  private byte[] transformMain() throws IOException {
    return transformer(Main.class)
        .transformMethodInsnInMethod(
            "callClInit",
            (opcode, owner, name, descriptor, isInterface, continuation) ->
                continuation.visitMethodInsn(opcode, owner, "<clinit>", descriptor, isInterface))
        .transform();
  }

  public interface I {

    default void foo() { // <-- will be rewritten to <clinit>
      System.out.println("I.foo");
    }
  }

  public static class A implements I {}

  public static class B implements I {}

  public static class Main {

    public static void main(String[] args) {
      callClInit(args.length == 0 ? new A() : new B());
    }

    private static void callClInit(I i) {
      i.foo(); // <-- will be i.<clinit>()
    }
  }
}
