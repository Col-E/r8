// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.horizontalclassmerging.policies.AllInstantiatedOrUninstantiated;
import com.android.tools.r8.horizontalclassmerging.policies.CheckAbstractClasses;
import com.android.tools.r8.horizontalclassmerging.policies.DontInlinePolicy;
import com.android.tools.r8.horizontalclassmerging.policies.DontMergeSynchronizedClasses;
import com.android.tools.r8.horizontalclassmerging.policies.IgnoreSynthetics;
import com.android.tools.r8.horizontalclassmerging.policies.LimitGroups;
import com.android.tools.r8.horizontalclassmerging.policies.NoAnnotations;
import com.android.tools.r8.horizontalclassmerging.policies.NoClassInitializerWithObservableSideEffects;
import com.android.tools.r8.horizontalclassmerging.policies.NoClassesOrMembersWithAnnotations;
import com.android.tools.r8.horizontalclassmerging.policies.NoDirectRuntimeTypeChecks;
import com.android.tools.r8.horizontalclassmerging.policies.NoEnums;
import com.android.tools.r8.horizontalclassmerging.policies.NoIndirectRuntimeTypeChecks;
import com.android.tools.r8.horizontalclassmerging.policies.NoInnerClasses;
import com.android.tools.r8.horizontalclassmerging.policies.NoInterfaces;
import com.android.tools.r8.horizontalclassmerging.policies.NoKeepRules;
import com.android.tools.r8.horizontalclassmerging.policies.NoKotlinLambdas;
import com.android.tools.r8.horizontalclassmerging.policies.NoKotlinMetadata;
import com.android.tools.r8.horizontalclassmerging.policies.NoNativeMethods;
import com.android.tools.r8.horizontalclassmerging.policies.NoServiceLoaders;
import com.android.tools.r8.horizontalclassmerging.policies.NotMatchedByNoHorizontalClassMerging;
import com.android.tools.r8.horizontalclassmerging.policies.NotVerticallyMergedIntoSubtype;
import com.android.tools.r8.horizontalclassmerging.policies.PreserveMethodCharacteristics;
import com.android.tools.r8.horizontalclassmerging.policies.PreventMergeIntoMainDex;
import com.android.tools.r8.horizontalclassmerging.policies.PreventMethodImplementation;
import com.android.tools.r8.horizontalclassmerging.policies.RespectPackageBoundaries;
import com.android.tools.r8.horizontalclassmerging.policies.SameFeatureSplit;
import com.android.tools.r8.horizontalclassmerging.policies.SameFields;
import com.android.tools.r8.horizontalclassmerging.policies.SameNestHost;
import com.android.tools.r8.horizontalclassmerging.policies.SameParentClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.shaking.MainDexTracingResult;
import com.android.tools.r8.shaking.RuntimeTypeCheckInfo;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class HorizontalClassMerger {
  private final AppView<AppInfoWithLiveness> appView;

  public HorizontalClassMerger(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    assert appView.options().enableInlining;
  }

  // TODO(b/165577835): replace Collection<DexProgramClass> with MergeGroup
  public HorizontalClassMergerGraphLens run(
      DirectMappedDexApplication.Builder appBuilder,
      MainDexTracingResult mainDexTracingResult,
      RuntimeTypeCheckInfo runtimeTypeCheckInfo) {
    MergeGroup initialGroup = new MergeGroup(appView.appInfo().classesWithDeterministicOrder());

    // Run the policies on all program classes to produce a final grouping.
    List<Policy> policies = getPolicies(mainDexTracingResult, runtimeTypeCheckInfo);
    Collection<MergeGroup> groups =
        new SimplePolicyExecutor().run(Collections.singletonList(initialGroup), policies);

    // If there are no groups, then end horizontal class merging.
    if (groups.isEmpty()) {
      appView.setHorizontallyMergedClasses(HorizontallyMergedClasses.empty());
      return null;
    }

    HorizontallyMergedClasses.Builder mergedClassesBuilder =
        new HorizontallyMergedClasses.Builder();
    HorizontalClassMergerGraphLens.Builder lensBuilder =
        new HorizontalClassMergerGraphLens.Builder();
    FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder =
        new FieldAccessInfoCollectionModifier.Builder();

    // Set up a class merger for each group.
    List<ClassMerger> classMergers =
        initializeClassMergers(
            mergedClassesBuilder, lensBuilder, fieldAccessChangesBuilder, groups);
    Iterable<DexProgramClass> allMergeClasses =
        Iterables.concat(
            Iterables.transform(classMergers, classMerger -> classMerger.getGroup().getClasses()));

    // Merge the classes.
    SyntheticArgumentClass syntheticArgumentClass =
        new SyntheticArgumentClass.Builder().build(appView, appBuilder, allMergeClasses);
    applyClassMergers(classMergers, syntheticArgumentClass);

    // Generate the class lens.
    HorizontallyMergedClasses mergedClasses = mergedClassesBuilder.build();
    appView.setHorizontallyMergedClasses(mergedClasses);
    return createLens(
        mergedClasses, lensBuilder, fieldAccessChangesBuilder, syntheticArgumentClass);
  }

  private List<Policy> getPolicies(
      MainDexTracingResult mainDexTracingResult,
      RuntimeTypeCheckInfo runtimeTypeCheckInfo) {
    return ImmutableList.of(
        new NotMatchedByNoHorizontalClassMerging(appView),
        new SameFields(),
        new NoInterfaces(),
        new NoAnnotations(),
        new NoEnums(appView),
        new CheckAbstractClasses(appView),
        new IgnoreSynthetics(appView),
        new NoClassesOrMembersWithAnnotations(),
        new NoInnerClasses(),
        new NoClassInitializerWithObservableSideEffects(),
        new NoNativeMethods(),
        new NoKeepRules(appView),
        new NoKotlinMetadata(),
        new NoKotlinLambdas(appView),
        new NoServiceLoaders(appView),
        new NotVerticallyMergedIntoSubtype(appView),
        new NoDirectRuntimeTypeChecks(runtimeTypeCheckInfo),
        new NoIndirectRuntimeTypeChecks(appView, runtimeTypeCheckInfo),
        new PreventMethodImplementation(appView),
        new DontInlinePolicy(appView, mainDexTracingResult),
        new PreventMergeIntoMainDex(appView, mainDexTracingResult),
        new AllInstantiatedOrUninstantiated(appView),
        new SameParentClass(),
        new SameNestHost(),
        new PreserveMethodCharacteristics(),
        new SameFeatureSplit(appView),
        new RespectPackageBoundaries(appView),
        new DontMergeSynchronizedClasses(appView),
        new LimitGroups(appView));
  }

  /**
   * Prepare horizontal class merging by determining which virtual methods and constructors need to
   * be merged and how the merging should be performed.
   */
  private List<ClassMerger> initializeClassMergers(
      HorizontallyMergedClasses.Builder mergedClassesBuilder,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder,
      Collection<MergeGroup> groups) {
    List<ClassMerger> classMergers = new ArrayList<>();

    // TODO(b/166577694): Replace Collection<DexProgramClass> with MergeGroup
    for (MergeGroup group : groups) {
      assert !group.isEmpty();
      ClassMerger merger =
          new ClassMerger.Builder(appView, group)
              .build(mergedClassesBuilder, lensBuilder, fieldAccessChangesBuilder);
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
      FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder,
      SyntheticArgumentClass syntheticArgumentClass) {

    HorizontalClassMergerGraphLens lens =
        new TreeFixer(
                appView,
                mergedClasses,
                lensBuilder,
                fieldAccessChangesBuilder,
                syntheticArgumentClass)
            .fixupTypeReferences();
    return lens;
  }
}
