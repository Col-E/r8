// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.proto;

import it.unimi.dsi.fastutil.ints.Int2IntMap;

public class ArgumentPermutationMap extends ArgumentPermutation {

  private final Int2IntMap newArgumentIndices;

  public ArgumentPermutationMap(Int2IntMap newArgumentIndices) {
    assert newArgumentIndices.defaultReturnValue() == -1;
    this.newArgumentIndices = newArgumentIndices;
  }

  @Override
  public int getNewArgumentIndex(int argumentIndex) {
    int newArgumentIndex = newArgumentIndices.get(argumentIndex);
    return newArgumentIndex >= 0 ? newArgumentIndex : argumentIndex;
  }
}
