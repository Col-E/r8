// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graphinfo;

import com.android.tools.r8.Keep;
import com.android.tools.r8.graph.DexType;

@Keep
public final class ClassGraphNode extends GraphNode {

  private final DexType clazz;

  public ClassGraphNode(DexType clazz) {
    assert clazz != null;
    this.clazz = clazz;
  }

  @Override
  public boolean equals(Object o) {
    return this == o || (o instanceof ClassGraphNode && ((ClassGraphNode) o).clazz == clazz);
  }

  @Override
  public int hashCode() {
    return clazz.hashCode();
  }

  public String getDescriptor() {
    return clazz.toDescriptorString();
  }

  /**
   * Get a unique identity string determining this clazz node.
   *
   * <p>This is just the class descriptor.
   */
  @Override
  public String identity() {
    return getDescriptor();
  }
}
