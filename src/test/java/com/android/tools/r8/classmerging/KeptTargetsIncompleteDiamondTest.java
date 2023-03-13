// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.classmerging;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestAppViewBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.LookupResult.LookupResultSuccess;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptTargetsIncompleteDiamondTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KeptTargetsIncompleteDiamondTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private AppView<AppInfoWithLiveness> computeAppViewWithLiveness(
      Class<?> methodToBeKept, Class<?> classToBeKept) throws Exception {
    return TestAppViewBuilder.builder()
        .addProgramClasses(I.class, J.class, K.class, L.class, A.class, Main.class)
        .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
        .addKeepRuleBuilder(
            factory ->
                ImmutableList.<ProguardConfigurationRule>builder()
                    .addAll(buildKeepRuleForClassAndMethods(methodToBeKept, factory))
                    .addAll(buildKeepRuleForClass(classToBeKept, factory))
                    .addAll(buildKeepRuleForClassAndMethods(Main.class, factory))
                    .build())
        .setMinApi(AndroidApiLevel.N)
        .buildWithLiveness();
  }

  @Test
  public void testRefinedReceiverOnStaticTarget() throws Exception {
    // I { <-- kept
    //   foo() <-- kept, initial
    // }
    //
    // J {
    //   default foo() { ... } <-- kept, resolved
    // }
    //
    // K {  }
    //
    // L extends I, K { } <-- upperbound
    //
    // A implements L { <-- lowerbound
    //
    // }
    AppView<AppInfoWithLiveness> appView = computeAppViewWithLiveness(I.class, I.class);
    DexMethod method = buildNullaryVoidMethod(I.class, "foo", appView.dexItemFactory());
    MethodResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOnInterfaceHolderLegacy(method);
    DexType typeI = buildType(I.class, appView.dexItemFactory());
    DexType typeL = buildType(L.class, appView.dexItemFactory());
    DexType typeA = buildType(A.class, appView.dexItemFactory());
    DexProgramClass classI = appView.definitionForProgramType(typeI);
    DexProgramClass classL = appView.definitionForProgramType(typeL);
    DexProgramClass classA = appView.definitionForProgramType(typeA);
    LookupResult lookupResult =
        resolutionResult.lookupVirtualDispatchTargets(classI, appView, classL, classA);
    assertTrue(lookupResult.isLookupResultSuccess());
    LookupResultSuccess lookupResultSuccess = lookupResult.asLookupResultSuccess();
    Set<String> targets = new HashSet<>();
    lookupResultSuccess.forEach(
        methodTarget -> targets.add(methodTarget.asMethodTarget().getDefinition().qualifiedName()),
        lambdaTarget -> {
          assert false;
        });
    Set<String> expected = ImmutableSet.of(J.class.getTypeName() + ".foo");
    assertEquals(expected, targets);
    assertTrue(lookupResultSuccess.isComplete());
  }

  @Test
  public void testRefinedReceiverOnStaticTargetInRange() throws Exception {
    // I { <-- kept
    //   foo() <-- kept, initial, upperbound
    // }
    //
    // J {
    //   default foo() { ... } <-- kept, resolved
    // }
    //
    // K {  }
    //
    // L extends I, K { }
    //
    // A implements L { <-- lowerbound
    //
    // }
    AppView<AppInfoWithLiveness> appView = computeAppViewWithLiveness(I.class, I.class);
    DexMethod method = buildNullaryVoidMethod(I.class, "foo", appView.dexItemFactory());
    MethodResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOnInterfaceHolderLegacy(method);
    DexType typeI = buildType(I.class, appView.dexItemFactory());
    DexType typeL = buildType(L.class, appView.dexItemFactory());
    DexType typeA = buildType(A.class, appView.dexItemFactory());
    DexProgramClass classI = appView.definitionForProgramType(typeI);
    DexProgramClass classA = appView.definitionForProgramType(typeA);
    LookupResult lookupResult =
        resolutionResult.lookupVirtualDispatchTargets(classI, appView, classI, classA);
    assertTrue(lookupResult.isLookupResultSuccess());
    LookupResultSuccess lookupResultSuccess = lookupResult.asLookupResultSuccess();
    Set<String> targets = new HashSet<>();
    lookupResultSuccess.forEach(
        methodTarget -> targets.add(methodTarget.asMethodTarget().getDefinition().qualifiedName()),
        lambdaTarget -> {
          assert false;
        });
    Set<String> expected = ImmutableSet.of(J.class.getTypeName() + ".foo");
    assertEquals(expected, targets);
    assertTrue(lookupResultSuccess.isComplete());
  }

  @Test
  public void testRefinedReceiverWithKeptDispatchTarget() throws Exception {
    // I {
    //   foo() <-- initial
    // }
    //
    // J { <-- kept
    //   default foo() { ... } <-- kept, resolved
    // }
    //
    // K {  }
    //
    // L extends I, K { }
    //
    // A implements L { <-- lowerbound, upperbound
    //
    // }
    AppView<AppInfoWithLiveness> appView = computeAppViewWithLiveness(J.class, J.class);
    DexMethod method = buildNullaryVoidMethod(I.class, "foo", appView.dexItemFactory());
    MethodResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOnInterfaceHolderLegacy(method);
    DexType typeI = buildType(I.class, appView.dexItemFactory());
    DexType typeB = buildType(A.class, appView.dexItemFactory());
    DexProgramClass classI = appView.definitionForProgramType(typeI);
    DexProgramClass classA = appView.definitionForProgramType(typeB);
    LookupResult lookupResult =
        resolutionResult.lookupVirtualDispatchTargets(classI, appView, classA, classA);
    assertTrue(lookupResult.isLookupResultSuccess());
    LookupResultSuccess lookupResultSuccess = lookupResult.asLookupResultSuccess();
    Set<String> targets = new HashSet<>();
    lookupResultSuccess.forEach(
        methodTarget -> targets.add(methodTarget.asMethodTarget().getDefinition().qualifiedName()),
        lambdaTarget -> {
          assert false;
        });
    Set<String> expected = ImmutableSet.of(J.class.getTypeName() + ".foo");
    assertEquals(expected, targets);
    assertTrue(lookupResultSuccess.isIncomplete());
  }

  @Test
  public void testRefinedReceiverWithKeptRefinedReceiver() throws Exception {
    // I {
    //   foo() <-- initial
    // }
    //
    // J {
    //   default foo() { ... } <-- kept, resolved
    // }
    //
    // K {  }
    //
    // A implements I, K { <-- kept, lowerbound, upperbound
    //
    // }
    AppView<AppInfoWithLiveness> appView = computeAppViewWithLiveness(J.class, A.class);
    DexMethod method = buildNullaryVoidMethod(I.class, "foo", appView.dexItemFactory());
    MethodResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOnInterfaceHolderLegacy(method);
    DexType typeI = buildType(I.class, appView.dexItemFactory());
    DexType typeB = buildType(A.class, appView.dexItemFactory());
    DexProgramClass classI = appView.definitionForProgramType(typeI);
    DexProgramClass classA = appView.definitionForProgramType(typeB);
    LookupResult lookupResult =
        resolutionResult.lookupVirtualDispatchTargets(classI, appView, classA, classA);
    assertTrue(lookupResult.isLookupResultSuccess());
    LookupResultSuccess lookupResultSuccess = lookupResult.asLookupResultSuccess();
    Set<String> targets = new HashSet<>();
    lookupResultSuccess.forEach(
        methodTarget -> targets.add(methodTarget.asMethodTarget().getDefinition().qualifiedName()),
        lambdaTarget -> {
          assert false;
        });
    Set<String> expected = ImmutableSet.of(J.class.getTypeName() + ".foo");
    assertEquals(expected, targets);
    assertTrue(lookupResultSuccess.isIncomplete());
  }

  @Test
  public void testRefinedReceiverWithKeptClassOnInitial() throws Exception {
    // I { <-- kept
    //   foo() <-- kept, initial
    // }
    //
    // J {
    //   default foo() { ... } <-- resolved
    // }
    //
    // K {  }
    //
    // L extends I, K { }
    //
    // A implements L { <-- lowerbound, upperbound
    //
    // }
    AppView<AppInfoWithLiveness> appView = computeAppViewWithLiveness(I.class, I.class);
    DexMethod method = buildNullaryVoidMethod(I.class, "foo", appView.dexItemFactory());
    MethodResolutionResult resolutionResult =
        appView.appInfo().resolveMethodOnInterfaceHolderLegacy(method);
    DexType typeI = buildType(I.class, appView.dexItemFactory());
    DexType typeB = buildType(A.class, appView.dexItemFactory());
    DexProgramClass classI = appView.definitionForProgramType(typeI);
    DexProgramClass classA = appView.definitionForProgramType(typeB);
    LookupResult lookupResult =
        resolutionResult.lookupVirtualDispatchTargets(classI, appView, classA, classA);
    assertTrue(lookupResult.isLookupResultSuccess());
    LookupResultSuccess lookupResultSuccess = lookupResult.asLookupResultSuccess();
    Set<String> targets = new HashSet<>();
    lookupResultSuccess.forEach(
        methodTarget -> targets.add(methodTarget.asMethodTarget().getDefinition().qualifiedName()),
        lambdaTarget -> {
          assert false;
        });
    Set<String> expected = ImmutableSet.of(J.class.getTypeName() + ".foo");
    assertEquals(expected, targets);
    assertTrue(lookupResultSuccess.isComplete());
  }

  public interface I {
    void foo();
  }

  public interface J extends I {
    @Override
    default void foo() {
      System.out.println("K.foo");
    }
  }

  public interface K extends J {}

  public interface L extends I, K {}

  public static class A implements L {}

  public static class Main {

    public static void main(String[] args) {
      new A();
    }
  }
}
