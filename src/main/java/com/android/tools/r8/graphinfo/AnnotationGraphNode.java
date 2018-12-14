// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graphinfo;

import com.android.tools.r8.Keep;
import com.android.tools.r8.graph.DexItem;

@Keep
public final class AnnotationGraphNode extends GraphNode {

  private final DexItem annotatedItem;

  public AnnotationGraphNode(DexItem annotatedItem) {
    assert annotatedItem != null;
    this.annotatedItem = annotatedItem;
  }

  @Override
  public boolean equals(Object o) {
    return this == o
        || (o instanceof AnnotationGraphNode
            && ((AnnotationGraphNode) o).annotatedItem == annotatedItem);
  }

  @Override
  public int hashCode() {
    return annotatedItem.hashCode();
  }

  public String getDescriptor() {
    return annotatedItem.toSourceString();
  }

  /**
   * Get a unique identity string determining this annotated-item node.
   *
   * <p>This is the descriptor of the concrete node type.
   */
  @Override
  public String identity() {
    return getDescriptor();
  }
}
