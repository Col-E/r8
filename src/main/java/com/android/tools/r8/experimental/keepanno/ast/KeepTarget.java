// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

public class KeepTarget {

  public static KeepTarget any() {
    return KeepTarget.builder().setItem(KeepItemPattern.any()).build();
  }

  public static class Builder {

    private KeepItemPattern item;
    private KeepOptions options = KeepOptions.keepAll();

    private Builder() {}

    public Builder setItem(KeepItemPattern item) {
      this.item = item;
      return this;
    }

    public Builder setOptions(KeepOptions options) {
      this.options = options;
      return this;
    }

    public KeepTarget build() {
      if (item == null) {
        throw new KeepEdgeException("Target must define an item pattern");
      }
      return new KeepTarget(item, options);
    }
  }

  private final KeepItemPattern item;
  private final KeepOptions options;

  private KeepTarget(KeepItemPattern item, KeepOptions options) {
    this.item = item;
    this.options = options;
  }

  public static Builder builder() {
    return new Builder();
  }

  public KeepItemPattern getItem() {
    return item;
  }

  public KeepOptions getOptions() {
    return options;
  }
}
