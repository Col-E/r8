// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.Unreachable;
import java.util.List;

public class InlineRule extends ProguardConfigurationRule {

  public enum Type {
    ALWAYS, FORCE, NEVER
  }

  public static class Builder extends ProguardConfigurationRule.Builder {

    private Builder() {
    }

    Type type;

    public Builder setType(Type type) {
      this.type = type;
      return this;
    }

    public InlineRule build() {
      return new InlineRule(classAnnotation, classAccessFlags,
          negatedClassAccessFlags, classTypeNegated, classType, classNames, inheritanceAnnotation,
          inheritanceClassName, inheritanceIsExtends, memberRules, type);
    }
  }

  private final Type type;

  private InlineRule(
      ProguardTypeMatcher classAnnotation,
      ProguardAccessFlags classAccessFlags,
      ProguardAccessFlags negatedClassAccessFlags,
      boolean classTypeNegated,
      ProguardClassType classType,
      ProguardClassNameList classNames,
      ProguardTypeMatcher inheritanceAnnotation,
      ProguardTypeMatcher inheritanceClassName,
      boolean inheritanceIsExtends,
      List<ProguardMemberRule> memberRules,
      Type type) {
    super(classAnnotation, classAccessFlags, negatedClassAccessFlags, classTypeNegated, classType,
        classNames, inheritanceAnnotation, inheritanceClassName, inheritanceIsExtends, memberRules);
    this.type = type;
  }

  public static InlineRule.Builder builder() {
    return new InlineRule.Builder();
  }

  public Type getType() {
    return type;
  }

  public ProguardCheckDiscardRule asProguardCheckDiscardRule() {
    assert type == Type.FORCE;
    ProguardCheckDiscardRule.Builder builder = ProguardCheckDiscardRule.builder();
    builder.setClassAnnotation(getClassAnnotation());
    builder.setClassAccessFlags(getClassAccessFlags());
    builder.setNegatedClassAccessFlags(getNegatedClassAccessFlags());
    builder.setClassTypeNegated(getClassTypeNegated());
    builder.setClassType(getClassType());
    builder.setClassNames(getClassNames());
    builder.setInheritanceAnnotation(getInheritanceAnnotation());
    builder.setInheritanceIsExtends(getInheritanceIsExtends());
    builder.setMemberRules(getMemberRules());
    return builder.build();
  }

  @Override
  String typeString() {
    switch (type) {
      case ALWAYS:
        return "alwaysinline";
      case FORCE:
        return "forceinline";
      case NEVER:
        return "neverinline";
    }
    throw new Unreachable("Unknown inline type " + type);
  }
}
