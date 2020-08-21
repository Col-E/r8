// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public class ProgramPackageCollection implements Iterable<ProgramPackage> {

  private final Map<String, ProgramPackage> packages;

  private ProgramPackageCollection(Map<String, ProgramPackage> packages) {
    this.packages = packages;
  }

  public static ProgramPackageCollection create(AppView<?> appView) {
    Map<String, ProgramPackage> packages = new HashMap<>();
    assert !appView.appInfo().getSyntheticItems().hasPendingSyntheticClasses();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      packages
          .computeIfAbsent(clazz.getType().getPackageDescriptor(), ProgramPackage::new)
          .add(clazz);
    }
    return new ProgramPackageCollection(packages);
  }

  @Override
  public Iterator<ProgramPackage> iterator() {
    return packages.values().iterator();
  }
}
