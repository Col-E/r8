// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.proto;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;

public abstract class ArgumentPermutation {

  public static Builder builder(int size) {
    return new Builder(size);
  }

  public static DefaultArgumentPermutation getDefault() {
    return DefaultArgumentPermutation.get();
  }

  public abstract int getNewArgumentIndex(int argumentIndex);

  public boolean isDefault() {
    return false;
  }

  public static class Builder {

    private final Int2IntMap newArgumentIndices;

    private Builder(int size) {
      Int2IntMap newArgumentIndices = size <= 30 ? new Int2IntArrayMap() : new Int2IntOpenHashMap();
      newArgumentIndices.defaultReturnValue(-1);
      this.newArgumentIndices = newArgumentIndices;
    }

    public boolean isDefault() {
      return newArgumentIndices.isEmpty();
    }

    public Builder setNewArgumentIndex(int argumentIndex, int newArgumentIndex) {
      if (argumentIndex != newArgumentIndex) {
        newArgumentIndices.put(argumentIndex, newArgumentIndex);
      } else {
        newArgumentIndices.remove(argumentIndex);
      }
      return this;
    }

    public ArgumentPermutation build() {
      if (isDefault()) {
        return getDefault();
      }
      return new ArgumentPermutationMap(newArgumentIndices);
    }
  }
}
