// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import com.android.tools.r8.keepanno.ast.KeepBindings;
import com.android.tools.r8.keepanno.ast.KeepBindings.BindingSymbol;
import com.android.tools.r8.keepanno.ast.KeepCheck;
import com.android.tools.r8.keepanno.ast.KeepCheck.KeepCheckKind;
import com.android.tools.r8.keepanno.ast.KeepCondition;
import com.android.tools.r8.keepanno.ast.KeepDeclaration;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.ast.KeepEdgeMetaInfo;
import com.android.tools.r8.keepanno.ast.KeepFieldAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepFieldPattern;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepItemReference;
import com.android.tools.r8.keepanno.ast.KeepMemberPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodAccessPattern;
import com.android.tools.r8.keepanno.ast.KeepMethodPattern;
import com.android.tools.r8.keepanno.ast.KeepOptions;
import com.android.tools.r8.keepanno.ast.KeepOptions.KeepOption;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import com.android.tools.r8.keepanno.keeprules.PgRule.PgConditionalRule;
import com.android.tools.r8.keepanno.keeprules.PgRule.PgDependentMembersRule;
import com.android.tools.r8.keepanno.keeprules.PgRule.PgUnconditionalRule;
import com.android.tools.r8.keepanno.keeprules.PgRule.TargetKeepKind;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/** Extract the PG keep rules that over-approximate a keep edge. */
public class KeepRuleExtractor {

  private final Consumer<String> ruleConsumer;

  public KeepRuleExtractor(Consumer<String> ruleConsumer) {
    this.ruleConsumer = ruleConsumer;
  }

  public void extract(KeepDeclaration declaration) {
    List<PgRule> rules = split(declaration);
    PgRule.groupByKinds(rules);
    StringBuilder builder = new StringBuilder();
    for (PgRule rule : rules) {
      rule.printRule(builder);
      builder.append("\n");
    }
    ruleConsumer.accept(builder.toString());
  }

  private static List<PgRule> split(KeepDeclaration declaration) {
    if (declaration.isKeepCheck()) {
      return generateCheckRules(declaration.asKeepCheck());
    }
    return doSplit(KeepEdgeNormalizer.normalize(declaration.asKeepEdge()));
  }

  private static List<PgRule> generateCheckRules(KeepCheck check) {
    KeepItemPattern itemPattern = check.getItemPattern();
    boolean isRemovedPattern = check.getKind() == KeepCheckKind.REMOVED;
    List<PgRule> rules = new ArrayList<>(isRemovedPattern ? 2 : 1);
    Holder holder;
    Map<BindingSymbol, KeepMemberPattern> memberPatterns;
    List<BindingSymbol> targetMembers;
    KeepBindings.Builder builder = KeepBindings.builder();
    BindingSymbol symbol = builder.generateFreshSymbol("CLASS");
    if (itemPattern.isClassItemPattern()) {
      builder.addBinding(symbol, check.getItemPattern());
      memberPatterns = Collections.emptyMap();
      targetMembers = Collections.emptyList();
    } else {
      builder.addBinding(symbol, KeepEdgeNormalizer.getClassItemPattern(check.getItemPattern()));
      KeepMemberPattern memberPattern = itemPattern.getMemberPattern();
      // This does not actually allocate a binding as the mapping is maintained in 'memberPatterns'.
      BindingSymbol memberSymbol = new BindingSymbol("MEMBERS");
      memberPatterns = Collections.singletonMap(memberSymbol, memberPattern);
      targetMembers = Collections.singletonList(memberSymbol);
    }
    holder = Holder.create(symbol, builder.build());
    // Add a -checkdiscard rule for the class or members.
    rules.add(
        new PgUnconditionalRule(
            check.getMetaInfo(),
            holder,
            KeepOptions.keepAll(),
            memberPatterns,
            targetMembers,
            TargetKeepKind.CHECK_DISCARD));
    // If the check declaration is to ensure full removal we generate a soft-pin rule to disallow
    // moving/inlining the items.
    if (isRemovedPattern) {
      KeepOptions allowShrinking = KeepOptions.allow(KeepOption.SHRINKING);
      if (itemPattern.isClassItemPattern()) {
        // A check removal on a class means that the entire class is removed, thus soft-pin the
        // class and *all* of its members.
        BindingSymbol memberSymbol = new BindingSymbol("MEMBERS");
        rules.add(
            new PgUnconditionalRule(
                check.getMetaInfo(),
                holder,
                allowShrinking,
                Collections.singletonMap(memberSymbol, KeepMemberPattern.allMembers()),
                Collections.singletonList(memberSymbol),
                TargetKeepKind.CLASS_OR_MEMBERS));
      } else {
        // A check removal on members just soft-pins the members.
        rules.add(
            new PgDependentMembersRule(
                check.getMetaInfo(),
                holder,
                allowShrinking,
                memberPatterns,
                Collections.emptyList(),
                targetMembers,
                TargetKeepKind.JUST_MEMBERS));
      }
    }
    return rules;
  }

