// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution.access;

import static com.android.tools.r8.TestRuntime.CfVm.JDK11;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestRunResult;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.references.Reference;
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
public class NestInvokeSpecialMethodAccessTest extends TestBase {

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

  public NestInvokeSpecialMethodAccessTest(TestParameters parameters, boolean inSameNest) {
    this.parameters = parameters;
    this.inSameNest = inSameNest;
  }

  private Collection<Class<?>> getClasses() {
    return ImmutableList.of(Main.class);
  }

  private Collection<byte[]> getTransformedClasses() throws Exception {
    return ImmutableList.of(
        withNest(A.class).setPrivate(A.class.getDeclaredMethod("bar")).transform(),
        withNest(B.class).transform());
  }

  private ClassFileTransformer withNest(Class<?> clazz) throws Exception {
    if (inSameNest) {
      // If in the same nest make A host and B a member.
      return transformer(clazz).setNest(A.class, B.class);
    }
    // Otherwise, set the class to be its own host and no additional members.
    return transformer(clazz).setNest(clazz);
  }

  @Test
  public void testResolutionAccess() throws Exception {
    // White-box test of the R8 resolution and lookup methods.
    Class<?> definingClass = A.class;
    Class<?> declaredClass = A.class;
    Class<?> callerClass = B.class;

    AppView<AppInfoWithClassHierarchy> appView = getAppView();
    AppInfoWithClassHierarchy appInfo = appView.appInfo();

    DexClass definingClassDefinition = getDexProgramClass(definingClass, appView);
    DexClass declaredClassDefinition = getDexProgramClass(declaredClass, appView);
    DexProgramClass callerClassDefinition = getDexProgramClass(callerClass, appView);

    DexMethod method = getTargetMethodSignature(declaredClass, appView);

    assertCallingClassCallsTarget(callerClass, appView, method);

    // Resolve the method from the point of the declared holder.
    assertEquals(method.holder, declaredClassDefinition.type);
    MethodResolutionResult resolutionResult =
        appInfo.resolveMethodOnLegacy(declaredClassDefinition, method);

    // Verify that the resolved method is on the defining class.
    assertEquals(
        definingClassDefinition, resolutionResult.asSingleResolution().getResolvedHolder());

    // Verify that the resolved method is accessible if in the same nest.
    assertEquals(
        OptionalBool.of(inSameNest),
        resolutionResult.isAccessibleFrom(callerClassDefinition, appView));

    // Verify that looking up the dispatch target returns the defining method.
    DexClassAndMethod targetSpecial =
        resolutionResult.lookupInvokeSpecialTarget(callerClassDefinition, appView);
    DexClassAndMethod targetSuper =
        resolutionResult.lookupInvokeSuperTarget(callerClassDefinition, appView);
    if (inSameNest) {
      assertEquals(definingClassDefinition.type, targetSpecial.getHolderType());
      assertEquals(targetSpecial.getReference(), targetSuper.getReference());
    } else {
      assertNull(targetSpecial);
      assertNull(targetSuper);
    }
  }

  private void assertCallingClassCallsTarget(
      Class<?> callerClass, AppView<?> appView, DexMethod target) {
    CodeInspector inspector = new CodeInspector(appView.appInfo().app());
    MethodSubject foo = inspector.clazz(callerClass).uniqueMethodWithOriginalName("foo");
    assertTrue(
        foo.streamInstructions()
            .anyMatch(i -> i.asCfInstruction().isInvokeSpecial() && i.getMethod() == target));
  }

  private DexMethod getTargetMethodSignature(Class<?> declaredClass, AppView<?> appView) {
    return buildMethod(
        Reference.method(Reference.classFromClass(declaredClass), "bar", ImmutableList.of(), null),
        appView.dexItemFactory());
  }

  private DexProgramClass getDexProgramClass(Class<?> definingClass, AppView<?> appView) {
    return appView
        .definitionFor(buildType(definingClass, appView.dexItemFactory()))
        .asProgramClass();
  }

  private AppView<AppInfoWithClassHierarchy> getAppView() throws Exception {
    return computeAppViewWithClassHierarchy(
        buildClasses(getClasses()).addClassProgramData(getTransformedClasses()).build());
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
    if (inSameNest) {
      result.assertSuccessWithOutput(EXPECTED);
    } else {
      result.assertFailureWithErrorThatThrows(IllegalAccessError.class);
    }
  }

  static class A {
    /* will be private */ void bar() {
      System.out.println("A::bar");
    }
  }

  static class B extends A {
    public void foo() {
      // invoke-special to private method.
      super.bar();
    }
  }

  static class Main {
    public static void main(String[] args) {
      new B().foo();
    }
  }
}
