// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.policies.AllInstantiatedOrUninstantiated;
import com.android.tools.r8.horizontalclassmerging.policies.AtMostOneClassInitializer;
import com.android.tools.r8.horizontalclassmerging.policies.CheckAbstractClasses;
import com.android.tools.r8.horizontalclassmerging.policies.CheckSyntheticClasses;
import com.android.tools.r8.horizontalclassmerging.policies.LimitGroups;
import com.android.tools.r8.horizontalclassmerging.policies.MinimizeInstanceFieldCasts;
import com.android.tools.r8.horizontalclassmerging.policies.NoAnnotationClasses;
import com.android.tools.r8.horizontalclassmerging.policies.NoClassAnnotationCollisions;
import com.android.tools.r8.horizontalclassmerging.policies.NoClassInitializerWithObservableSideEffects;
import com.android.tools.r8.horizontalclassmerging.policies.NoConstructorCollisions;
import com.android.tools.r8.horizontalclassmerging.policies.NoDeadEnumLiteMaps;
import com.android.tools.r8.horizontalclassmerging.policies.NoDeadLocks;
import com.android.tools.r8.horizontalclassmerging.policies.NoDefaultInterfaceMethodCollisions;
import com.android.tools.r8.horizontalclassmerging.policies.NoDefaultInterfaceMethodMerging;
import com.android.tools.r8.horizontalclassmerging.policies.NoDirectRuntimeTypeChecks;
import com.android.tools.r8.horizontalclassmerging.policies.NoEnums;
import com.android.tools.r8.horizontalclassmerging.policies.NoIllegalInlining;
import com.android.tools.r8.horizontalclassmerging.policies.NoIndirectRuntimeTypeChecks;
import com.android.tools.r8.horizontalclassmerging.policies.NoInnerClasses;
import com.android.tools.r8.horizontalclassmerging.policies.NoInstanceFieldAnnotations;
import com.android.tools.r8.horizontalclassmerging.policies.NoInstanceInitializers;
import com.android.tools.r8.horizontalclassmerging.policies.NoInterfaces;
import com.android.tools.r8.horizontalclassmerging.policies.NoKeepRules;
import com.android.tools.r8.horizontalclassmerging.policies.NoKotlinMetadata;
import com.android.tools.r8.horizontalclassmerging.policies.NoNativeMethods;
import com.android.tools.r8.horizontalclassmerging.policies.NoNonPrivateVirtualMethods;
import com.android.tools.r8.horizontalclassmerging.policies.NoServiceLoaders;
import com.android.tools.r8.horizontalclassmerging.policies.NoVerticallyMergedClasses;
import com.android.tools.r8.horizontalclassmerging.policies.NotMatchedByNoHorizontalClassMerging;
import com.android.tools.r8.horizontalclassmerging.policies.OnlyDirectlyConnectedOrUnrelatedInterfaces;
import com.android.tools.r8.horizontalclassmerging.policies.PreserveMethodCharacteristics;
import com.android.tools.r8.horizontalclassmerging.policies.PreventClassMethodAndDefaultMethodCollisions;
import com.android.tools.r8.horizontalclassmerging.policies.RespectPackageBoundaries;
import com.android.tools.r8.horizontalclassmerging.policies.SameFeatureSplit;
import com.android.tools.r8.horizontalclassmerging.policies.SameInstanceFields;
import com.android.tools.r8.horizontalclassmerging.policies.SameMainDexGroup;
import com.android.tools.r8.horizontalclassmerging.policies.SameNestHost;
import com.android.tools.r8.horizontalclassmerging.policies.SameParentClass;
import com.android.tools.r8.horizontalclassmerging.policies.SyntheticItemsPolicy;
import com.android.tools.r8.horizontalclassmerging.policies.VerifyPolicyAlwaysSatisfied;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.RuntimeTypeCheckInfo;
import com.google.common.collect.ImmutableList;
import java.util.List;

public class PolicyScheduler {

  public static List<Policy> getPolicies(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Mode mode,
      RuntimeTypeCheckInfo runtimeTypeCheckInfo) {
    return ImmutableList.<Policy>builder()
        .addAll(getSingleClassPolicies(appView, mode, runtimeTypeCheckInfo))
        .addAll(getMultiClassPolicies(appView, mode, runtimeTypeCheckInfo))
        .build();
  }

  private static List<SingleClassPolicy> getSingleClassPolicies(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Mode mode,
      RuntimeTypeCheckInfo runtimeTypeCheckInfo) {
    ImmutableList.Builder<SingleClassPolicy> builder = ImmutableList.builder();

    addRequiredSingleClassPolicies(appView, builder);

    if (mode.isInitial()) {
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      builder.add(
          new NoDeadEnumLiteMaps(appViewWithLiveness, mode),
          new NoIllegalInlining(appViewWithLiveness, mode),
          new NoVerticallyMergedClasses(appViewWithLiveness, mode));
    } else {
      assert mode.isFinal();
      // TODO(b/181846319): Allow constructors, as long as the constructor protos remain unchanged
      //  (in particular, we can't add nulls at constructor call sites).
      // TODO(b/181846319): Allow virtual methods, as long as they do not require any merging.
      builder.add(new NoInstanceInitializers(mode), new NoNonPrivateVirtualMethods(mode));
    }

    if (appView.options().horizontalClassMergerOptions().isRestrictedToSynthetics()) {
      assert verifySingleClassPoliciesIrrelevantForMergingSynthetics(appView, mode, builder);
    } else {
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      addSingleClassPoliciesForMergingNonSyntheticClasses(
          appViewWithLiveness, mode, runtimeTypeCheckInfo, builder);
    }

    return builder.build();
  }

