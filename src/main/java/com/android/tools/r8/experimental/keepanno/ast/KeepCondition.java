// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

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

    private KeepUsageKind usageKind = KeepUsageKind.symbolicReference();
    private KeepItemPattern itemPattern;

    private Builder() {}

    public Builder setUsageKind(KeepUsageKind usageKind) {
      this.usageKind = usageKind;
      return this;
    }

    public Builder setItem(KeepItemPattern itemPattern) {
      this.itemPattern = itemPattern;
      return this;
    }

    public KeepCondition build() {
      return new KeepCondition(usageKind, itemPattern);
    }
  }

  private final KeepUsageKind usageKind;
  private final KeepItemPattern itemPattern;

  private KeepCondition(KeepUsageKind usageKind, KeepItemPattern itemPattern) {
    this.usageKind = usageKind;
    this.itemPattern = itemPattern;
    }

  public KeepUsageKind getUsageKind() {
    return usageKind;
    }

  public KeepItemPattern getItemPattern() {
    return itemPattern;
    }
}
