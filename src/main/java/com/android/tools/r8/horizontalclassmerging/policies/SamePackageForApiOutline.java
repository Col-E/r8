// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import static com.android.tools.r8.utils.FunctionUtils.ignoreArgument;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.horizontalclassmerging.HorizontalClassMerger.Mode;
import com.android.tools.r8.horizontalclassmerging.MergeGroup;
import com.android.tools.r8.horizontalclassmerging.MultiClassPolicy;
import com.android.tools.r8.synthesis.SyntheticItems;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;

public class SamePackageForApiOutline extends MultiClassPolicy {

  private final AppView<AppInfo> appView;
  private final Mode mode;

  public SamePackageForApiOutline(AppView<AppInfo> appView, Mode mode) {
    this.appView = appView;
    this.mode = mode;
  }

  /** Sort unrestricted classes into restricted classes if they are in the same package. */
  private void tryFindRestrictedPackage(
      MergeGroup unrestrictedClasses, Map<String, MergeGroup> restrictedClasses) {
    unrestrictedClasses.removeIf(
        clazz -> {
          MergeGroup restrictedPackage = restrictedClasses.get(clazz.type.getPackageDescriptor());
          if (restrictedPackage != null) {
            restrictedPackage.add(clazz);
            return true;
          }
          return false;
        });
  }

  @Override
  public Collection<MergeGroup> apply(MergeGroup group) {
    Map<String, MergeGroup> restrictedClasses = new LinkedHashMap<>();
    MergeGroup unrestrictedClasses = new MergeGroup();
    SyntheticItems syntheticItems = appView.getSyntheticItems();

    // Sort all restricted classes into packages.
    for (DexProgramClass clazz : group) {
      if (syntheticItems.isSyntheticOfKind(clazz.getType(), k -> k.API_MODEL_OUTLINE)) {
        restrictedClasses
            .computeIfAbsent(
                clazz.getType().getPackageDescriptor(), ignoreArgument(MergeGroup::new))
            .add(clazz);
      } else {
        unrestrictedClasses.add(clazz);
      }
    }

    tryFindRestrictedPackage(unrestrictedClasses, restrictedClasses);
    removeTrivialGroups(restrictedClasses.values());

    Collection<MergeGroup> groups = new ArrayList<>(restrictedClasses.size() + 1);
    if (unrestrictedClasses.size() > 1) {
      groups.add(unrestrictedClasses);
    }
    groups.addAll(restrictedClasses.values());
    return groups;
  }

  @Override
  public String getName() {
    return "SamePackageForApiOutline";
  }
}
