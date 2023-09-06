// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.collections.LongLivedProgramMethodSetBuilder;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class EnumUnboxingCandidateInfoCollection {

  private final Map<DexType, EnumUnboxingCandidateInfo> enumTypeToInfo = new ConcurrentHashMap<>();
  private final Map<DexType, DexType> subEnumToSuperEnumMap = new IdentityHashMap<>();
  private final Set<DexMethod> prunedMethods = SetUtils.newConcurrentHashSet();

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

  public Set<DexType> getSubtypes(DexType enumType) {
    return SetUtils.mapIdentityHashSet(
        enumTypeToInfo.get(enumType).getSubclasses(), DexClass::getType);
  }

  public void setEnumSubclasses(DexType superEnum, Set<DexProgramClass> subclasses) {
    enumTypeToInfo.get(superEnum).setSubclasses(subclasses);
    for (DexProgramClass subclass : subclasses) {
      subEnumToSuperEnumMap.put(subclass.getType(), superEnum);
    }
  }

  public void addPrunedMethod(ProgramMethod method) {
    prunedMethods.add(method.getReference());
  }

  public void removeCandidate(DexProgramClass enumClass) {
    removeCandidate(enumClass.getType());
  }

  public void removeCandidate(DexType enumType) {
    enumTypeToInfo.remove(subEnumToSuperEnumMap.getOrDefault(enumType, enumType));
  }

  public boolean isCandidate(DexType enumType) {
    return enumTypeToInfo.containsKey(subEnumToSuperEnumMap.getOrDefault(enumType, enumType));
  }

  public boolean isEmpty() {
    return enumTypeToInfo.isEmpty();
  }

  public ImmutableSet<DexType> candidates() {
    return ImmutableSet.copyOf(enumTypeToInfo.keySet());
  }

  public ImmutableMap<DexProgramClass, Set<DexProgramClass>> candidateClassesWithSubclasses() {
    ImmutableMap.Builder<DexProgramClass, Set<DexProgramClass>> builder = ImmutableMap.builder();
    for (EnumUnboxingCandidateInfo info : enumTypeToInfo.values()) {
      builder.put(info.getEnumClass(), info.getSubclasses());
    }
    return builder.build();
  }

  /** Answers true if both enums are identical, or if one inherit from the other. */
  public boolean isAssignableTo(DexType subtype, DexType superType) {
    assert superType != null;
    assert subtype != null;
    if (superType == subtype) {
      return true;
    }
    return superType == subEnumToSuperEnumMap.get(subtype);
  }

  public DexProgramClass getCandidateClassOrNull(DexType enumType) {
    DexType superEnum = subEnumToSuperEnumMap.getOrDefault(enumType, enumType);
    EnumUnboxingCandidateInfo info = enumTypeToInfo.get(superEnum);
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

  public void forEachCandidateInfo(Consumer<EnumUnboxingCandidateInfo> consumer) {
    enumTypeToInfo.values().forEach(consumer);
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

  public boolean verifyIsSuperEnumUnboxingCandidate(DexProgramClass clazz) {
    assert enumTypeToInfo.containsKey(clazz.getType());
    return true;
  }

  public static class EnumUnboxingCandidateInfo {

    private final DexProgramClass enumClass;
    private final LongLivedProgramMethodSetBuilder<ProgramMethodSet> methodDependencies;
    private final Set<DexField> requiredInstanceFieldData = SetUtils.newConcurrentHashSet();

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
      assert subclasses != null;
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