  private static void addRequiredSingleClassPolicies(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmutableList.Builder<SingleClassPolicy> builder) {
    builder.add(
        new CheckSyntheticClasses(appView),
        new NoKeepRules(appView),
        new NoClassInitializerWithObservableSideEffects());
  }

  private static void addSingleClassPoliciesForMergingNonSyntheticClasses(
      AppView<AppInfoWithLiveness> appView,
      Mode mode,
      RuntimeTypeCheckInfo runtimeTypeCheckInfo,
      ImmutableList.Builder<SingleClassPolicy> builder) {
    builder.add(
        new NotMatchedByNoHorizontalClassMerging(appView),
        new NoAnnotationClasses(),
        new NoDirectRuntimeTypeChecks(appView, mode, runtimeTypeCheckInfo),
        new NoEnums(appView),
        new NoInterfaces(appView, mode),
        new NoInnerClasses(),
        new NoInstanceFieldAnnotations(),
        new NoKotlinMetadata(),
        new NoNativeMethods(),
        new NoServiceLoaders(appView));
  }

  private static boolean verifySingleClassPoliciesIrrelevantForMergingSynthetics(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Mode mode,
      ImmutableList.Builder<SingleClassPolicy> builder) {
    List<SingleClassPolicy> policies =
        ImmutableList.of(
            new NoAnnotationClasses(),
            new NoDirectRuntimeTypeChecks(appView, mode),
            new NoEnums(appView),
            new NoInterfaces(appView, mode),
            new NoInnerClasses(),
            new NoInstanceFieldAnnotations(),
            new NoKotlinMetadata(),
            new NoNativeMethods(),
            new NoServiceLoaders(appView));
    policies.stream().map(VerifyPolicyAlwaysSatisfied::new).forEach(builder::add);
    return true;
  }

  private static List<Policy> getMultiClassPolicies(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Mode mode,
      RuntimeTypeCheckInfo runtimeTypeCheckInfo) {
    ImmutableList.Builder<Policy> builder = ImmutableList.builder();

    addRequiredMultiClassPolicies(appView, mode, builder);

    if (!appView.options().horizontalClassMergerOptions().isRestrictedToSynthetics()) {
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      addMultiClassPoliciesForMergingNonSyntheticClasses(
          appViewWithLiveness, runtimeTypeCheckInfo, builder);
    }

    if (mode.isInitial()) {
      AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
      builder.add(
          new AllInstantiatedOrUninstantiated(appViewWithLiveness, mode),
          new PreserveMethodCharacteristics(appViewWithLiveness, mode),
          new MinimizeInstanceFieldCasts());
    } else {
      assert mode.isFinal();
      // TODO(b/185472598): Add support for merging class initializers with dex code.
      builder.add(new AtMostOneClassInitializer(mode), new NoConstructorCollisions(appView, mode));
    }

    addMultiClassPoliciesForInterfaceMerging(appView, mode, builder);

    return builder.add(new LimitGroups(appView)).build();
  }

  private static void addRequiredMultiClassPolicies(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Mode mode,
      ImmutableList.Builder<Policy> builder) {
    builder.add(
        new CheckAbstractClasses(appView),
        new NoClassAnnotationCollisions(),
        new SameFeatureSplit(appView),
        new SameInstanceFields(appView, mode),
        new SameMainDexGroup(appView),
        new SameNestHost(appView),
        new SameParentClass(),
        new SyntheticItemsPolicy(appView, mode),
        new RespectPackageBoundaries(appView),
        new PreventClassMethodAndDefaultMethodCollisions(appView));
  }

  private static void addMultiClassPoliciesForMergingNonSyntheticClasses(
      AppView<AppInfoWithLiveness> appView,
      RuntimeTypeCheckInfo runtimeTypeCheckInfo,
      ImmutableList.Builder<Policy> builder) {
    builder.add(
        new NoDeadLocks(appView), new NoIndirectRuntimeTypeChecks(appView, runtimeTypeCheckInfo));
  }

  private static void addMultiClassPoliciesForInterfaceMerging(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Mode mode,
      ImmutableList.Builder<Policy> builder) {
    builder.add(
        new OnlyDirectlyConnectedOrUnrelatedInterfaces(appView, mode),
        new NoDefaultInterfaceMethodMerging(appView, mode),
        new NoDefaultInterfaceMethodCollisions(appView, mode));
  }
}
