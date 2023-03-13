// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resolution.virtualtargets;

import static com.android.tools.r8.ToolHelper.getMostRecentAndroidJar;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.LookupResult.LookupResultSuccess;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptTargetsIncompleteLookupTest extends TestBase {

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public KeptTargetsIncompleteLookupTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  private LookupResultSuccess testLookup(Class<?> methodToBeKept) throws Exception {
    return testLookup(B.class, methodToBeKept, methodToBeKept, A.class, C.class);
  }

  private LookupResultSuccess testLookup(
      Class<?> initial,
      Class<?> methodToBeKept,
      Class<?> classToBeKept,
      Class<?>... expectedMethodHolders)
      throws Exception {
    return testLookup(
        initial,
        methodToBeKept,
        classToBeKept,
        Arrays.asList(expectedMethodHolders),
        Arrays.asList(I.class, A.class, B.class, C.class, Main.class, Unrelated.class),
        Main.class);
  }

  private LookupResultSuccess testLookup(
      Class<?> initial,
      Class<?> methodToBeKept,
      Class<?> classToBeKept,
      Collection<Class<?>> expectedMethodHolders,
      Collection<Class<?>> classes,
      Class<?> main)
      throws Exception {
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(classes).addLibraryFile(getMostRecentAndroidJar()).build(),
            factory -> {
              List<ProguardConfigurationRule> rules = new ArrayList<>();
              rules.addAll(buildKeepRuleForClassAndMethods(methodToBeKept, factory));
              rules.addAll(buildKeepRuleForClass(classToBeKept, factory));
              rules.addAll(buildKeepRuleForClassAndMethods(main, factory));
              return buildConfigForRules(factory, rules);
            });
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(initial, "foo", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexProgramClass context =
        DexProgramClass.asProgramClassOrNull(
            appView
                .appInfo()
                .definitionForWithoutExistenceAssert(
                    buildType(Unrelated.class, appInfo.dexItemFactory())));
    LookupResult lookupResult = resolutionResult.lookupVirtualDispatchTargets(context, appView);
    assertTrue(lookupResult.isLookupResultSuccess());
    LookupResultSuccess lookupResultSuccess = lookupResult.asLookupResultSuccess();
    Set<String> targets = new HashSet<>();
    lookupResult.forEach(
        target -> targets.add(target.getDefinition().qualifiedName()), lambda -> fail());
    Set<String> expected =
        expectedMethodHolders.stream()
            .map(c -> c.getTypeName() + ".foo")
            .collect(Collectors.toSet());
    assertEquals(expected, targets);
    return lookupResultSuccess;
  }

  @Test
  public void testCompleteLookupResultWhenKeepingUnrelated() throws Exception {
    // I {
    //   foo()
    // }
    //
    // A implements I {
    //   foo() <-- resolved
    // }
    //
    // B extends A { } <-- initial
    //
    // C extends B {
    //   foo()
    // }
    assertTrue(
        testLookup(B.class, Unrelated.class, Unrelated.class, A.class, C.class).isComplete());
  }

  @Test
  public void testKeptResolvedAndNoKeepInSubtreeFromInitial() throws Exception {
    // I {
    //   foo()
    // }
    //
    // A implements I {
    //   foo() <-- kept, resolved
    // }
    //
    // B extends A { } <-- initial
    //
    // C extends B {
    //   foo()
    // }
    assertTrue(testLookup(A.class).isIncomplete());
  }

  @Test
  public void testIncompleteLookupResultWhenKeepingStaticReceiver() throws Exception {
    // I {
    //   foo()
    // }
    //
    // A implements I {
    //   foo() <-- resolved
    // }
    //
    // B extends A { } <-- initial, kept
    //
    // C extends B {
    //   foo()
    // }
    assertTrue(testLookup(B.class).isIncomplete());
  }

  @Test
  public void testIncompleteLookupResultWhenKeepingSubTypeMethod() throws Exception {
    // I {
    //   foo()
    // }
    //
    // A implements I {
    //   foo() <-- resolved
    // }
    //
    // B extends A { } <-- initial
    //
    // C extends B {
    //   foo() <-- kept
    // }
    assertTrue(testLookup(C.class).isIncomplete());
  }

  @Test
  public void testIncompleteLookupResultWhenKeepingMethodOnParentToResolveAndKeepClass()
      throws Exception {
    // I {
    //   foo()
    // }
    //
    // A implements I {
    //   foo() <-- kept
    // }
    //
    // B extends A { }
    //
    // C extends B { <-- initial, resolved, kept
    //   foo()
    // }
    assertTrue(testLookup(C.class, A.class, C.class, C.class).isIncomplete());
  }

  @Test
  public void testCompleteLookupResultWhenKeepingMethodOnParentToResolveAndNotKeepClass()
      throws Exception {
    // I {
    //   foo()
    // }
    //
    // A implements I {
    //   foo() <-- kept
    // }
    //
    // B extends A { }
    //
    // C extends B { <-- initial, resolved
    //   foo()
    // }
    assertTrue(testLookup(C.class, A.class, Unrelated.class, C.class).isComplete());
  }

  @Test
  public void testLibraryWithNoOverride() throws Exception {
    // ----- Library -----
    // I {
    //   foo()
    // }
    //
    // A implements I {
    //   foo() <-- resolved
    // }
    //
    // ----- Program -----
    // B extends A { } <-- initial
    AppView<AppInfoWithClassHierarchy> appView =
        computeAppViewWithClassHierarchy(
            buildClasses(Collections.singletonList(B.class), Arrays.asList(A.class, I.class))
                .build());
    AppInfoWithClassHierarchy appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(B.class, "foo", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexType typeA = buildType(A.class, appInfo.dexItemFactory());
    DexType typeB = buildType(B.class, appInfo.dexItemFactory());
    DexProgramClass classB = appInfo.definitionForProgramType(typeB);
    LookupResult lookupResult =
        resolutionResult.lookupVirtualDispatchTargets(
            classB,
            appView,
            (type, subTypeConsumer, callSiteConsumer) -> {
              if (type == typeB) {
                subTypeConsumer.accept(classB);
              }
            },
            reference -> false);
    assertTrue(lookupResult.isLookupResultSuccess());
    LookupResultSuccess lookupResultSuccess = lookupResult.asLookupResultSuccess();
    Set<String> targets = new HashSet<>();
    lookupResult.forEach(
        target -> targets.add(target.getDefinition().qualifiedName()), lambda -> fail());
    Set<String> expected = ImmutableSet.of(A.class.getTypeName() + ".foo");
    assertEquals(expected, targets);
    assertTrue(lookupResultSuccess.isComplete());
  }

  @Test
  public void testPrivateKeep() throws Exception {
    // Unrelated { <-- kept
    //   private foo() <-- kept
    // }
    AppView<AppInfoWithLiveness> appView =
        computeAppViewWithLiveness(
            buildClasses(Unrelated.class).addLibraryFile(getMostRecentAndroidJar()).build(),
            Unrelated.class);
    AppInfoWithLiveness appInfo = appView.appInfo();
    DexMethod method = buildNullaryVoidMethod(Unrelated.class, "foo", appInfo.dexItemFactory());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    DexProgramClass context =
        appView.definitionForProgramType(buildType(Unrelated.class, appInfo.dexItemFactory()));
    LookupResult lookupResult = resolutionResult.lookupVirtualDispatchTargets(context, appView);
    assertTrue(lookupResult.isLookupResultSuccess());
    LookupResultSuccess lookupResultSuccess = lookupResult.asLookupResultSuccess();
    Set<String> targets = new HashSet<>();
    lookupResult.forEach(
        target -> targets.add(target.getDefinition().qualifiedName()), lambda -> fail());
    Set<String> expected = ImmutableSet.of(Unrelated.class.getTypeName() + ".foo");
    assertEquals(expected, targets);
    assertTrue(lookupResultSuccess.isIncomplete());
  }

  @Test
  public void testInterfaceKept() throws Exception {
    // I {
    //   foo() <-- kept
    // }
    //
    // A implements I {
    //   foo() <-- resolved
    // }
    //
    // B extends A { } <-- initial, kept
    //
    // C extends B {
    //   foo()
    // }
    assertTrue(testLookup(B.class, I.class, B.class, A.class, C.class).isIncomplete());
  }

  @Test
  public void testInterfaceKeptWithoutKeepInLookup() throws Exception {
    // I {
    //   foo() <-- kept
    // }
    //
    // A implements I {
    //   foo() <-- resolved
    // }
    //
    // B extends A { } <-- initial
    //
    // C extends B {
    //   foo()
    // }
    assertTrue(testLookup(B.class, I.class, Unrelated.class, A.class, C.class).isComplete());
  }

  @Test
  public void testInterfaceKeptAndImplementedInSupType() throws Exception {
    // X {
    //   foo() <-- resolved
    // }
    //
    // I {
    //   foo() <-- kept
    // }
    //
    // Y extends X implements I { } <-- initial
    //
    // Z extends Y { } <-- kept
    assertTrue(
        testLookup(
                Y.class,
                I.class,
                Z.class,
                Collections.singleton(X.class),
                Arrays.asList(X.class, I.class, Y.class, Z.class),
                MainXYZ.class)
            .isIncomplete());
  }

  // TODO(b/148769279): We need to look at the call site to see if it overrides
  //   a method that is kept.

  public static class Unrelated {

    private void foo() {
      System.out.println("Unrelated.foo");
    }
  }

  public interface I {
    void foo();
  }

  public static class A implements I {
    @Override
    public void foo() {
      System.out.println("A.foo");
    }
  }

  public static class B extends A {}

  public static class C extends B {
    @Override
    public void foo() {
      System.out.println("C.foo");
    }
  }

  public static class Main {

    public static void main(String[] args) {
      // This is necessary for considering the classes as instantiated.
      new Unrelated();
      new A();
      new B();
      new C();
    }
  }

  public static class X {

    public void foo() {
      System.out.println("X.foo");
    }
  }

  public static class Y extends X implements I {}

  public static class Z extends Y {}

  public static class MainXYZ {

    public static void main(String[] args) {
      // This is necessary for considering the classes as instantiated.
      new X();
      new Y();
      new Z();
    }
  }
}
