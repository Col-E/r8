// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resolution;

import com.android.tools.r8.AsmTestBase;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.resolution.singletarget.Main;
import com.android.tools.r8.resolution.singletarget.one.AbstractSubClass;
import com.android.tools.r8.resolution.singletarget.one.AbstractTopClass;
import com.android.tools.r8.resolution.singletarget.one.InterfaceWithDefault;
import com.android.tools.r8.resolution.singletarget.one.IrrelevantInterfaceWithDefault;
import com.android.tools.r8.resolution.singletarget.one.IrrelevantInterfaceWithDefaultDump;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassOne;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassThree;
import com.android.tools.r8.resolution.singletarget.one.SubSubClassTwo;
import com.android.tools.r8.resolution.singletarget.three.ThirdAbstractTopClass;
import com.android.tools.r8.resolution.singletarget.three.ThirdSubClassOne;
import com.android.tools.r8.resolution.singletarget.three.ThirdSubClassTwo;
import com.android.tools.r8.resolution.singletarget.three.ThirdSubClassTwoDump;
import com.android.tools.r8.resolution.singletarget.two.OtherAbstractSubClassOne;
import com.android.tools.r8.resolution.singletarget.two.OtherAbstractSubClassTwo;
import com.android.tools.r8.resolution.singletarget.two.OtherAbstractTopClass;
import com.android.tools.r8.resolution.singletarget.two.OtherSubSubClassOne;
import com.android.tools.r8.resolution.singletarget.two.OtherSubSubClassTwo;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ProguardClassNameList;
import com.android.tools.r8.shaking.ProguardConfigurationRule;
import com.android.tools.r8.shaking.ProguardKeepRule;
import com.android.tools.r8.shaking.ProguardKeepRule.Builder;
import com.android.tools.r8.shaking.ProguardKeepRuleType;
import com.android.tools.r8.shaking.ProguardTypeMatcher;
import com.android.tools.r8.shaking.RootSetBuilder;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class SingleTargetLookupTest extends AsmTestBase {

  /**
   * Initialized in @Before rule.
   */
  public static AppInfoWithLiveness appInfo;

  public static List<Class> CLASSES = ImmutableList.of(
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
      Main.class
  );

  public static List<byte[]> ASM_CLASSES = ImmutableList.of(
      getBytesFromAsmClass(IrrelevantInterfaceWithDefaultDump::dump),
      getBytesFromAsmClass(ThirdSubClassTwoDump::dump)
  );

  public SingleTargetLookupTest(String methodName, Class invokeReceiver,
      Class singleTargetHolderOrNull, List<Class> allTargetHolders) {
    this.methodName = methodName;
    this.invokeReceiver = invokeReceiver;
    this.singleTargetHolderOrNull = singleTargetHolderOrNull;
    this.allTargetHolders = allTargetHolders;
  }

  @BeforeClass
  public static void computeAppInfo() throws Exception {
    // Run the tree shaker to compute an instance of AppInfoWithLiveness.
    Timing timing = new Timing(SingleTargetLookupTest.class.getCanonicalName());
    InternalOptions options = new InternalOptions();
    AndroidApp app = readClassesAndAsmDump(CLASSES, ASM_CLASSES);
    DexApplication application = new ApplicationReader(app, options, timing).read().toDirect();
    AppInfoWithSubtyping appInfoWithSubtyping = new AppInfoWithSubtyping(application);

    ExecutorService executor = Executors.newSingleThreadExecutor();
    RootSet rootSet = new RootSetBuilder(appInfoWithSubtyping, application,
        buildKeepRuleForClass(Main.class, application.dexItemFactory), options).run(executor);
    appInfo = new Enqueuer(appInfoWithSubtyping, options, options.forceProguardCompatibility)
        .traceApplication(rootSet, executor, timing);
    // We do not run the tree pruner to ensure that the hierarchy is as designed and not modified
    // due to liveness.
  }

  private static List<ProguardConfigurationRule> buildKeepRuleForClass(Class clazz,
      DexItemFactory factory) {
    Builder keepRuleBuilder = ProguardKeepRule.builder();
    keepRuleBuilder.setType(ProguardKeepRuleType.KEEP);
    keepRuleBuilder.setClassNames(
        ProguardClassNameList.singletonList(ProguardTypeMatcher.create(factory
            .createType(DescriptorUtils.javaTypeToDescriptor(clazz.getCanonicalName())))));
    return Collections.singletonList(keepRuleBuilder.build());
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
    return ImmutableList.copyOf(new Object[][]{
        singleTarget("singleTargetAtTop", AbstractTopClass.class),
        singleTargetWithAbstracts("singleShadowingOverride", AbstractTopClass.class,
            AbstractSubClass.class, AbstractTopClass.class),
        manyTargets("abstractTargetAtTop", AbstractTopClass.class, AbstractTopClass.class,
            SubSubClassOne.class, SubSubClassTwo.class),
        singleTargetWithAbstracts("overridenInAbstractClassOnly", AbstractTopClass.class,
            AbstractTopClass.class, SubSubClassThree.class),
        onlyUnreachableTargets("overridenInAbstractClassOnly", SubSubClassThree.class,
            SubSubClassThree.class),
        manyTargets("overriddenInTwoSubTypes", AbstractTopClass.class, AbstractTopClass.class,
            SubSubClassOne.class, SubSubClassTwo.class),
        manyTargets("definedInTwoSubTypes", AbstractTopClass.class, AbstractTopClass.class,
            SubSubClassOne.class, SubSubClassTwo.class),
        onlyUnreachableTargets("staticMethod", AbstractTopClass.class, AbstractTopClass.class),
        manyTargets("overriddenInTwoSubTypes", OtherAbstractTopClass.class,
            OtherAbstractTopClass.class, OtherSubSubClassOne.class, OtherSubSubClassTwo.class),
        manyTargets("abstractOverriddenInTwoSubTypes", OtherAbstractTopClass.class,
            OtherAbstractTopClass.class, OtherSubSubClassOne.class, OtherSubSubClassTwo.class),
        manyTargets("overridesOnDifferentLevels", OtherAbstractTopClass.class,
            OtherAbstractTopClass.class, OtherSubSubClassOne.class, OtherAbstractSubClassTwo.class),
        singleTarget("defaultMethod", AbstractTopClass.class, InterfaceWithDefault.class),
        manyTargets("overriddenDefault", AbstractTopClass.class, InterfaceWithDefault.class,
            SubSubClassTwo.class),
        singleTarget("overriddenDefault", SubSubClassTwo.class),
        singleTarget("overriddenByIrrelevantInterface", AbstractTopClass.class),
        singleTarget("overriddenByIrrelevantInterface", SubSubClassOne.class,
            AbstractTopClass.class),
        manyTargets("overriddenInOtherInterface", AbstractTopClass.class,
            InterfaceWithDefault.class,
            IrrelevantInterfaceWithDefault.class),
        manyTargets("overriddenInOtherInterface", SubSubClassOne.class,
            InterfaceWithDefault.class,
            IrrelevantInterfaceWithDefault.class),
        manyTargets("abstractMethod", ThirdAbstractTopClass.class, ThirdAbstractTopClass.class,
            ThirdSubClassOne.class),
        manyTargets("instanceMethod", ThirdAbstractTopClass.class, ThirdAbstractTopClass.class,
            ThirdSubClassTwo.class),
        manyTargets("otherInstanceMethod", ThirdAbstractTopClass.class, ThirdAbstractTopClass.class,
            ThirdSubClassTwo.class),
    });
  }

  private static DexMethod buildMethod(Class clazz, String name) {
    return appInfo.dexItemFactory.createMethod(
        toType(clazz),
        appInfo.dexItemFactory.createProto(appInfo.dexItemFactory.voidType),
        name
    );
  }

  private static DexType toType(Class clazz) {
    return appInfo.dexItemFactory
        .createType(DescriptorUtils.javaTypeToDescriptor(clazz.getCanonicalName()));
  }

  private final String methodName;
  private final Class invokeReceiver;
  private final Class singleTargetHolderOrNull;
  private final List<Class> allTargetHolders;

  @Test
  public void lookupSingleTarget() {
    DexMethod method = buildMethod(invokeReceiver, methodName);
    Assert.assertNotNull(appInfo.resolveMethod(toType(invokeReceiver), method).asResultOfResolve());
    DexEncodedMethod singleVirtualTarget = appInfo.lookupSingleVirtualTarget(method);
    if (singleTargetHolderOrNull == null) {
      Assert.assertNull(singleVirtualTarget);
    } else {
      Assert.assertNotNull(singleVirtualTarget);
      Assert.assertEquals(toType(singleTargetHolderOrNull), singleVirtualTarget.method.holder);
    }
  }

  @Test
  public void lookupVirtualTargets() {
    DexMethod method = buildMethod(invokeReceiver, methodName);
    Assert.assertNotNull(appInfo.resolveMethod(toType(invokeReceiver), method).asResultOfResolve());
    Set<DexEncodedMethod> targets = appInfo.lookupVirtualTargets(method);
    Set<DexType> targetHolders = targets.stream().map(m -> m.method.holder)
        .collect(Collectors.toSet());
    Assert.assertEquals(allTargetHolders.size(), targetHolders.size());
    Assert.assertTrue(allTargetHolders.stream().map(SingleTargetLookupTest::toType)
        .allMatch(targetHolders::contains));
  }
}
