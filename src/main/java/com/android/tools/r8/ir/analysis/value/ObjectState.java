// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public abstract class ObjectState {

  public static ObjectState empty() {
    return EmptyObjectState.getInstance();
  }

  public abstract boolean isEmpty();

  public abstract ObjectState rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, GraphLense lens);

  @Override
  public abstract boolean equals(Object o);

  @Override
  public abstract int hashCode();
}
