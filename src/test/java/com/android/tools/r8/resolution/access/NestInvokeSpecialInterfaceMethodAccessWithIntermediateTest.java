// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.access;

import static com.android.tools.r8.TestRuntime.CfVm.JDK11;
import static org.hamcrest.core.StringContains.containsString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import com.android.tools.r8.TestAppViewBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.MethodResolutionResult.NoSuchMethodResult;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.synthesis.SyntheticItemsTestUtils;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.objectweb.asm.Opcodes;

/** Tests the behavior of invoke-special on interfaces with an indirect private definition. */
@RunWith(Parameterized.class)
public class NestInvokeSpecialInterfaceMethodAccessWithIntermediateTest extends TestBase {

  private final TestParameters parameters;

  // If true, all classes are in the same nest, otherwise each is in its own.
  private final boolean inSameNest;

  // If true, the invoke will reference the actual type defining the method.
  private final boolean symbolicReferenceIsDefiningType;

  @Parameterized.Parameters(name = "{0}, in-same-nest:{1}, sym-ref-is-def-type:{2}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withCfRuntimesStartingFromIncluding(JDK11)
            .withDexRuntimes()
            .withAllApiLevels()
            .enableApiLevelsForCf()
            .build(),
        BooleanUtils.values(),
        BooleanUtils.values());
  }

  public NestInvokeSpecialInterfaceMethodAccessWithIntermediateTest(
      TestParameters parameters, boolean inSameNest, boolean symbolicReferenceIsDefiningType) {
    this.parameters = parameters;
    this.inSameNest = inSameNest;
    this.symbolicReferenceIsDefiningType = symbolicReferenceIsDefiningType;
  }

  public Collection<Class<?>> getClasses() {
    return ImmutableList.of(Main.class);
  }

  public Collection<byte[]> getTransformedClasses() throws Exception {
    return ImmutableList.of(
        withNest(I.class).setPrivate(I.class.getDeclaredMethod("bar")).transform(),
        withNest(J.class).transform(),
        withNest(A.class)
            .transformMethodInsnInMethod(
                "foo",
                (opcode, owner, name, descriptor, isInterface, continuation) -> {
                  assertEquals(Opcodes.INVOKEVIRTUAL, opcode);
                  assertEquals(DescriptorUtils.getBinaryNameFromJavaType(A.class.getName()), owner);
                  String newOwner =
                      symbolicReferenceIsDefiningType
                          ? DescriptorUtils.getBinaryNameFromJavaType(I.class.getName())
                          : DescriptorUtils.getBinaryNameFromJavaType(J.class.getName());
                  continuation.visitMethodInsn(
                      Opcodes.INVOKESPECIAL, newOwner, name, descriptor, true);
                })
            .transform());
  }

  private ClassFileTransformer withNest(Class<?> clazz) throws Exception {
    if (inSameNest) {
      // If in the same nest make A host and B a member.
      return transformer(clazz).setNest(I.class, J.class, A.class);
    }
    // Otherwise, set the class to be its own host and no additional members.
    return transformer(clazz).setNest(clazz);
  }

  @Test
  public void testResolutionAccess() throws Exception {
    assumeFalse(
        "b/144410139. Don't test internals for non-verifying input",
        symbolicReferenceIsDefiningType);

    // White-box test of the R8 resolution and lookup methods.
    Class<?> definingClass = I.class;
    Class<?> declaredClass = symbolicReferenceIsDefiningType ? definingClass : J.class;
    Class<?> callerClass = A.class;

    AppView<AppInfoWithLiveness> appView = getAppView();
    AppInfoWithLiveness appInfo = appView.appInfo();

    DexProgramClass declaredClassDefinition = getDexProgramClass(declaredClass, appInfo);

    DexMethod method = getTargetMethodSignature(declaredClass, appInfo);

    assertCallingClassCallsTarget(callerClass, appInfo, method);

    // Resolve the method from the point of the declared holder.
    assertEquals(method.holder, declaredClassDefinition.type);
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethodOnLegacy(declaredClassDefinition, method);

    // The targeted method is a private interface method and thus not a maximally specific method.
    assertTrue(resolutionResult instanceof NoSuchMethodResult);
  }

  private void assertCallingClassCallsTarget(
      Class<?> callerClass, AppInfoWithLiveness appInfo, DexMethod method) {
    CodeInspector inspector = new CodeInspector(appInfo.app());
    MethodSubject foo = inspector.clazz(callerClass).uniqueMethodWithOriginalName("foo");
    assertTrue(
        foo.streamInstructions()
            .anyMatch(
                i -> {
                  if (parameters.canUseDefaultAndStaticInterfaceMethodsWhenDesugaring()) {
                    return i.asCfInstruction().isInvokeSpecial() && i.getMethod() == method;
                  } else {
                    return i.isInvokeStatic()
                        && SyntheticItemsTestUtils.isInternalThrowNSME(
                            i.getMethod().asMethodReference());
                  }
                }));
  }

  private DexMethod getTargetMethodSignature(Class<?> declaredClass, AppInfoWithLiveness appInfo) {
    return buildMethod(
        Reference.method(Reference.classFromClass(declaredClass), "bar", ImmutableList.of(), null),
        appInfo.dexItemFactory());
  }

  private DexProgramClass getDexProgramClass(Class<?> definingClass, AppInfoWithLiveness appInfo) {
    return appInfo
        .definitionFor(buildType(definingClass, appInfo.dexItemFactory()))
        .asProgramClass();
  }

  private AppView<AppInfoWithLiveness> getAppView() throws Exception {
    return TestAppViewBuilder.builder()
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .addLibraryFiles(parameters.getDefaultRuntimeLibrary())
        .addKeepMainRule(Main.class)
        .setMinApi(parameters)
        .buildWithLiveness();
  }

  @Test
  public void test() throws Exception {
    parameters.assumeRuntimeTestParameters();
    testForRuntime(parameters)
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkExpectedResult(result, false));
  }

  @Test
  public void testR8() throws Exception {
    parameters.assumeR8TestParameters();
    testForR8(parameters.getBackend())
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(result -> checkExpectedResult(result, true));
  }

  private void checkExpectedResult(TestRunResult<?> result, boolean isR8) {
    if (symbolicReferenceIsDefiningType) {
      assumeTrue(
          "TODO(b/144410139): Input does not verify. Should compilation throw an error?",
          parameters.isCfRuntime() && !isR8);
      result.assertFailureWithErrorThatMatches(containsString(VerifyError.class.getName()));
    } else {
      result.assertFailureWithErrorThatMatches(containsString(NoSuchMethodError.class.getName()));
    }
  }

  interface I {
    /* will be private */ default void bar() {
      System.out.println("I::bar");
    }
  }

  interface J extends I {
    // Intentionally empty.
  }

  static class A implements J {
    public void foo() {
      // Rewritten to invoke-special I.bar or J.bar which resolves to private method I.bar
      // With sym-ref I.bar the classfile fails verification.
      // With sym-ref J.bar results in a NoSuchMethodError.
      bar();
    }
  }

  static class Main {
    public static void main(String[] args) {
      new A().foo();
    }
  }
}
