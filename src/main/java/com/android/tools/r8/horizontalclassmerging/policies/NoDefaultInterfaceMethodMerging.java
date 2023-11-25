// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.DexMethodSignatureMap;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.google.common.collect.Lists;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;

/**
 * For interfaces, we cannot introduce an instance field `int $r8$classId`. Therefore, we can't
 * merge two interfaces that declare the same default interface method.
 *
 * <p>This policy attempts to split a merge group consisting of interfaces into smaller merge groups
 * such that each pairs of interfaces in each merge group does not have conflicting default
 * interface methods.
 */
public class NoDefaultInterfaceMethodMerging extends MultiClassPolicy {

  private final AppView<?> appView;
  private final DexType MULTIPLE_SENTINEL;

  public NoDefaultInterfaceMethodMerging(AppView<?> appView, Mode mode) {
    this.appView = appView;
    // Use the java.lang.Object type to indicate more than one interface type, as that type
    // itself is not an interface type.
    this.MULTIPLE_SENTINEL = appView.dexItemFactory().objectType;
  }

  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    // Split the group into smaller groups such that no default methods collide.
    // TODO(b/229951607): This fixes the ICCE issue for synthetic lambda classes, but a more
    //  general solution possibly extending the policy NoDefaultInterfaceMethodCollisions.
    Map<MergeGroup, DexMethodSignatureMap<DexType>> newGroups = new LinkedHashMap<>();
    for (DexProgramClass clazz : group) {
      addClassToGroup(
          clazz,
          newGroups,
          group.isInterfaceGroup()
              ? this::collectDefaultMethodsInInterfaces
              : this::collectDefaultMethodsInImplementedInterfaces);
    }

    return removeTrivialGroups(Lists.newLinkedList(newGroups.keySet()));
  }

  @SuppressWarnings("ReferenceEquality")
  private void addClassToGroup(
      DexProgramClass clazz,
      Map<MergeGroup, DexMethodSignatureMap<DexType>> newGroups,
      Function<DexProgramClass, DexMethodSignatureMap<DexType>> fn) {
    DexMethodSignatureMap<DexType> classSignatures = fn.apply(clazz);

    // Find a group that does not have any collisions with `clazz`.
    nextGroup:
    for (Entry<MergeGroup, DexMethodSignatureMap<DexType>> entry : newGroups.entrySet()) {
      MergeGroup group = entry.getKey();
      DexMethodSignatureMap<DexType> groupSignatures = entry.getValue();
      if (!groupSignatures.containsAnyKeyOf(classSignatures.keySet())) {
        groupSignatures.putAll(classSignatures);
        group.add(clazz);
        return;
      } else {
        DexMethodSignatureSet overlappingSignatures =
            groupSignatures.intersectionWithKeys(classSignatures.keySet());
        for (DexMethodSignature signature : overlappingSignatures) {
          if ((groupSignatures.get(signature) != classSignatures.get(signature))
              || (groupSignatures.get(signature) == MULTIPLE_SENTINEL)) {
            continue nextGroup;
          }
          groupSignatures.putAll(classSignatures);
          group.add(clazz);
          return;
        }
      }
    }

    // Else create a new group.
    newGroups.put(new MergeGroup(clazz), classSignatures);
  }

  @SuppressWarnings("ReferenceEquality")
  private void addDefaultMethods(DexMethodSignatureMap<DexType> signatures, DexProgramClass iface) {
    // When the same signature is added from several interfaces just move to the "multiple" state
    // and do not keep track of the actual interfaces.
    iface.forEachProgramVirtualMethodMatching(
        DexEncodedMethod::isDefaultMethod,
        method ->
            signatures.merge(
                method.getDefinition(),
                iface.getType(),
                (ignoreKey, current) -> current == iface.getType() ? current : MULTIPLE_SENTINEL));
  }

  private DexMethodSignatureMap<DexType> collectDefaultMethodsInInterfaces(DexProgramClass iface) {
    assert iface.isInterface();
    DexMethodSignatureMap<DexType> signatures = DexMethodSignatureMap.create();
    WorkList<DexProgramClass> workList = WorkList.newIdentityWorkList();
    workList.addIfNotSeen(iface);
    while (workList.hasNext()) {
      DexProgramClass item = workList.next();
      assert item.isInterface();
      addDefaultMethods(signatures, item);
      addInterfacesToWorklist(item, workList);
    }
    return signatures;
  }

  // TODO(b/229951607): This only adresses the ICCE issue for synthetic lambda classes.
  private DexMethodSignatureMap<DexType> collectDefaultMethodsInImplementedInterfaces(
      DexProgramClass clazz) {
    assert !clazz.isInterface();
    DexMethodSignatureMap<DexType> signatures = DexMethodSignatureMap.create();
    WorkList<DexProgramClass> workList = WorkList.newIdentityWorkList();
    addInterfacesToWorklist(clazz, workList);
    while (workList.hasNext()) {
      DexProgramClass item = workList.next();
      assert item.isInterface();
      addDefaultMethods(signatures, item);
      addInterfacesToWorklist(item, workList);
    }
    return signatures;
  }

  private void addInterfacesToWorklist(DexProgramClass clazz, WorkList<DexProgramClass> worklist) {
    for (DexType iface : clazz.getInterfaces()) {
      DexProgramClass ifaceDefinition = appView.programDefinitionFor(iface, clazz);
      if (ifaceDefinition != null && ifaceDefinition.isInterface()) {
        worklist.addIfNotSeen(ifaceDefinition);
      }
    }
  }

  @Override
  public String getName() {
    return "NoDefaultInterfaceMethodMerging";
  }
}
