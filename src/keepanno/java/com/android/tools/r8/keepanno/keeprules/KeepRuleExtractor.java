// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import com.android.tools.r8.keepanno.ast.KeepConsequences;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern.KeepClassPattern;
import com.android.tools.r8.keepanno.ast.KeepMembersPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodNamePattern;
import com.android.tools.r8.keepanno.ast.KeepMethodParametersPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodReturnTypePattern;
import com.android.tools.r8.keepanno.ast.KeepOptions;
import com.android.tools.r8.keepanno.ast.KeepOptions.KeepOption;
import com.android.tools.r8.keepanno.ast.KeepPackagePattern;
import com.android.tools.r8.keepanno.ast.KeepPreconditions;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import com.android.tools.r8.keepanno.ast.KeepTypePattern;
import com.android.tools.r8.keepanno.ast.KeepUnqualfiedClassNamePattern;
import com.android.tools.r8.keepanno.utils.Unimplemented;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

public class KeepRuleExtractor {

  private final Consumer<String> ruleConsumer;

  public KeepRuleExtractor(Consumer<String> ruleConsumer) {
    this.ruleConsumer = ruleConsumer;
  }

  public void extract(KeepEdge edge) {
    List<ItemRule> consequentRules = getConsequentRules(edge.getConsequences());
    printConditionalRules(consequentRules, edge.getPreconditions());
  }

  private List<ItemRule> getConsequentRules(KeepConsequences consequences) {
    List<ItemRule> consequentItems = new ArrayList<>();
    consequences.forEachTarget(target -> consequentItems.add(new ItemRule(target)));
    return consequentItems;
  }

  private void printConditionalRules(
      List<ItemRule> consequentRules, KeepPreconditions preconditions) {
    boolean[] hasAtLeastOneConditionalClause = new boolean[1];
    preconditions.forEach(
        condition -> {
          // The usage kind for a predicate is not expressible in keep rules, so it is
          // ignored.
          condition
              .getItemPattern()
              .match(
                  () -> {
                    // If the conditions is "any" then we ignore it for now (identity of
                    // conjunction).
                    return null;
                  },
                  conditionItem -> {
                    hasAtLeastOneConditionalClause[0] = true;
                    consequentRules.forEach(
                        consequentItem -> {
                          // Since conjunctions are not supported in keep rules, we expand them into
                          // disjunctions so conservatively we keep the consequences if any one of
                          // the preconditions hold.
                          StringBuilder builder = new StringBuilder();
                          if (!consequentItem.isMemberConsequent()
                              || !conditionItem
                                  .getClassNamePattern()
                                  .equals(consequentItem.getHolderPattern())) {
                            builder.append("-if ");
                            printClassItem(builder, conditionItem);
                            builder.append(' ');
                          }
                          printConsequentRule(builder, consequentItem);
                          ruleConsumer.accept(builder.toString());
                        });
                    return null;
                  });
        });
    assert !(preconditions.isAlways() && hasAtLeastOneConditionalClause[0]);
    if (!hasAtLeastOneConditionalClause[0]) {
      // If there are no preconditions, print each consequent as is.
      consequentRules.forEach(
          r -> ruleConsumer.accept(printConsequentRule(new StringBuilder(), r).toString()));
    }
  }

  private static StringBuilder printConsequentRule(StringBuilder builder, ItemRule rule) {
    if (rule.isMemberConsequent()) {
      builder.append("-keepclassmembers");
    } else {
      builder.append("-keep");
    }
    for (KeepOption option : KeepOption.values()) {
      if (rule.options.isAllowed(option)) {
        builder.append(",allow").append(getOptionString(option));
      }
    }
    return builder.append(" ").append(rule.getKeepRuleForItem());
  }

  private static StringBuilder printClassItem(
      StringBuilder builder, KeepClassPattern clazzPattern) {
    builder.append("class ");
    printClassName(builder, clazzPattern.getClassNamePattern());
    if (!clazzPattern.getExtendsPattern().isAny()) {
      throw new Unimplemented();
    }
    KeepMembersPattern members = clazzPattern.getMembersPattern();
    if (members.isNone()) {
      return builder;
    }
    if (members.isAll()) {
      return builder.append(" { *; }");
    }
    builder.append(" {");
    members.forEach(
        field -> printField(builder.append(' '), field),
        method -> printMethod(builder.append(' '), method));
    return builder.append(" }");
  }

