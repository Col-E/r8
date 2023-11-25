// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.utils.ArrayUtils;
import java.io.IOException;
import java.io.OutputStreamWriter;

public class ArtProfileMethodRuleInfoImpl implements ArtProfileMethodRuleInfo {

  private static final ArtProfileMethodRuleInfoImpl[] INSTANCES =
      ArrayUtils.initialize(new ArtProfileMethodRuleInfoImpl[8], ArtProfileMethodRuleInfoImpl::new);

  private final int flags;

  private ArtProfileMethodRuleInfoImpl(int flags) {
    this.flags = flags;
  }

  public static Builder builder() {
    return new Builder();
  }

  public static ArtProfileMethodRuleInfoImpl empty() {
    return INSTANCES[0];
  }

  public int getFlags() {
    return flags;
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

  public void writeHumanReadableFlags(OutputStreamWriter writer) throws IOException {
    if (isHot()) {
      writer.write('H');
    }
    if (isStartup()) {
      writer.write('S');
    }
    if (isPostStartup()) {
      writer.write('P');
    }
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

    Builder clear() {
      flags = 0;
      return this;
    }

    public int getFlags() {
      return flags;
    }

    public boolean isGreaterThanOrEqualTo(Builder builder) {
      return flags == (flags | builder.flags);
    }

    public Builder merge(ArtProfileMethodRuleInfo methodRuleInfo) {
      if (methodRuleInfo.isHot()) {
        setIsHot();
      }
      if (methodRuleInfo.isStartup()) {
        setIsStartup();
      }
      if (methodRuleInfo.isPostStartup()) {
        setIsPostStartup();
      }
      return this;
    }

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

    public Builder joinFlags(ArtProfileMethodRuleInfoImpl methodRuleInfo) {
      flags |= methodRuleInfo.getFlags();
      return this;
    }

    public Builder joinFlags(ArtProfileMethodRule.Builder methodRuleBuilder) {
      flags |= methodRuleBuilder.getMethodRuleInfoBuilder().getFlags();
      return this;
    }

    public ArtProfileMethodRuleInfoImpl build() {
      assert 0 <= flags;
      assert flags < INSTANCES.length;
      return INSTANCES[flags];
    }
  }
}
