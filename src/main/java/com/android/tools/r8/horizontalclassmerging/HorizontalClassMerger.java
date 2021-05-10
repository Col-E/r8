// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.policies.AllInstantiatedOrUninstantiated;
import com.android.tools.r8.horizontalclassmerging.policies.CheckAbstractClasses;
import com.android.tools.r8.horizontalclassmerging.policies.DontInlinePolicy;
import com.android.tools.r8.horizontalclassmerging.policies.DontMergeSynchronizedClasses;
import com.android.tools.r8.horizontalclassmerging.policies.LimitGroups;
import com.android.tools.r8.horizontalclassmerging.policies.MinimizeFieldCasts;
import com.android.tools.r8.horizontalclassmerging.policies.NoAnnotationClasses;
import com.android.tools.r8.horizontalclassmerging.policies.NoClassAnnotationCollisions;
import com.android.tools.r8.horizontalclassmerging.policies.NoClassInitializerWithObservableSideEffects;
import com.android.tools.r8.horizontalclassmerging.policies.NoDeadEnumLiteMaps;
import com.android.tools.r8.horizontalclassmerging.policies.NoDefaultInterfaceMethodCollisions;
import com.android.tools.r8.horizontalclassmerging.policies.NoDefaultInterfaceMethodMerging;
import com.android.tools.r8.horizontalclassmerging.policies.NoDirectRuntimeTypeChecks;
import com.android.tools.r8.horizontalclassmerging.policies.NoEnums;
import com.android.tools.r8.horizontalclassmerging.policies.NoIndirectRuntimeTypeChecks;
import com.android.tools.r8.horizontalclassmerging.policies.NoInnerClasses;
import com.android.tools.r8.horizontalclassmerging.policies.NoInstanceFieldAnnotations;
import com.android.tools.r8.horizontalclassmerging.policies.NoKeepRules;
import com.android.tools.r8.horizontalclassmerging.policies.NoKotlinMetadata;
import com.android.tools.r8.horizontalclassmerging.policies.NoNativeMethods;
import com.android.tools.r8.horizontalclassmerging.policies.NoServiceLoaders;
import com.android.tools.r8.horizontalclassmerging.policies.NotMatchedByNoHorizontalClassMerging;
import com.android.tools.r8.horizontalclassmerging.policies.NotVerticallyMergedIntoSubtype;
import com.android.tools.r8.horizontalclassmerging.policies.OnlyDirectlyConnectedOrUnrelatedInterfaces;
import com.android.tools.r8.horizontalclassmerging.policies.PreserveMethodCharacteristics;
import com.android.tools.r8.horizontalclassmerging.policies.PreventClassMethodAndDefaultMethodCollisions;
import com.android.tools.r8.horizontalclassmerging.policies.PreventMergeIntoDifferentMainDexGroups;
import com.android.tools.r8.horizontalclassmerging.policies.RespectPackageBoundaries;
import com.android.tools.r8.horizontalclassmerging.policies.SameFeatureSplit;
import com.android.tools.r8.horizontalclassmerging.policies.SameInstanceFields;
import com.android.tools.r8.horizontalclassmerging.policies.SameNestHost;
import com.android.tools.r8.horizontalclassmerging.policies.SameParentClass;
import com.android.tools.r8.horizontalclassmerging.policies.SyntheticItemsPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.shaking.RuntimeTypeCheckInfo;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class HorizontalClassMerger {

  // TODO(b/181846319): Add 'FINAL' mode that runs after synthetic finalization.
  public enum Mode {
    INITIAL;

    public boolean isInitial() {
      return this == INITIAL;
    }
  }

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Mode mode = Mode.INITIAL;

  public HorizontalClassMerger(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    assert appView.options().enableInlining;
  }

  public HorizontalClassMergerResult run(RuntimeTypeCheckInfo runtimeTypeCheckInfo, Timing timing) {
    // Run the policies on all program classes to produce a final grouping.
    List<Policy> policies = getPolicies(runtimeTypeCheckInfo);
    Collection<MergeGroup> groups = new PolicyExecutor().run(getInitialGroups(), policies, timing);

    // If there are no groups, then end horizontal class merging.
    if (groups.isEmpty()) {
      appView.setHorizontallyMergedClasses(HorizontallyMergedClasses.empty());
      return null;
    }

    HorizontalClassMergerGraphLens.Builder lensBuilder =
        new HorizontalClassMergerGraphLens.Builder();

    // Merge the classes.
    List<ClassMerger> classMergers = initializeClassMergers(lensBuilder, groups);
    SyntheticArgumentClass syntheticArgumentClass =
        mode.isInitial()
            ? new SyntheticArgumentClass.Builder(appView.withLiveness()).build(groups)
            : null;
    applyClassMergers(classMergers, syntheticArgumentClass);

    // Generate the graph lens.
    HorizontallyMergedClasses mergedClasses =
        HorizontallyMergedClasses.builder().addMergeGroups(groups).build();
    appView.setHorizontallyMergedClasses(mergedClasses);
    HorizontalClassMergerGraphLens lens =
        createLens(mergedClasses, lensBuilder, syntheticArgumentClass);

    // Prune keep info.
    appView
        .getKeepInfo()
        .mutate(mutator -> mutator.removeKeepInfoForPrunedItems(mergedClasses.getSources()));

    return new HorizontalClassMergerResult(createFieldAccessInfoCollectionModifier(groups), lens);
  }

  private FieldAccessInfoCollectionModifier createFieldAccessInfoCollectionModifier(
      Collection<MergeGroup> groups) {
    FieldAccessInfoCollectionModifier.Builder builder =
        new FieldAccessInfoCollectionModifier.Builder();
    for (MergeGroup group : groups) {
      if (group.hasClassIdField()) {
        DexProgramClass target = group.getTarget();
        target.forEachProgramInstanceInitializerMatching(
            definition -> definition.getCode().isHorizontalClassMergingCode(),
            method -> builder.recordFieldWrittenInContext(group.getClassIdField(), method));
        target.forEachProgramVirtualMethodMatching(
            definition ->
                definition.hasCode() && definition.getCode().isHorizontalClassMergingCode(),
            method -> builder.recordFieldReadInContext(group.getClassIdField(), method));
      }
    }
    return builder.build();
  }

  private List<MergeGroup> getInitialGroups() {
    MergeGroup initialClassGroup = new MergeGroup();
    MergeGroup initialInterfaceGroup = new MergeGroup();
    for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) {
      if (clazz.isInterface()) {
        if (appView.options().horizontalClassMergerOptions().isInterfaceMergingEnabled()) {
          initialInterfaceGroup.add(clazz);
        }
      } else {
        initialClassGroup.add(clazz);
      }
    }
    List<MergeGroup> initialGroups = new LinkedList<>();
    initialGroups.add(initialClassGroup);
    initialGroups.add(initialInterfaceGroup);
    initialGroups.removeIf(MergeGroup::isTrivial);
    return initialGroups;
  }

  private List<Policy> getPolicies(RuntimeTypeCheckInfo runtimeTypeCheckInfo) {
    AppView<AppInfoWithLiveness> appViewWithLiveness = appView.withLiveness();
    List<SingleClassPolicy> singleClassPolicies =
        ImmutableList.of(
            new NotMatchedByNoHorizontalClassMerging(appViewWithLiveness),
            new NoDeadEnumLiteMaps(appViewWithLiveness),
            new NoAnnotationClasses(),
            new NoEnums(appView),
            new NoInnerClasses(),
            new NoInstanceFieldAnnotations(),
            new NoClassInitializerWithObservableSideEffects(),
            new NoNativeMethods(),
            new NoKeepRules(appView),
            new NoKotlinMetadata(),
            new NoServiceLoaders(appView),
            new NotVerticallyMergedIntoSubtype(appView),
            new NoDirectRuntimeTypeChecks(appView, runtimeTypeCheckInfo),
            new DontInlinePolicy(appViewWithLiveness));
    List<Policy> multiClassPolicies =
        ImmutableList.of(
            new SameInstanceFields(appView),
            new NoClassAnnotationCollisions(),
            new CheckAbstractClasses(appView),
            new SyntheticItemsPolicy(appView),
            new NoIndirectRuntimeTypeChecks(appView, runtimeTypeCheckInfo),
            new PreventClassMethodAndDefaultMethodCollisions(appView),
            new PreventMergeIntoDifferentMainDexGroups(appView),
            new AllInstantiatedOrUninstantiated(appViewWithLiveness),
            new SameParentClass(),
            new SameNestHost(appView),
            new PreserveMethodCharacteristics(appViewWithLiveness),
            new SameFeatureSplit(appView),
            new RespectPackageBoundaries(appView),
            new DontMergeSynchronizedClasses(appViewWithLiveness),
            new MinimizeFieldCasts(),
            new OnlyDirectlyConnectedOrUnrelatedInterfaces(appView),
            new NoDefaultInterfaceMethodMerging(appView),
            new NoDefaultInterfaceMethodCollisions(appView),
            new LimitGroups(appView));
    return ImmutableList.<Policy>builder()
        .addAll(singleClassPolicies)
        .addAll(multiClassPolicies)
        .build();
  }

  /**
   * Prepare horizontal class merging by determining which virtual methods and constructors need to
   * be merged and how the merging should be performed.
   */
  private List<ClassMerger> initializeClassMergers(
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      Collection<MergeGroup> groups) {
    List<ClassMerger> classMergers = new ArrayList<>();

    // TODO(b/166577694): Replace Collection<DexProgramClass> with MergeGroup
    for (MergeGroup group : groups) {
      assert !group.isEmpty();
      ClassMerger merger = new ClassMerger.Builder(appView, group).build(lensBuilder);
      classMergers.add(merger);
    }

    return classMergers;
  }

  /** Merges all class groups using {@link ClassMerger}. */
  private void applyClassMergers(
      Collection<ClassMerger> classMergers, SyntheticArgumentClass syntheticArgumentClass) {
    for (ClassMerger merger : classMergers) {
      merger.mergeGroup(syntheticArgumentClass);
    }
  }

  /**
   * Fix all references to merged classes using the {@link TreeFixer}. Construct a graph lens
   * containing all changes performed by horizontal class merging.
   */
  private HorizontalClassMergerGraphLens createLens(
      HorizontallyMergedClasses mergedClasses,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      SyntheticArgumentClass syntheticArgumentClass) {
    return new TreeFixer(appView, mergedClasses, lensBuilder, syntheticArgumentClass)
        .fixupTypeReferences();
  }
}
