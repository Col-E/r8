// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.utils.structural.Equatable;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import java.util.Arrays;
import java.util.List;

public class DexDebugInfo extends CachedHashValueDexItem implements StructuralItem<DexDebugInfo> {

  public final int startLine;
  public final DexString[] parameters;
  public DexDebugEvent[] events;

  private static void specify(StructuralSpecification<DexDebugInfo, ?> spec) {
    spec.withInt(d -> d.startLine)
        .withItemArrayAllowingNullMembers(d -> d.parameters)
        .withItemArray(d -> d.events);
  }

  public DexDebugInfo(int startLine, DexString[] parameters, DexDebugEvent[] events) {
    assert startLine >= 0;
    this.startLine = startLine;
    this.parameters = parameters;
    this.events = events;
    // This call to hashCode is just an optimization to speedup equality when
    // canonicalizing DexDebugInfo objects inside a synchronize method.
    hashCode();
  }

  @Override
  public DexDebugInfo self() {
    return this;
  }

  @Override
  public StructuralMapping<DexDebugInfo> getStructuralMapping() {
    return DexDebugInfo::specify;
  }

  public List<DexDebugEntry> computeEntries(DexMethod method) {
    DexDebugEntryBuilder builder = new DexDebugEntryBuilder(startLine, method);
    for (DexDebugEvent event : events) {
      event.accept(builder);
    }
    return builder.build();
  }

  @Override
  public int computeHashCode() {
    return startLine
        + Arrays.hashCode(parameters) * 7
        + Arrays.hashCode(events) * 13;
  }

  @Override
  public final boolean computeEquals(Object other) {
    return Equatable.equalsImpl(this, other);
  }

  public void collectIndexedItems(IndexedItemCollection indexedItems, GraphLens graphLens) {
    for (DexString parameter : parameters) {
      if (parameter != null) {
        parameter.collectIndexedItems(indexedItems);
      }
    }
    for (DexDebugEvent event : events) {
      event.collectIndexedItems(indexedItems, graphLens);
    }
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection collection) {
    collection.add(this);
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("DebugInfo (line " + startLine + ") events: [\n");
    for (DexDebugEvent event : events) {
      builder.append("  ").append(event).append("\n");
    }
    builder.append("  END_SEQUENCE\n");
    builder.append("]\n");
    return builder.toString();
  }
}
