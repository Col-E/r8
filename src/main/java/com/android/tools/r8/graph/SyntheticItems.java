// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;

public class SyntheticItems {

  // For some optimizations, e.g. optimizing synthetic classes, we may need to resolve the current
  // class being optimized.
  private final ConcurrentHashMap<DexType, DexProgramClass> synthesizedClasses =
      new ConcurrentHashMap<>();

  private SyntheticItems() {}

  public static SyntheticItems createInitialSyntheticItems() {
    return new SyntheticItems();
  }

  public Collection<DexProgramClass> getSyntheticClasses() {
    return Collections.unmodifiableCollection(synthesizedClasses.values());
  }

  public void addSyntheticClass(DexProgramClass clazz) {
    assert clazz.type.isD8R8SynthesizedClassType();
    DexProgramClass previous = synthesizedClasses.put(clazz.type, clazz);
    assert previous == null || previous == clazz;
  }

  public DexProgramClass getSyntheticClass(DexType type) {
    return synthesizedClasses.get(type);
  }

  public SyntheticItems commit(DexApplication application) {
    assert verifyAllSyntheticsAreInApp(application, this);
    // All synthetics are in the app proper and no further meta-data is present so the empty
    // collection is currently returned here.
    return new SyntheticItems();
  }

  private static boolean verifyAllSyntheticsAreInApp(
      DexApplication app, SyntheticItems synthetics) {
    for (DexProgramClass clazz : synthetics.getSyntheticClasses()) {
      assert app.programDefinitionFor(clazz.type) != null;
    }
    return true;
  }
}
