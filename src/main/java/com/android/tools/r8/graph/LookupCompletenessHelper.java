// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.LookupResult.LookupResultSuccess.LookupResultCollectionState;
import com.android.tools.r8.utils.WorkList;
import com.google.common.collect.Sets;
import java.util.Set;

public class LookupCompletenessHelper {

  private final PinnedPredicate pinnedPredicate;

  private Set<DexType> pinnedInstantiations;
  private Set<DexMethod> pinnedMethods;

  LookupCompletenessHelper(PinnedPredicate pinnedPredicate) {
    this.pinnedPredicate = pinnedPredicate;
  }

  void checkClass(DexClass clazz) {
    if (pinnedPredicate.isPinned(clazz)) {
      if (pinnedInstantiations == null) {
        pinnedInstantiations = Sets.newIdentityHashSet();
      }
      pinnedInstantiations.add(clazz.type);
    }
  }

  void checkMethod(DexEncodedMethod method) {
    if (pinnedPredicate.isPinned(method)) {
      if (pinnedMethods == null) {
        pinnedMethods = Sets.newIdentityHashSet();
      }
      pinnedMethods.add(method.method);
    }
  }

  void checkDexClassAndMethod(DexClassAndMethod classAndMethod) {
    checkClass(classAndMethod.getHolder());
    checkMethod(classAndMethod.getDefinition());
  }

  LookupResultCollectionState computeCollectionState(
      DexMethod method, AppInfoWithClassHierarchy appInfo) {
    assert pinnedInstantiations == null || !pinnedInstantiations.isEmpty();
    if (pinnedInstantiations == null) {
      return LookupResultCollectionState.Complete;
    }
    if (pinnedMethods != null) {
      return LookupResultCollectionState.Incomplete;
    }
    WorkList<DexType> workList = WorkList.newIdentityWorkList(pinnedInstantiations);
    while (workList.hasNext()) {
      if (isMethodKeptInSuperTypeOrIsLibrary(workList, method, appInfo)) {
        return LookupResultCollectionState.Incomplete;
      }
    }
    return LookupResultCollectionState.Complete;
  }

  private boolean isMethodKeptInSuperTypeOrIsLibrary(
      WorkList<DexType> workList, DexMethod method, AppInfoWithClassHierarchy appInfo) {
    while (workList.hasNext()) {
      DexClass parent = appInfo.definitionFor(workList.next());
      if (parent == null) {
        continue;
      }
      DexEncodedMethod methodInClass = parent.lookupVirtualMethod(method);
      if (methodInClass != null
          && (parent.isNotProgramClass() || pinnedPredicate.isPinned(methodInClass))) {
        return true;
      }
      if (parent.superType != null) {
        workList.addIfNotSeen(parent.superType);
      }
      workList.addIfNotSeen(parent.interfaces.values);
    }
    return false;
  }
}
