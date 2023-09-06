// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import java.util.List;

public class ProguardCheckDiscardRule extends ProguardConfigurationRule {

  @SuppressWarnings("NonCanonicalType")
  public static class Builder
      extends ProguardConfigurationRule.Builder<ProguardCheckDiscardRule, Builder> {

    private Builder() {
      super();
    }

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public ProguardCheckDiscardRule build() {
      return new ProguardCheckDiscardRule(
          origin,
          getPosition(),
          source,
          buildClassAnnotations(),
          classAccessFlags,
          negatedClassAccessFlags,
          classTypeNegated,
          classType,
          classNames,
          buildInheritanceAnnotations(),
          inheritanceClassName,
          inheritanceIsExtends,
          memberRules);
    }
  }

  private ProguardCheckDiscardRule(
      Origin origin,
      Position position,
      String source,
      List<ProguardTypeMatcher> classAnnotations,
      ProguardAccessFlags classAccessFlags,
      ProguardAccessFlags negatedClassAccessFlags,
      boolean classTypeNegated,
      ProguardClassType classType,
      ProguardClassNameList classNames,
      List<ProguardTypeMatcher> inheritanceAnnotations,
      ProguardTypeMatcher inheritanceClassName,
      boolean inheritanceIsExtends,
      List<ProguardMemberRule> memberRules) {
    super(
        origin,
        position,
        source,
        classAnnotations,
        classAccessFlags,
        negatedClassAccessFlags,
        classTypeNegated,
        classType,
        classNames,
        inheritanceAnnotations,
        inheritanceClassName,
        inheritanceIsExtends,
        memberRules);
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean isProguardCheckDiscardRule() {
    return true;
  }

  @Override
  public ProguardCheckDiscardRule asProguardCheckDiscardRule() {
    return this;
  }

  @Override
  String typeString() {
    return "checkdiscard";
  }
}
