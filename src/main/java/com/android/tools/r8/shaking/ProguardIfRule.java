// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import java.util.Set;

public class ProguardIfRule extends ProguardConfigurationRule {

  final ProguardKeepRule subsequentRule;

  public static class Builder extends ProguardConfigurationRule.Builder {

    ProguardKeepRule subsequentRule = null;

    private Builder() {
    }

    public void setSubsequentRule(ProguardKeepRule rule) {
      subsequentRule = rule;
    }

    public ProguardIfRule build() {
      assert subsequentRule != null : "Option -if without a subsequent rule.";
      return new ProguardIfRule(classAnnotation, classAccessFlags,
          negatedClassAccessFlags, classTypeNegated, classType, classNames, inheritanceAnnotation,
          inheritanceClassName, inheritanceIsExtends, memberRules, subsequentRule);
    }
  }

  private ProguardIfRule(ProguardTypeMatcher classAnnotation,
      ProguardAccessFlags classAccessFlags,
      ProguardAccessFlags negatedClassAccessFlags, boolean classTypeNegated,
      ProguardClassType classType, ProguardClassNameList classNames,
      ProguardTypeMatcher inheritanceAnnotation,
      ProguardTypeMatcher inheritanceClassName, boolean inheritanceIsExtends,
      Set<ProguardMemberRule> memberRules,
      ProguardKeepRule subsequentRule) {
    super(classAnnotation, classAccessFlags, negatedClassAccessFlags, classTypeNegated, classType,
        classNames, inheritanceAnnotation, inheritanceClassName, inheritanceIsExtends, memberRules);
    this.subsequentRule = subsequentRule;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  String typeString() {
    return "if";
  }
}
