// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.horizontalclassmerging.ClassInstanceFieldsMerger;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.IRCodeProvider;
import com.android.tools.r8.horizontalclassmerging.InstanceInitializerAnalysis;
import com.android.tools.r8.horizontalclassmerging.InstanceInitializerDescription;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.collections.BidirectionalManyToOneHashMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalManyToOneMap;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.google.common.collect.Iterators;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/**
 * Identifies when instance initializer merging is required and bails out. This is needed to ensure
 * that we don't need to append extra null arguments at constructor call sites, such that the result
 * of the final round of class merging can be described as a renaming only.
 *
 * <p>This policy requires that all instance initializers with the same signature (relaxed, by
 * converting references types to java.lang.Object) have the same behavior.
 */
public class NoInstanceInitializerMerging extends MultiClassPolicy {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final IRCodeProvider codeProvider;

  public NoInstanceInitializerMerging(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      IRCodeProvider codeProvider,
      Mode mode) {
    assert mode.isFinal();
    this.appView = appView;
    this.codeProvider = codeProvider;
  }

  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    assert !group.hasTarget();
    assert !group.hasInstanceFieldMap();

    if (group.isInterfaceGroup()) {
      return ListUtils.newLinkedList(group);
    }

    // When we merge equivalent instance initializers with different protos, we find the least upper
    // bound of each parameter type. As a result of this, the final instance initializer signatures
    // are not known until all instance initializers in the group are known. Therefore, we disallow
    // merging of classes that have multiple methods with the same relaxed method signature (where
    // reference parameters are converted to java.lang.Object), to ensure that merging will result
    // in a simple renaming (specifically, we must not need to append null arguments to constructor
    // calls due to constructor collisions).
    group.removeIf(this::hasMultipleInstanceInitializersWithSameRelaxedSignature);

    if (group.isEmpty()) {
      return Collections.emptyList();
    }

    // We want to allow merging of equivalent instance initializers. Equivalence depends on the
    // mapping of instance fields, so we must compute this mapping now.
    group.selectTarget(appView);
    group.selectInstanceFieldMap(appView);

    Map<MergeGroup, Map<DexMethodSignature, ProgramMethod>> newGroups = new LinkedHashMap<>();

    // Caching of instance initializer descriptions, which are used to determine equivalence.
    // TODO(b/181846319): Make this cache available to the instance initializer merger so that we
    //  don't reanalyze instance initializers.
    ProgramMethodMap<Optional<InstanceInitializerDescription>> instanceInitializerDescriptions =
        ProgramMethodMap.create();
    Function<ProgramMethod, Optional<InstanceInitializerDescription>>
        instanceInitializerDescriptionProvider =
            instanceInitializer ->
                getOrComputeInstanceInitializerDescription(
                    group, instanceInitializer, instanceInitializerDescriptions);

    // Partition group into smaller groups where there are no (non-equivalent) instance initializer
    // collisions.
    for (DexProgramClass clazz : group) {
      MergeGroup newGroup = null;
      Map<DexMethodSignature, ProgramMethod> classInstanceInitializers =
          getInstanceInitializersByRelaxedSignature(clazz);
      for (Entry<MergeGroup, Map<DexMethodSignature, ProgramMethod>> entry : newGroups.entrySet()) {
        MergeGroup candidateGroup = entry.getKey();
        Map<DexMethodSignature, ProgramMethod> groupInstanceInitializers = entry.getValue();
        if (canAddClassToGroup(
            classInstanceInitializers,
            groupInstanceInitializers,
            instanceInitializerDescriptionProvider)) {
          newGroup = candidateGroup;
          classInstanceInitializers.forEach(groupInstanceInitializers::put);
          break;
        }
      }
      if (newGroup != null) {
        newGroup.add(clazz);
      } else {
        newGroups.put(new MergeGroup(clazz), classInstanceInitializers);
      }
    }

