// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation.assume;

import com.android.tools.r8.shaking.ProguardMemberRule;
import com.android.tools.r8.shaking.ProguardMemberRuleReturnValue;

public class AssumeInfo {

  public enum AssumeType {
    ASSUME_NO_SIDE_EFFECTS,
    ASSUME_VALUES;

    AssumeType meet(AssumeType type) {
      return this == ASSUME_NO_SIDE_EFFECTS || type == ASSUME_NO_SIDE_EFFECTS
          ? ASSUME_NO_SIDE_EFFECTS
          : ASSUME_VALUES;
    }
  }

  private final AssumeType type;
  private final ProguardMemberRule rule;

  public AssumeInfo(AssumeType type, ProguardMemberRule rule) {
    this.type = type;
    this.rule = rule;
  }

  public boolean hasReturnInfo() {
    return rule.hasReturnValue();
  }

  public ProguardMemberRuleReturnValue getReturnInfo() {
    return rule.getReturnValue();
  }

  public boolean isAssumeNoSideEffects() {
    return type == AssumeType.ASSUME_NO_SIDE_EFFECTS;
  }

  public boolean isAssumeValues() {
    return type == AssumeType.ASSUME_VALUES;
  }

  public AssumeInfo meet(AssumeInfo lookup) {
    return new AssumeInfo(type.meet(lookup.type), rule.hasReturnValue() ? rule : lookup.rule);
  }

  @Override
  public boolean equals(Object other) {
    if (other == null) {
      return false;
    }
    if (!(other instanceof AssumeInfo)) {
      return false;
    }
    AssumeInfo assumeInfo = (AssumeInfo) other;
    return type == assumeInfo.type && rule == assumeInfo.rule;
  }

  @Override
  public int hashCode() {
    return type.ordinal() * 31 + rule.hashCode();
  }
}
