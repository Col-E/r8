// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.GraphLense.rewriteMutableReferenceKeys;
import static com.android.tools.r8.graph.GraphLense.rewriteReferenceKeys;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RootSetBuilder {

  private final AppView<?> appView;
  private final DirectMappedDexApplication application;
  private final Iterable<? extends ProguardConfigurationRule> rules;
  private final Map<DexReference, Set<ProguardKeepRule>> noShrinking = new IdentityHashMap<>();
  private final Set<DexReference> noOptimization = Sets.newIdentityHashSet();
  private final Set<DexReference> noObfuscation = Sets.newIdentityHashSet();
  private final LinkedHashMap<DexReference, DexReference> reasonAsked = new LinkedHashMap<>();
  private final Set<ProguardConfigurationRule> rulesThatUseExtendsOrImplementsWrong =
      Sets.newIdentityHashSet();
  private final LinkedHashMap<DexReference, DexReference> checkDiscarded = new LinkedHashMap<>();
  private final Set<DexMethod> alwaysInline = Sets.newIdentityHashSet();
  private final Set<DexMethod> forceInline = Sets.newIdentityHashSet();
  private final Set<DexMethod> neverInline = Sets.newIdentityHashSet();
  private final Set<DexMethod> keepParametersWithConstantValue = Sets.newIdentityHashSet();
  private final Set<DexMethod> keepUnusedArguments = Sets.newIdentityHashSet();
  private final Set<DexType> neverClassInline = Sets.newIdentityHashSet();
  private final Set<DexType> neverMerge = Sets.newIdentityHashSet();
  private final Set<DexReference> neverPropagateValue = Sets.newIdentityHashSet();
  private final Map<DexReference, Map<DexReference, Set<ProguardKeepRule>>> dependentNoShrinking =
      new IdentityHashMap<>();
  private final Map<DexReference, ProguardMemberRule> mayHaveSideEffects = new IdentityHashMap<>();
  private final Map<DexReference, ProguardMemberRule> noSideEffects = new IdentityHashMap<>();
  private final Map<DexReference, ProguardMemberRule> assumedValues = new IdentityHashMap<>();
  private final Set<DexReference> identifierNameStrings = Sets.newIdentityHashSet();
  private final InternalOptions options;

  private final DexStringCache dexStringCache = new DexStringCache();
  private final Set<ProguardIfRule> ifRules = Sets.newIdentityHashSet();

  public RootSetBuilder(
      AppView<?> appView,
      DexApplication application,
      Iterable<? extends ProguardConfigurationRule> rules) {
    this.appView = appView;
    this.application = application.asDirect();
    this.rules = rules;
    this.options = appView.options();
  }

  RootSetBuilder(AppView<?> appView, Collection<ProguardIfRule> ifRules) {
    this(appView, appView.appInfo().app(), ifRules);
  }

  // Process a class with the keep rule.
  private void process(
      DexClass clazz,
      ProguardConfigurationRule rule,
      ProguardIfRule ifRule) {
    if (!satisfyClassType(rule, clazz)) {
      return;
    }
    if (!satisfyAccessFlag(rule, clazz)) {
      return;
    }
    if (!satisfyAnnotation(rule, clazz)) {
      return;
    }
    // In principle it should make a difference whether the user specified in a class
    // spec that a class either extends or implements another type. However, proguard
    // seems not to care, so users have started to use this inconsistently. We are thus
    // inconsistent, as well, but tell them.
    // TODO(herhut): One day make this do what it says.
    if (rule.hasInheritanceClassName() && !satisfyInheritanceRule(clazz, rule)) {
      return;
    }

    if (rule.getClassNames().matches(clazz.type)) {
      Collection<ProguardMemberRule> memberKeepRules = rule.getMemberRules();
      Map<Predicate<DexDefinition>, DexDefinition> preconditionSupplier;
      if (rule instanceof ProguardKeepRule) {
        if (clazz.isNotProgramClass()) {
          return;
        }
        switch (((ProguardKeepRule) rule).getType()) {
          case KEEP_CLASS_MEMBERS:
            // Members mentioned at -keepclassmembers always depend on their holder.
            preconditionSupplier = ImmutableMap.of(definition -> true, clazz);
            markMatchingVisibleMethods(clazz, memberKeepRules, rule, preconditionSupplier, false);
            markMatchingVisibleFields(clazz, memberKeepRules, rule, preconditionSupplier, false);
            break;
          case KEEP_CLASSES_WITH_MEMBERS:
            if (!allRulesSatisfied(memberKeepRules, clazz)) {
              break;
            }
            // fallthrough;
          case KEEP:
            markClass(clazz, rule);
            preconditionSupplier = new HashMap<>();
            if (ifRule != null) {
              // Static members in -keep are pinned no matter what.
              preconditionSupplier.put(DexDefinition::isStaticMember, null);
              // Instance members may need to be kept even though the holder is not instantiated.
              preconditionSupplier.put(definition -> !definition.isStaticMember(), clazz);
            } else {
              // Members mentioned at -keep should always be pinned as long as that -keep rule is
              // not triggered conditionally.
              preconditionSupplier.put((definition -> true), null);
            }
            markMatchingVisibleMethods(clazz, memberKeepRules, rule, preconditionSupplier, false);
            markMatchingVisibleFields(clazz, memberKeepRules, rule, preconditionSupplier, false);
            break;
          case CONDITIONAL:
            throw new Unreachable("-if rule will be evaluated separately, not here.");
        }
      } else if (rule instanceof ProguardIfRule) {
        throw new Unreachable("-if rule will be evaluated separately, not here.");
      } else if (rule instanceof ProguardCheckDiscardRule) {
        if (memberKeepRules.isEmpty()) {
          markClass(clazz, rule);
        } else {
          preconditionSupplier = ImmutableMap.of((definition -> true), clazz);
          markMatchingFields(clazz, memberKeepRules, rule, preconditionSupplier);
          markMatchingMethods(clazz, memberKeepRules, rule, preconditionSupplier);
        }
      } else if (rule instanceof ProguardWhyAreYouKeepingRule) {
        markClass(clazz, rule);
        markMatchingVisibleMethods(clazz, memberKeepRules, rule, null, true);
        markMatchingVisibleFields(clazz, memberKeepRules, rule, null, true);
      } else if (rule instanceof ProguardAssumeMayHaveSideEffectsRule
          || rule instanceof ProguardAssumeNoSideEffectRule) {
        markMatchingVisibleMethods(clazz, memberKeepRules, rule, null, true);
        if (appView.appInfo().hasSubtyping()) {
          markMatchingOverriddenMethods(
              appView.appInfo().withSubtyping(), clazz, memberKeepRules, rule, null, true);
        }
        markMatchingVisibleFields(clazz, memberKeepRules, rule, null, true);
      } else if (rule instanceof ClassMergingRule) {
        if (allRulesSatisfied(memberKeepRules, clazz)) {
          markClass(clazz, rule);
        }
      } else if (rule instanceof InlineRule
          || rule instanceof ConstantArgumentRule
          || rule instanceof UnusedArgumentRule) {
        markMatchingMethods(clazz, memberKeepRules, rule, null);
      } else if (rule instanceof ClassInlineRule) {
        if (allRulesSatisfied(memberKeepRules, clazz)) {
          markClass(clazz, rule);
        }
      } else if (rule instanceof MemberValuePropagationRule) {
        markMatchingVisibleMethods(clazz, memberKeepRules, rule, null, true);
        markMatchingVisibleFields(clazz, memberKeepRules, rule, null, true);
      } else if (rule instanceof ProguardAssumeValuesRule) {
        markMatchingVisibleMethods(clazz, memberKeepRules, rule, null, true);
        markMatchingVisibleFields(clazz, memberKeepRules, rule, null, true);
      } else {
        assert rule instanceof ProguardIdentifierNameStringRule;
        markMatchingFields(clazz, memberKeepRules, rule, null);
        markMatchingMethods(clazz, memberKeepRules, rule, null);
      }
    }
  }

  private void runPerRule(
      ExecutorService executorService,
      List<Future<?>> futures,
      ProguardConfigurationRule rule,
      ProguardIfRule ifRule) {
    List<DexType> specifics = rule.getClassNames().asSpecificDexTypes();
    if (specifics != null) {
      // This keep rule only lists specific type matches.
      // This means there is no need to iterate over all classes.
      for (DexType type : specifics) {
        DexClass clazz = application.definitionFor(type);
        // Ignore keep rule iff it does not reference a class in the app.
        if (clazz != null) {
          process(clazz, rule, ifRule);
        }
      }
    } else {
      futures.add(
          executorService.submit(
              () -> {
                for (DexProgramClass clazz : application.classes()) {
                  process(clazz, rule, ifRule);
                }
                if (rule.applyToNonProgramClasses()) {
                  for (DexLibraryClass clazz : application.libraryClasses()) {
                    process(clazz, rule, ifRule);
                  }
                }
              }));
    }
  }

  public RootSet run(ExecutorService executorService) throws ExecutionException {
    application.timing.begin("Build root set...");
    try {
      List<Future<?>> futures = new ArrayList<>();
      // Mark all the things explicitly listed in keep rules.
      if (rules != null) {
        for (ProguardConfigurationRule rule : rules) {
          if (rule instanceof ProguardIfRule) {
            ProguardIfRule ifRule = (ProguardIfRule) rule;
            ifRules.add(ifRule);
          } else {
            runPerRule(executorService, futures, rule, null);
          }
        }
        ThreadUtils.awaitFutures(futures);
      }
    } finally {
      application.timing.end();
    }
    return new RootSet(
        noShrinking,
        noOptimization,
        noObfuscation,
        ImmutableList.copyOf(reasonAsked.values()),
        ImmutableList.copyOf(checkDiscarded.values()),
        alwaysInline,
        forceInline,
        neverInline,
        keepParametersWithConstantValue,
        keepUnusedArguments,
        neverClassInline,
        neverMerge,
        neverPropagateValue,
        mayHaveSideEffects,
        noSideEffects,
        assumedValues,
        dependentNoShrinking,
        identifierNameStrings,
        ifRules);
  }

  IfRuleEvaluator getIfRuleEvaluator(
      Set<DexEncodedField> liveFields,
      Set<DexEncodedMethod> liveMethods,
      Set<DexEncodedMethod> targetedMethods,
      ExecutorService executorService) {
    return new IfRuleEvaluator(liveFields, liveMethods, targetedMethods, executorService);
  }

  class IfRuleEvaluator {

    private final Set<DexEncodedField> liveFields;
    private final Set<DexEncodedMethod> liveMethods;
    private final Set<DexEncodedMethod> targetedMethods;

    private final ExecutorService executorService;

    private final List<Future<?>> futures = new ArrayList<>();

    public IfRuleEvaluator(
        Set<DexEncodedField> liveFields,
        Set<DexEncodedMethod> liveMethods,
        Set<DexEncodedMethod> targetedMethods,
        ExecutorService executorService) {
      this.liveFields = liveFields;
      this.liveMethods = liveMethods;
      this.targetedMethods = targetedMethods;
      this.executorService = executorService;
    }

    public ConsequentRootSet run(Set<DexType> liveTypes) throws ExecutionException {
      application.timing.begin("Find consequent items for -if rules...");
      try {
        if (rules != null) {
          for (ProguardConfigurationRule rule : rules) {
            assert rule instanceof ProguardIfRule;
            ProguardIfRule ifRule = (ProguardIfRule) rule;
            // Depending on which types that trigger the -if rule, the application of the subsequent
            // -keep rule may vary (due to back references). So, we need to try all pairs of -if
            // rule and live types.
            for (DexType type : liveTypes) {
              DexClass clazz = appView.definitionFor(type);
              if (clazz == null) {
                continue;
              }

              // Check if the class matches the if-rule.
              evaluateIfRule(ifRule, clazz, clazz);

              // Check if one of the types that have been merged into `clazz` satisfies the if-rule.
              if (options.enableVerticalClassMerging && appView.verticallyMergedClasses() != null) {
                for (DexType sourceType : appView.verticallyMergedClasses().getSourcesFor(type)) {
                  // Note that, although `sourceType` has been merged into `type`, the dex class for
                  // `sourceType` is still available until the second round of tree shaking. This
                  // way
                  // we can still retrieve the access flags of `sourceType`.
                  DexClass sourceClass = appView.definitionFor(sourceType);
                  assert sourceClass != null;
                  evaluateIfRule(ifRule, sourceClass, clazz);
                }
              }
            }
          }
          ThreadUtils.awaitFutures(futures);
        }
      } finally {
        application.timing.end();
      }
      return new ConsequentRootSet(
          neverInline,
          neverClassInline,
          noShrinking,
          noOptimization,
          noObfuscation,
          dependentNoShrinking);
    }

    /**
     * Determines if `sourceClass` satisfies the given if-rule. If `sourceClass` has not been merged
     * into another class, then `targetClass` is the same as `sourceClass`. Otherwise, `targetClass`
     * denotes the class that `sourceClass` has been merged into.
     */
    private void evaluateIfRule(ProguardIfRule rule, DexClass sourceClass, DexClass targetClass) {
      if (!satisfyClassType(rule, sourceClass)) {
        return;
      }
      if (!satisfyAccessFlag(rule, sourceClass)) {
        return;
      }
      if (!satisfyAnnotation(rule, sourceClass)) {
        return;
      }
      if (!rule.getClassNames().matches(sourceClass.type)) {
        return;
      }
      if (rule.hasInheritanceClassName()) {
        // Note that, in presence of vertical class merging, we check if the resulting class
        // (i.e., the target class) satisfies the implements/extends-matcher.
        if (!satisfyInheritanceRule(targetClass, rule)) {
          // Try another live type since the current one doesn't satisfy the inheritance rule.
          return;
        }
      }
      Collection<ProguardMemberRule> memberKeepRules = rule.getMemberRules();
      if (memberKeepRules.isEmpty()) {
        materializeIfRule(rule);
        return;
      }

      Set<DexDefinition> filteredMembers = Sets.newIdentityHashSet();
      Iterables.addAll(
          filteredMembers,
          targetClass.fields(
              f ->
                  liveFields.contains(f)
                      && appView.graphLense().getOriginalFieldSignature(f.field).holder
                          == sourceClass.type));
      Iterables.addAll(
          filteredMembers,
          targetClass.methods(
              m ->
                  (liveMethods.contains(m) || targetedMethods.contains(m))
                      && appView.graphLense().getOriginalMethodSignature(m.method).holder
                          == sourceClass.type));

      // If the number of member rules to hold is more than live members, we can't make it.
      if (filteredMembers.size() < memberKeepRules.size()) {
        return;
      }

      // Depending on which members trigger the -if rule, the application of the subsequent
      // -keep rule may vary (due to back references). So, we need to try literally all
      // combinations of live members. But, we can at least limit the number of elements per
      // combination as the size of member rules to satisfy.
      Sets.combinations(filteredMembers, memberKeepRules.size())
          .forEach(
              combination -> {
                Collection<DexEncodedField> fieldsInCombination =
                    DexDefinition.filterDexEncodedField(combination.stream())
                        .collect(Collectors.toList());
                Collection<DexEncodedMethod> methodsInCombination =
                    DexDefinition.filterDexEncodedMethod(combination.stream())
                        .collect(Collectors.toList());
                // Member rules are combined as AND logic: if found unsatisfied member rule, this
                // combination of live members is not a good fit.
                boolean satisfied =
                    memberKeepRules.stream()
                        .allMatch(
                            memberRule ->
                                ruleSatisfiedByFields(memberRule, fieldsInCombination)
                                    || ruleSatisfiedByMethods(memberRule, methodsInCombination));
                if (satisfied) {
                  materializeIfRule(rule);
                }
              });
    }

    private void materializeIfRule(ProguardIfRule rule) {
      ProguardIfRule materializedRule = rule.materialize();

      // We need to abort class inlining of classes that could be matched by the condition of this
      // -if rule.
      ClassInlineRule neverClassInlineRuleForCondition =
          materializedRule.neverClassInlineRuleForCondition();
      if (neverClassInlineRuleForCondition != null) {
        runPerRule(executorService, futures, neverClassInlineRuleForCondition, materializedRule);
      }

      // If the condition of the -if rule has any members, then we need to keep these members to
      // ensure that the subsequent rule will be applied again in the second round of tree
      // shaking.
      InlineRule neverInlineRuleForCondition = materializedRule.neverInlineRuleForCondition();
      if (neverInlineRuleForCondition != null) {
        runPerRule(executorService, futures, neverInlineRuleForCondition, materializedRule);
      }

      // Keep whatever is required by the -if rule.
      runPerRule(executorService, futures, materializedRule.subsequentRule, materializedRule);
    }
  }

  private static DexDefinition testAndGetPrecondition(
      DexDefinition definition, Map<Predicate<DexDefinition>, DexDefinition> preconditionSupplier) {
    if (preconditionSupplier == null) {
      return null;
    }
    DexDefinition precondition = null;
    boolean conditionEverMatched = false;
    for (Entry<Predicate<DexDefinition>, DexDefinition> entry : preconditionSupplier.entrySet()) {
      if (entry.getKey().test(definition)) {
        precondition = entry.getValue();
        conditionEverMatched = true;
        break;
      }
    }
    // If precondition-supplier is given, there should be at least one predicate that holds.
    // Actually, there should be only one predicate as we break the loop when it is found.
    assert conditionEverMatched;
    return precondition;
  }

  private void markMatchingVisibleMethods(
      DexClass clazz,
      Collection<ProguardMemberRule> memberKeepRules,
      ProguardConfigurationRule rule,
      Map<Predicate<DexDefinition>, DexDefinition> preconditionSupplier,
      boolean includeLibraryClasses) {
    Set<Wrapper<DexMethod>> methodsMarked =
        options.forceProguardCompatibility || rule instanceof ProguardAssumeNoSideEffectRule
            ? null : new HashSet<>();
    DexClass startingClass = clazz;
    while (clazz != null) {
      if (!includeLibraryClasses && clazz.isNotProgramClass()) {
        return;
      }
      // In compat mode traverse all direct methods in the hierarchy.
      if (clazz == startingClass || options.forceProguardCompatibility) {
        clazz
            .directMethods()
            .forEach(
                method -> {
                  DexDefinition precondition = testAndGetPrecondition(method, preconditionSupplier);
                  markMethod(method, memberKeepRules, methodsMarked, rule, precondition);
                });
      }
      clazz
          .virtualMethods()
          .forEach(
              method -> {
                DexDefinition precondition = testAndGetPrecondition(method, preconditionSupplier);
                markMethod(method, memberKeepRules, methodsMarked, rule, precondition);
              });
      clazz = clazz.superType == null ? null : application.definitionFor(clazz.superType);
    }
  }

  private void markMatchingOverriddenMethods(
      AppInfoWithSubtyping appInfoWithSubtyping,
      DexClass clazz,
      Collection<ProguardMemberRule> memberKeepRules,
      ProguardConfigurationRule rule,
      Map<Predicate<DexDefinition>, DexDefinition> preconditionSupplier,
      boolean onlyIncludeProgramClasses) {
    Set<DexType> visited = new HashSet<>();
    Deque<DexType> worklist = new ArrayDeque<>();
    // Intentionally skip the current `clazz`, assuming it's covered by markMatchingVisibleMethods.
    worklist.addAll(appInfoWithSubtyping.allImmediateSubtypes(clazz.type));

    while (!worklist.isEmpty()) {
      DexType currentType = worklist.poll();
      if (!visited.add(currentType)) {
        continue;
      }
      DexClass currentClazz = appView.definitionFor(currentType);
      if (currentClazz == null) {
        continue;
      }
      if (!onlyIncludeProgramClasses && currentClazz.isNotProgramClass()) {
        continue;
      }
      currentClazz
          .virtualMethods()
          .forEach(
              method -> {
                DexDefinition precondition = testAndGetPrecondition(method, preconditionSupplier);
                markMethod(method, memberKeepRules, null, rule, precondition);
              });
      worklist.addAll(appInfoWithSubtyping.allImmediateSubtypes(currentClazz.type));
    }
  }

  private void markMatchingMethods(
      DexClass clazz,
      Collection<ProguardMemberRule> memberKeepRules,
      ProguardConfigurationRule rule,
      Map<Predicate<DexDefinition>, DexDefinition> preconditionSupplier) {
    clazz.forEachMethod(
        method -> {
          DexDefinition precondition = testAndGetPrecondition(method, preconditionSupplier);
          markMethod(method, memberKeepRules, null, rule, precondition);
        });
  }

  private void markMatchingVisibleFields(
      DexClass clazz,
      Collection<ProguardMemberRule> memberKeepRules,
      ProguardConfigurationRule rule,
      Map<Predicate<DexDefinition>, DexDefinition> preconditionSupplier,
      boolean includeLibraryClasses) {
    while (clazz != null) {
      if (!includeLibraryClasses && clazz.isNotProgramClass()) {
        return;
      }
      clazz.forEachField(
          field -> {
            DexDefinition precondition = testAndGetPrecondition(field, preconditionSupplier);
            markField(field, memberKeepRules, rule, precondition);
          });
      clazz = clazz.superType == null ? null : application.definitionFor(clazz.superType);
    }
  }

  private void markMatchingFields(
      DexClass clazz,
      Collection<ProguardMemberRule> memberKeepRules,
      ProguardConfigurationRule rule,
      Map<Predicate<DexDefinition>, DexDefinition> preconditionSupplier) {
    clazz.forEachField(
        field -> {
          DexDefinition precondition = testAndGetPrecondition(field, preconditionSupplier);
          markField(field, memberKeepRules, rule, precondition);
        });
  }

  // TODO(b/67934426): Test this code.
  public static void writeSeeds(
      AppInfoWithLiveness appInfo, PrintStream out, Predicate<DexType> include) {
    for (DexReference seed : appInfo.getPinnedItems()) {
      if (seed.isDexType()) {
        if (include.test(seed.asDexType())) {
          out.println(seed.toSourceString());
        }
      } else if (seed.isDexField()) {
        DexField field = seed.asDexField();
        if (include.test(field.holder)) {
          out.println(
              field.holder.toSourceString()
                  + ": "
                  + field.type.toSourceString()
                  + " "
                  + field.name.toSourceString());
        }
      } else {
        assert seed.isDexMethod();
        DexMethod method = seed.asDexMethod();
        if (!include.test(method.holder)) {
          continue;
        }
        out.print(method.holder.toSourceString() + ": ");
        DexEncodedMethod encodedMethod = appInfo.definitionFor(method);
        if (encodedMethod.accessFlags.isConstructor()) {
          if (encodedMethod.accessFlags.isStatic()) {
            out.print(Constants.CLASS_INITIALIZER_NAME);
          } else {
            String holderName = method.holder.toSourceString();
            String constrName = holderName.substring(holderName.lastIndexOf('.') + 1);
            out.print(constrName);
          }
        } else {
          out.print(
              method.proto.returnType.toSourceString() + " " + method.name.toSourceString());
        }
        boolean first = true;
        out.print("(");
        for (DexType param : method.proto.parameters.values) {
          if (!first) {
            out.print(",");
          }
          first = false;
          out.print(param.toSourceString());
        }
        out.println(")");
      }
    }
    out.close();
  }

  private boolean satisfyClassType(ProguardConfigurationRule rule, DexClass clazz) {
    return rule.getClassType().matches(clazz) != rule.getClassTypeNegated();
  }

  private static boolean satisfyAccessFlag(ProguardConfigurationRule rule, DexClass clazz) {
    return rule.getClassAccessFlags().containsAll(clazz.accessFlags)
        && rule.getNegatedClassAccessFlags().containsNone(clazz.accessFlags);
  }

  private static boolean satisfyAnnotation(ProguardConfigurationRule rule, DexClass clazz) {
    return containsAnnotation(rule.getClassAnnotation(), clazz.annotations);
  }

  private boolean satisfyInheritanceRule(DexClass clazz, ProguardConfigurationRule rule) {
    boolean extendsExpected = satisfyExtendsRule(clazz, rule);
    boolean implementsExpected = false;
    if (!extendsExpected) {
      implementsExpected = satisfyImplementsRule(clazz, rule);
    }
    if (extendsExpected || implementsExpected) {
      // Warn if users got it wrong, but only warn once. Also, only warn if rule is actually
      // specific (there is no correct way to write "keep class X that extends or implements from
      // a class or interface in package Y").
      if (rule.getInheritanceClassName().matchesSpecificType()) {
        if (rule.getInheritanceIsExtends()) {
          if (implementsExpected && rulesThatUseExtendsOrImplementsWrong.add(rule)) {
            assert options.testing.allowProguardRulesThatUseExtendsOrImplementsWrong;
            options.reporter.warning(
                new StringDiagnostic(
                    "The rule `" + rule + "` uses extends but actually matches implements."));
          }
        } else if (extendsExpected && rulesThatUseExtendsOrImplementsWrong.add(rule)) {
          assert options.testing.allowProguardRulesThatUseExtendsOrImplementsWrong;
          options.reporter.warning(
              new StringDiagnostic(
                  "The rule `" + rule + "` uses implements but actually matches extends."));
        }
      }
      return true;
    }
    return false;
  }

  private boolean satisfyExtendsRule(DexClass clazz, ProguardConfigurationRule rule) {
    if (anySuperTypeMatchesExtendsRule(clazz.superType, rule)) {
      return true;
    }
    // It is possible that this class used to inherit from another class X, but no longer does it,
    // because X has been merged into `clazz`.
    return anySourceMatchesInheritanceRuleDirectly(clazz, rule, false);
  }

  private boolean anySuperTypeMatchesExtendsRule(DexType type, ProguardConfigurationRule rule) {
    while (type != null) {
      DexClass clazz = application.definitionFor(type);
      if (clazz == null) {
        // TODO(herhut): Warn about broken supertype chain?
        return false;
      }
      // TODO(b/110141157): Should the vertical class merger move annotations from the source to
      // the target class? If so, it is sufficient only to apply the annotation-matcher to the
      // annotations of `class`.
      if (rule.getInheritanceClassName().matches(clazz.type, appView)
          && containsAnnotation(rule.getInheritanceAnnotation(), clazz.annotations)) {
        return true;
      }
      type = clazz.superType;
    }
    return false;
  }

  private boolean satisfyImplementsRule(DexClass clazz, ProguardConfigurationRule rule) {
    if (anyImplementedInterfaceMatchesImplementsRule(clazz, rule)) {
      return true;
    }
    // It is possible that this class used to implement an interface I, but no longer does it,
    // because I has been merged into `clazz`.
    return anySourceMatchesInheritanceRuleDirectly(clazz, rule, true);
  }

  private boolean anyImplementedInterfaceMatchesImplementsRule(
      DexClass clazz, ProguardConfigurationRule rule) {
    // TODO(herhut): Maybe it would be better to do this breadth first.
    if (clazz == null) {
      return false;
    }
    for (DexType iface : clazz.interfaces.values) {
      DexClass ifaceClass = application.definitionFor(iface);
      if (ifaceClass == null) {
        // TODO(herhut): Warn about broken supertype chain?
        return false;
      }
      // TODO(b/110141157): Should the vertical class merger move annotations from the source to
      // the target class? If so, it is sufficient only to apply the annotation-matcher to the
      // annotations of `ifaceClass`.
      if (rule.getInheritanceClassName().matches(iface, appView)
          && containsAnnotation(rule.getInheritanceAnnotation(), ifaceClass.annotations)) {
        return true;
      }
      if (anyImplementedInterfaceMatchesImplementsRule(ifaceClass, rule)) {
        return true;
      }
    }
    if (clazz.superType == null) {
      return false;
    }
    DexClass superClass = application.definitionFor(clazz.superType);
    if (superClass == null) {
      // TODO(herhut): Warn about broken supertype chain?
      return false;
    }
    return anyImplementedInterfaceMatchesImplementsRule(superClass, rule);
  }

  private boolean anySourceMatchesInheritanceRuleDirectly(
      DexClass clazz, ProguardConfigurationRule rule, boolean isInterface) {
    // TODO(b/110141157): Figure out what to do with annotations. Should the annotations of
    // the DexClass corresponding to `sourceType` satisfy the `annotation`-matcher?
    return appView.verticallyMergedClasses() != null
        && appView.verticallyMergedClasses().getSourcesFor(clazz.type).stream()
            .filter(
                sourceType ->
                    appView.definitionFor(sourceType).accessFlags.isInterface() == isInterface)
            .anyMatch(rule.getInheritanceClassName()::matches);
  }

  private boolean allRulesSatisfied(Collection<ProguardMemberRule> memberKeepRules,
      DexClass clazz) {
    for (ProguardMemberRule rule : memberKeepRules) {
      if (!ruleSatisfied(rule, clazz)) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks whether the given rule is satisfied by this clazz, not taking superclasses into
   * account.
   */
  private boolean ruleSatisfied(ProguardMemberRule rule, DexClass clazz) {
    return ruleSatisfiedByMethods(rule, clazz.directMethods())
        || ruleSatisfiedByMethods(rule, clazz.virtualMethods())
        || ruleSatisfiedByFields(rule, clazz.staticFields())
        || ruleSatisfiedByFields(rule, clazz.instanceFields());
  }

  private boolean ruleSatisfiedByMethods(
      ProguardMemberRule rule, Iterable<DexEncodedMethod> methods) {
    if (rule.getRuleType().includesMethods()) {
      for (DexEncodedMethod method : methods) {
        if (rule.matches(method, appView, dexStringCache)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean ruleSatisfiedByMethods(ProguardMemberRule rule, DexEncodedMethod[] methods) {
    return ruleSatisfiedByMethods(rule, Arrays.asList(methods));
  }

  private boolean ruleSatisfiedByFields(ProguardMemberRule rule, Iterable<DexEncodedField> fields) {
    if (rule.getRuleType().includesFields()) {
      for (DexEncodedField field : fields) {
        if (rule.matches(field, appView, dexStringCache)) {
          return true;
        }
      }
    }
    return false;
  }

  private boolean ruleSatisfiedByFields(ProguardMemberRule rule, DexEncodedField[] fields) {
    return ruleSatisfiedByFields(rule, Arrays.asList(fields));
  }

  static boolean containsAnnotation(ProguardTypeMatcher classAnnotation,
      DexAnnotationSet annotations) {
    if (classAnnotation == null) {
      return true;
    }
    if (annotations.isEmpty()) {
      return false;
    }
    for (DexAnnotation annotation : annotations.annotations) {
      if (classAnnotation.matches(annotation.annotation.type)) {
        return true;
      }
    }
    return false;
  }

  private void markMethod(
      DexEncodedMethod method,
      Collection<ProguardMemberRule> rules,
      Set<Wrapper<DexMethod>> methodsMarked,
      ProguardConfigurationRule context,
      DexDefinition precondition) {
    if (methodsMarked != null
        && methodsMarked.contains(MethodSignatureEquivalence.get().wrap(method.method))) {
      // Ignore, method is overridden in sub class.
      return;
    }
    for (ProguardMemberRule rule : rules) {
      if (rule.matches(method, appView, dexStringCache)) {
        if (Log.ENABLED) {
          Log.verbose(getClass(), "Marking method `%s` due to `%s { %s }`.", method, context,
              rule);
        }
        if (methodsMarked != null) {
          methodsMarked.add(MethodSignatureEquivalence.get().wrap(method.method));
        }
        addItemToSets(method, context, rule, precondition);
      }
    }
  }

  private void markField(
      DexEncodedField field,
      Collection<ProguardMemberRule> rules,
      ProguardConfigurationRule context,
      DexDefinition precondition) {
    for (ProguardMemberRule rule : rules) {
      if (rule.matches(field, appView, dexStringCache)) {
        if (Log.ENABLED) {
          Log.verbose(getClass(), "Marking field `%s` due to `%s { %s }`.", field, context,
              rule);
        }
        addItemToSets(field, context, rule, precondition);
      }
    }
  }

  private void markClass(DexClass clazz, ProguardConfigurationRule rule) {
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Marking class `%s` due to `%s`.", clazz.type, rule);
    }
    addItemToSets(clazz, rule, null, null);
  }

  private void includeDescriptor(DexDefinition item, DexType type, ProguardKeepRule context) {
    if (type.isVoidType()) {
      return;
    }
    if (type.isArrayType()) {
      type = type.toBaseType(appView.dexItemFactory());
    }
    if (type.isPrimitiveType()) {
      return;
    }
    DexClass definition = appView.definitionFor(type);
    if (definition == null || definition.isNotProgramClass()) {
      return;
    }
    // Keep the type if the item is also kept.
    dependentNoShrinking
        .computeIfAbsent(item.toReference(), x -> new IdentityHashMap<>())
        .computeIfAbsent(type, k -> new HashSet<>())
        .add(context);
    // Unconditionally add to no-obfuscation, as that is only checked for surviving items.
    noObfuscation.add(type);
  }

  private void includeDescriptorClasses(DexDefinition item, ProguardKeepRule context) {
    if (item.isDexEncodedMethod()) {
      DexMethod method = item.asDexEncodedMethod().method;
      includeDescriptor(item, method.proto.returnType, context);
      for (DexType value : method.proto.parameters.values) {
        includeDescriptor(item, value, context);
      }
    } else if (item.isDexEncodedField()) {
      DexField field = item.asDexEncodedField().field;
      includeDescriptor(item, field.type, context);
    } else {
      assert item.isDexClass();
    }
  }

  private synchronized void addItemToSets(
      DexDefinition item,
      ProguardConfigurationRule context,
      ProguardMemberRule rule,
      DexDefinition precondition) {
    if (context instanceof ProguardKeepRule) {
      if (item.isDexEncodedMethod()) {
        DexEncodedMethod encodedMethod = item.asDexEncodedMethod();
        if (options.isGeneratingDex()
            && encodedMethod.method.isLambdaDeserializeMethod(appView.dexItemFactory())) {
          // Don't keep lambda deserialization methods.
          return;
        }
        // If desugaring is enabled, private and static interface methods will be moved to a
        // companion class. So we don't need to add them to the root set in the beginning.
        if (options.isInterfaceMethodDesugaringEnabled()
            && encodedMethod.hasCode()
            && (encodedMethod.isPrivateMethod() || encodedMethod.isStaticMember())) {
          DexClass holder = appView.definitionFor(encodedMethod.method.holder);
          if (holder != null && holder.isInterface()) {
            if (rule.isSpecific()) {
              options.reporter.warning(
                  new StringDiagnostic(
                      "The rule `" + rule + "` is ignored because the targeting interface method `"
                          + encodedMethod.method.toSourceString() + "` will be desugared."));
            }
            return;
          }
        }
      }

      ProguardKeepRule keepRule = (ProguardKeepRule) context;
      ProguardKeepRuleModifiers modifiers = keepRule.getModifiers();
      if (!modifiers.allowsShrinking) {
        if (precondition != null) {
          dependentNoShrinking
              .computeIfAbsent(precondition.toReference(), x -> new IdentityHashMap<>())
              .computeIfAbsent(item.toReference(), i -> new HashSet<>())
              .add(keepRule);
        } else {
          noShrinking.computeIfAbsent(item.toReference(), i -> new HashSet<>()).add(keepRule);
        }
      }
      if (!modifiers.allowsOptimization) {
        noOptimization.add(item.toReference());
      }
      if (!modifiers.allowsObfuscation) {
        noObfuscation.add(item.toReference());
      }
      if (modifiers.includeDescriptorClasses) {
        includeDescriptorClasses(item, keepRule);
      }
    } else if (context instanceof ProguardAssumeMayHaveSideEffectsRule) {
      mayHaveSideEffects.put(item.toReference(), rule);
    } else if (context instanceof ProguardAssumeNoSideEffectRule) {
      noSideEffects.put(item.toReference(), rule);
    } else if (context instanceof ProguardWhyAreYouKeepingRule) {
      reasonAsked.computeIfAbsent(item.toReference(), i -> i);
    } else if (context instanceof ProguardAssumeValuesRule) {
      assumedValues.put(item.toReference(), rule);
    } else if (context instanceof ProguardCheckDiscardRule) {
      checkDiscarded.computeIfAbsent(item.toReference(), i -> i);
    } else if (context instanceof InlineRule) {
      if (item.isDexEncodedMethod()) {
        switch (((InlineRule) context).getType()) {
          case ALWAYS:
            alwaysInline.add(item.asDexEncodedMethod().method);
            break;
          case FORCE:
            forceInline.add(item.asDexEncodedMethod().method);
            break;
          case NEVER:
            neverInline.add(item.asDexEncodedMethod().method);
            break;
          default:
            throw new Unreachable();
        }
      }
    } else if (context instanceof ClassInlineRule) {
      switch (((ClassInlineRule) context).getType()) {
        case NEVER:
          if (item.isDexClass()) {
            neverClassInline.add(item.asDexClass().type);
          }
          break;
        default:
          throw new Unreachable();
      }
    } else if (context instanceof ClassMergingRule) {
      switch (((ClassMergingRule) context).getType()) {
        case NEVER:
          if (item.isDexClass()) {
            neverMerge.add(item.asDexClass().type);
          }
          break;
        default:
          throw new Unreachable();
      }
    } else if (context instanceof MemberValuePropagationRule) {
      switch (((MemberValuePropagationRule) context).getType()) {
        case NEVER:
          // Only add members from propgram classes to `neverPropagateValue` since class member
          // values from library types are not propagated by default.
          if (item.isDexEncodedField()) {
            DexEncodedField field = item.asDexEncodedField();
            if (field.isProgramField(appView)) {
              neverPropagateValue.add(item.asDexEncodedField().field);
            }
          } else if (item.isDexEncodedMethod()) {
            DexEncodedMethod method = item.asDexEncodedMethod();
            if (method.isProgramMethod(appView)) {
              neverPropagateValue.add(item.asDexEncodedMethod().method);
            }
          }
          break;
        default:
          throw new Unreachable();
      }
    } else if (context instanceof ProguardIdentifierNameStringRule) {
      if (item.isDexEncodedField()) {
        identifierNameStrings.add(item.asDexEncodedField().field);
      } else if (item.isDexEncodedMethod()) {
        identifierNameStrings.add(item.asDexEncodedMethod().method);
      }
    } else if (context instanceof ConstantArgumentRule) {
      if (item.isDexEncodedMethod()) {
        keepParametersWithConstantValue.add(item.asDexEncodedMethod().method);
      }
    } else if (context instanceof UnusedArgumentRule) {
      if (item.isDexEncodedMethod()) {
        keepUnusedArguments.add(item.asDexEncodedMethod().method);
      }
    }
  }

  public static class RootSet {

    public final Map<DexReference, Set<ProguardKeepRule>> noShrinking;
    public final Set<DexReference> noOptimization;
    private final Set<DexReference> noObfuscation;
    public final ImmutableList<DexReference> reasonAsked;
    public final ImmutableList<DexReference> checkDiscarded;
    public final Set<DexMethod> alwaysInline;
    public final Set<DexMethod> forceInline;
    public final Set<DexMethod> neverInline;
    public final Set<DexMethod> keepConstantArguments;
    public final Set<DexMethod> keepUnusedArguments;
    public final Set<DexType> neverClassInline;
    public final Set<DexType> neverMerge;
    public final Set<DexReference> neverPropagateValue;
    public final Map<DexReference, ProguardMemberRule> mayHaveSideEffects;
    public final Map<DexReference, ProguardMemberRule> noSideEffects;
    public final Map<DexReference, ProguardMemberRule> assumedValues;
    private final Map<DexReference, Map<DexReference, Set<ProguardKeepRule>>>
        dependentNoShrinking;
    public final Set<DexReference> identifierNameStrings;
    public final Set<ProguardIfRule> ifRules;

    private RootSet(
        Map<DexReference, Set<ProguardKeepRule>> noShrinking,
        Set<DexReference> noOptimization,
        Set<DexReference> noObfuscation,
        ImmutableList<DexReference> reasonAsked,
        ImmutableList<DexReference> checkDiscarded,
        Set<DexMethod> alwaysInline,
        Set<DexMethod> forceInline,
        Set<DexMethod> neverInline,
        Set<DexMethod> keepConstantArguments,
        Set<DexMethod> keepUnusedArguments,
        Set<DexType> neverClassInline,
        Set<DexType> neverMerge,
        Set<DexReference> neverPropagateValue,
        Map<DexReference, ProguardMemberRule> mayHaveSideEffects,
        Map<DexReference, ProguardMemberRule> noSideEffects,
        Map<DexReference, ProguardMemberRule> assumedValues,
        Map<DexReference, Map<DexReference, Set<ProguardKeepRule>>> dependentNoShrinking,
        Set<DexReference> identifierNameStrings,
        Set<ProguardIfRule> ifRules) {
      this.noShrinking = noShrinking;
      this.noOptimization = noOptimization;
      this.noObfuscation = noObfuscation;
      this.reasonAsked = reasonAsked;
      this.checkDiscarded = checkDiscarded;
      this.alwaysInline = Collections.unmodifiableSet(alwaysInline);
      this.forceInline = Collections.unmodifiableSet(forceInline);
      this.neverInline = neverInline;
      this.keepConstantArguments = keepConstantArguments;
      this.keepUnusedArguments = keepUnusedArguments;
      this.neverClassInline = neverClassInline;
      this.neverMerge = Collections.unmodifiableSet(neverMerge);
      this.neverPropagateValue = neverPropagateValue;
      this.mayHaveSideEffects = mayHaveSideEffects;
      this.noSideEffects = noSideEffects;
      this.assumedValues = assumedValues;
      this.dependentNoShrinking = dependentNoShrinking;
      this.identifierNameStrings = Collections.unmodifiableSet(identifierNameStrings);
      this.ifRules = Collections.unmodifiableSet(ifRules);
    }

    private RootSet(RootSet previous, GraphLense lense) {
      this.noShrinking = rewriteMutableReferenceKeys(previous.noShrinking, lense::lookupReference);
      this.noOptimization = lense.rewriteMutableReferencesConservatively(previous.noOptimization);
      this.noObfuscation = lense.rewriteMutableReferencesConservatively(previous.noObfuscation);
      this.reasonAsked = lense.rewriteReferencesConservatively(previous.reasonAsked);
      this.checkDiscarded = lense.rewriteReferencesConservatively(previous.checkDiscarded);
      this.alwaysInline = lense.rewriteMethodsConservatively(previous.alwaysInline);
      this.forceInline = lense.rewriteMethodsConservatively(previous.forceInline);
      this.neverInline = lense.rewriteMutableMethodsConservatively(previous.neverInline);
      this.keepConstantArguments =
          lense.rewriteMutableMethodsConservatively(previous.keepConstantArguments);
      this.keepUnusedArguments =
          lense.rewriteMutableMethodsConservatively(previous.keepUnusedArguments);
      this.neverClassInline = lense.rewriteMutableTypesConservatively(previous.neverClassInline);
      this.neverMerge = lense.rewriteTypesConservatively(previous.neverMerge);
      this.neverPropagateValue =
          lense.rewriteMutableReferencesConservatively(previous.neverPropagateValue);
      this.mayHaveSideEffects =
          rewriteMutableReferenceKeys(previous.mayHaveSideEffects, lense::lookupReference);
      this.noSideEffects =
          rewriteMutableReferenceKeys(previous.noSideEffects, lense::lookupReference);
      this.assumedValues =
          rewriteMutableReferenceKeys(previous.assumedValues, lense::lookupReference);
      this.dependentNoShrinking =
          rewriteDependentReferenceKeys(previous.dependentNoShrinking, lense::lookupReference);
      this.identifierNameStrings =
          lense.rewriteReferencesConservatively(previous.identifierNameStrings);
      this.ifRules = Collections.unmodifiableSet(previous.ifRules);
    }

    public RootSet rewrittenWithLense(GraphLense lense) {
      return new RootSet(this, lense);
    }

    private static <T extends DexReference, S> Map<T, Map<T, S>> rewriteDependentReferenceKeys(
        Map<T, Map<T, S>> original, Function<T, T> rewrite) {
      Map<T, Map<T, S>> result = new IdentityHashMap<>();
      for (T item : original.keySet()) {
        result.put(rewrite.apply(item), rewriteReferenceKeys(original.get(item), rewrite));
      }
      return result;
    }

    void addConsequentRootSet(ConsequentRootSet consequentRootSet) {
      neverInline.addAll(consequentRootSet.neverInline);
      neverClassInline.addAll(consequentRootSet.neverClassInline);
      noOptimization.addAll(consequentRootSet.noOptimization);
      noObfuscation.addAll(consequentRootSet.noObfuscation);
      addDependentItems(consequentRootSet.dependentNoShrinking);
    }

    // Add dependent items that depend on -if rules.
    private void addDependentItems(
        Map<DexReference, Map<DexReference, Set<ProguardKeepRule>>> dependentItems) {
      dependentItems.forEach(
          (reference, dependence) ->
              dependentNoShrinking
                  .computeIfAbsent(reference, x -> new IdentityHashMap<>())
                  .putAll(dependence));
    }

    Map<DexReference, Set<ProguardKeepRule>> getDependentItems(DexDefinition item) {
      return Collections.unmodifiableMap(
          dependentNoShrinking.getOrDefault(item.toReference(), Collections.emptyMap()));
    }

    public void copy(DexReference original, DexReference rewritten) {
      if (noShrinking.containsKey(original)) {
        noShrinking.put(rewritten, noShrinking.get(original));
      }
      if (noOptimization.contains(original)) {
        noOptimization.add(rewritten);
      }
      if (noObfuscation.contains(original)) {
        noObfuscation.add(rewritten);
      }
      if (noSideEffects.containsKey(original)) {
        noSideEffects.put(rewritten, noSideEffects.get(original));
      }
      if (assumedValues.containsKey(original)) {
        assumedValues.put(rewritten, assumedValues.get(original));
      }
    }

    public void prune(DexReference reference) {
      noShrinking.remove(reference);
      noOptimization.remove(reference);
      noObfuscation.remove(reference);
      noSideEffects.remove(reference);
      assumedValues.remove(reference);
    }

    public void move(DexReference original, DexReference rewritten) {
      copy(original, rewritten);
      prune(original);
    }

    void shouldNotBeMinified(DexReference reference) {
      noObfuscation.add(reference);
    }

    public boolean mayBeMinified(DexReference reference, AppView<?> appView) {
      return !mayNotBeMinified(reference, appView);
    }

    public boolean mayNotBeMinified(DexReference reference, AppView<?> appView) {
      if (reference.isDexType()) {
        return noObfuscation.contains(
            appView.graphLense().getOriginalType(reference.asDexType()));
      } else if (reference.isDexMethod()) {
        return noObfuscation.contains(
            appView.graphLense().getOriginalMethodSignature(reference.asDexMethod()));
      } else {
        assert reference.isDexField();
        return noObfuscation.contains(
            appView.graphLense().getOriginalFieldSignature(reference.asDexField()));
      }
    }

    public boolean verifyKeptFieldsAreAccessedAndLive(AppInfoWithLiveness appInfo) {
      for (DexReference reference : noShrinking.keySet()) {
        if (reference.isDexField()) {
          DexField field = reference.asDexField();
          DexEncodedField encodedField = appInfo.definitionFor(field);
          if (encodedField != null
              && (encodedField.isStatic() || isKeptDirectlyOrIndirectly(field.holder, appInfo))) {
            assert appInfo.isFieldRead(field)
                : "Expected kept field `" + field.toSourceString() + "` to be read";
            assert appInfo.isFieldWritten(field)
                : "Expected kept field `" + field.toSourceString() + "` to be written";
          }
        }
      }
      return true;
    }

    public boolean verifyKeptMethodsAreTargetedAndLive(AppInfoWithLiveness appInfo) {
      for (DexReference reference : noShrinking.keySet()) {
        if (reference.isDexMethod()) {
          DexMethod method = reference.asDexMethod();
          assert appInfo.targetedMethods.contains(method)
              : "Expected kept method `" + method.toSourceString() + "` to be targeted";
          DexEncodedMethod encodedMethod = appInfo.definitionFor(method);
          if (!encodedMethod.accessFlags.isAbstract()
              && isKeptDirectlyOrIndirectly(method.holder, appInfo)) {
            assert appInfo.liveMethods.contains(method)
                : "Expected non-abstract kept method `"
                    + method.toSourceString()
                    + "` to be live";
          }
        }
      }
      return true;
    }

    public boolean verifyKeptTypesAreLive(AppInfoWithLiveness appInfo) {
      for (DexReference reference : noShrinking.keySet()) {
        if (reference.isDexType()) {
          DexType type = reference.asDexType();
          assert appInfo.liveTypes.contains(type)
              : "Expected kept type `" + type.toSourceString() + "` to be live";
        }
      }
      return true;
    }

    private boolean isKeptDirectlyOrIndirectly(DexType type, AppInfoWithLiveness appInfo) {
      if (noShrinking.containsKey(type)) {
        return true;
      }
      DexClass clazz = appInfo.definitionFor(type);
      if (clazz == null) {
        return false;
      }
      if (clazz.superType != null) {
        return isKeptDirectlyOrIndirectly(clazz.superType, appInfo);
      }
      return false;
    }

    public boolean verifyKeptItemsAreKept(DexApplication application, AppInfo appInfo) {
      Set<DexReference> pinnedItems =
          appInfo.hasLiveness() ? appInfo.withLiveness().pinnedItems : null;

      // Create a mapping from each required type to the set of required members on that type.
      Map<DexType, Set<DexReference>> requiredReferencesPerType = new IdentityHashMap<>();
      for (DexReference reference : noShrinking.keySet()) {
        // Check that `pinnedItems` is a super set of the root set.
        assert pinnedItems == null || pinnedItems.contains(reference)
            : "Expected reference `" + reference.toSourceString() + "` to be pinned";
        if (reference.isDexType()) {
          DexType type = reference.asDexType();
          requiredReferencesPerType.putIfAbsent(type, Sets.newIdentityHashSet());
        } else {
          assert reference.isDexField() || reference.isDexMethod();
          DexType holder =
              reference.isDexField()
                  ? reference.asDexField().holder
                  : reference.asDexMethod().holder;
          requiredReferencesPerType
              .computeIfAbsent(holder, key -> Sets.newIdentityHashSet())
              .add(reference);
        }
      }

      // Run through each class in the program and check that it has members it must have.
      for (DexProgramClass clazz : application.classes()) {
        Set<DexReference> requiredReferences =
            requiredReferencesPerType.getOrDefault(clazz.type, ImmutableSet.of());

        Set<DexField> fields = null;
        Set<DexMethod> methods = null;

        for (DexReference requiredReference : requiredReferences) {
          if (requiredReference.isDexField()) {
            DexField requiredField = requiredReference.asDexField();
            if (fields == null) {
              // Create a Set of the fields to avoid quadratic behavior.
              fields =
                  Streams.stream(clazz.fields())
                      .map(DexEncodedField::getKey)
                      .collect(Collectors.toSet());
            }
            assert fields.contains(requiredField)
                : "Expected field `"
                    + requiredField.toSourceString()
                    + "` from the root set to be present";
          } else if (requiredReference.isDexMethod()) {
            DexMethod requiredMethod = requiredReference.asDexMethod();
            if (methods == null) {
              // Create a Set of the methods to avoid quadratic behavior.
              methods =
                  Streams.stream(clazz.methods())
                      .map(DexEncodedMethod::getKey)
                      .collect(Collectors.toSet());
            }
            assert methods.contains(requiredMethod)
                : "Expected method `"
                    + requiredMethod.toSourceString()
                    + "` from the root set to be present";
          } else {
            assert false;
          }
        }
        requiredReferencesPerType.remove(clazz.type);
      }

      // If the map is non-empty, then a type in the root set was not in the application.
      if (!requiredReferencesPerType.isEmpty()) {
        DexType type = requiredReferencesPerType.keySet().iterator().next();
        DexClass clazz = application.definitionFor(type);
        assert clazz == null || clazz.isProgramClass()
            : "Unexpected library type in root set: `" + type + "`";
        assert requiredReferencesPerType.isEmpty()
            : "Expected type `" + type.toSourceString() + "` to be present";
      }

      return true;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("RootSet");

      builder.append("\nnoShrinking: " + noShrinking.size());
      builder.append("\nnoOptimization: " + noOptimization.size());
      builder.append("\nnoObfuscation: " + noObfuscation.size());
      builder.append("\nreasonAsked: " + reasonAsked.size());
      builder.append("\ncheckDiscarded: " + checkDiscarded.size());
      builder.append("\nnoSideEffects: " + noSideEffects.size());
      builder.append("\nassumedValues: " + assumedValues.size());
      builder.append("\ndependentNoShrinking: " + dependentNoShrinking.size());
      builder.append("\nidentifierNameStrings: " + identifierNameStrings.size());
      builder.append("\nifRules: " + ifRules.size());

      builder.append("\n\nNo Shrinking:");
      noShrinking.keySet().stream()
          .sorted(Comparator.comparing(DexReference::toSourceString))
          .forEach(a -> builder
              .append("\n").append(a.toSourceString()).append(" ").append(noShrinking.get(a)));
      builder.append("\n");
      return builder.toString();
    }
  }

  // A partial RootSet that becomes live due to the enabled -if rule.
  static class ConsequentRootSet {
    final Set<DexMethod> neverInline;
    final Set<DexType> neverClassInline;
    final Map<DexReference, Set<ProguardKeepRule>> noShrinking;
    final Set<DexReference> noOptimization;
    final Set<DexReference> noObfuscation;
    final Map<DexReference, Map<DexReference, Set<ProguardKeepRule>>> dependentNoShrinking;

    private ConsequentRootSet(
        Set<DexMethod> neverInline,
        Set<DexType> neverClassInline,
        Map<DexReference, Set<ProguardKeepRule>> noShrinking,
        Set<DexReference> noOptimization,
        Set<DexReference> noObfuscation,
        Map<DexReference, Map<DexReference, Set<ProguardKeepRule>>> dependentNoShrinking) {
      this.neverInline = Collections.unmodifiableSet(neverInline);
      this.neverClassInline = Collections.unmodifiableSet(neverClassInline);
      this.noShrinking = Collections.unmodifiableMap(noShrinking);
      this.noOptimization = Collections.unmodifiableSet(noOptimization);
      this.noObfuscation = Collections.unmodifiableSet(noObfuscation);
      this.dependentNoShrinking = Collections.unmodifiableMap(dependentNoShrinking);
    }
  }
}
