// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

public class ArtProfileMethodRuleInfoImpl implements ArtProfileMethodRuleInfo {

  private static final int FLAG_HOT = 1;
  private static final int FLAG_STARTUP = 2;
  private static final int FLAG_POST_STARTUP = 4;

  private final int flags;

  ArtProfileMethodRuleInfoImpl(int flags) {
    this.flags = flags;
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEmpty() {
    return flags == 0;
  }

  @Override
  public boolean isHot() {
    return (flags & FLAG_HOT) != 0;
  }

  @Override
  public boolean isStartup() {
    return (flags & FLAG_STARTUP) != 0;
  }

  @Override
  public boolean isPostStartup() {
    return (flags & FLAG_POST_STARTUP) != 0;
  }

  public static class Builder {

    private int flags;

    public Builder setHot() {
      flags |= FLAG_HOT;
      return this;
    }

    public Builder setStartup() {
      flags |= FLAG_STARTUP;
      return this;
    }

    public Builder setPostStartup() {
      flags |= FLAG_POST_STARTUP;
      return this;
    }

    public ArtProfileMethodRuleInfoImpl build() {
      return new ArtProfileMethodRuleInfoImpl(flags);
    }
  }
}
