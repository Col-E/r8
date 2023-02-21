// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.packageprivate;

import static org.hamcrest.CoreMatchers.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.R8TestRunResult;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.ToolHelper.DexVm;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.resolution.packageprivate.a.Abstract;
import com.android.tools.r8.resolution.packageprivate.a.AbstractWidening;
import com.android.tools.r8.resolution.packageprivate.a.I;
import com.android.tools.r8.resolution.packageprivate.a.J;
import com.android.tools.r8.resolution.packageprivate.a.NonAbstractWideningExtendingA;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.transformers.ClassTransformer;
import com.google.common.collect.ImmutableSet;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;
import org.objectweb.asm.MethodVisitor;

@RunWith(Parameterized.class)
public class PackagePrivateWithDefaultMethod2Test extends TestBase {

  private final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  public PackagePrivateWithDefaultMethod2Test(TestParameters parameters) {
    this.parameters = parameters;
  }

  @Test
  public void testResolution() throws Exception {
    assumeTrue(parameters.isOrSimulateNoneRuntime());
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(
                    Abstract.class,
                    AbstractWidening.class,
                    I.class,
                    A.class,
                    NonAbstractWideningExtendingA.class,
                    J.class,
                    Main.class)
                .addClassProgramData(getNonAbstractWithoutDeclaredMethods())
                .addLibraryFile(parameters.getDefaultRuntimeLibrary())
                .build(),
            Main.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(A.class, "foo", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexProgramClass context =
        appView.definitionForProgramType(buildType(A.class, appInfo.dexItemFactory()));
    LookupResult lookupResult = resolutionResult.lookupVirtualDispatchTargets(context, appInfo);
    assertTrue(lookupResult.isLookupResultSuccess());
    Set<String> targets = new HashSet<>();
    lookupResult.forEach(
        target -> targets.add(target.getDefinition().qualifiedName()), lambda -> fail());
    // TODO(b/148591377): The set should be empty.
    ImmutableSet<String> expected = ImmutableSet.of(AbstractWidening.class.getTypeName() + ".foo");
    assertEquals(expected, targets);
  }

  @Test
  public void testRuntime() throws ExecutionException, CompilationFailedException, IOException {
    TestRunResult<?> runResult =
        testForRuntime(parameters)
            .addProgramClasses(
                Abstract.class,
                AbstractWidening.class,
                I.class,
                A.class,
                NonAbstractWideningExtendingA.class,
                J.class,
                Main.class)
            .addProgramClassFileData(getNonAbstractWithoutDeclaredMethods())
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_TARGET)) {
      runResult.assertFailure();
    } else {
      runResult.assertFailureWithErrorThatMatches(containsString("AbstractMethodError"));
    }
  }

  @Test
  public void testR8() throws ExecutionException, CompilationFailedException, IOException {
    R8TestRunResult runResult =
        testForR8(parameters.getBackend())
            .addProgramClasses(
                Abstract.class,
                AbstractWidening.class,
                I.class,
                A.class,
                NonAbstractWideningExtendingA.class,
                J.class,
                Main.class)
            .addProgramClassFileData(getNonAbstractWithoutDeclaredMethods())
            .setMinApi(parameters)
            .addKeepMainRule(Main.class)
            .run(parameters.getRuntime(), Main.class);
    if (parameters.isDexRuntime()
        && parameters.getRuntime().asDex().getVm().isOlderThanOrEqual(DexVm.ART_4_4_4_TARGET)) {
      runResult.assertFailure();
    } else {
      runResult.assertFailureWithErrorThatMatches(containsString("AbstractMethodError"));
    }
  }

  private byte[] getNonAbstractWithoutDeclaredMethods() throws IOException {
    return transformer(NonAbstractWidening.class)
        .addClassTransformer(
            new ClassTransformer() {
              @Override
              public MethodVisitor visitMethod(
                  int access,
                  String name,
                  String descriptor,
                  String signature,
                  String[] exceptions) {
                if (!name.equals("foo")) {
                  return super.visitMethod(access, name, descriptor, signature, exceptions);
                }
                return null;
              }
            })
        .transform();
  }

  public static class A extends NonAbstractWidening {}

  public static class Main {

    public static void main(String[] args) {
      NonAbstractWideningExtendingA d = new NonAbstractWideningExtendingA();
      Abstract.run(d);
      d.foo();
    }
  }
}
