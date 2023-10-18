// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.singletarget;

import static com.android.tools.r8.ToolHelper.getMostRecentAndroidJar;
import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertNull;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.DynamicTypeWithLowerBound;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.LibraryModeledPredicate;
import com.google.common.collect.Sets;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class InstantiatedLowerBoundTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public InstantiatedLowerBoundTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void testSingleTargetLowerBoundInstantiated() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(A.class, B.class, Main.class)
                .addLibraryFile(getMostRecentAndroidJar())
                .build(),
            factory ->
                buildConfigForRules(factory, buildKeepRuleForClassAndMethods(Main.class, factory)));
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexType typeA = buildType(A.class, appInfo.dexItemFactory());
    DexType typeB = buildType(B.class, appInfo.dexItemFactory());
    DexType typeMain = buildType(Main.class, appInfo.dexItemFactory());
    DexMethod mainMethodReference =
        buildMethod(Main.class.getDeclaredMethod("main", String[].class), appInfo.dexItemFactory());
    ProgramMethod mainMethod =
        appInfo.definitionForProgramType(typeMain).lookupProgramMethod(mainMethodReference);
    DexMethod fooA = buildNullaryVoidMethod(A.class, "foo", appInfo.dexItemFactory());
    TypeElement latticeA = typeA.toTypeElement(appView);
    ClassTypeElement latticeB = typeB.toTypeElement(appView).asClassType();
    DexClassAndMethod singleTarget =
        appInfo.lookupSingleVirtualTargetForTesting(
            appView,
            fooA,
            mainMethod,
            false,
            LibraryModeledPredicate.alwaysFalse(),
            DynamicTypeWithLowerBound.create(appView, latticeA, latticeB));
    assertNotNull(singleTarget);
    DexMethod fooB = buildNullaryVoidMethod(B.class, "foo", appInfo.dexItemFactory());
    assertEquals(fooB, singleTarget.getReference());
  }

  @Test
  public void testSingleTargetLowerBoundInMiddleInstantiated() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(A.class, B.class, C.class, Main.class)
                .addLibraryFile(getMostRecentAndroidJar())
                .build(),
            factory ->
                buildConfigForRules(factory, buildKeepRuleForClassAndMethods(Main.class, factory)));
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexType typeA = buildType(A.class, appInfo.dexItemFactory());
    DexType typeB = buildType(B.class, appInfo.dexItemFactory());
    DexType typeMain = buildType(Main.class, appInfo.dexItemFactory());
    DexMethod mainMethodReference =
        buildMethod(Main.class.getDeclaredMethod("main", String[].class), appInfo.dexItemFactory());
    ProgramMethod mainMethod =
        appInfo.definitionForProgramType(typeMain).lookupProgramMethod(mainMethodReference);
    DexMethod fooA = buildNullaryVoidMethod(A.class, "foo", appInfo.dexItemFactory());
    TypeElement latticeA = typeA.toTypeElement(appView);
    ClassTypeElement latticeB = typeB.toTypeElement(appView).asClassType();
    DexClassAndMethod singleTarget =
        appInfo.lookupSingleVirtualTargetForTesting(
            appView,
            fooA,
            mainMethod,
            false,
            LibraryModeledPredicate.alwaysFalse(),
            DynamicTypeWithLowerBound.create(appView, latticeA, latticeB));
    assertNotNull(singleTarget);
    DexMethod fooB = buildNullaryVoidMethod(B.class, "foo", appInfo.dexItemFactory());
    assertEquals(fooB, singleTarget.getReference());
  }

  @Test
  public void testSingleTargetLowerAllInstantiated() throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(A.class, B.class, C.class, MainAllInstantiated.class)
                .addLibraryFile(getMostRecentAndroidJar())
                .build(),
            factory ->
                buildConfigForRules(
                    factory, buildKeepRuleForClassAndMethods(MainAllInstantiated.class, factory)));
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexType typeA = buildType(A.class, appInfo.dexItemFactory());
    DexType typeC = buildType(C.class, appInfo.dexItemFactory());
    DexType typeMain = buildType(MainAllInstantiated.class, appInfo.dexItemFactory());
    DexMethod mainMethodReference =
        buildMethod(Main.class.getDeclaredMethod("main", String[].class), appInfo.dexItemFactory());
    ProgramMethod mainMethod =
        appInfo.definitionForProgramType(typeMain).lookupProgramMethod(mainMethodReference);
    DexMethod fooA = buildNullaryVoidMethod(A.class, "foo", appInfo.dexItemFactory());
    DexMethod fooB = buildNullaryVoidMethod(B.class, "foo", appInfo.dexItemFactory());
    DexMethod fooC = buildNullaryVoidMethod(C.class, "foo", appInfo.dexItemFactory());
    MethodResolutionResult resolution = appInfo.resolveMethodOnClassHolderLegacy(fooA);
    DexProgramClass context = appView.definitionForProgramType(typeMain);
    DexProgramClass upperBound = appView.definitionForProgramType(typeA);
    DexProgramClass lowerBound = appView.definitionForProgramType(typeC);
    LookupResult lookupResult =
        resolution.lookupVirtualDispatchTargets(context, appView, upperBound, lowerBound);
    Set<DexMethod> expected = Sets.newIdentityHashSet();
    expected.add(fooA);
    expected.add(fooB);
    expected.add(fooC);
    assertTrue(lookupResult.isLookupResultSuccess());
    Set<DexMethod> actual = Sets.newIdentityHashSet();
    lookupResult
        .asLookupResultSuccess()
        .forEach(
            clazzAndMethod -> actual.add(clazzAndMethod.getDefinition().getReference()),
            lambdaTarget -> {
              assert false;
            });
    assertEquals(expected, actual);
    TypeElement latticeA = typeA.toTypeElement(appView);
    ClassTypeElement latticeC = typeC.toTypeElement(appView).asClassType();
    DexClassAndMethod singleTarget =
        appInfo.lookupSingleVirtualTargetForTesting(
            appView,
            fooA,
            mainMethod,
            false,
            LibraryModeledPredicate.alwaysFalse(),
            DynamicTypeWithLowerBound.create(appView, latticeA, latticeC));
    assertNull(singleTarget);
  }

  public static class A {

    public void foo() {
      System.out.println("A.foo");
    }
  }

  public static class B extends A {

    @Override
    public void foo() {
      System.out.println("B.foo");
    }
  }

  public static class C extends B {

    @Override
    public void foo() {
      System.out.println("C.foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      new B();
    }
  }

  public static class MainAllInstantiated {

    public static void main(String[] args) {
      new A();
      new B();
      new C();
    }
  }
}
