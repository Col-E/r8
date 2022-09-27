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
public abstract class KeepCondition {

  /** A condition that is unconditionally true. */
  public static KeepCondition trueCondition() {
    return KeepConditionTrue.getInstance();
  }

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
      return new KeepConditionItem(itemPattern);
    }
  }

  private static class KeepConditionTrue extends KeepCondition {

    private static KeepConditionTrue INSTANCE = null;

    public static KeepConditionTrue getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new KeepConditionTrue();
      }
      return INSTANCE;
    }
  }

  private static class KeepConditionFalse extends KeepCondition {

    private static KeepConditionFalse INSTANCE = null;

    public static KeepConditionFalse getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new KeepConditionFalse();
      }
      return INSTANCE;
    }
  }

  private static class KeepConditionItem extends KeepCondition {

    private final KeepItemPattern itemPattern;

    private KeepConditionItem(KeepItemPattern itemPattern) {
      this.itemPattern = itemPattern;
    }
  }

  private KeepCondition() {}
}
