// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ImmediateProgramSubtypingInfo;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.optimize.argumentpropagation.utils.ProgramClassesBidirectedGraph;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.google.common.collect.Sets;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NoWeakerAccessPrivileges extends MultiClassPolicy {

  private final ProgramClassesBidirectedGraph graph;
  private final ImmediateProgramSubtypingInfo immediateSubtypingInfo;

  private final Map<DexClass, DexMethodSignatureSet> inheritedInterfaceMethodsCache =
      new IdentityHashMap<>();
  private final Map<Set<DexProgramClass>, DexMethodSignatureSet>
      nonPublicVirtualMethodSignaturesCache = new IdentityHashMap<>();
  private final Map<DexClass, DexMethodSignatureSet> nonPublicVirtualLibraryMethodSignaturesCache =
      new IdentityHashMap<>();
  private final Map<DexProgramClass, Set<DexProgramClass>> stronglyConnectedComponentsCache =
      new IdentityHashMap<>();

  public NoWeakerAccessPrivileges(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ImmediateProgramSubtypingInfo immediateSubtypingInfo) {
    this.graph = new ProgramClassesBidirectedGraph(appView, immediateSubtypingInfo);
    this.immediateSubtypingInfo = immediateSubtypingInfo;
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    List<MergeGroup> newMergeGroups = new LinkedList<>();
    Map<MergeGroup, DexMethodSignatureSet> inheritedInterfaceMethodsPerGroup =
        new IdentityHashMap<>();
    for (DexProgramClass clazz : group) {
      // Find an existing merge group that the current class can be added to.
      MergeGroup newMergeGroup = null;
      for (MergeGroup candidateMergeGroup : newMergeGroups) {
        DexMethodSignatureSet inheritedInterfaceMethodsInGroup =
            inheritedInterfaceMethodsPerGroup.get(candidateMergeGroup);
        if (canAddToGroup(clazz, candidateMergeGroup, inheritedInterfaceMethodsInGroup)) {
          newMergeGroup = candidateMergeGroup;
          break;
        }
      }

      DexMethodSignatureSet inheritedInterfaceMethodsInGroup;
      if (newMergeGroup == null) {
        // Form a new singleton merge group from the current class.
        newMergeGroup = new MergeGroup(clazz);
        newMergeGroups.add(newMergeGroup);
        inheritedInterfaceMethodsInGroup = DexMethodSignatureSet.create();
        inheritedInterfaceMethodsPerGroup.put(newMergeGroup, inheritedInterfaceMethodsInGroup);
      } else {
        // Add the current class to the existing merge group.
        newMergeGroup.add(clazz);
        inheritedInterfaceMethodsInGroup = inheritedInterfaceMethodsPerGroup.get(newMergeGroup);
      }

      // Record that the merge group now contains the direct and indirect interface methods of the
      // current class.
      inheritedInterfaceMethodsInGroup.addAll(getOrComputeInheritedInterfaceMethods(clazz));
    }

    // Remove any singleton merge groups.
    return removeTrivialGroups(newMergeGroups);
  }

  private boolean canAddToGroup(
      DexProgramClass clazz,
      MergeGroup group,
      DexMethodSignatureSet inheritedInterfaceMethodsInGroup) {
    // We need to ensure that adding class to the group is OK.
    DexMethodSignatureSet nonPublicVirtualMethodSignaturesInClassComponent =
        getOrComputeNonPublicVirtualMethodSignaturesInComponentOf(clazz);
    if (nonPublicVirtualMethodSignaturesInClassComponent.containsAnyOf(
        inheritedInterfaceMethodsInGroup)) {
      return false;
    }

    // We need to ensure adding all classes in the group to the class is OK.
    Set<Set<DexProgramClass>> components = Sets.newIdentityHashSet();
    for (DexProgramClass member : group) {
      components.add(getOrComputeStronglyConnectedComponent(member));
    }
    for (Set<DexProgramClass> component : components) {
      if (getOrComputeNonPublicVirtualMethodSignaturesInComponent(component)
          .containsAnyOf(getOrComputeInheritedInterfaceMethods(clazz))) {
        return false;
      }
    }
    return true;
  }

  private DexMethodSignatureSet getOrComputeInheritedInterfaceMethods(DexClass clazz) {
    if (inheritedInterfaceMethodsCache.containsKey(clazz)) {
      return inheritedInterfaceMethodsCache.get(clazz);
    }
    DexMethodSignatureSet inheritedInterfaceMethods = DexMethodSignatureSet.create();
    immediateSubtypingInfo.forEachImmediateSuperClassMatching(
        clazz,
        DexClass::isInterface,
        superclass ->
            inheritedInterfaceMethods.addAll(getOrComputeInheritedInterfaceMethods(superclass)));
    if (clazz.isInterface()) {
      clazz.forEachClassMethodMatching(
          DexEncodedMethod::belongsToVirtualPool, inheritedInterfaceMethods::add);
    }
    inheritedInterfaceMethodsCache.put(clazz, inheritedInterfaceMethods);
    return inheritedInterfaceMethods;
  }

  private Set<DexProgramClass> getOrComputeStronglyConnectedComponent(DexProgramClass clazz) {
    if (stronglyConnectedComponentsCache.containsKey(clazz)) {
      return stronglyConnectedComponentsCache.get(clazz);
    }
    Set<DexProgramClass> stronglyConnectedComponent =
        graph.computeStronglyConnectedComponent(clazz);
    for (DexProgramClass member : stronglyConnectedComponent) {
      stronglyConnectedComponentsCache.put(member, stronglyConnectedComponent);
    }
    return stronglyConnectedComponent;
  }

  private DexMethodSignatureSet getOrComputeNonPublicVirtualMethodSignaturesInComponentOf(
      DexProgramClass clazz) {
    return getOrComputeNonPublicVirtualMethodSignaturesInComponent(
        getOrComputeStronglyConnectedComponent(clazz));
  }

  private DexMethodSignatureSet getOrComputeNonPublicVirtualMethodSignaturesInComponent(
      Set<DexProgramClass> stronglyConnectedComponent) {
    if (nonPublicVirtualMethodSignaturesCache.containsKey(stronglyConnectedComponent)) {
      return nonPublicVirtualMethodSignaturesCache.get(stronglyConnectedComponent);
    }
    DexMethodSignatureSet nonPublicVirtualMethodSignatures = DexMethodSignatureSet.create();
    for (DexProgramClass clazz : stronglyConnectedComponent) {
      clazz.forEachProgramVirtualMethodMatching(
          method -> method.getAccessFlags().isPackagePrivateOrProtected(),
          nonPublicVirtualMethodSignatures::add);
      immediateSubtypingInfo.forEachImmediateSuperClassMatching(
          clazz,
          superclass -> !superclass.isProgramClass(),
          superclass ->
              nonPublicVirtualMethodSignatures.addAll(
                  getOrComputeNonPublicVirtualLibraryMethodSignatures(superclass)));
    }
    nonPublicVirtualMethodSignaturesCache.put(
        stronglyConnectedComponent, nonPublicVirtualMethodSignatures);
    return nonPublicVirtualMethodSignatures;
  }

  private DexMethodSignatureSet getOrComputeNonPublicVirtualLibraryMethodSignatures(
      DexClass clazz) {
    if (nonPublicVirtualLibraryMethodSignaturesCache.containsKey(clazz)) {
      return nonPublicVirtualLibraryMethodSignaturesCache.get(clazz);
    }
    DexMethodSignatureSet nonPublicVirtualLibraryMethodSignatures = DexMethodSignatureSet.create();
    clazz.forEachClassMethodMatching(
        method -> method.getAccessFlags().isPackagePrivateOrProtected(),
        nonPublicVirtualLibraryMethodSignatures::add);
    immediateSubtypingInfo.forEachImmediateSuperClass(
        clazz,
        superclass ->
            nonPublicVirtualLibraryMethodSignatures.addAll(
                getOrComputeNonPublicVirtualLibraryMethodSignatures(superclass)));
    nonPublicVirtualLibraryMethodSignaturesCache.put(
        clazz, nonPublicVirtualLibraryMethodSignatures);
    return nonPublicVirtualLibraryMethodSignatures;
  }

  @Override
  public void clear() {
    inheritedInterfaceMethodsCache.clear();
    nonPublicVirtualMethodSignaturesCache.clear();
    stronglyConnectedComponentsCache.clear();
  }

  @Override
  public String getName() {
    return "NoWeakerAccessPriviledges";
  }
}