    // Remove trivial groups and finalize the newly created groups.
    Collection<MergeGroup> newNonTrivialGroups = removeTrivialGroups(newGroups.keySet());
    setInstanceFieldMaps(newNonTrivialGroups, group);
    return newNonTrivialGroups;
  }

  private boolean canAddClassToGroup(
      Map<DexMethodSignature, ProgramMethod> classInstanceInitializers,
      Map<DexMethodSignature, ProgramMethod> groupInstanceInitializers,
      Function<ProgramMethod, Optional<InstanceInitializerDescription>>
          instanceInitializerDescriptionProvider) {
    for (Entry<DexMethodSignature, ProgramMethod> entry : classInstanceInitializers.entrySet()) {
      DexMethodSignature relaxedSignature = entry.getKey();
      ProgramMethod classInstanceInitializer = entry.getValue();
      ProgramMethod groupInstanceInitializer = groupInstanceInitializers.get(relaxedSignature);
      if (groupInstanceInitializer == null) {
        continue;
      }

      Optional<InstanceInitializerDescription> classInstanceInitializerDescription =
          instanceInitializerDescriptionProvider.apply(classInstanceInitializer);
      if (!classInstanceInitializerDescription.isPresent()) {
        return false;
      }

      Optional<InstanceInitializerDescription> groupInstanceInitializerDescription =
          instanceInitializerDescriptionProvider.apply(groupInstanceInitializer);
      if (!groupInstanceInitializerDescription.isPresent()
          || !classInstanceInitializerDescription.equals(groupInstanceInitializerDescription)) {
        return false;
      }
    }
    return true;
  }

  private boolean hasMultipleInstanceInitializersWithSameRelaxedSignature(DexProgramClass clazz) {
    Iterator<ProgramMethod> instanceInitializers = clazz.programInstanceInitializers().iterator();
    if (!instanceInitializers.hasNext()) {
      // No instance initializers.
      return false;
    }

    ProgramMethod first = instanceInitializers.next();
    if (!instanceInitializers.hasNext()) {
      // Only a single instance initializer.
      return false;
    }

    Set<DexMethod> seen = SetUtils.newIdentityHashSet(getRelaxedSignature(first));
    return Iterators.any(
        instanceInitializers,
        instanceInitializer -> !seen.add(getRelaxedSignature(instanceInitializer)));
  }

  private Map<DexMethodSignature, ProgramMethod> getInstanceInitializersByRelaxedSignature(
      DexProgramClass clazz) {
    Map<DexMethodSignature, ProgramMethod> result = new HashMap<>();
    for (ProgramMethod instanceInitializer : clazz.programInstanceInitializers()) {
      DexMethodSignature relaxedSignature = getRelaxedSignature(instanceInitializer).getSignature();
      ProgramMethod previous = result.put(relaxedSignature, instanceInitializer);
      assert previous == null;
    }
    return result;
  }

  private Optional<InstanceInitializerDescription> getOrComputeInstanceInitializerDescription(
      MergeGroup group,
      ProgramMethod instanceInitializer,
      ProgramMethodMap<Optional<InstanceInitializerDescription>> instanceInitializerDescriptions) {
    return instanceInitializerDescriptions.computeIfAbsent(
        instanceInitializer,
        key -> {
          InstanceInitializerDescription instanceInitializerDescription =
              InstanceInitializerAnalysis.analyze(
                  appView, codeProvider, group, instanceInitializer);
          return Optional.ofNullable(instanceInitializerDescription);
        });
  }

  private DexMethod getRelaxedSignature(ProgramMethod instanceInitializer) {
    DexType objectType = appView.dexItemFactory().objectType;
    DexTypeList parameters = instanceInitializer.getParameters();
    DexTypeList relaxedParameters =
        parameters.map(parameter -> parameter.isPrimitiveType() ? parameter : objectType);
    return parameters != relaxedParameters
        ? appView
            .dexItemFactory()
            .createInstanceInitializer(instanceInitializer.getHolderType(), relaxedParameters)
        : instanceInitializer.getReference();
  }

  private void setInstanceFieldMaps(Iterable<MergeGroup> newGroups, MergeGroup group) {
    for (MergeGroup newGroup : newGroups) {
      // Set target.
      newGroup.selectTarget(appView);

      // Construct mapping from instance fields on old target to instance fields on new target.
      // Note the importance of this: If we create a fresh mapping from the instance fields of each
      // source class to the new target class, we could invalidate the constructor equivalence.
      Map<DexEncodedField, DexEncodedField> oldTargetToNewTargetInstanceFieldMap =
          new IdentityHashMap<>();
      if (newGroup.getTarget() != group.getTarget()) {
        ClassInstanceFieldsMerger.mapFields(
            appView,
            group.getTarget(),
            newGroup.getTarget(),
            oldTargetToNewTargetInstanceFieldMap::put);
      }

      // Construct mapping from source to target fields.
      MutableBidirectionalManyToOneMap<DexEncodedField, DexEncodedField> instanceFieldMap =
          BidirectionalManyToOneHashMap.newLinkedHashMap();
      newGroup.forEachSource(
          source ->
              source.forEachProgramInstanceField(
                  sourceField -> {
                    DexEncodedField oldTargetInstanceField =
                        group.getTargetInstanceField(sourceField).getDefinition();
                    DexEncodedField newTargetInstanceField =
                        oldTargetToNewTargetInstanceFieldMap.getOrDefault(
                            oldTargetInstanceField, oldTargetInstanceField);
                    instanceFieldMap.put(sourceField.getDefinition(), newTargetInstanceField);
                  }));
      newGroup.setInstanceFieldMap(instanceFieldMap);
    }
  }

  @Override
  public String getName() {
    return "NoInstanceInitializerMerging";
  }

  @Override
  public boolean isIdentityForInterfaceGroups() {
    return true;
  }
}