  private static StringBuilder printField(StringBuilder builder, KeepFieldPattern field) {
    if (field.isAnyField()) {
      return builder.append("<fields>;");
    } else {
      throw new Unimplemented();
    }
  }

  private static StringBuilder printMethod(StringBuilder builder, KeepMethodPattern methodPattern) {
    if (methodPattern.isAnyMethod()) {
      return builder.append("<methods>;");
    }
    printAccess(builder, " ", methodPattern.getAccessPattern());
    printReturnType(builder, methodPattern.getReturnTypePattern());
    builder.append(' ');
    printMethodName(builder, methodPattern.getNamePattern());
    printParameters(builder, methodPattern.getParametersPattern());
    return builder.append(';');
  }

  private static StringBuilder printParameters(
      StringBuilder builder, KeepMethodParametersPattern parametersPattern) {
    return parametersPattern.match(
        () -> builder.append("(***)"),
        list ->
            builder
                .append('(')
                .append(list.stream().map(Object::toString).collect(Collectors.joining(", ")))
                .append(')'));
  }

  private static StringBuilder printMethodName(
      StringBuilder builder, KeepMethodNamePattern namePattern) {
    return namePattern.match(() -> builder.append("*"), builder::append);
  }

  private static StringBuilder printReturnType(
      StringBuilder builder, KeepMethodReturnTypePattern returnTypePattern) {
    return returnTypePattern.match(
        () -> builder.append("void"), typePattern -> printType(builder, typePattern));
  }

  private static StringBuilder printType(StringBuilder builder, KeepTypePattern typePattern) {
    if (typePattern.isAny()) {
      return builder.append("*");
    }
    throw new Unimplemented();
  }

  private static StringBuilder printAccess(
      StringBuilder builder, String indent, KeepMethodAccessPattern accessPattern) {
    if (accessPattern.isAny()) {
      // No text will match any access pattern.
      // Don't print the indent in this case.
      return builder;
    }
    throw new Unimplemented();
  }

  private static StringBuilder printClassName(
      StringBuilder builder, KeepQualifiedClassNamePattern classNamePattern) {
    if (classNamePattern.isAny()) {
      return builder.append('*');
    }
    printPackagePrefix(builder, classNamePattern.getPackagePattern());
    return printSimpleClassName(builder, classNamePattern.getNamePattern());
  }

  private static StringBuilder printPackagePrefix(
      StringBuilder builder, KeepPackagePattern packagePattern) {
    if (packagePattern.isAny()) {
      return builder.append("**.");
    }
    if (packagePattern.isTop()) {
      return builder;
    }
    assert packagePattern.isExact();
    return builder.append(packagePattern.asExact().getExactPackageAsString()).append('.');
  }

  private static StringBuilder printSimpleClassName(
      StringBuilder builder, KeepUnqualfiedClassNamePattern namePattern) {
    if (namePattern.isAny()) {
      return builder.append('*');
    }
    assert namePattern.isExact();
    return builder.append(namePattern.asExact().getExactNameAsString());
  }

  private static String getOptionString(KeepOption option) {
    switch (option) {
      case SHRINKING:
        return "shrinking";
      case OPTIMIZING:
        return "optimization";
      case OBFUSCATING:
        return "obfuscation";
      case ACCESS_MODIFYING:
        return "accessmodification";
      default:
        throw new Unimplemented();
    }
  }

  private static class ItemRule {
    private final KeepTarget target;
    private final KeepOptions options;
    private String ruleLine = null;

    public ItemRule(KeepTarget target) {
      this.target = target;
      this.options = target.getOptions();
    }

    public boolean isMemberConsequent() {
      return target.getItem().match(() -> false, clazz -> !clazz.getMembersPattern().isNone());
    }

    public KeepQualifiedClassNamePattern getHolderPattern() {
      return target
          .getItem()
          .match(KeepQualifiedClassNamePattern::any, KeepClassPattern::getClassNamePattern);
    }

    public String getKeepRuleForItem() {
      if (ruleLine == null) {
        ruleLine =
            target
                .getItem()
                .match(
                    () -> "class * { *; }",
                    clazz -> printClassItem(new StringBuilder(), clazz).toString());
      }
      return ruleLine;
    }
  }
}
