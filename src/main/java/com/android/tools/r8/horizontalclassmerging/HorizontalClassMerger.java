// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.policies.NoFields;
import com.android.tools.r8.horizontalclassmerging.policies.NoInterfaces;
import com.android.tools.r8.horizontalclassmerging.policies.NoInternalUtilityClasses;
import com.android.tools.r8.horizontalclassmerging.policies.NoOverlappingConstructors;
import com.android.tools.r8.horizontalclassmerging.policies.NoRuntimeTypeChecks;
import com.android.tools.r8.horizontalclassmerging.policies.NoStaticClassInitializer;
import com.android.tools.r8.horizontalclassmerging.policies.NotEntryPoint;
import com.android.tools.r8.horizontalclassmerging.policies.SameParentClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.ClassMergingEnqueuerExtension;
import com.android.tools.r8.shaking.MainDexClasses;
import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class HorizontalClassMerger {
  private final AppView<AppInfoWithLiveness> appView;
  private final PolicyExecutor policyExecutor;

  public HorizontalClassMerger(
      AppView<AppInfoWithLiveness> appView,
      MainDexClasses mainDexClasses,
      ClassMergingEnqueuerExtension classMergingEnqueuerExtension) {
    this.appView = appView;

    List<Policy> policies =
        ImmutableList.of(
            new NoFields(),
            // TODO(b/166071504): Allow merging of classes that implement interfaces.
            new NoInterfaces(),
            new NoStaticClassInitializer(),
            new NoRuntimeTypeChecks(classMergingEnqueuerExtension),
            new NotEntryPoint(appView.dexItemFactory()),
            new NoInternalUtilityClasses(appView.dexItemFactory()),
            new SameParentClass(),
            new NoOverlappingConstructors()
            // TODO: add policies
            );

    this.policyExecutor = new SimplePolicyExecutor(policies);
  }

  public HorizontalClassMergerGraphLens run() {
    Map<FieldMultiset, Collection<DexProgramClass>> classes = new HashMap<>();

    // Group classes by same field signature using the hash map.
    for (DexProgramClass clazz : appView.appInfo().app().classesWithDeterministicOrder()) {
      classes.computeIfAbsent(new FieldMultiset(clazz), ignore -> new ArrayList<>()).add(clazz);
    }

    // Run the policies on all collected classes to produce a final grouping.
    Collection<Collection<DexProgramClass>> groups = policyExecutor.run(classes.values());

    return createLens(groups);
  }

  // TODO(b/165577835): replace Collection<DexProgramClass> with MergeGroup
  /**
   * Merges all class groups using {@link ClassMerger}. Then fix all references to merged classes
   * using the {@link TreeFixer}. Constructs a graph lens containing all changes while performing
   * merging.
   */
  private HorizontalClassMergerGraphLens createLens(
      Collection<Collection<DexProgramClass>> groups) {
    Map<DexType, DexType> mergedClasses = new IdentityHashMap<>();
    HorizontalClassMergerGraphLens.Builder lensBuilder =
        new HorizontalClassMergerGraphLens.Builder();

    // TODO(b/166577694): Replace Collection<DexProgramClass> with MergeGroup
    for (Collection<DexProgramClass> group : groups) {
      assert !group.isEmpty();

      DexProgramClass target = group.stream().findFirst().get();
      group.remove(target);

      for (DexProgramClass clazz : group) {
        mergedClasses.put(clazz.type, target.type);
      }

      ClassMerger merger = new ClassMerger(appView, lensBuilder, target, group);
      merger.mergeGroup();
    }

    HorizontalClassMergerGraphLens lens =
        new TreeFixer(appView, lensBuilder, mergedClasses).fixupTypeReferences();
    return lens;
  }
}
