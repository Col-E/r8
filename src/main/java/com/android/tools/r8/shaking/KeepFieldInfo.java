// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

/** Immutable keep requirements for a field. */
public final class KeepFieldInfo extends KeepInfo {

  // Requires all aspects of a field to be kept.
  private static final KeepFieldInfo TOP = new KeepFieldInfo(true);

  // Requires no aspects of a field to be kept.
  private static final KeepFieldInfo BOTTOM = new KeepFieldInfo(false);

  public static KeepFieldInfo top() {
    return TOP;
  }

  public static KeepFieldInfo bottom() {
    return BOTTOM;
  }

  private KeepFieldInfo(boolean pinned) {
    super(pinned);
  }

  public Builder builder() {
    return new Builder(this);
  }

  public static class Builder extends KeepInfo.Builder<Builder, KeepFieldInfo> {

    private Builder(KeepFieldInfo original) {
      super(original);
    }

    @Override
    public KeepFieldInfo top() {
      return TOP;
    }

    @Override
    public KeepFieldInfo bottom() {
      return BOTTOM;
    }

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public boolean isEqualTo(KeepFieldInfo other) {
      return true;
    }

    @Override
    public KeepFieldInfo doBuild() {
      return new KeepFieldInfo(isPinned());
    }
  }
}
