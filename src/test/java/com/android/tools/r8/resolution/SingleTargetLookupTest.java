// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import static com.android.tools.r8.ToolHelper.getMostRecentAndroidJar;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.TestAppViewBuilder;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.LookupResult;
import com.android.tools.r8.graph.MethodResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.resolution.singletarget.Main;
import com.android.tools.r8.resolution.singletarget.one.AbstractSubClass;
import com.android.tools.r8.resolution.singletarget.one.AbstractTopClass;
import com.android.tools.r8.resolution.singletarget.one.InterfaceWithDefault;
import com.android.tools.r8.resolution.singletarget.one.IrrelevantInterfaceWithDefaultDump;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassOne;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassThree;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassTwo;
import com.android.tools.r8.resolution.singletarget.three.ThirdAbstractTopClass;
import com.android.tools.r8.resolution.singletarget.three.ThirdSubClassOne;
import com.android.tools.r8.resolution.singletarget.three.ThirdSubClassTwoDump;
import com.android.tools.r8.resolution.singletarget.two.OtherAbstractSubClassOne;
import com.android.tools.r8.resolution.singletarget.two.OtherAbstractSubClassTwo;
import com.android.tools.r8.resolution.singletarget.two.OtherAbstractTopClass;
import com.android.tools.r8.resolution.singletarget.two.OtherSubSubClassOne;
import com.android.tools.r8.resolution.singletarget.two.OtherSubSubClassTwo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingleTargetLookupTest extends AsmTestBase {

  /** Initialized in @Before rule. */
  public static AppView<AppInfoWithLiveness> appView;

  public static AppInfoWithLiveness appInfo;

  public static List<Class<?>> CLASSES =
      ImmutableList.of(
          InterfaceWithDefault.class,
          AbstractTopClass.class,
          AbstractSubClass.class,
          SubSubClassOne.class,
          SubSubClassTwo.class,
          SubSubClassThree.class,
          OtherAbstractTopClass.class,
          OtherAbstractSubClassOne.class,
          OtherAbstractSubClassTwo.class,
          OtherSubSubClassOne.class,
          OtherSubSubClassTwo.class,
          ThirdAbstractTopClass.class,
          ThirdSubClassOne.class,
          Main.class);

  public static List<byte[]> ASM_CLASSES = ImmutableList.of(
      getBytesFromAsmClass(IrrelevantInterfaceWithDefaultDump::dump),
      getBytesFromAsmClass(ThirdSubClassTwoDump::dump)
  );

  public SingleTargetLookupTest(
      String methodName,
      Class<?> invokeReceiver,
      Class<?> singleTargetHolderOrNull,
      List<Class<?>> virtualTargetHolders) {
    this.methodName = methodName;
    this.invokeReceiver = invokeReceiver;
    this.singleTargetHolderOrNull = singleTargetHolderOrNull;
    this.virtualTargetHolders = virtualTargetHolders;
  }

  @BeforeClass
  public static void computeAppInfo() throws Exception {
    appView =
        TestAppViewBuilder.builder()
            .addAndroidApp(
                buildClassesWithTestingAnnotations(CLASSES)
                    .addClassProgramData(ASM_CLASSES)
                    .addLibraryFile(getMostRecentAndroidJar())
                    .build())
            .addKeepMainRule(Main.class)
            // Some of these tests resolve default methods.
            // If desugared they will hit the forward methods and not the expected defaults.
            .setMinApi(apiLevelWithDefaultInterfaceMethodsSupport())
            .buildWithLiveness();
    appInfo = appView.appInfo();
  }

  private static Object[] noVirtualSingleTarget(String name, Class<?> receiverAndTarget) {
    return new Object[] {name, receiverAndTarget, null, Collections.emptyList()};
  }

  private static Object[] singleTarget(String name, Class<?> receiverAndTarget) {
    return new Object[]{name, receiverAndTarget, receiverAndTarget,
        Collections.singletonList(receiverAndTarget)};
  }

  private static Object[] singleTarget(String name, Class<?> receiver, Class<?> target) {
    return new Object[]{name, receiver, target, Collections.singletonList(target)};
  }

  private static Object[] singleTargetWithAbstracts(String name, Class<?> receiver,
      Class<?> target, Class<?>... extras) {
    return new Object[]{name, receiver, target,
        ImmutableList.builder().add(target).add((Object[]) extras).build()};
  }

  private static Object[] manyTargets(String name, Class<?> receiver, Class<?>... targets) {
    return new Object[]{name, receiver, null, ImmutableList.copyOf(targets)};
  }

  private static Object[] onlyUnreachableTargets(String name, Class<?> receiver,
      Class<?>... targets) {
    return new Object[]{name, receiver, null, ImmutableList.copyOf(targets)};
  }

  @Parameters(name = "{1}.{0} -> {2}")
  public static List<Object[]> getData() {
    return ImmutableList.copyOf(
        new Object[][] {
          singleTarget("singleTargetAtTop", AbstractTopClass.class),
          singleTargetWithAbstracts(
              "singleShadowingOverride", AbstractTopClass.class, AbstractSubClass.class),
          manyTargets(
              "abstractTargetAtTop",
              AbstractTopClass.class,
              SubSubClassOne.class,
              SubSubClassTwo.class),
          singleTargetWithAbstracts(
              "overridenInAbstractClassOnly", AbstractTopClass.class, AbstractTopClass.class),
          onlyUnreachableTargets("overridenInAbstractClassOnly", SubSubClassThree.class),
          manyTargets(
              "overriddenInTwoSubTypes",
              AbstractTopClass.class,
              SubSubClassOne.class,
              SubSubClassTwo.class),
          manyTargets(
              "definedInTwoSubTypes",
              AbstractTopClass.class,
              SubSubClassOne.class,
              SubSubClassTwo.class),
          onlyUnreachableTargets("staticMethod", AbstractTopClass.class),
          manyTargets("overriddenInTwoSubTypes", OtherAbstractTopClass.class),
          manyTargets("abstractOverriddenInTwoSubTypes", OtherAbstractTopClass.class),
          manyTargets("overridesOnDifferentLevels", OtherAbstractTopClass.class),
          singleTarget("defaultMethod", AbstractTopClass.class, InterfaceWithDefault.class),
          manyTargets(
              "overriddenDefault",
              AbstractTopClass.class,
              InterfaceWithDefault.class,
              SubSubClassTwo.class),
          singleTarget("overriddenDefault", SubSubClassTwo.class),
          singleTarget("overriddenByIrrelevantInterface", AbstractTopClass.class),
          singleTarget(
              "overriddenByIrrelevantInterface", SubSubClassOne.class, AbstractTopClass.class),
          singleTarget(
              "overriddenInOtherInterface", AbstractTopClass.class, InterfaceWithDefault.class),
          manyTargets("abstractMethod", ThirdAbstractTopClass.class),
          noVirtualSingleTarget("instanceMethod", ThirdAbstractTopClass.class),
          noVirtualSingleTarget("otherInstanceMethod", ThirdAbstractTopClass.class),
        });
  }

  private static DexType toType(Class<?> clazz, AppInfo appInfo) {
    return buildType(clazz, appInfo.dexItemFactory());
  }

  private final String methodName;
  private final Class<?> invokeReceiver;
  private final Class<?> singleTargetHolderOrNull;
  private final List<Class<?>> virtualTargetHolders;

  @Test
  public void lookupSingleTarget() {
    DexMethod reference =
        buildNullaryVoidMethod(invokeReceiver, methodName, appInfo.dexItemFactory());
    ProgramMethod context =
        appInfo.definitionForProgramType(reference.holder).getProgramDefaultInitializer();
    Assert.assertNotNull(appInfo.resolveMethodOnClassHolderLegacy(reference).getSingleTarget());
    DexClassAndMethod singleVirtualTarget =
        appInfo.lookupSingleVirtualTarget(appView, reference, context, false);
    if (singleTargetHolderOrNull == null) {
      Assert.assertNull(singleVirtualTarget);
    } else {
      Assert.assertNotNull(singleVirtualTarget);
      Assert.assertEquals(
          toType(singleTargetHolderOrNull, appInfo), singleVirtualTarget.getHolderType());
    }
  }

  @Test
  public void lookupVirtualTargets() {
    DexMethod method = buildNullaryVoidMethod(invokeReceiver, methodName, appInfo.dexItemFactory());
    Assert.assertNotNull(appInfo.resolveMethodOnClassHolderLegacy(method).getSingleTarget());
    MethodResolutionResult resolutionResult = appInfo.resolveMethodOnClassHolderLegacy(method);
    if (resolutionResult.isVirtualTarget()) {
      LookupResult lookupResult =
          resolutionResult.lookupVirtualDispatchTargets(
              appView.definitionForProgramType(buildType(Main.class, appView.dexItemFactory())),
              appView);
      assertTrue(lookupResult.isLookupResultSuccess());
      assertFalse(lookupResult.asLookupResultSuccess().hasLambdaTargets());
      Set<DexType> targetHolders = Sets.newIdentityHashSet();
      lookupResult
          .asLookupResultSuccess()
          .forEach(
              methodTarget -> targetHolders.add(methodTarget.getHolder().type),
              lambdaTarget -> {
                assert false;
              });
      Assert.assertEquals(virtualTargetHolders.size(), targetHolders.size());
      assertTrue(
          virtualTargetHolders.stream()
              .map(t -> toType(t, appInfo))
              .allMatch(targetHolders::contains));
    } else {
      assertTrue(virtualTargetHolders.isEmpty());
    }
  }
}
