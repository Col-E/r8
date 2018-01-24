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
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
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
      Class targetHolderOrNull) {
    this.methodName = methodName;
    this.invokeReceiver = invokeReceiver;
    this.targetHolderOrNull = targetHolderOrNull;
  }

  @BeforeClass
  public static void computeAppInfo() throws Exception {
    // Run the tree shaker to compute an instance of AppInfoWithLiveness.
    Timing timing = new Timing(SingleTargetLookupTest.class.getCanonicalName());
    InternalOptions options = new InternalOptions();
    AndroidApp app = readClassesAndAsmDump(CLASSES, ASM_CLASSES);
    DexApplication application = new ApplicationReader(app, options, timing).read().toDirect();
    AppInfoWithSubtyping appInfoWithSubtyping = new AppInfoWithSubtyping(application);

    RootSet rootSet = new RootSetBuilder(application, appInfoWithSubtyping,
        buildKeepRuleForClass(Main.class, application.dexItemFactory), options).run(
        Executors.newSingleThreadExecutor());
    appInfo = new Enqueuer(appInfoWithSubtyping, options).traceApplication(rootSet, timing);
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

  @Parameters(name = "{1}.{0} -> {2}")
  public static List<Object[]> getData() {
    return ImmutableList.copyOf(new Object[][]{
        {"singleTargetAtTop", AbstractTopClass.class, AbstractTopClass.class},
        {"singleShadowingOverride", AbstractTopClass.class, AbstractSubClass.class},
        {"abstractTargetAtTop", AbstractTopClass.class, null},
        {"overridenInAbstractClassOnly", AbstractTopClass.class, AbstractTopClass.class},
        {"overridenInAbstractClassOnly", SubSubClassThree.class, null},
        {"overriddenInTwoSubTypes", AbstractTopClass.class, null},
        {"definedInTwoSubTypes", AbstractTopClass.class, null},
        {"staticMethod", AbstractTopClass.class, null},
        {"overriddenInTwoSubTypes", OtherAbstractTopClass.class, null},
        {"abstractOverriddenInTwoSubTypes", OtherAbstractTopClass.class, null},
        {"overridesOnDifferentLevels", OtherAbstractTopClass.class, null},
        {"defaultMethod", AbstractTopClass.class, InterfaceWithDefault.class},
        {"overriddenDefault", AbstractTopClass.class, null},
        {"overriddenDefault", SubSubClassTwo.class, SubSubClassTwo.class},
        {"overriddenByIrrelevantInterface", AbstractTopClass.class, AbstractTopClass.class},
        {"overriddenByIrrelevantInterface", SubSubClassOne.class, AbstractTopClass.class},
        {"overriddenInOtherInterface", AbstractTopClass.class, null},
        {"overriddenInOtherInterface", SubSubClassOne.class, null},
        {"abstractMethod", ThirdAbstractTopClass.class, null},
        {"instanceMethod", ThirdAbstractTopClass.class, null},
        {"otherInstanceMethod", ThirdAbstractTopClass.class, null}
    });
  }

  private static DexMethod buildMethod(Class clazz, String name) {
    return appInfo.dexItemFactory.createMethod(
        toType(clazz),
        appInfo.dexItemFactory.createProto(appInfo.dexItemFactory.voidType),
        name
    );
  }

  protected static DexType toType(Class clazz) {
    return appInfo.dexItemFactory
        .createType(DescriptorUtils.javaTypeToDescriptor(clazz.getCanonicalName()));
  }

  public final String methodName;
  public final Class invokeReceiver;
  public final Class targetHolderOrNull;

  @Test
  public void lookupSingleTarget() {
    DexMethod method = buildMethod(invokeReceiver, methodName);
    Assert.assertNotNull(appInfo.resolveMethod(toType(invokeReceiver), method).asResultOfResolve());
    DexEncodedMethod singleVirtualTarget = appInfo.lookupSingleVirtualTarget(method);
    if (targetHolderOrNull == null) {
      Assert.assertNull(singleVirtualTarget);
    } else {
      Assert.assertNotNull(singleVirtualTarget);
      Assert.assertEquals(toType(targetHolderOrNull), singleVirtualTarget.method.holder);
    }
  }
}
