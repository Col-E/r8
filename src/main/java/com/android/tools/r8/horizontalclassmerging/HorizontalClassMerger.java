// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.horizontalclassmerging.policies.DontMergeIntoLessVisible;
import com.android.tools.r8.horizontalclassmerging.policies.DontMergeSynchronizedClasses;
import com.android.tools.r8.horizontalclassmerging.policies.NoAbstractClasses;
import com.android.tools.r8.horizontalclassmerging.policies.NoAnnotations;
import com.android.tools.r8.horizontalclassmerging.policies.NoClassesOrMembersWithAnnotations;
import com.android.tools.r8.horizontalclassmerging.policies.NoClassesWithInterfaces;
import com.android.tools.r8.horizontalclassmerging.policies.NoFields;
import com.android.tools.r8.horizontalclassmerging.policies.NoInnerClasses;
import com.android.tools.r8.horizontalclassmerging.policies.NoInterfaces;
import com.android.tools.r8.horizontalclassmerging.policies.NoKeepRules;
import com.android.tools.r8.horizontalclassmerging.policies.NoRuntimeTypeChecks;
import com.android.tools.r8.horizontalclassmerging.policies.NoStaticClassInitializer;
import com.android.tools.r8.horizontalclassmerging.policies.NotEntryPoint;
import com.android.tools.r8.horizontalclassmerging.policies.NotMatchedByNoHorizontalClassMerging;
import com.android.tools.r8.horizontalclassmerging.policies.NotVerticallyMergedIntoSubtype;
import com.android.tools.r8.horizontalclassmerging.policies.PreventChangingVisibility;
import com.android.tools.r8.horizontalclassmerging.policies.PreventMergeIntoMainDex;
import com.android.tools.r8.horizontalclassmerging.policies.RespectPackageBoundaries;
import com.android.tools.r8.horizontalclassmerging.policies.SameFeatureSplit;
import com.android.tools.r8.horizontalclassmerging.policies.SameNestHost;
import com.android.tools.r8.horizontalclassmerging.policies.SameParentClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ClassMergingEnqueuerExtension;
import com.android.tools.r8.shaking.FieldAccessInfoCollectionModifier;
import com.android.tools.r8.shaking.MainDexTracingResult;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HorizontalClassMerger {
  private final AppView<AppInfoWithLiveness> appView;
  private final PolicyExecutor policyExecutor;

  public HorizontalClassMerger(
      AppView<AppInfoWithLiveness> appView,
      MainDexTracingResult mainDexTracingResult,
      ClassMergingEnqueuerExtension classMergingEnqueuerExtension) {
    this.appView = appView;
    assert appView.options().enableInlining;

    List<Policy> policies =
        ImmutableList.of(
            new NotMatchedByNoHorizontalClassMerging(appView),
            new NoFields(),
            // TODO(b/166071504): Allow merging of classes that implement interfaces.
            new NoInterfaces(),
            new NoClassesWithInterfaces(),
            new NoAnnotations(),
            new NoAbstractClasses(),
            new NoClassesOrMembersWithAnnotations(),
            new NoInnerClasses(),
            new NoStaticClassInitializer(),
            new NoKeepRules(appView),
            new NotVerticallyMergedIntoSubtype(appView),
            new NoRuntimeTypeChecks(classMergingEnqueuerExtension),
            new NotEntryPoint(appView.dexItemFactory()),
            new PreventMergeIntoMainDex(appView, mainDexTracingResult),
            new SameParentClass(),
            new SameNestHost(),
            new PreventChangingVisibility(),
            new SameFeatureSplit(appView),
            new RespectPackageBoundaries(appView),
            new DontMergeSynchronizedClasses(appView),
            // TODO(b/166577694): no policies should be run after this policy, as it would
            // potentially break tests
            new DontMergeIntoLessVisible()
            // TODO: add policies
            );

    this.policyExecutor = new SimplePolicyExecutor(policies);
  }

  // TODO(b/165577835): replace Collection<DexProgramClass> with MergeGroup
  public HorizontalClassMergerGraphLens run(DirectMappedDexApplication.Builder appBuilder) {
    Map<FieldMultiset, Collection<DexProgramClass>> classes = new HashMap<>();

    // Group classes by same field signature using the hash map.
    for (DexProgramClass clazz : appView.appInfo().app().classesWithDeterministicOrder()) {
      classes.computeIfAbsent(new FieldMultiset(clazz), ignore -> new ArrayList<>()).add(clazz);
    }

    // Run the policies on all collected classes to produce a final grouping.
    Collection<Collection<DexProgramClass>> groups = policyExecutor.run(classes.values());
    // If there are no groups, then end horizontal class merging.
    if (groups.isEmpty()) {
      return null;
    }

    HorizontallyMergedClasses.Builder mergedClassesBuilder =
        new HorizontallyMergedClasses.Builder();
    HorizontalClassMergerGraphLens.Builder lensBuilder =
        new HorizontalClassMergerGraphLens.Builder();
    FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder =
        new FieldAccessInfoCollectionModifier.Builder();

    // Set up a class merger for each group.
    Collection<ClassMerger> classMergers =
        initializeClassMergers(
            mergedClassesBuilder, lensBuilder, fieldAccessChangesBuilder, groups);
    Iterable<DexProgramClass> allMergeClasses =
        Iterables.concat(Iterables.transform(classMergers, ClassMerger::getClasses));

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

  /**
   * Prepare horizontal class merging by determining which virtual methods and constructors need to
   * be merged and how the merging should be performed.
   */
  private Collection<ClassMerger> initializeClassMergers(
      HorizontallyMergedClasses.Builder mergedClassesBuilder,
      HorizontalClassMergerGraphLens.Builder lensBuilder,
      FieldAccessInfoCollectionModifier.Builder fieldAccessChangesBuilder,
      Collection<Collection<DexProgramClass>> groups) {
    Collection<ClassMerger> classMergers = new ArrayList<>();

    // TODO(b/166577694): Replace Collection<DexProgramClass> with MergeGroup
    for (Collection<DexProgramClass> group : groups) {
      assert !group.isEmpty();

      DexProgramClass target = group.stream().findFirst().get();
      group.remove(target);

      ClassMerger merger =
          new ClassMerger.Builder(target)
              .addClassesToMerge(group)
              .build(appView, mergedClassesBuilder, lensBuilder, fieldAccessChangesBuilder);
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
