// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.MainDexClasses;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public class HorizontalClassMerger {

  private final AppView<AppInfoWithLiveness> appView;
  private final MainDexClasses mainDexClasses;

  private final PolicyExecutor policyExecutor;

  public HorizontalClassMerger(
      AppView<AppInfoWithLiveness> appView, MainDexClasses mainDexClasses) {
    this.appView = appView;
    this.mainDexClasses = mainDexClasses;

    Policy[] policies = {
      // TODO: add policies
    };
    this.policyExecutor = new SimplePolicyExecutor(Arrays.asList(policies));
  }

  public Collection<Collection<DexProgramClass>> run() {
    Map<FieldMultiset, Collection<DexProgramClass>> classes = new HashMap<>();

    // Group classes by same field signature using the hash map.
    for (DexProgramClass clazz : appView.appInfo().app().classesWithDeterministicOrder()) {
      classes.computeIfAbsent(new FieldMultiset(clazz), ignore -> new ArrayList<>()).add(clazz);
    }

    // Run the policies on all collected classes to produce a final grouping.
    Collection<Collection<DexProgramClass>> groups = policyExecutor.run(classes.values());

    return groups;
  }
}
