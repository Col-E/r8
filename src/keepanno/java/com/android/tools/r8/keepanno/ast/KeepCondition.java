// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

/**
 * A keep condition is the content of an item in the set of preconditions.
 *
 * <p>It can be trivially true, or represent some program item pattern that must be present in the
 * program residual. When an condition is denoted by a program item, the condition also specifies
 * the extent of the item for which it is predicated on. The extent is given by a "usage kind" that
 * can be either its "symbolic reference" or its "actual use".
 */
public final class KeepCondition {

  public static Builder builder() {
    return new Builder();
  }

  public static class Builder {

    private KeepItemReference item;

    private Builder() {}

    public Builder setItemReference(KeepItemReference item) {
      this.item = item;
      return this;
    }

    public Builder setItemPattern(KeepItemPattern itemPattern) {
      return setItemReference(itemPattern.toItemReference());
    }

    public KeepCondition build() {
      return new KeepCondition(item);
    }
  }

  private final KeepItemReference item;

  private KeepCondition(KeepItemReference item) {
    assert item != null;
    this.item = item;
  }

  public KeepItemReference getItem() {
    return item;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    KeepCondition that = (KeepCondition) o;
    return item.equals(that.item);
  }

  @Override
  public int hashCode() {
    return item.hashCode();
  }

  @Override
  public String toString() {
    return item.toString();
  }
}
