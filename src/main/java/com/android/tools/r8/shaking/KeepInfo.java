// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

/** Keep information that can be associated with any item, i.e., class, method or field. */
public abstract class KeepInfo {

  private final boolean pinned;

  public KeepInfo(boolean pinned) {
    this.pinned = pinned;
  }

  public boolean isPinned() {
    return pinned;
  }

  public abstract static class Builder<B extends Builder, K extends KeepInfo> {

    public abstract B self();

    public abstract K doBuild();

    public abstract K top();

    public abstract K bottom();

    public abstract boolean isEqualTo(K other);

    private K original;
    private boolean pinned;

    public Builder(K original) {
      this.original = original;
      pinned = original.isPinned();
    }

    public K build() {
      if (internalIsEqualTo(original)) {
        return original;
      }
      if (internalIsEqualTo(top())) {
        return top();
      }
      if (internalIsEqualTo(bottom())) {
        return bottom();
      }
      return doBuild();
    }

    private boolean internalIsEqualTo(K other) {
      return isPinned() == other.isPinned() && isEqualTo(other);
    }

    public boolean isPinned() {
      return pinned;
    }

    public B setPinned(boolean pinned) {
      this.pinned = pinned;
      return self();
    }

    public B pin() {
      return setPinned(true);
    }

    public B unpin() {
      return setPinned(false);
    }
  }
}
