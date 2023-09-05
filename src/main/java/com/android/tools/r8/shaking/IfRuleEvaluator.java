// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.shaking.InlineRule.Type;
import com.android.tools.r8.shaking.RootSetUtils.ConsequentRootSet;
import com.android.tools.r8.shaking.RootSetUtils.ConsequentRootSetBuilder;
import com.android.tools.r8.shaking.RootSetUtils.RootSetBuilder;
import com.android.tools.r8.utils.InternalOptions.TestingOptions.ProguardIfRuleEvaluationData;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

public class IfRuleEvaluator {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final SubtypingInfo subtypingInfo;
  private final Enqueuer enqueuer;
  private final ExecutorService executorService;
  private final List<Future<?>> futures = new ArrayList<>();
  private final Map<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> ifRules;
  private final ConsequentRootSetBuilder rootSetBuilder;

  IfRuleEvaluator(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      SubtypingInfo subtypingInfo,
      Enqueuer enqueuer,
      ExecutorService executorService,
      Map<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> ifRules,
      ConsequentRootSetBuilder rootSetBuilder) {
    this.appView = appView;
    this.subtypingInfo = subtypingInfo;
    this.enqueuer = enqueuer;
    this.executorService = executorService;
    this.ifRules = ifRules;
    this.rootSetBuilder = rootSetBuilder;
  }

  public ConsequentRootSet run() throws ExecutionException {
    appView.appInfo().app().timing.begin("Find consequent items for -if rules...");
    try {
      if (ifRules != null && !ifRules.isEmpty()) {
        Iterator<Map.Entry<Wrapper<ProguardIfRule>, Set<ProguardIfRule>>> it =
            ifRules.entrySet().iterator();
        while (it.hasNext()) {
          Map.Entry<Wrapper<ProguardIfRule>, Set<ProguardIfRule>> ifRuleEntry = it.next();
          ProguardIfRule ifRuleKey = ifRuleEntry.getKey().get();
          Set<ProguardIfRule> ifRulesInEquivalence = ifRuleEntry.getValue();
          ProguardIfRuleEvaluationData ifRuleEvaluationData =
              appView.options().testing.proguardIfRuleEvaluationData;
          List<ProguardIfRule> toRemove = new ArrayList<>();

          // Depending on which types that trigger the -if rule, the application of the subsequent
          // -keep rule may vary (due to back references). So, we need to try all pairs of -if
          // rule and live types.
          for (DexProgramClass clazz :
              ifRuleKey.relevantCandidatesForRule(
                  appView, subtypingInfo, appView.appInfo().classes())) {
            if (!isEffectivelyLive(clazz)) {
              continue;
            }

            // Check if the class matches the if-rule.
            if (appView.options().testing.measureProguardIfRuleEvaluations) {
              ifRuleEvaluationData.numberOfProguardIfRuleClassEvaluations++;
            }
            if (evaluateClassForIfRule(ifRuleKey, clazz)) {
              // When matching an if rule against a type, the if-rule are filled with the current
              // capture of wildcards. Propagate this down to member rules with same class part
              // equivalence.
              ifRulesInEquivalence.forEach(
                  ifRule -> {
                    registerClassCapture(ifRule, clazz, clazz);
                    if (appView.options().testing.measureProguardIfRuleEvaluations) {
                      ifRuleEvaluationData.numberOfProguardIfRuleMemberEvaluations++;
                    }
                    boolean matched = evaluateIfRuleMembersAndMaterialize(ifRule, clazz, clazz);
                    if (matched && canRemoveSubsequentKeepRule(ifRule)) {
                      toRemove.add(ifRule);
                    }
                  });
            }

            // Check if one of the types that have been merged into `clazz` satisfies the if-rule.
            if (appView.verticallyMergedClasses() != null) {
              Iterable<DexType> sources =
                  appView.verticallyMergedClasses().getSourcesFor(clazz.type);
              for (DexType sourceType : sources) {
                // Note that, although `sourceType` has been merged into `type`, the dex class for
                // `sourceType` is still available until the second round of tree shaking. This
                // way we can still retrieve the access flags of `sourceType`.
                DexProgramClass sourceClass =
                    asProgramClassOrNull(
                        appView.appInfo().definitionForWithoutExistenceAssert(sourceType));
                if (sourceClass == null) {
                  // TODO(b/266049507): The evaluation of -if rules in the final round of tree
                  //  shaking and during -whyareyoukeeping should be the same. Currently the pruning
                  //  of classes changes behavior.
                  assert enqueuer.getMode().isWhyAreYouKeeping();
                  continue;
                }
                if (appView.options().testing.measureProguardIfRuleEvaluations) {
                  ifRuleEvaluationData.numberOfProguardIfRuleClassEvaluations++;
                }
                if (evaluateClassForIfRule(ifRuleKey, sourceClass)) {
                  ifRulesInEquivalence.forEach(
                      ifRule -> {
                        registerClassCapture(ifRule, sourceClass, clazz);
                        if (appView.options().testing.measureProguardIfRuleEvaluations) {
                          ifRuleEvaluationData.numberOfProguardIfRuleMemberEvaluations++;
                        }
                        if (evaluateIfRuleMembersAndMaterialize(ifRule, sourceClass, clazz)
                            && canRemoveSubsequentKeepRule(ifRule)) {
                          toRemove.add(ifRule);
                        }
                      });
                }
              }
            }
          }
          if (ifRulesInEquivalence.size() == toRemove.size()) {
            it.remove();
          } else if (!toRemove.isEmpty()) {
            ifRulesInEquivalence.removeAll(toRemove);
          }
        }
        ThreadUtils.awaitFutures(futures);
      }
    } finally {
      appView.appInfo().app().timing.end();
    }
    return rootSetBuilder.buildConsequentRootSet();
  }

