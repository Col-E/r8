// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import java.util.List;

public class ReprocessMethodRule extends ProguardConfigurationRule {

  public enum Type {
    ALWAYS,
    NEVER
  }

  @SuppressWarnings("NonCanonicalType")
  public static class Builder
      extends ProguardConfigurationRule.Builder<ReprocessMethodRule, Builder> {

    private Type type;

    private Builder() {
      super();
    }

    public Builder setType(Type type) {
      this.type = type;
      return this;
    }

    @Override
    public Builder self() {
      return this;
    }

    @Override
    public ReprocessMethodRule build() {
      return new ReprocessMethodRule(
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
          memberRules,
          type);
    }
  }

  private final Type type;

  private ReprocessMethodRule(
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
      List<ProguardMemberRule> memberRules,
      Type type) {
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
    this.type = type;
  }

  public static Builder builder() {
    return new Builder();
  }

  public Type getType() {
    return type;
  }

  @Override
  public boolean isReprocessMethodRule() {
    return true;
  }

  @Override
  public ReprocessMethodRule asReprocessMethodRule() {
    return this;
  }

  @Override
  String typeString() {
    switch (type) {
      case ALWAYS:
        return "reprocessmethod";
      case NEVER:
        return "neverreprocessmethod";
      default:
        throw new Unreachable();
    }
  }
}
