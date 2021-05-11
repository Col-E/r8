// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.horizontalclassmerging.policies.AllInstantiatedOrUninstantiated;
import com.android.tools.r8.horizontalclassmerging.policies.CheckAbstractClasses;
import com.android.tools.r8.horizontalclassmerging.policies.DontInlinePolicy;
import com.android.tools.r8.horizontalclassmerging.policies.DontMergeSynchronizedClasses;
import com.android.tools.r8.horizontalclassmerging.policies.LimitGroups;
import com.android.tools.r8.horizontalclassmerging.policies.MinimizeInstanceFieldCasts;
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
import com.android.tools.r8.horizontalclassmerging.policies.NoInterfaces;
import com.android.tools.r8.horizontalclassmerging.policies.NoKeepRules;
import com.android.tools.r8.horizontalclassmerging.policies.NoKotlinMetadata;
import com.android.tools.r8.horizontalclassmerging.policies.NoNativeMethods;
import com.android.tools.r8.horizontalclassmerging.policies.NoServiceLoaders;
import com.android.tools.r8.horizontalclassmerging.policies.NotMatchedByNoHorizontalClassMerging;
import com.android.tools.r8.horizontalclassmerging.policies.NotVerticallyMergedIntoSubtype;
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
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.shaking.KeepInfoCollection;
import com.android.tools.r8.shaking.RuntimeTypeCheckInfo;
import com.android.tools.r8.synthesis.SyntheticItems;
import com.android.tools.r8.utils.InternalOptions.HorizontalClassMergerOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedList;
import java.util.List;

public class HorizontalClassMerger {

  public enum Mode {
    INITIAL,
    FINAL;

    public boolean isInitial() {
      return this == INITIAL;
    }

    public boolean isFinal() {
      return this == FINAL;
    }
  }

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Mode mode;
  private final HorizontalClassMergerOptions options;

  private HorizontalClassMerger(AppView<? extends AppInfoWithClassHierarchy> appView, Mode mode) {
    this.appView = appView;
    this.mode = mode;
    this.options = appView.options().horizontalClassMergerOptions();
  }

  public static HorizontalClassMerger createForInitialClassMerging(
      AppView<AppInfoWithLiveness> appView) {
    return new HorizontalClassMerger(appView, Mode.INITIAL);
  }

  public static HorizontalClassMerger createForFinalClassMerging(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    return new HorizontalClassMerger(appView, Mode.FINAL);
  }

  public void runIfNecessary(RuntimeTypeCheckInfo runtimeTypeCheckInfo, Timing timing) {
    if (options.isEnabled(mode)) {
      timing.begin("HorizontalClassMerger (" + mode.toString() + ")");
      run(runtimeTypeCheckInfo, timing);
      timing.end();
    } else {
      appView.setHorizontallyMergedClasses(HorizontallyMergedClasses.empty(), mode);
    }
  }

