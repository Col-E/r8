// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.errors.Unreachable;

public class DefaultInitClassLens extends InitClassLens {

  private static final DefaultInitClassLens INSTANCE = new DefaultInitClassLens();

  private DefaultInitClassLens() {}

  public static DefaultInitClassLens getInstance() {
    return INSTANCE;
  }

  @Override
  public DexField getInitClassField(DexType type) {
    throw new Unreachable("Unexpected InitClass instruction for `" + type.toSourceString() + "`");
  }

  @Override
  public InitClassLens rewrittenWithLens(GraphLens lens) {
    return this;
  }
}
