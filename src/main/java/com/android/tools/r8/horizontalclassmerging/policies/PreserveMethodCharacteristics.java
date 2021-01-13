// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.OptionalBool;
import com.google.common.collect.Iterables;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * Policy that enforces that methods are only merged if they have the same visibility and library
 * method override information.
 */
public class PreserveMethodCharacteristics extends MultiClassPolicy {

  static class MethodCharacteristics {

    private final MethodAccessFlags accessFlags;
    private final boolean isAssumeNoSideEffectsMethod;
    private final OptionalBool isLibraryMethodOverride;

    private MethodCharacteristics(boolean isAssumeNoSideEffectsMethod, DexEncodedMethod method) {
      this.accessFlags =
          MethodAccessFlags.builder()
              .setPrivate(method.getAccessFlags().isPrivate())
              .setProtected(method.getAccessFlags().isProtected())
              .setPublic(method.getAccessFlags().isPublic())
              .setStrict(method.getAccessFlags().isStrict())
              .setSynchronized(method.getAccessFlags().isSynchronized())
              .build();
      this.isAssumeNoSideEffectsMethod = isAssumeNoSideEffectsMethod;
      this.isLibraryMethodOverride = method.isLibraryMethodOverride();
    }

    static MethodCharacteristics create(
        AppView<AppInfoWithLiveness> appView, DexEncodedMethod method) {
      return new MethodCharacteristics(
          appView.appInfo().isAssumeNoSideEffectsMethod(method.getReference()), method);
    }

    @Override
    public int hashCode() {
      return (accessFlags.hashCode() << 2) | isLibraryMethodOverride.ordinal();
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      MethodCharacteristics characteristics = (MethodCharacteristics) obj;
      return accessFlags.equals(characteristics.accessFlags)
          && isAssumeNoSideEffectsMethod == characteristics.isAssumeNoSideEffectsMethod
          && isLibraryMethodOverride == characteristics.isLibraryMethodOverride;
    }
  }

  private final AppView<AppInfoWithLiveness> appView;

  public PreserveMethodCharacteristics(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public static class TargetGroup {

    private final MergeGroup group = new MergeGroup();
    private final Map<DexMethodSignature, MethodCharacteristics> methodMap = new HashMap<>();

    public MergeGroup getGroup() {
      return group;
    }

    public boolean tryAdd(AppView<AppInfoWithLiveness> appView, DexProgramClass clazz) {
      Map<DexMethodSignature, MethodCharacteristics> newMethods = new HashMap<>();
      for (DexEncodedMethod method : clazz.methods()) {
        DexMethodSignature signature = method.getSignature();
        MethodCharacteristics existingCharacteristics = methodMap.get(signature);
        MethodCharacteristics methodCharacteristics = MethodCharacteristics.create(appView, method);
        if (existingCharacteristics == null) {
          newMethods.put(signature, methodCharacteristics);
          continue;
        }
        if (!methodCharacteristics.equals(existingCharacteristics)) {
          return false;
        }
      }
      methodMap.putAll(newMethods);
      group.add(clazz);
      return true;
    }
  }

  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    List<TargetGroup> groups = new ArrayList<>();

    for (DexProgramClass clazz : group) {
      boolean added = Iterables.any(groups, targetGroup -> targetGroup.tryAdd(appView, clazz));
      if (!added) {
        TargetGroup newGroup = new TargetGroup();
        added = newGroup.tryAdd(appView, clazz);
        assert added;
        groups.add(newGroup);
      }
    }

    LinkedList<MergeGroup> newGroups = new LinkedList<>();
    for (TargetGroup newGroup : groups) {
      if (!newGroup.getGroup().isTrivial()) {
        newGroups.add(newGroup.getGroup());
      }
    }
    return newGroups;
  }
}