  private void run(RuntimeTypeCheckInfo runtimeTypeCheckInfo, Timing timing) {
    // Run the policies on all program classes to produce a final grouping.
    List<Policy> policies = getPolicies(runtimeTypeCheckInfo);
    Collection<MergeGroup> groups = new PolicyExecutor().run(getInitialGroups(), policies, timing);

    // If there are no groups, then end horizontal class merging.
    if (groups.isEmpty()) {
      appView.setHorizontallyMergedClasses(HorizontallyMergedClasses.empty(), mode);
      return;
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
    appView.setHorizontallyMergedClasses(mergedClasses, mode);

    HorizontalClassMergerGraphLens horizontalClassMergerGraphLens =
        createLens(mergedClasses, lensBuilder, syntheticArgumentClass);

    // Prune keep info.
    KeepInfoCollection keepInfo = appView.getKeepInfo();
    keepInfo.mutate(mutator -> mutator.removeKeepInfoForPrunedItems(mergedClasses.getSources()));

    // Must rewrite AppInfoWithLiveness before pruning the merged classes, to ensure that allocation
    // sites, fields accesses, etc. are correctly transferred to the target classes.
    appView.rewriteWithLensAndApplication(
        horizontalClassMergerGraphLens, getNewApplication(mergedClasses));

    // Record where the synthesized $r8$classId fields are read and written.
    if (mode.isInitial()) {
      createFieldAccessInfoCollectionModifier(groups).modify(appView.withLiveness());
    } else {
      assert groups.stream().noneMatch(MergeGroup::hasClassIdField);
    }

    appView.pruneItems(
        PrunedItems.builder()
            .setPrunedApp(appView.appInfo().app())
            .addRemovedClasses(mergedClasses.getSources())
            .addNoLongerSyntheticItems(mergedClasses.getSources())
            .build());
  }

  private FieldAccessInfoCollectionModifier createFieldAccessInfoCollectionModifier(
      Collection<MergeGroup> groups) {
    assert mode.isInitial();
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

  private DirectMappedDexApplication getNewApplication(HorizontallyMergedClasses mergedClasses) {
    // In the second round of class merging, we must forcefully remove the merged classes from the
    // application, since we won't run tree shaking before writing the application.
    DirectMappedDexApplication application = appView.appInfo().app().asDirect();
    return mode.isInitial()
        ? application
        : application
            .builder()
            .removeProgramClasses(
                clazz -> mergedClasses.hasBeenMergedIntoDifferentType(clazz.getType()))
            .build();
  }

  private List<MergeGroup> getInitialGroups() {
    MergeGroup initialClassGroup = new MergeGroup();
    MergeGroup initialInterfaceGroup = new MergeGroup();
    HorizontalClassMergerOptions options = appView.options().horizontalClassMergerOptions();
    SyntheticItems syntheticItems = appView.getSyntheticItems();
    for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) {
      if (options.isRestrictedToSynthetics() && !syntheticItems.isSyntheticClass(clazz)) {
        continue;
      }
      if (clazz.isInterface()) {
        initialInterfaceGroup.add(clazz);
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
            new NoDeadEnumLiteMaps(appViewWithLiveness, mode),
            new NoAnnotationClasses(),
            new NoEnums(appView),
            new NoInterfaces(appView, mode),
            new NoInnerClasses(),
            new NoInstanceFieldAnnotations(),
            new NoClassInitializerWithObservableSideEffects(),
            new NoNativeMethods(),
            new NoKeepRules(appView),
            new NoKotlinMetadata(),
            new NoServiceLoaders(appView),
            new NotVerticallyMergedIntoSubtype(appView, mode),
            new NoDirectRuntimeTypeChecks(appView, runtimeTypeCheckInfo),
            new DontInlinePolicy(appViewWithLiveness, mode));
    List<Policy> multiClassPolicies =
        ImmutableList.of(
            new SameFeatureSplit(appView),
            new SameInstanceFields(appView),
            new SameMainDexGroup(appView),
            new SameNestHost(appView),
            new SameParentClass(),
            new NoClassAnnotationCollisions(),
            new CheckAbstractClasses(appView),
            new SyntheticItemsPolicy(appView),
            new NoIndirectRuntimeTypeChecks(appView, runtimeTypeCheckInfo),
            new PreventClassMethodAndDefaultMethodCollisions(appView),
            new AllInstantiatedOrUninstantiated(appViewWithLiveness, mode),
            new PreserveMethodCharacteristics(appViewWithLiveness, mode),
            new RespectPackageBoundaries(appView),
            new DontMergeSynchronizedClasses(appViewWithLiveness),
            new MinimizeInstanceFieldCasts(),
            new OnlyDirectlyConnectedOrUnrelatedInterfaces(appView, mode),
            new NoDefaultInterfaceMethodMerging(appView, mode),
            new NoDefaultInterfaceMethodCollisions(appView, mode),
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
    List<ClassMerger> classMergers = new ArrayList<>(groups.size());
    for (MergeGroup group : groups) {
      assert group.isNonTrivial();
      classMergers.add(new ClassMerger.Builder(appView, group).setMode(mode).build(lensBuilder));
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