  /**
   * Utility to package up a class binding with its name and item pattern.
   *
   * <p>This is useful as the normalizer will have introduced class reference indirections so a
   * given item may need to.
   */
  public static class Holder {
    final KeepItemPattern itemPattern;
    final KeepQualifiedClassNamePattern namePattern;

    static Holder create(BindingSymbol bindingName, KeepBindings bindings) {
      KeepItemPattern itemPattern = bindings.get(bindingName).getItem();
      assert itemPattern.isClassItemPattern();
      KeepQualifiedClassNamePattern namePattern = getClassNamePattern(itemPattern, bindings);
      return new Holder(itemPattern, namePattern);
    }

    private Holder(KeepItemPattern itemPattern, KeepQualifiedClassNamePattern namePattern) {
      this.itemPattern = itemPattern;
      this.namePattern = namePattern;
    }
  }

  private static class BindingUsers {

    final Holder holder;
    final Set<BindingSymbol> conditionRefs = new HashSet<>();
    final Map<KeepOptions, Set<BindingSymbol>> targetRefs = new HashMap<>();

    static BindingUsers create(BindingSymbol bindingName, KeepBindings bindings) {
      return new BindingUsers(Holder.create(bindingName, bindings));
    }

    private BindingUsers(Holder holder) {
      this.holder = holder;
    }

    public void addCondition(KeepCondition condition) {
      assert condition.getItem().isBindingReference();
      conditionRefs.add(condition.getItem().asBindingReference());
    }

    public void addTarget(KeepTarget target) {
      assert target.getItem().isBindingReference();
      targetRefs
          .computeIfAbsent(target.getOptions(), k -> new HashSet<>())
          .add(target.getItem().asBindingReference());
    }
  }

  private static List<PgRule> doSplit(KeepEdge edge) {
    List<PgRule> rules = new ArrayList<>();

    // First step after normalizing is to group up all conditions and targets on their target class.
    // Here we use the normalized binding as the notion of identity on a class.
    KeepBindings bindings = edge.getBindings();
    Map<BindingSymbol, BindingUsers> bindingUsers = new HashMap<>();
    edge.getPreconditions()
        .forEach(
            condition -> {
              BindingSymbol classReference =
                  getClassItemBindingReference(condition.getItem(), bindings);
              assert classReference != null;
              bindingUsers
                  .computeIfAbsent(classReference, k -> BindingUsers.create(k, bindings))
                  .addCondition(condition);
            });
    edge.getConsequences()
        .forEachTarget(
            target -> {
              BindingSymbol classReference =
                  getClassItemBindingReference(target.getItem(), bindings);
              assert classReference != null;
              bindingUsers
                  .computeIfAbsent(classReference, k -> BindingUsers.create(k, bindings))
                  .addTarget(target);
            });

    bindingUsers.forEach(
        (targetBindingName, users) -> {
          Holder targetHolder = users.holder;
          if (!users.conditionRefs.isEmpty() && !users.targetRefs.isEmpty()) {
            // The targets depend on the condition and thus we generate just the dependent edges.
            users.targetRefs.forEach(
                (options, targets) -> {
                  createDependentRules(
                      rules,
                      targetHolder,
                      edge.getMetaInfo(),
                      bindings,
                      options,
                      users.conditionRefs,
                      targets);
                });
          } else if (!users.targetRefs.isEmpty()) {
            // The targets don't have a binding relation to any conditions, so we generate a rule
            // per condition, or a single unconditional edge if no conditions exist.
            if (edge.getPreconditions().isAlways()) {
              users.targetRefs.forEach(
                  ((options, targets) -> {
                    createUnconditionalRules(
                        rules, targetHolder, edge.getMetaInfo(), bindings, options, targets);
                  }));
            } else {
              users.targetRefs.forEach(
                  ((options, targets) -> {
                    // Note that here we iterate over *all* non-empty conditions and create rules.
                    // Doing so over-approximates the matching instances of the edge, but gives
                    // better stability of the extraction as it avoids picking a particular
                    // precondition as the "primary" one to act on.
                    bindingUsers.forEach(
                        (conditionBindingName, conditionUsers) -> {
                          if (!conditionUsers.conditionRefs.isEmpty()) {
                            createConditionalRules(
                                rules,
                                edge.getMetaInfo(),
                                conditionUsers.holder,
                                targetHolder,
                                bindings,
                                options,
                                conditionUsers.conditionRefs,
                                targets);
                          }
                        });
                  }));
            }
          }
        });

    assert !rules.isEmpty();
    return rules;
  }

