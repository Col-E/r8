// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

/** Immutable keep requirements for a class. */
public final class KeepClassInfo extends KeepInfo {

  // Requires all aspects of a class to be kept.
  private static final KeepClassInfo TOP = new KeepClassInfo(true);

  // Requires no aspects of a class to be kept.
  private static final KeepClassInfo BOTTOM = new KeepClassInfo(false);

  public static KeepClassInfo top() {
    return TOP;
  }

  public static KeepClassInfo bottom() {
    return BOTTOM;
  }

  private KeepClassInfo(boolean pinned) {
    super(pinned);
  }

  public Builder builder() {
    return new Builder(this);
  }

  public static class Builder extends KeepInfo.Builder<Builder, KeepClassInfo> {

    private Builder(KeepClassInfo original) {
      super(original);
    }

    @Override
    public KeepClassInfo top() {
      return TOP;
    }

    @Override
    public KeepClassInfo bottom() {
      return BOTTOM;
    }

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public boolean isEqualTo(KeepClassInfo other) {
      return true;
    }

    @Override
    public KeepClassInfo doBuild() {
      return new KeepClassInfo(isPinned());
    }
  }
}
