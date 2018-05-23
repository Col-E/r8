// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.google.common.collect.Iterables;
import java.util.List;
import java.util.stream.Collectors;

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
      List<ProguardMemberRule> memberRules,
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
  protected Iterable<ProguardWildcard> getWildcards() {
    return Iterables.concat(super.getWildcards(), subsequentRule.getWildcards());
  }

  @Override
  protected ProguardIfRule materialize() {
    return new ProguardIfRule(
        getClassAnnotation(),
        getClassAccessFlags(),
        getNegatedClassAccessFlags(),
        getClassTypeNegated(),
        getClassType(),
        getClassNames().materialize(),
        getInheritanceAnnotation() == null ? null : getInheritanceAnnotation().materialize(),
        getInheritanceClassName() == null ? null : getInheritanceClassName().materialize(),
        getInheritanceIsExtends(),
        getMemberRules() == null ? null :
            getMemberRules().stream()
                .map(ProguardMemberRule::materialize).collect(Collectors.toList()),
        subsequentRule.materialize());
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