  private static List<BindingSymbol> computeConditions(
      Set<BindingSymbol> conditions,
      KeepBindings bindings,
      Map<BindingSymbol, KeepMemberPattern> memberPatterns) {
    List<BindingSymbol> conditionMembers = new ArrayList<>();
    conditions.forEach(
        conditionReference -> {
          KeepItemPattern item = bindings.get(conditionReference).getItem();
          if (!item.isClassItemPattern()) {
            KeepMemberPattern old = memberPatterns.put(conditionReference, item.getMemberPattern());
            conditionMembers.add(conditionReference);
            assert old == null;
          }
        });
    return conditionMembers;
  }

  @FunctionalInterface
  private interface OnTargetCallback {
    void accept(
        Map<BindingSymbol, KeepMemberPattern> memberPatterns,
        List<BindingSymbol> memberTargets,
        TargetKeepKind keepKind);
  }

  private static void computeTargets(
      Set<BindingSymbol> targets,
      KeepBindings bindings,
      Map<BindingSymbol, KeepMemberPattern> memberPatterns,
      OnTargetCallback callback) {
    TargetKeepKind keepKind = TargetKeepKind.JUST_MEMBERS;
    List<BindingSymbol> targetMembers = new ArrayList<>();
    for (BindingSymbol targetReference : targets) {
      KeepItemPattern item = bindings.get(targetReference).getItem();
      if (bindings.isAny(item)) {
        // If the target is "any item" then it contains any other target pattern.
        memberPatterns.put(targetReference, item.getMemberPattern());
        callback.accept(
            memberPatterns,
            Collections.singletonList(targetReference),
            TargetKeepKind.CLASS_OR_MEMBERS);
        return;
      }
      if (item.isClassItemPattern()) {
        keepKind = TargetKeepKind.CLASS_AND_MEMBERS;
      } else {
        memberPatterns.putIfAbsent(targetReference, item.getMemberPattern());
        targetMembers.add(targetReference);
      }
    }
    if (targetMembers.isEmpty()) {
      keepKind = TargetKeepKind.CLASS_OR_MEMBERS;
    }
    callback.accept(memberPatterns, targetMembers, keepKind);
  }

  private static void createUnconditionalRules(
      List<PgRule> rules,
      Holder holder,
      KeepEdgeMetaInfo metaInfo,
      KeepBindings bindings,
      KeepOptions options,
      Set<BindingSymbol> targets) {
    computeTargets(
        targets,
        bindings,
        new HashMap<>(),
        (memberPatterns, targetMembers, targetKeepKind) -> {
          if (targetKeepKind.equals(TargetKeepKind.JUST_MEMBERS)) {
            // Members dependent on the class, so they go to the implicitly dependent rule.
            rules.add(
                new PgDependentMembersRule(
                    metaInfo,
                    holder,
                    options,
                    memberPatterns,
                    Collections.emptyList(),
                    targetMembers,
                    targetKeepKind));
          } else {
            rules.add(
                new PgUnconditionalRule(
                    metaInfo, holder, options, memberPatterns, targetMembers, targetKeepKind));
          }
        });
  }

  private static void createConditionalRules(
      List<PgRule> rules,
      KeepEdgeMetaInfo metaInfo,
      Holder conditionHolder,
      Holder targetHolder,
      KeepBindings bindings,
      KeepOptions options,
      Set<BindingSymbol> conditions,
      Set<BindingSymbol> targets) {
    if (conditionHolder.namePattern.isExact()
        && conditionHolder.itemPattern.equals(targetHolder.itemPattern)) {
      // If the targets are conditional on its holder, the rule can be simplified as a dependent
      // rule. Note that this is only valid on an *exact* class matching as otherwise any
      // wildcard is allowed to be matched independently on the left and right of the edge.
      createDependentRules(rules, targetHolder, metaInfo, bindings, options, conditions, targets);
      return;
    }
    Map<BindingSymbol, KeepMemberPattern> memberPatterns = new HashMap<>();
    List<BindingSymbol> conditionMembers = computeConditions(conditions, bindings, memberPatterns);
    computeTargets(
        targets,
        bindings,
        memberPatterns,
        (ignore, targetMembers, targetKeepKind) ->
            rules.add(
                new PgConditionalRule(
                    metaInfo,
                    options,
                    conditionHolder,
                    targetHolder,
                    memberPatterns,
                    conditionMembers,
                    targetMembers,
                    targetKeepKind)));
  }

