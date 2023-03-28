// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.info.initializer;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class EmptyInstanceInitializerInfoCollection extends InstanceInitializerInfoCollection {

  private static final EmptyInstanceInitializerInfoCollection EMPTY =
      new EmptyInstanceInitializerInfoCollection();

  private EmptyInstanceInitializerInfoCollection() {}

  public static EmptyInstanceInitializerInfoCollection getInstance() {
    return EMPTY;
  }

  @Override
  public DefaultInstanceInitializerInfo getContextInsensitive() {
    return DefaultInstanceInitializerInfo.getInstance();
  }

  @Override
  public DefaultInstanceInitializerInfo get(InvokeDirect invoke) {
    return DefaultInstanceInitializerInfo.getInstance();
  }

  @Override
  public boolean isEmpty() {
    return true;
  }

  @Override
  public InstanceInitializerInfoCollection fixupAfterParametersChanged(
      AppView<AppInfoWithLiveness> appView, ArgumentInfoCollection argumentInfoCollection) {
    return this;
  }

  @Override
  public EmptyInstanceInitializerInfoCollection rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView,
      GraphLens lens,
      GraphLens codeLens,
      PrunedItems prunedItems) {
    return this;
  }
}
