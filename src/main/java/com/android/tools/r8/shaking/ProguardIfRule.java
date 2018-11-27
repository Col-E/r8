// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import java.util.List;
import java.util.stream.Collectors;

public class ProguardIfRule extends ProguardKeepRuleBase {

  final ProguardKeepRule subsequentRule;

  public static class Builder extends ProguardKeepRuleBase.Builder<ProguardIfRule, Builder> {

    ProguardKeepRule subsequentRule = null;

    protected Builder() {
      super();
    }

    @Override
    public Builder self() {
      return this;
    }

    public void setSubsequentRule(ProguardKeepRule rule) {
      subsequentRule = rule;
    }

    @Override
    public ProguardIfRule build() {
      assert subsequentRule != null : "Option -if without a subsequent rule.";
      return new ProguardIfRule(origin, getPosition(), source, classAnnotation, classAccessFlags,
          negatedClassAccessFlags, classTypeNegated, classType, classNames, inheritanceAnnotation,
          inheritanceClassName, inheritanceIsExtends, memberRules, subsequentRule);
    }
  }

  private ProguardIfRule(
      Origin origin,
      Position position,
      String source,
      ProguardTypeMatcher classAnnotation,
      ProguardAccessFlags classAccessFlags,
      ProguardAccessFlags negatedClassAccessFlags, boolean classTypeNegated,
      ProguardClassType classType, ProguardClassNameList classNames,
      ProguardTypeMatcher inheritanceAnnotation,
      ProguardTypeMatcher inheritanceClassName, boolean inheritanceIsExtends,
      List<ProguardMemberRule> memberRules,
      ProguardKeepRule subsequentRule) {
    super(origin, position, source, classAnnotation, classAccessFlags, negatedClassAccessFlags,
        classTypeNegated, classType, classNames, inheritanceAnnotation, inheritanceClassName,
        inheritanceIsExtends, memberRules,
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

  protected ProguardIfRule materialize() {
    return new ProguardIfRule(
        Origin.unknown(),
        Position.UNKNOWN,
        null,
        getClassAnnotation() == null ? null : getClassAnnotation().materialize(),
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

  protected ClassInlineRule neverClassInlineRuleForCondition() {
    return new ClassInlineRule(
        Origin.unknown(),
        Position.UNKNOWN,
        null,
        getClassAnnotation() == null ? null : getClassAnnotation().materialize(),
        getClassAccessFlags(),
        getNegatedClassAccessFlags(),
        getClassTypeNegated(),
        getClassType(),
        getClassNames().materialize(),
        getInheritanceAnnotation() == null ? null : getInheritanceAnnotation().materialize(),
        getInheritanceClassName() == null ? null : getInheritanceClassName().materialize(),
        getInheritanceIsExtends(),
        ImmutableList.of(),
        ClassInlineRule.Type.NEVER);
  }

  /**
   * Consider the following rule, which requests that class Y should be kept if the method X.m() is
   * in the final output.
   *
   * <pre>
   * -if class X {
   *   public void m();
   * }
   * -keep class Y
   * </pre>
   *
   * When the {@link Enqueuer} finds that the method X.m() is reachable, it applies the subsequent
   * keep rule of the -if rule. Thus, Y will be marked as pinned, which guarantees, for example,
   * that it will not be merged into another class by the vertical class merger.
   *
   * <p>However, when the {@link Enqueuer} runs for the second time, it is important that X.m() has
   * not been inlined into another method Z.z(), because that would mean that Z.z() now relies on
   * the presence of Y, meanwhile Y will not be kept because X.m() is no longer present.
   *
   * <p>Therefore, each time the subsequent rule of an -if rule is applied, we also apply a
   * -neverinline rule for the condition of the -if rule.
   */
  protected InlineRule neverInlineRuleForCondition() {
    if (getMemberRules() == null || getMemberRules().isEmpty()) {
      return null;
    }
    return new InlineRule(
        Origin.unknown(),
        Position.UNKNOWN,
        null,
        getClassAnnotation() == null ? null : getClassAnnotation().materialize(),
        getClassAccessFlags(),
        getNegatedClassAccessFlags(),
        getClassTypeNegated(),
        getClassType(),
        getClassNames().materialize(),
        getInheritanceAnnotation() == null ? null : getInheritanceAnnotation().materialize(),
        getInheritanceClassName() == null ? null : getInheritanceClassName().materialize(),
        getInheritanceIsExtends(),
        getMemberRules().stream()
            .filter(rule -> rule.getRuleType().includesMethods())
            .map(ProguardMemberRule::materialize)
            .collect(Collectors.toList()),
        InlineRule.Type.NEVER);
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
