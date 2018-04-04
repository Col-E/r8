// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import java.util.Set;

public class ProguardIfRule extends ProguardKeepRule {

  final ProguardKeepRule subsequentRule;

  public static class Builder extends ProguardKeepRule.Builder {

    ProguardKeepRule subsequentRule = null;

    public void setSubsequentRule(ProguardKeepRule rule) {
      subsequentRule = rule;
    }

    @Override
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
        classNames, inheritanceAnnotation, inheritanceClassName, inheritanceIsExtends, memberRules,
        ProguardKeepRuleType.CONDITIONAL, ProguardKeepRuleModifiers.builder().build());
    this.subsequentRule = subsequentRule;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ProguardIfRule)) {
      return false;
    }
    ProguardIfRule other = (ProguardIfRule) o;
    if (subsequentRule != other.subsequentRule) {
      return false;
    }
    return super.equals(o);
  }

  @Override
  public int hashCode() {
    return super.hashCode() * 3 + subsequentRule.hashCode();
  }

  @Override
  String typeString() {
    return "if";
  }
}
