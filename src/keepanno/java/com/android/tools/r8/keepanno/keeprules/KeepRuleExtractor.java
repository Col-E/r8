// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.keeprules;

import com.android.tools.r8.keepanno.ast.KeepBindings;
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
    Collection<PgRule> rules = split(declaration);
    StringBuilder builder = new StringBuilder();
    for (PgRule rule : rules) {
      rule.printRule(builder);
      builder.append("\n");
    }
    ruleConsumer.accept(builder.toString());
  }

  private static Collection<PgRule> split(KeepDeclaration declaration) {
    if (declaration.isKeepCheck()) {
      return generateCheckRules(declaration.asKeepCheck());
    }
    return doSplit(KeepEdgeNormalizer.normalize(declaration.asKeepEdge()));
  }

  private static Collection<PgRule> generateCheckRules(KeepCheck check) {
    KeepItemPattern itemPattern = check.getItemPattern();
    boolean isRemovedPattern = check.getKind() == KeepCheckKind.REMOVED;
    List<PgRule> rules = new ArrayList<>(isRemovedPattern ? 2 : 1);
    Holder holder;
    Map<String, KeepMemberPattern> memberPatterns;
    List<String> targetMembers;
    if (itemPattern.isClassItemPattern()) {
      KeepBindings bindings =
          KeepBindings.builder().addBinding("CLASS", check.getItemPattern()).build();
      holder = Holder.create("CLASS", bindings);
      memberPatterns = Collections.emptyMap();
      targetMembers = Collections.emptyList();
    } else {
      KeepBindings bindings =
          KeepBindings.builder()
              .addBinding("CLASS", KeepEdgeNormalizer.getClassItemPattern(check.getItemPattern()))
              .build();
      holder = Holder.create("CLASS", bindings);
      KeepMemberPattern memberPattern = itemPattern.getMemberPattern();
      memberPatterns = Collections.singletonMap("MEMBER", memberPattern);
      targetMembers = Collections.singletonList("MEMBER");
    }
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
        rules.add(
            new PgUnconditionalRule(
                check.getMetaInfo(),
                holder,
                allowShrinking,
                Collections.singletonMap("MEMBERS", KeepMemberPattern.allMembers()),
                Collections.singletonList("MEMBERS"),
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
        Map<String, KeepMemberPattern> memberPatterns,
        List<String> memberTargets,
        TargetKeepKind keepKind);
  }

  private static void computeTargets(
      Set<String> targets,
      KeepBindings bindings,
      Map<String, KeepMemberPattern> memberPatterns,
      OnTargetCallback callback) {
    boolean keepClassTarget = false;
    List<String> disjunctiveTargetMembers = new ArrayList<>();
    List<String> classConjunctiveTargetMembers = new ArrayList<>();

    for (String targetReference : targets) {
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
        keepClassTarget = true;
      } else {
        memberPatterns.putIfAbsent(targetReference, item.getMemberPattern());
        if (item.isClassAndMemberPattern()) {
          // If a target is a "class and member" target then it must be added as a separate rule.
          classConjunctiveTargetMembers.add(targetReference);
        } else {
          assert item.isMemberItemPattern();
          disjunctiveTargetMembers.add(targetReference);
        }
      }
    }

    // The class is targeted, so that part of a class-and-member conjunction is satisfied.
    // The conjunctive members can thus be moved to the disjunctive set.
    if (keepClassTarget) {
      disjunctiveTargetMembers.addAll(classConjunctiveTargetMembers);
      classConjunctiveTargetMembers.clear();
    }

    if (!disjunctiveTargetMembers.isEmpty()) {
      TargetKeepKind keepKind =
          keepClassTarget ? TargetKeepKind.CLASS_OR_MEMBERS : TargetKeepKind.JUST_MEMBERS;
      callback.accept(memberPatterns, disjunctiveTargetMembers, keepKind);
    } else if (keepClassTarget) {
      callback.accept(
          Collections.emptyMap(), Collections.emptyList(), TargetKeepKind.CLASS_OR_MEMBERS);
    }

    if (!classConjunctiveTargetMembers.isEmpty()) {
      assert !keepClassTarget;
      for (String targetReference : classConjunctiveTargetMembers) {
        callback.accept(
            memberPatterns,
            Collections.singletonList(targetReference),
            TargetKeepKind.CLASS_AND_MEMBERS);
      }
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
      Set<String> conditions,
      Set<String> targets) {
    Map<String, KeepMemberPattern> memberPatterns = new HashMap<>();
    List<String> conditionMembers = computeConditions(conditions, bindings, memberPatterns);
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
      Set<String> conditions,
      Set<String> targets) {
    Map<String, KeepMemberPattern> memberPatterns = new HashMap<>();
    List<String> conditionMembers = computeConditions(conditions, bindings, memberPatterns);
    computeTargets(
        targets,
        bindings,
        memberPatterns,
        (ignore, targetMembers, targetKeepKind) -> {
          List<String> nonAllMemberTargets = new ArrayList<>(targetMembers.size());
          for (String targetMember : targetMembers) {
            KeepMemberPattern memberPattern = memberPatterns.get(targetMember);
            if (memberPattern.isGeneralMember() && conditionMembers.contains(targetMember)) {
              // This pattern is on "members in general" and it is bound by a condition.
              // Since backrefs can't reference a *-member we split this target in two, one for
              // fields and one for methods.
              HashMap<String, KeepMemberPattern> copyWithMethod = new HashMap<>(memberPatterns);
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
              HashMap<String, KeepMemberPattern> copyWithField = new HashMap<>(memberPatterns);
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
