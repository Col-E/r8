// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.lens;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.Timing;

public class ThrowingInitClassLens extends InitClassLens {

  private static final ThrowingInitClassLens INSTANCE = new ThrowingInitClassLens();

  private ThrowingInitClassLens() {}

  public static ThrowingInitClassLens getInstance() {
    return INSTANCE;
  }

  @Override
  public DexField getInitClassField(DexType type) {
    throw new Unreachable("Unexpected InitClass instruction for `" + type.toSourceString() + "`");
  }

  @Override
  public InitClassLens rewrittenWithLens(GraphLens lens, Timing timing) {
    return this;
  }
}
