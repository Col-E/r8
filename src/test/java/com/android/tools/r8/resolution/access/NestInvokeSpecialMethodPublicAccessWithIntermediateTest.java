// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.access;

import static com.android.tools.r8.TestRuntime.CfVm.JDK11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.transformers.ClassFileTransformer;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.google.common.collect.ImmutableList;
import java.util.Collection;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class NestInvokeSpecialMethodPublicAccessWithIntermediateTest extends TestBase {

  static final String EXPECTED = StringUtils.lines("A::bar");

  private final TestParameters parameters;
  private final boolean inSameNest;

  @Parameterized.Parameters(name = "{0}, in-same-nest:{1}")
  public static List<Object[]> data() {
    return buildParameters(
        getTestParameters()
            .withCfRuntimesStartingFromIncluding(JDK11)
            .withDexRuntimes()
            .withAllApiLevels()
            .build(),
        BooleanUtils.values());
  }

  public NestInvokeSpecialMethodPublicAccessWithIntermediateTest(
      TestParameters parameters, boolean inSameNest) {
    this.parameters = parameters;
    this.inSameNest = inSameNest;
  }

  public Collection<Class<?>> getClasses() {
    return ImmutableList.of(Main.class);
  }

  public Collection<byte[]> getTransformedClasses() throws Exception {
    return ImmutableList.of(
        withNest(A.class).transform(),
        withNest(B.class).transform(),
        withNest(C.class).transform());
  }

  private ClassFileTransformer withNest(Class<?> clazz) throws Exception {
    if (inSameNest) {
      // If in the same nest make A host and B a member.
      return transformer(clazz).setNest(A.class, B.class, C.class);
    }
    // Otherwise, set the class to be its own host and no additional members.
    return transformer(clazz).setNest(clazz);
  }

  @Test
  public void testResolutionAccess() throws Exception {
    // White-box test of the R8 resolution and lookup methods.
    Class<?> definingClass = A.class;
    Class<?> declaredClass = B.class;
    Class<?> callerClass = C.class;

    AppView<AppInfoWithLiveness> appView = getAppView();

    AppInfoWithLiveness appInfo = appView.appInfo();

    DexClass definingClassDefinition = getDexProgramClass(definingClass, appInfo);
    DexClass declaredClassDefinition = getDexProgramClass(declaredClass, appInfo);
    DexProgramClass callerClassDefinition = getDexProgramClass(callerClass, appInfo);

    DexMethod method = getTargetMethodSignature(declaredClass, appInfo);

    assertCallingClassCallsTarget(callerClass, appInfo, method);

    // Resolve the method from the point of the declared holder.
    assertEquals(method.holder, declaredClassDefinition.type);
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethodOnLegacy(declaredClassDefinition, method);

    // Verify that the resolved method is on the defining class.
    assertEquals(
        definingClassDefinition, resolutionResult.asSingleResolution().getResolvedHolder());

    // Verify that the resolved method is accessible (it is public).
    assertEquals(
        OptionalBool.TRUE, resolutionResult.isAccessibleFrom(callerClassDefinition, appView));

    // Verify that looking up the dispatch target returns the defining method.
    DexClassAndMethod targetSpecial =
        resolutionResult.lookupInvokeSpecialTarget(callerClassDefinition, appView);
    assertEquals(definingClassDefinition.type, targetSpecial.getHolderType());

    DexClassAndMethod targetSuper =
        resolutionResult.lookupInvokeSuperTarget(callerClassDefinition, appView);
    assertEquals(targetSpecial.getReference(), targetSuper.getReference());
  }

  private void assertCallingClassCallsTarget(
      Class<?> callerClass, AppInfoWithLiveness appInfo, DexMethod target) {
    CodeInspector inspector = new CodeInspector(appInfo.app());
    MethodSubject foo = inspector.clazz(callerClass).uniqueMethodWithOriginalName("foo");
    assertTrue(
        foo.streamInstructions()
            .anyMatch(i -> i.asCfInstruction().isInvokeSpecial() && i.getMethod() == target));
  }

  private DexMethod getTargetMethodSignature(Class<?> declaredClass, AppInfoWithLiveness appInfo) {
    return buildMethod(
        Reference.method(Reference.classFromClass(declaredClass), "bar", ImmutableList.of(), null),
        appInfo.dexItemFactory());
  }

  private DexProgramClass getDexProgramClass(Class<?> clazz, AppInfoWithLiveness appInfo) {
    return appInfo.definitionFor(buildType(clazz, appInfo.dexItemFactory())).asProgramClass();
  }

  private AppView<AppInfoWithLiveness> getAppView() throws Exception {
    return computeAppViewWithLiveness(
        buildClasses(getClasses())
            .addClassProgramData(getTransformedClasses())
            .addLibraryFile(parameters.getDefaultRuntimeLibrary())
            .build(),
        Main.class);
  }

  @Test
  public void test() throws Exception {
    testForRuntime(parameters)
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkExpectedResult);
  }

  @Test
  public void testR8() throws Exception {
    testForR8(parameters.getBackend())
        .addProgramClasses(getClasses())
        .addProgramClassFileData(getTransformedClasses())
        .setMinApi(parameters)
        .addKeepMainRule(Main.class)
        .run(parameters.getRuntime(), Main.class)
        .apply(this::checkExpectedResult);
  }

  private void checkExpectedResult(TestRunResult<?> result) {
    result.assertSuccessWithOutput(EXPECTED);
  }

  static class A {
    public void bar() {
      System.out.println("A::bar");
    }
  }

  static class B extends A {
    // Intentionally empty.
  }

  static class C extends B {
    public void foo() {
      // invoke-special B.bar which resolves to private method A.bar
      // Without nests, results in an IllegalAccessError.
      // With nests, results in a NoSuchMethodError.
      super.bar();
    }
  }

  static class Main {
    public static void main(String[] args) {
      new C().foo();
    }
  }
}
