// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.collections.LongLivedProgramMethodSetBuilder;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class EnumUnboxingCandidateInfoCollection {

  private final Map<DexType, EnumUnboxingCandidateInfo> enumTypeToInfo = new ConcurrentHashMap<>();
  private final Set<DexMethod> prunedMethods = Sets.newConcurrentHashSet();

  public void addCandidate(
      AppView<AppInfoWithLiveness> appView,
      DexProgramClass enumClass,
      GraphLens graphLensForPrimaryOptimizationPass) {
    assert !enumTypeToInfo.containsKey(enumClass.type);
    enumTypeToInfo.put(
        enumClass.type,
        new EnumUnboxingCandidateInfo(appView, enumClass, graphLensForPrimaryOptimizationPass));
  }

  public boolean hasSubtypes(DexType enumType) {
    return !enumTypeToInfo.get(enumType).getSubclasses().isEmpty();
  }

  public void setEnumSubclasses(DexType superEnum, Set<DexProgramClass> subclasses) {
    enumTypeToInfo.get(superEnum).setSubclasses(subclasses);
  }

  public void addPrunedMethod(ProgramMethod method) {
    prunedMethods.add(method.getReference());
  }

  public void removeCandidate(DexProgramClass enumClass) {
    removeCandidate(enumClass.getType());
  }

  public void removeCandidate(DexType enumType) {
    enumTypeToInfo.remove(enumType);
  }

  public boolean isCandidate(DexType enumType) {
    return enumTypeToInfo.containsKey(enumType);
  }

  public boolean isEmpty() {
    return enumTypeToInfo.isEmpty();
  }

  public ImmutableSet<DexType> candidates() {
    return ImmutableSet.copyOf(enumTypeToInfo.keySet());
  }

  public ImmutableSet<DexProgramClass> candidateClasses() {
    ImmutableSet.Builder<DexProgramClass> builder = ImmutableSet.builder();
    for (EnumUnboxingCandidateInfo info : enumTypeToInfo.values()) {
      builder.add(info.getEnumClass());
    }
    return builder.build();
  }

  public DexProgramClass getCandidateClassOrNull(DexType enumType) {
    EnumUnboxingCandidateInfo info = enumTypeToInfo.get(enumType);
    if (info == null) {
      return null;
    }
    return info.enumClass;
  }

  public LongLivedProgramMethodSetBuilder<ProgramMethodSet> allMethodDependencies() {
    Iterator<EnumUnboxingCandidateInfo> candidateInfoIterator = enumTypeToInfo.values().iterator();
    assert candidateInfoIterator.hasNext();
    LongLivedProgramMethodSetBuilder<ProgramMethodSet> allMethodDependencies =
        candidateInfoIterator.next().methodDependencies;
    while (candidateInfoIterator.hasNext()) {
      allMethodDependencies.merge(candidateInfoIterator.next().methodDependencies);
    }
    allMethodDependencies.removeAll(prunedMethods);
    return allMethodDependencies;
  }

  public void addMethodDependency(DexType enumType, ProgramMethod programMethod) {
    // The enumType may be removed concurrently map from enumTypeToInfo. It means in that
    // case the enum is no longer a candidate, and dependencies don't need to be recorded
    // anymore.
    EnumUnboxingCandidateInfo info = enumTypeToInfo.get(enumType);
    if (info == null) {
      return;
    }
    info.addMethodDependency(programMethod);
  }

  public void addRequiredEnumInstanceFieldData(DexProgramClass enumClass, DexField field) {
    // The enumType may be removed concurrently map from enumTypeToInfo. It means in that
    // case the enum is no longer a candidate, and dependencies don't need to be recorded
    // anymore.
    EnumUnboxingCandidateInfo info = enumTypeToInfo.get(enumClass.getType());
    if (info == null) {
      return;
    }
    info.addRequiredInstanceFieldData(field);
  }

  public void forEachCandidate(Consumer<DexProgramClass> enumClassConsumer) {
    enumTypeToInfo.values().forEach(info -> enumClassConsumer.accept(info.enumClass));
  }

  public void forEachCandidateAndRequiredInstanceFieldData(
      BiConsumer<DexProgramClass, Set<DexField>> biConsumer) {
    enumTypeToInfo
        .values()
        .forEach(
            info -> biConsumer.accept(info.getEnumClass(), info.getRequiredInstanceFieldData()));
  }

  public void clear() {
    enumTypeToInfo.clear();
  }

  public boolean verifyAllSubtypesAreSet() {
    for (EnumUnboxingCandidateInfo value : enumTypeToInfo.values()) {
      assert value.subclasses != null;
    }
    return true;
  }

  private static class EnumUnboxingCandidateInfo {

    private final DexProgramClass enumClass;
    private final LongLivedProgramMethodSetBuilder<ProgramMethodSet> methodDependencies;
    private final Set<DexField> requiredInstanceFieldData = Sets.newConcurrentHashSet();

    private Set<DexProgramClass> subclasses = null;

    public EnumUnboxingCandidateInfo(
        AppView<AppInfoWithLiveness> appView,
        DexProgramClass enumClass,
        GraphLens graphLensForPrimaryOptimizationPass) {
      assert enumClass != null;
      assert appView.graphLens() == graphLensForPrimaryOptimizationPass;
      this.enumClass = enumClass;
      this.methodDependencies =
          LongLivedProgramMethodSetBuilder.createConcurrentForIdentitySet(
              graphLensForPrimaryOptimizationPass);
    }

    public Set<DexProgramClass> getSubclasses() {
      return subclasses;
    }

    public void setSubclasses(Set<DexProgramClass> subclasses) {
      this.subclasses = subclasses;
    }

    public DexProgramClass getEnumClass() {
      return enumClass;
    }

    public void addMethodDependency(ProgramMethod method) {
      methodDependencies.add(method);
    }

    public void addRequiredInstanceFieldData(DexField field) {
      requiredInstanceFieldData.add(field);
    }

    public Set<DexField> getRequiredInstanceFieldData() {
      return requiredInstanceFieldData;
    }
  }
}
