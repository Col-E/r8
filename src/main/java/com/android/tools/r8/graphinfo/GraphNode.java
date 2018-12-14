// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graphinfo;

import com.android.tools.r8.Keep;

@Keep
public abstract class GraphNode {

  public abstract String identity();

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();

  @Override
  public String toString() {
    return identity();
  }
}
