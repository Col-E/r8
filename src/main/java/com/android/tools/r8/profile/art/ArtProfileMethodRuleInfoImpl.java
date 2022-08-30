// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

public class ArtProfileMethodRuleInfoImpl implements ArtProfileMethodRuleInfo {

  private static final ArtProfileMethodRuleInfoImpl EMPTY = new ArtProfileMethodRuleInfoImpl(0);

  private final int flags;

  ArtProfileMethodRuleInfoImpl(int flags) {
    this.flags = flags;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static ArtProfileMethodRuleInfoImpl empty() {
    return EMPTY;
  }

  public boolean isEmpty() {
    return flags == 0;
  }

  @Override
  public boolean isHot() {
    return ArtProfileMethodRuleFlagsUtils.isHot(flags);
  }

  @Override
  public boolean isStartup() {
    return ArtProfileMethodRuleFlagsUtils.isStartup(flags);
  }

  @Override
  public boolean isPostStartup() {
    return ArtProfileMethodRuleFlagsUtils.isPostStartup(flags);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ArtProfileMethodRuleInfoImpl that = (ArtProfileMethodRuleInfoImpl) o;
    return flags == that.flags;
  }

  @Override
  public int hashCode() {
    return flags;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    if (isHot()) {
      builder.append('H');
    }
    if (isStartup()) {
      builder.append('S');
    }
    if (isPostStartup()) {
      builder.append('P');
    }
    return builder.toString();
  }

  public static class Builder implements ArtProfileMethodRuleInfoBuilder {

    private int flags;

    public Builder setIsHot() {
      return setIsHot(true);
    }

    @Override
    public Builder setIsHot(boolean isHot) {
      flags = ArtProfileMethodRuleFlagsUtils.setIsHot(flags, isHot);
      return this;
    }

    public Builder setIsStartup() {
      return setIsStartup(true);
    }

    @Override
    public Builder setIsStartup(boolean isStartup) {
      flags = ArtProfileMethodRuleFlagsUtils.setIsStartup(flags, isStartup);
      return this;
    }

    public Builder setIsPostStartup() {
      return setIsPostStartup(true);
    }

    @Override
    public Builder setIsPostStartup(boolean isPostStartup) {
      flags = ArtProfileMethodRuleFlagsUtils.setIsPostStartup(flags, isPostStartup);
      return this;
    }

    public ArtProfileMethodRuleInfoImpl build() {
      return new ArtProfileMethodRuleInfoImpl(flags);
    }
  }
}
