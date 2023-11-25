// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import java.util.Objects;

public class KeepTarget {

  public static class Builder {

    private KeepItemReference item;
    private KeepOptions options = KeepOptions.keepAll();

    private Builder() {}

    public Builder setItemReference(KeepItemReference item) {
      this.item = item;
      return this;
    }

    public Builder setItemPattern(KeepItemPattern itemPattern) {
      return setItemReference(itemPattern.toItemReference());
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

  private final KeepItemReference item;
  private final KeepOptions options;

  private KeepTarget(KeepItemReference item, KeepOptions options) {
    assert item != null;
    assert options != null;
    this.item = item;
    this.options = options;
  }

  public static Builder builder() {
    return new Builder();
  }

  public KeepItemReference getItem() {
    return item;
  }

  public KeepOptions getOptions() {
    return options;
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KeepTarget that = (KeepTarget) o;
    return item.equals(that.item) && options.equals(that.options);
  }

  @Override
  public int hashCode() {
    return Objects.hash(item, options);
  }

  @Override
  public String toString() {
    return "KeepTarget{" + "item=" + item + ", options=" + options + '}';
  }
}
