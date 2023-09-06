// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.inspector.internal;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.inspector.ClassInspector;
import com.android.tools.r8.inspector.Inspector;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;

public class InspectorImpl implements Inspector {

  // This wrapping appears odd, but allows hooking in inspections on the impl type from tests.
  @SuppressWarnings("MixedMutabilityReturnType")
  public static List<Consumer<InspectorImpl>> wrapInspections(
      Collection<Consumer<Inspector>> inspections) {
    if (inspections == null || inspections.isEmpty()) {
      return Collections.emptyList();
    }
    List<Consumer<InspectorImpl>> wrapped = new ArrayList<>(inspections.size());
    for (Consumer<Inspector> inspection : inspections) {
      wrapped.add(inspection::accept);
    }
    return wrapped;
  }

  public static void runInspections(
      List<Consumer<InspectorImpl>> inspections, Collection<DexProgramClass> classes) {
    if (inspections == null || inspections.isEmpty()) {
      return;
    }
    InspectorImpl inspector = new InspectorImpl(classes);
    for (Consumer<InspectorImpl> inspection : inspections) {
      inspection.accept(inspector);
    }
  }

  private final Collection<DexProgramClass> classes;

  public InspectorImpl(Collection<DexProgramClass> classes) {
    this.classes = classes;
  }

  @Override
  public void forEachClass(Consumer<ClassInspector> inspection) {
    for (DexProgramClass clazz : classes) {
      inspection.accept(new ClassInspectorImpl(clazz));
    }
  }
}
