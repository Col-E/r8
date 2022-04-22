// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.SetUtils;
import com.google.common.collect.Iterables;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Identifies when virtual method merging is required and bails out. This is needed to ensure that
 * we don't need to synthesize any $r8$classId fields, such that the result of the final round of
 * class merging can be described as a renaming only.
 */
public class NoVirtualMethodMerging extends MultiClassPolicy {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  public NoVirtualMethodMerging(AppView<? extends AppInfoWithClassHierarchy> appView, Mode mode) {
    assert mode.isFinal();
    this.appView = appView;
  }

  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    Map<MergeGroup, Map<DexMethodSignature, ProgramMethod>> newGroups = new LinkedHashMap<>();
    for (DexProgramClass clazz : group) {
      Map<DexMethodSignature, ProgramMethod> classMethods = new HashMap<>();
      clazz.forEachProgramVirtualMethodMatching(
          DexEncodedMethod::isNonAbstractVirtualMethod,
          method -> classMethods.put(method.getMethodSignature(), method));

      MergeGroup newGroup = null;
      for (Entry<MergeGroup, Map<DexMethodSignature, ProgramMethod>> entry : newGroups.entrySet()) {
        MergeGroup candidateGroup = entry.getKey();
        Map<DexMethodSignature, ProgramMethod> groupMethods = entry.getValue();
        if (canAddNonAbstractVirtualMethodsToGroup(
            clazz, classMethods.values(), candidateGroup, groupMethods)) {
          newGroup = candidateGroup;
          groupMethods.putAll(classMethods);
          break;
        }
      }

      if (newGroup != null) {
        newGroup.add(clazz);
      } else {
        newGroups.put(new MergeGroup(clazz), classMethods);
      }
    }
    return removeTrivialGroups(newGroups.keySet());
  }

  private boolean canAddNonAbstractVirtualMethodsToGroup(
      DexProgramClass clazz,
      Collection<ProgramMethod> methods,
      MergeGroup group,
      Map<DexMethodSignature, ProgramMethod> groupMethods) {
    // For each of clazz' virtual methods, check that adding these methods to the group does not
    // require method merging.
    for (ProgramMethod method : methods) {
      ProgramMethod groupMethod = groupMethods.get(method.getMethodSignature());
      if (groupMethod != null || hasNonAbstractDefinitionInHierarchy(group, method)) {
        return false;
      }
    }
    // For each of the group's virtual methods, check that adding these methods clazz does not
    // require method merging.
    for (ProgramMethod method : groupMethods.values()) {
      if (hasNonAbstractDefinitionInHierarchy(clazz, method)) {
        return false;
      }
    }
    return true;
  }

  private boolean hasNonAbstractDefinitionInHierarchy(MergeGroup group, ProgramMethod method) {
    return hasNonAbstractDefinitionInSuperClass(group.getSuperType(), method)
        || hasNonAbstractDefinitionInSuperInterface(
            SetUtils.newIdentityHashSet(IterableUtils.flatMap(group, DexClass::getInterfaces)),
            method);
  }

  private boolean hasNonAbstractDefinitionInHierarchy(DexProgramClass clazz, ProgramMethod method) {
    return hasNonAbstractDefinitionInSuperClass(clazz.getSuperType(), method)
        || hasNonAbstractDefinitionInSuperInterface(clazz.getInterfaces(), method);
  }

  private boolean hasNonAbstractDefinitionInSuperClass(DexType superType, ProgramMethod method) {
    SingleResolutionResult<?> resolutionResult =
        appView
            .appInfo()
            .resolveMethodOnClassLegacy(superType, method.getReference())
            .asSingleResolution();
    return resolutionResult != null && !resolutionResult.getResolvedMethod().isAbstract();
  }

  private boolean hasNonAbstractDefinitionInSuperInterface(
      Iterable<DexType> interfaceTypes, ProgramMethod method) {
    return Iterables.any(
        interfaceTypes,
        interfaceType -> {
          SingleResolutionResult<?> resolutionResult =
              appView
                  .appInfo()
                  .resolveMethodOnInterfaceLegacy(interfaceType, method.getReference())
                  .asSingleResolution();
          return resolutionResult != null && !resolutionResult.getResolvedMethod().isAbstract();
        });
  }

  @Override
  public String getName() {
    return "NoVirtualMethodMerging";
  }
}
