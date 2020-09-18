// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ProgramPackageCollection implements Iterable<ProgramPackage> {

  protected final Map<String, ProgramPackage> packages;

  protected ProgramPackageCollection(Map<String, ProgramPackage> packages) {
    this.packages = packages;
  }

  public static ProgramPackageCollection createWithAllProgramClasses(AppView<?> appView) {
    assert !appView.appInfo().getSyntheticItems().hasPendingSyntheticClasses();
    ProgramPackageCollection programPackages = new ProgramPackageCollection(new HashMap<>());
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      programPackages.addProgramClass(clazz);
    }
    return programPackages;
  }

  public static ProgramPackageCollection createEmpty() {
    return new ProgramPackageCollection(new HashMap<>());
  }

  public boolean addProgramClass(DexProgramClass clazz) {
    return packages
        .computeIfAbsent(clazz.getType().getPackageDescriptor(), ProgramPackage::new)
        .add(clazz);
  }

  public boolean isEmpty() {
    return packages.isEmpty();
  }

  @Override
  public Iterator<ProgramPackage> iterator() {
    return packages.values().iterator();
  }
}
