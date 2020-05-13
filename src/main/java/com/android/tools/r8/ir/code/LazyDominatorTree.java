// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.ir.code.DominatorTree.Assumption;
import com.android.tools.r8.utils.Box;

public class LazyDominatorTree extends Box<DominatorTree> {

  private final IRCode code;

  public LazyDominatorTree(IRCode code) {
    this.code = code;
  }

  @Override
  public DominatorTree get() {
    return computeIfAbsent(() -> new DominatorTree(code, Assumption.MAY_HAVE_UNREACHABLE_BLOCKS));
  }
}