  private boolean canRemoveSubsequentKeepRule(ProguardIfRule rule) {
    return Iterables.isEmpty(rule.subsequentRule.getWildcards());
  }

  /**
   * To ensure the matching work correctly going forward, when a class has matched, we have to run
   * the capturing again on the member rule to ensure the wild cards are correctly populated.
   *
   * @param memberRule The member rule to populate with wildcards.
   * @param source The source class.
   * @param target The target class that can be different when we have vertically merged classes.
   */
  private void registerClassCapture(ProguardIfRule memberRule, DexClass source, DexClass target) {
    boolean classNameResult = memberRule.getClassNames().matches(source.type);
    assert classNameResult;
    if (memberRule.hasInheritanceClassName()) {
      boolean inheritanceResult = rootSetBuilder.satisfyInheritanceRule(target, memberRule);
      assert inheritanceResult;
    }
  }

  private boolean isEffectivelyLive(DexProgramClass clazz) {
    // A type is effectively live if (1) it is truly live, (2) the value of one of its fields has
    // been inlined by the member value propagation, or (3) the return value of one of its methods
    // has been forwarded by the member value propagation.
    if (enqueuer.isTypeLive(clazz)) {
      return true;
    }
    for (DexEncodedField field : clazz.fields()) {
      if (field.getOptimizationInfo().valueHasBeenPropagated()) {
        return true;
      }
    }
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.getOptimizationInfo().returnValueHasBeenPropagated()) {
        return true;
      }
    }
    return false;
  }

  /** Determines if {@param clazz} satisfies the given if-rule class specification. */
  private boolean evaluateClassForIfRule(ProguardIfRule rule, DexProgramClass clazz) {
    if (!RootSetBuilder.satisfyClassType(rule, clazz)) {
      return false;
    }
    if (!RootSetBuilder.satisfyAccessFlag(rule, clazz)) {
      return false;
    }
    AnnotationMatchResult annotationMatchResult = RootSetBuilder.satisfyAnnotation(rule, clazz);
    if (annotationMatchResult == null) {
      return false;
    }
    rootSetBuilder.handleMatchedAnnotation(annotationMatchResult);
    if (!rule.getClassNames().matches(clazz.type)) {
      return false;
    }
    if (rule.hasInheritanceClassName()) {
      // Try another live type since the current one doesn't satisfy the inheritance rule.
      return rootSetBuilder.satisfyInheritanceRule(clazz, rule);
    }
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean evaluateIfRuleMembersAndMaterialize(
      ProguardIfRule rule, DexClass sourceClass, DexClass targetClass) {
    Collection<ProguardMemberRule> memberKeepRules = rule.getMemberRules();
    if (memberKeepRules.isEmpty()) {
      materializeIfRule(rule, ImmutableSet.of(sourceClass.getReference()));
      return true;
    }

    List<DexClassAndField> fieldsInlinedByJavaC = new ArrayList<>();
    Set<DexDefinition> filteredMembers = Sets.newIdentityHashSet();
    Iterables.addAll(
        filteredMembers,
        targetClass.fields(
            f -> {
              // Fields that are javac inlined are unsound as predicates for conditional rules.
              // Ignore any such field members and record it for possible reporting later.
              if (isFieldInlinedByJavaC(f)) {
                fieldsInlinedByJavaC.add(DexClassAndField.create(targetClass, f));
                return false;
              }
              // Fields referenced only by -keep may not be referenced, we therefore have to
              // filter on both live and referenced.
              return (enqueuer.isFieldLive(f)
                      || enqueuer.isFieldReferenced(f)
                      || f.getOptimizationInfo().valueHasBeenPropagated())
                  && (appView.graphLens().getOriginalFieldSignature(f.getReference()).holder
                      == sourceClass.type);
            }));
    Iterables.addAll(
        filteredMembers,
        targetClass.methods(
            m ->
                (enqueuer.isMethodLive(m)
                        || enqueuer.isMethodTargeted(m)
                        || m.getOptimizationInfo().returnValueHasBeenPropagated())
                    && appView.graphLens().getOriginalMethodSignature(m.getReference()).holder
                        == sourceClass.type));

    // Check if the rule could hypothetically have matched a javac inlined field.
    // If so mark the rule. Reporting happens only if the rule is otherwise unused.
    if (!fieldsInlinedByJavaC.isEmpty()) {
      for (ProguardMemberRule memberRule : memberKeepRules) {
        if (!memberRule.getRuleType().includesFields()) {
          continue;
        }
        for (DexClassAndField field : fieldsInlinedByJavaC) {
          if (rootSetBuilder.sideEffectFreeIsRuleSatisfiedByField(memberRule, field)) {
            rule.addInlinableFieldMatchingPrecondition(field.getReference());
          }
        }
      }
    }

    // If the number of member rules to hold is more than live members, we can't make it.
    if (filteredMembers.size() < memberKeepRules.size()) {
      return false;
    }

    // Depending on which members trigger the -if rule, the application of the subsequent
    // -keep rule may vary (due to back references). So, we need to try literally all
    // combinations of live members. But, we can at least limit the number of elements per
    // combination as the size of member rules to satisfy.
    // TODO(b/206086945): Consider ways of reducing the size of this set computation.
    for (Set<DexDefinition> combination :
        Sets.combinations(filteredMembers, memberKeepRules.size())) {
      Collection<DexClassAndField> fieldsInCombination =
          DexDefinition.filterDexEncodedField(
                  combination.stream(), field -> DexClassAndField.create(targetClass, field))
              .collect(Collectors.toList());
      Collection<DexClassAndMethod> methodsInCombination =
          DexDefinition.filterDexEncodedMethod(
                  combination.stream(), method -> DexClassAndMethod.create(targetClass, method))
              .collect(Collectors.toList());
      // Member rules are combined as AND logic: if found unsatisfied member rule, this
      // combination of live members is not a good fit.
      boolean satisfied =
          memberKeepRules.stream()
              .allMatch(
                  memberRule ->
                      rootSetBuilder.ruleSatisfiedByFields(memberRule, fieldsInCombination)
                          || rootSetBuilder.ruleSatisfiedByMethods(
                              memberRule, methodsInCombination));
      if (satisfied) {
        materializeIfRule(rule, ImmutableSet.of(sourceClass.getReference()));
        if (canRemoveSubsequentKeepRule(rule)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean isFieldInlinedByJavaC(DexEncodedField field) {
    if (enqueuer.getMode().isFinalTreeShaking()) {
      // Ignore any field value in the final tree shaking pass so it remains consistent with the
      // initial pass.
      return field.getIsInlinableByJavaC();
    }
    return field.getOrComputeIsInlinableByJavaC(appView.dexItemFactory());
  }

  private void materializeIfRule(ProguardIfRule rule, Set<DexReference> preconditions) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    ProguardIfRule materializedRule = rule.materialize(dexItemFactory, preconditions);

    if (enqueuer.getMode().isInitialTreeShaking() && !rule.isUsed()) {
      // We need to abort class inlining of classes that could be matched by the condition of this
      // -if rule.
      ClassInlineRule neverClassInlineRuleForCondition =
          materializedRule.neverClassInlineRuleForCondition(dexItemFactory);
      if (neverClassInlineRuleForCondition != null) {
        rootSetBuilder.runPerRule(executorService, futures, neverClassInlineRuleForCondition, null);
      }

      InlineRule neverInlineForClassInliningRuleForCondition =
          materializedRule.neverInlineRuleForCondition(dexItemFactory, Type.NEVER_CLASS_INLINE);
      if (neverInlineForClassInliningRuleForCondition != null) {
        rootSetBuilder.runPerRule(
            executorService, futures, neverInlineForClassInliningRuleForCondition, null);
      }

      // If the condition of the -if rule has any members, then we need to keep these members to
      // ensure that the subsequent rule will be applied again in the second round of tree
      // shaking.
      InlineRule neverInlineRuleForCondition =
          materializedRule.neverInlineRuleForCondition(dexItemFactory, Type.NEVER);
      if (neverInlineRuleForCondition != null) {
        rootSetBuilder.runPerRule(executorService, futures, neverInlineRuleForCondition, null);
      }

      // Prevent horizontal class merging of any -if rule members.
      NoHorizontalClassMergingRule noHorizontalClassMergingRule =
          materializedRule.noHorizontalClassMergingRuleForCondition(dexItemFactory);
      if (noHorizontalClassMergingRule != null) {
        rootSetBuilder.runPerRule(executorService, futures, noHorizontalClassMergingRule, null);
      }
    }

    // Keep whatever is required by the -if rule.
    rootSetBuilder.runPerRule(
        executorService, futures, materializedRule.subsequentRule, materializedRule);
    rule.markAsUsed();
  }
}
