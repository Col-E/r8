// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static junit.framework.TestCase.assertTrue;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assume.assumeTrue;
import static org.objectweb.asm.Opcodes.INVOKESPECIAL;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import java.io.IOException;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InvokeSuperCallInStaticTest extends TestBase {

  private static final String[] EXPECTED = new String[] {"Base.collect()"};

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public InvokeSuperCallInStaticTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(Base.class, Main.class)
                .addClassProgramData(getAWithRewrittenInvokeSpecialToBase())
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(Base.class, "collect", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    assertTrue(resolutionResult.isSingleResolution());
    DexProgramClass context =
        appView.definitionForProgramType(buildType(A.class, appInfo.dexItemFactory()));
    DexClassAndMethod lookedUpMethod =
        resolutionResult.lookupInvokeSuperTarget(context, appView.appInfo());
    assertNotNull(lookedUpMethod);
    assertEquals(lookedUpMethod.getReference(), method);
  }

  @Test
  public void testRuntime() throws IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters)
        .addProgramClasses(Base.class, Main.class)
        .addProgramClassFileData(getAWithRewrittenInvokeSpecialToBase())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8() throws IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(Base.class, Main.class)
        .addProgramClassFileData(getAWithRewrittenInvokeSpecialToBase())
        .addKeepMainRule(Main.class)
        .allowAccessModification()
        .setMinApi(parameters)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED)
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(A.class), not(isPresent()));
            });
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

  public static class Base {

    public void collect() {
      System.out.println("Base.collect()");
    }
  }

  public static class A extends Base {

    @Override
    public void collect() {
      System.out.println("A.collect()");
    }

    public static void callSuper(A a) {
      a.collect(); // Will be rewritten from invoke-virtual to invoke-special Base.collect();
    }
  }

  public static class Main {

    public static void main(String[] args) {
      A.callSuper(new A());
    }
  }
}
