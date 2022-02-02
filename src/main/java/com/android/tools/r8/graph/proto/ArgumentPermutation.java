// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.proto;

public abstract class ArgumentPermutation {

  public static Builder builder() {
    return new Builder();
  }

  public static DefaultArgumentPermutation getDefault() {
    return DefaultArgumentPermutation.get();
  }

  public abstract int getNewArgumentIndex(int argumentIndex);

  public boolean isDefault() {
    return false;
  }

  public static class Builder {

    public boolean isDefault() {
      return true;
    }

    public Builder setNewArgumentIndex(int argumentIndex, int newArgumentIndex) {
      assert argumentIndex == newArgumentIndex;
      return this;
    }

    public ArgumentPermutation build() {
      return getDefault();
    }
  }
}
