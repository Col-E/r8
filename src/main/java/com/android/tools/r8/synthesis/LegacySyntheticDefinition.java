// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramDefinition;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class LegacySyntheticDefinition {
  private final DexProgramClass clazz;
  private final Map<DexType, DexType> contexts = new ConcurrentHashMap<>();

  public LegacySyntheticDefinition(DexProgramClass clazz) {
    this.clazz = clazz;
  }

  public void addContext(ProgramDefinition clazz) {
    DexType type = clazz.getContextType();
    contexts.put(type, type);
  }

  public Set<DexType> getContexts() {
    return contexts.keySet();
  }

  public LegacySyntheticReference toReference() {
    return new LegacySyntheticReference(clazz.getType(), ImmutableSet.copyOf(contexts.keySet()));
  }

  public DexProgramClass getDefinition() {
    return clazz;
  }
}