  private static void createDependentRules(
      List<PgRule> rules,
      Holder holder,
      KeepEdgeMetaInfo metaInfo,
      KeepBindings bindings,
      KeepOptions options,
      Set<BindingSymbol> conditions,
      Set<BindingSymbol> targets) {
    Map<BindingSymbol, KeepMemberPattern> memberPatterns = new HashMap<>();
    List<BindingSymbol> conditionMembers = computeConditions(conditions, bindings, memberPatterns);
    computeTargets(
        targets,
        bindings,
        memberPatterns,
        (ignore, targetMembers, targetKeepKind) -> {
          List<BindingSymbol> nonAllMemberTargets = new ArrayList<>(targetMembers.size());
          for (BindingSymbol targetMember : targetMembers) {
            KeepMemberPattern memberPattern = memberPatterns.get(targetMember);
            if (memberPattern.isGeneralMember() && conditionMembers.contains(targetMember)) {
              // This pattern is on "members in general" and it is bound by a condition.
              // Since backrefs can't reference a *-member we split this target in two, one for
              // fields and one for methods.
              HashMap<BindingSymbol, KeepMemberPattern> copyWithMethod =
                  new HashMap<>(memberPatterns);
              copyWithMethod.put(targetMember, copyMethodFromMember(memberPattern));
              rules.add(
                  new PgDependentMembersRule(
                      metaInfo,
                      holder,
                      options,
                      copyWithMethod,
                      conditionMembers,
                      Collections.singletonList(targetMember),
                      targetKeepKind));
              HashMap<BindingSymbol, KeepMemberPattern> copyWithField =
                  new HashMap<>(memberPatterns);
              copyWithField.put(targetMember, copyFieldFromMember(memberPattern));
              rules.add(
                  new PgDependentMembersRule(
                      metaInfo,
                      holder,
                      options,
                      copyWithField,
                      conditionMembers,
                      Collections.singletonList(targetMember),
                      targetKeepKind));
            } else {
              nonAllMemberTargets.add(targetMember);
            }
          }
          if (targetKeepKind.equals(TargetKeepKind.JUST_MEMBERS) && nonAllMemberTargets.isEmpty()) {
            return;
          }
          rules.add(
              new PgDependentMembersRule(
                  metaInfo,
                  holder,
                  options,
                  memberPatterns,
                  conditionMembers,
                  nonAllMemberTargets,
                  targetKeepKind));
        });
  }

  private static KeepMethodPattern copyMethodFromMember(KeepMemberPattern pattern) {
    KeepMethodAccessPattern accessPattern =
        KeepMethodAccessPattern.builder().copyOfMemberAccess(pattern.getAccessPattern()).build();
    return KeepMethodPattern.builder().setAccessPattern(accessPattern).build();
  }

  private static KeepFieldPattern copyFieldFromMember(KeepMemberPattern pattern) {
    KeepFieldAccessPattern accessPattern =
        KeepFieldAccessPattern.builder().copyOfMemberAccess(pattern.getAccessPattern()).build();
    return KeepFieldPattern.builder().setAccessPattern(accessPattern).build();
  }

  private static KeepQualifiedClassNamePattern getClassNamePattern(
      KeepItemPattern itemPattern, KeepBindings bindings) {
    return itemPattern.getClassReference().isClassNamePattern()
        ? itemPattern.getClassReference().asClassNamePattern()
        : getClassNamePattern(
            bindings.get(itemPattern.getClassReference().asBindingReference()).getItem(), bindings);
  }

  private static BindingSymbol getClassItemBindingReference(
      KeepItemReference itemReference, KeepBindings bindings) {
    BindingSymbol classReference = null;
    for (BindingSymbol reference : getTransitiveBindingReferences(itemReference, bindings)) {
      if (bindings.get(reference).getItem().isClassItemPattern()) {
        if (classReference != null) {
          throw new KeepEdgeException("Unexpected reference to multiple class bindings");
        }
        classReference = reference;
      }
    }
    return classReference;
  }

  private static Set<BindingSymbol> getTransitiveBindingReferences(
      KeepItemReference itemReference, KeepBindings bindings) {
    Set<BindingSymbol> references = new HashSet<>(2);
    Deque<BindingSymbol> worklist = new ArrayDeque<>();
    worklist.addAll(getBindingReference(itemReference));
    while (!worklist.isEmpty()) {
      BindingSymbol bindingReference = worklist.pop();
      if (references.add(bindingReference)) {
        worklist.addAll(getBindingReference(bindings.get(bindingReference).getItem()));
      }
    }
    return references;
  }

  private static Collection<BindingSymbol> getBindingReference(KeepItemReference itemReference) {
    if (itemReference.isBindingReference()) {
      return Collections.singletonList(itemReference.asBindingReference());
    }
    return getBindingReference(itemReference.asItemPattern());
  }

  private static Collection<BindingSymbol> getBindingReference(KeepItemPattern itemPattern) {
    return itemPattern.getClassReference().isBindingReference()
        ? Collections.singletonList(itemPattern.getClassReference().asBindingReference())
        : Collections.emptyList();
  }
}
