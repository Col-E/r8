// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import java.util.TreeMap;

public class SortedProgramPackageCollection extends ProgramPackageCollection {

  private SortedProgramPackageCollection() {
    super(new TreeMap<>());
  }

  public static SortedProgramPackageCollection createWithAllProgramClasses(AppView<?> appView) {
    assert !appView.appInfo().getSyntheticItems().hasPendingSyntheticClasses();
    SortedProgramPackageCollection programPackages = new SortedProgramPackageCollection();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      programPackages.addProgramClass(clazz);
    }
    return programPackages;
  }

  @Override
  public boolean addProgramClass(DexProgramClass clazz) {
    return packages
        .computeIfAbsent(clazz.getType().getPackageDescriptor(), SortedProgramPackage::new)
        .add(clazz);
  }
}
