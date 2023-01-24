// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import com.android.tools.r8.keepanno.ast.KeepBindings;
import com.android.tools.r8.keepanno.ast.KeepCondition;
import com.android.tools.r8.keepanno.ast.KeepEdge;
import com.android.tools.r8.keepanno.ast.KeepEdgeException;
import com.android.tools.r8.keepanno.ast.KeepEdgeMetaInfo;
import com.android.tools.r8.keepanno.ast.KeepItemPattern;
import com.android.tools.r8.keepanno.ast.KeepItemReference;
import com.android.tools.r8.keepanno.ast.KeepMemberPattern;
import com.android.tools.r8.keepanno.ast.KeepOptions;
import com.android.tools.r8.keepanno.ast.KeepQualifiedClassNamePattern;
import com.android.tools.r8.keepanno.ast.KeepTarget;
import com.android.tools.r8.keepanno.keeprules.PgRule.PgConditionalClassRule;
import com.android.tools.r8.keepanno.keeprules.PgRule.PgConditionalMemberRule;
import com.android.tools.r8.keepanno.keeprules.PgRule.PgDependentClassRule;
import com.android.tools.r8.keepanno.keeprules.PgRule.PgDependentMembersRule;
import com.android.tools.r8.keepanno.keeprules.PgRule.PgUnconditionalClassRule;
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

/** Split a keep edge into multiple PG rules that over-approximate it. */
public class KeepEdgeSplitter {

  private final Consumer<String> ruleConsumer;

  public KeepEdgeSplitter(Consumer<String> ruleConsumer) {
    this.ruleConsumer = ruleConsumer;
  }

  public void extract(KeepEdge edge) {
    Collection<PgRule> rules = split(edge);
    StringBuilder builder = new StringBuilder();
    for (PgRule rule : rules) {
      rule.printRule(builder);
      builder.append("\n");
    }
    ruleConsumer.accept(builder.toString());
  }

