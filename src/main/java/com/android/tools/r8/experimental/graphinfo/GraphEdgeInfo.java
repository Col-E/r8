// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.graphinfo;

public class GraphEdgeInfo {

  // TODO(b/120959039): Simplify these. Most of the information is present in the source node.
  public enum EdgeKind {
    // Prioritized list of edge types.
    KeepRule,
    CompatibilityRule,
    InstantiatedIn,
    InvokedViaSuper,
    TargetedBySuper,
    InvokedFrom,
    InvokedFromLambdaCreatedIn,
    ReferencedFrom,
    ReachableFromLiveType,
    ReferencedInAnnotation,
    IsLibraryMethod,
  }

  private final EdgeKind kind;

  public GraphEdgeInfo(EdgeKind kind) {
    this.kind = kind;
  }

  public EdgeKind edgeKind() {
    return kind;
  }

  @Override
  public String toString() {
    return "{edge-type:" + kind.toString() + "}";
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof GraphEdgeInfo && ((GraphEdgeInfo) o).kind == kind);
  }

  @Override
  public int hashCode() {
    return kind.hashCode();
  }
}
