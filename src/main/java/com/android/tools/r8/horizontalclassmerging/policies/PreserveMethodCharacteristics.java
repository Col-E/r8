// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
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
import java.util.Objects;

/**
 * Policy that enforces that methods are only merged if they have the same visibility and library
 * method override information.
 */
public class PreserveMethodCharacteristics extends MultiClassPolicy {

  @Override
  public String getName() {
    return "PreserveMethodCharacteristics";
  }

  static class MethodCharacteristics {

    private final MethodAccessFlags accessFlags;
    private final boolean isAssumeNoSideEffectsMethod;
    private final OptionalBool isLibraryMethodOverride;
    private final boolean isMainDexRoot;

    private MethodCharacteristics(
        DexEncodedMethod method, boolean isAssumeNoSideEffectsMethod, boolean isMainDexRoot) {
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
      this.isMainDexRoot = isMainDexRoot;
    }

    static MethodCharacteristics create(
        AppView<AppInfoWithLiveness> appView, DexEncodedMethod method) {
      return new MethodCharacteristics(
          method,
          appView.getAssumeInfoCollection().isSideEffectFree(method.getReference()),
          appView.appInfo().getMainDexInfo().isTracedMethodRoot(method.getReference()));
    }

    @Override
    public int hashCode() {
      return Objects.hash(
          accessFlags,
          isAssumeNoSideEffectsMethod,
          isLibraryMethodOverride.ordinal(),
          isMainDexRoot);
    }

    @Override
    @SuppressWarnings({"EqualsGetClass", "ReferenceEquality"})
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
          && isLibraryMethodOverride == characteristics.isLibraryMethodOverride
          && isMainDexRoot == characteristics.isMainDexRoot;
    }
  }

  private final AppView<AppInfoWithLiveness> appView;

  public PreserveMethodCharacteristics(AppView<AppInfoWithLiveness> appView, Mode mode) {
    // This policy checks that method merging does invalidate various properties. Thus there is no
    // reason to run this policy if method merging is not allowed.
    assert mode.isInitial();
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
      for (DexEncodedMethod method : clazz.methods(this::isSubjectToMethodMerging)) {
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

    private boolean isSubjectToMethodMerging(DexEncodedMethod method) {
      if (method.isStatic() || (method.isPrivate() && !method.isInstanceInitializer())) {
        // Static and private instance methods can easily be renamed and do not require merging.
        return false;
      }
      return true;
    }
  }

  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
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
