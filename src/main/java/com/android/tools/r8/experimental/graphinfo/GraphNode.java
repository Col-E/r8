// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.graphinfo;

import com.android.tools.r8.Keep;

@Keep
public abstract class GraphNode {

  private final boolean isLibraryNode;

  public GraphNode(boolean isLibraryNode) {
    this.isLibraryNode = isLibraryNode;
  }

  public boolean isLibraryNode() {
    return isLibraryNode;
  }

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  @Override
  public abstract String toString();
}
