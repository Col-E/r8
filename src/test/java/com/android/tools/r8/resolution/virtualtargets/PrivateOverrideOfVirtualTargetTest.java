// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.virtualtargets;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.resolution.virtualtargets.package_a.A;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.Opcodes;

@RunWith(Parameterized.class)
public class PrivateOverrideOfVirtualTargetTest extends TestBase {

  private final TestParameters parameters;
  private static final String[] EXPECTED =
      new String[] {"A.foo", "A.bar", "B.foo", "A.bar", "B.bar"};

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PrivateOverrideOfVirtualTargetTest(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(A.class, Main.class)
                .addClassProgramData(getBWithModifiedInvokes())
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(A.class, "bar", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexProgramClass context =
        appView.definitionForProgramType(buildType(B.class, appInfo.dexItemFactory()));
    LookupResult lookupResult = resolutionResult.lookupVirtualDispatchTargets(context, appView);
    assertTrue(lookupResult.isLookupResultSuccess());
    Set<String> targets = new HashSet<>();
    lookupResult.forEach(
        target -> targets.add(target.getDefinition().qualifiedName()), lambda -> fail());
    ImmutableSet<String> expected = ImmutableSet.of(A.class.getTypeName() + ".bar");
    assertEquals(expected, targets);
  }

  @Test
  public void testRuntime()
      throws NoSuchMethodException, IOException, CompilationFailedException, ExecutionException {
    testForRuntime(parameters)
        .addProgramClasses(A.class, Main.class)
        .addProgramClassFileData(getBWithModifiedInvokes())
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  @Test
  public void testR8()
      throws NoSuchMethodException, IOException, CompilationFailedException, ExecutionException {
    testForR8(parameters.getBackend())
        .addProgramClasses(A.class, Main.class)
        .addProgramClassFileData(getBWithModifiedInvokes())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(EXPECTED);
  }

  private byte[] getBWithModifiedInvokes() throws NoSuchMethodException, IOException {
    Box<Boolean> modifyOwner = new Box<>(true);
    return transformer(B.class)
        .setPrivate(B.class.getDeclaredMethod("bar"))
        .transformMethodInsnInMethod(
            "callOnB",
            (opcode, owner, name, descriptor, isInterface, continuation) -> {
              if (name.equals("foo")) {
                continuation.visitMethodInsn(opcode, owner, name, descriptor, isInterface);
                return;
              }
              if (modifyOwner.get()) {
                continuation.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    DescriptorUtils.getBinaryNameFromJavaType(A.class.getTypeName()),
                    name,
                    descriptor,
                    isInterface);
                modifyOwner.set(false);
              } else {
                continuation.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL, owner, name, descriptor, isInterface);
              }
            })
        .transform();
  }

  public static class B extends A {
    private void foo() {
      System.out.println("B.foo");
    }

    @Override
    protected /* private */ void bar() {
      System.out.println("B.bar");
    }

    void callOnB() {
      foo();
      bar(); /* will become A.bar() */
      bar(); /* will become B.bar() */
    }
  }

  public static class Main {

    public static void main(String[] args) {
      B b = new B();
      A.run(b);
      b.callOnB();
    }
  }
}
