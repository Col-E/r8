// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.field;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

/**
 * Represents that no information is known about the way a particular constructor initializes an
 * instance field of the newly created instance.
 */
public class UnknownInstanceFieldInitializationInfo implements InstanceFieldInitializationInfo {

  private static final UnknownInstanceFieldInitializationInfo INSTANCE =
      new UnknownInstanceFieldInitializationInfo();

  private UnknownInstanceFieldInitializationInfo() {}

  public static UnknownInstanceFieldInitializationInfo getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isUnknown() {
    return true;
  }

  @Override
  public InstanceFieldInitializationInfo fixupAfterParametersChanged(
      ArgumentInfoCollection argumentInfoCollection) {
    return this;
  }

  @Override
  public InstanceFieldInitializationInfo rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView, DexType newType, GraphLens lens, GraphLens codeLens) {
    return this;
  }

  @Override
  public String toString() {
    return "UnknownInstanceFieldInitializationInfo";
  }
}
