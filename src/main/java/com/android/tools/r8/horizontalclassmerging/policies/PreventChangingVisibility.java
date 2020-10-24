// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

public class PreventChangingVisibility extends MultiClassPolicy {
  public PreventChangingVisibility() {}

  public static class TargetGroup {
    private final List<DexProgramClass> group = new LinkedList<>();
    private final Map<Wrapper<DexMethod>, MethodAccessFlags> methodMap = new HashMap<>();

    public List<DexProgramClass> getGroup() {
      return group;
    }

    public boolean tryAdd(DexProgramClass clazz) {
      Map<Wrapper<DexMethod>, MethodAccessFlags> newMethods = new HashMap<>();
      for (DexEncodedMethod method : clazz.methods()) {
        Wrapper<DexMethod> methodSignature =
            MethodSignatureEquivalence.get().wrap(method.getReference());
        MethodAccessFlags flags = methodMap.get(methodSignature);

        if (flags == null) {
          newMethods.put(methodSignature, method.getAccessFlags());
        } else {
          if (!flags.isSameVisibility(method.getAccessFlags())) {
            return false;
          }
        }
      }

      methodMap.putAll(newMethods);
      group.add(clazz);
      return true;
    }
  }

  @Override
  public Collection<List<DexProgramClass>> apply(List<DexProgramClass> group) {
    List<TargetGroup> groups = new ArrayList<>();

    for (DexProgramClass clazz : group) {
      boolean added = Iterables.any(groups, targetGroup -> targetGroup.tryAdd(clazz));
      if (!added) {
        TargetGroup newGroup = new TargetGroup();
        added = newGroup.tryAdd(clazz);
        assert added;
        groups.add(newGroup);
      }
    }

    Collection<List<DexProgramClass>> newGroups = new ArrayList<>();
    for (TargetGroup newGroup : groups) {
      if (!isTrivial(newGroup.getGroup())) {
        newGroups.add(newGroup.getGroup());
      }
    }

    return newGroups;
  }
}
