// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

/** Immutable keep requirements for a method. */
public final class KeepMethodInfo extends KeepInfo {

  // Requires all aspects of a method to be kept.
  private static final KeepMethodInfo TOP = new KeepMethodInfo(true);

  // Requires no aspects of a method to be kept.
  private static final KeepMethodInfo BOTTOM = new KeepMethodInfo(false);

  public static KeepMethodInfo top() {
    return TOP;
  }

  public static KeepMethodInfo bottom() {
    return BOTTOM;
  }

  private KeepMethodInfo(boolean pinned) {
    super(pinned);
  }

  public Builder builder() {
    return new Builder(this);
  }

  public static class Builder extends KeepInfo.Builder<Builder, KeepMethodInfo> {

    private Builder(KeepMethodInfo original) {
      super(original);
    }

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public KeepMethodInfo top() {
      return TOP;
    }

    @Override
    public KeepMethodInfo bottom() {
      return BOTTOM;
    }

    @Override
    public boolean isEqualTo(KeepMethodInfo other) {
      return true;
    }

    @Override
    public KeepMethodInfo doBuild() {
      return new KeepMethodInfo(isPinned());
    }
  }
}