  private static Collection<PgRule> split(KeepEdge edge) {
    return doSplit(KeepEdgeNormalizer.normalize(edge));
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

    static Holder create(String bindingName, KeepBindings bindings) {
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
    final Set<String> conditionRefs = new HashSet<>();
    final Map<KeepOptions, Set<String>> targetRefs = new HashMap<>();

    static BindingUsers create(String bindingName, KeepBindings bindings) {
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

  private static Collection<PgRule> doSplit(KeepEdge edge) {
    List<PgRule> rules = new ArrayList<>();

    // First step after normalizing is to group up all conditions and targets on their target class.
    // Here we use the normalized binding as the notion of identity on a class.
    KeepBindings bindings = edge.getBindings();
    Map<String, BindingUsers> bindingUsers = new HashMap<>();
    edge.getPreconditions()
        .forEach(
            condition -> {
              String classReference = getClassItemBindingReference(condition.getItem(), bindings);
              assert classReference != null;
              bindingUsers
                  .computeIfAbsent(classReference, k -> BindingUsers.create(k, bindings))
                  .addCondition(condition);
            });
    edge.getConsequences()
        .forEachTarget(
            target -> {
              String classReference = getClassItemBindingReference(target.getItem(), bindings);
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

  private static List<String> computeConditions(
      Set<String> conditions,
      KeepBindings bindings,
      Map<String, KeepMemberPattern> memberPatterns) {
    List<String> conditionMembers = new ArrayList<>();
    conditions.forEach(
        conditionReference -> {
          KeepItemPattern item = bindings.get(conditionReference).getItem();
          if (item.isMemberItemPattern()) {
            KeepMemberPattern old = memberPatterns.put(conditionReference, item.getMemberPattern());
            conditionMembers.add(conditionReference);
            assert old == null;
          }
        });
    return conditionMembers;
  }

  @FunctionalInterface
  private interface OnKeepMembers {
    void accept(
        Map<String, KeepMemberPattern> memberPatterns,
        List<String> targets,
        boolean classAndMembers);
  }

  private static void computeTargets(
      Set<String> targets,
      KeepBindings bindings,
      Map<String, KeepMemberPattern> memberPatterns,
      Runnable onKeepClass,
      OnKeepMembers onKeepMembers) {
    List<String> targetMembers = new ArrayList<>();
    boolean keepClassTarget = false;
    for (String targetReference : targets) {
      KeepItemPattern item = bindings.get(targetReference).getItem();
      if (bindings.isAny(item)) {
        // If the target is "any item" then it contains any other target pattern so just emit the
        // single "any item" targets: class and members both.
        onKeepClass.run();
        memberPatterns.put(targetReference, item.getMemberPattern());
        onKeepMembers.accept(memberPatterns, Collections.singletonList(targetReference), false);
        return;
      }
      if (item.isClassItemPattern()) {
        keepClassTarget = true;
      } else {
        memberPatterns.putIfAbsent(targetReference, item.getMemberPattern());
        if (item.isClassAndMemberPattern()) {
          // If a target is a "class and member" target then it must be added as a separate rule.
          onKeepMembers.accept(memberPatterns, Collections.singletonList(targetReference), true);
        } else {
          assert item.isMemberItemPattern();
          targetMembers.add(targetReference);
        }
      }
    }
    if (keepClassTarget) {
      onKeepClass.run();
    }
    if (!targetMembers.isEmpty()) {
      onKeepMembers.accept(memberPatterns, targetMembers, false);
    }
  }

  private static void createUnconditionalRules(
      List<PgRule> rules,
      Holder holder,
      KeepEdgeMetaInfo metaInfo,
      KeepBindings bindings,
      KeepOptions options,
      Set<String> targets) {
    computeTargets(
        targets,
        bindings,
        new HashMap<>(),
        () -> {
          rules.add(new PgUnconditionalClassRule(metaInfo, options, holder));
        },
        (memberPatterns, targetMembers, classAndMembers) -> {
          // Members are still dependent on the class, so they go to the implicitly dependent rule.
          rules.add(
              new PgDependentMembersRule(
                  metaInfo,
                  holder,
                  options,
                  memberPatterns,
                  Collections.emptyList(),
                  targetMembers,
                  classAndMembers));
        });
  }

  private static void createConditionalRules(
      List<PgRule> rules,
      KeepEdgeMetaInfo metaInfo,
      Holder conditionHolder,
      Holder targetHolder,
      KeepBindings bindings,
      KeepOptions options,
      Set<String> conditions,
      Set<String> targets) {

    Map<String, KeepMemberPattern> memberPatterns = new HashMap<>();
    List<String> conditionMembers = computeConditions(conditions, bindings, memberPatterns);

    computeTargets(
        targets,
        bindings,
        memberPatterns,
        () ->
            rules.add(
                new PgConditionalClassRule(
                    metaInfo,
                    options,
                    conditionHolder,
                    targetHolder,
                    memberPatterns,
                    conditionMembers)),
        (ignore, targetMembers, classAndMembers) ->
            rules.add(
                new PgConditionalMemberRule(
                    metaInfo,
                    options,
                    conditionHolder,
                    targetHolder,
                    memberPatterns,
                    conditionMembers,
                    targetMembers,
                    classAndMembers)));
  }

  // For a conditional and dependent edge (e.g., the condition and target both reference holder X),
  // we can assume the general form of:
  //
  //   { X, memberConds } -> { X, memberTargets }
  //
  // First, we assume that if memberConds=={} then X is in the conditions, otherwise the conditions
  // are empty (i.e. always true) and this is not a dependent edge.
  //
  // Without change in meaning we can always assume X in conditions as it either was and if not then
  // the condition on a member implicitly entails a condition on the holder.
  //
  // Next we can split any such edge into two edges:
  //
  //   { X, memberConds } -> { X }
  //   { X, memberConds } -> { memberTargets }
  //
  // The first edge, if present, gives rise to the rule:
  //
  //   -if class X { memberConds } -keep class <1>
  //
  // The second rule only pertains to keeping member targets and those targets are kept as a
  // -keepclassmembers such that they are still conditional on the holder being referenced/live.
  // If the only precondition is the holder, then it can omitted, thus we generate:
  // If memberConds={}:
  //   -keepclassmembers class X { memberTargets }
  // else:
  //   -if class X { memberConds } -keepclassmembers X { memberTargets }
  //
  private static void createDependentRules(
      List<PgRule> rules,
      Holder holder,
      KeepEdgeMetaInfo metaInfo,
      KeepBindings bindings,
      KeepOptions options,
      Set<String> conditions,
      Set<String> targets) {
    Map<String, KeepMemberPattern> memberPatterns = new HashMap<>();
    List<String> conditionMembers = computeConditions(conditions, bindings, memberPatterns);
    computeTargets(
        targets,
        bindings,
        memberPatterns,
        () ->
            rules.add(
                new PgDependentClassRule(
                    metaInfo, holder, options, memberPatterns, conditionMembers)),
        (ignore, targetMembers, classAndMembers) ->
            rules.add(
                new PgDependentMembersRule(
                    metaInfo,
                    holder,
                    options,
                    memberPatterns,
                    conditionMembers,
                    targetMembers,
                    classAndMembers)));
  }

  private static KeepQualifiedClassNamePattern getClassNamePattern(
      KeepItemPattern itemPattern, KeepBindings bindings) {
    return itemPattern.getClassReference().isClassNamePattern()
        ? itemPattern.getClassReference().asClassNamePattern()
        : getClassNamePattern(
            bindings.get(itemPattern.getClassReference().asBindingReference()).getItem(), bindings);
  }

  private static String getClassItemBindingReference(
      KeepItemReference itemReference, KeepBindings bindings) {
    String classReference = null;
    for (String reference : getTransitiveBindingReferences(itemReference, bindings)) {
      if (bindings.get(reference).getItem().isClassItemPattern()) {
        if (classReference != null) {
          throw new KeepEdgeException("Unexpected reference to multiple class bindings");
        }
        classReference = reference;
      }
    }
    return classReference;
  }

  private static Set<String> getTransitiveBindingReferences(
      KeepItemReference itemReference, KeepBindings bindings) {
    Set<String> references = new HashSet<>(2);
    Deque<String> worklist = new ArrayDeque<>();
    worklist.addAll(getBindingReference(itemReference));
    while (!worklist.isEmpty()) {
      String bindingReference = worklist.pop();
      if (references.add(bindingReference)) {
        worklist.addAll(getBindingReference(bindings.get(bindingReference).getItem()));
      }
    }
    return references;
  }

  private static Collection<String> getBindingReference(KeepItemReference itemReference) {
    if (itemReference.isBindingReference()) {
      return Collections.singletonList(itemReference.asBindingReference());
    }
    return getBindingReference(itemReference.asItemPattern());
  }

  private static Collection<String> getBindingReference(KeepItemPattern itemPattern) {
    return itemPattern.getClassReference().isBindingReference()
        ? Collections.singletonList(itemPattern.getClassReference().asBindingReference())
        : Collections.emptyList();
  }
}
