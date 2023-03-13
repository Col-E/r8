// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.interfacetargets;

import static org.junit.Assume.assumeTrue;
import static org.objectweb.asm.Opcodes.INVOKEINTERFACE;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeInterfaceWithStaticTargetTest extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeInterfaceWithStaticTargetTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(A.class, I.class)
                .addClassProgramData(transformMain())
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(I.class, "bar", appInfo.dexItemFactory());
    DexProgramClass context =
        appView.definitionForProgramType(buildType(Main.class, appInfo.dexItemFactory()));
    Assert.assertThrows(
        AssertionError.class,
        () ->
            appInfo
                .resolveMethodOnInterfaceHolderLegacy(method)
                .lookupVirtualDispatchTargets(context, appView));
  }

  @Test
  public void testRuntimeClInit()
      throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters)
        .addProgramClasses(A.class, I.class)
        .addProgramClassFileData(transformMain())
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  @Test
  public void testR8ClInit() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, I.class)
        .addProgramClassFileData(transformMain())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertFailureWithErrorThatThrows(IncompatibleClassChangeError.class);
  }

  private byte[] transformMain() throws IOException {
    return transformer(Main.class)
        .transformMethodInsnInMethod(
            "callFooBar",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (name.equals("notify")) {
                continuation.visitMethodInsn(
                    INVOKEINTERFACE,
                    DescriptorUtils.getBinaryNameFromJavaType(I.class.getTypeName()),
                    "bar",
                    descriptor,
                    true);
              } else {
                continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  public interface I {

    void foo();

    static void bar() {
      System.out.println("I.bar");
    }
  }

  public static class A implements I {

    @Override
    public void foo() {
      System.out.println("A.foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      callFooBar(args.length == 0 ? () -> System.out.println("Lambda.foo") : new A());
    }

    public static void callFooBar(I i) {
      i.foo();
      i.notify(); // <-- will be i.bar()
    }
  }
}
