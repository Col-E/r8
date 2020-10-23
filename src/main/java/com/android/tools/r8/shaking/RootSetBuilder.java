// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.AssumeNoSideEffectsRuleForObjectMembersDiagnostic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.BottomUpClassHierarchyTraversal;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexLibraryClass;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DirectMappedDexApplication;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.ResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.ir.analysis.proto.GeneratedMessageLiteBuilderShrinker;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AnnotationMatchResult.AnnotationsIgnoredMatchResult;
import com.android.tools.r8.shaking.AnnotationMatchResult.ConcreteAnnotationMatchResult;
import com.android.tools.r8.shaking.DelayedRootSetActionItem.InterfaceMethodSyntheticBridgeAction;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.Consumer3;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.OriginWithPosition;
import com.android.tools.r8.utils.PredicateSet;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.io.PrintStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.Stack;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RootSetBuilder {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final SubtypingInfo subtypingInfo;
  private final DirectMappedDexApplication application;
  private final Iterable<? extends ProguardConfigurationRule> rules;
  private final MutableItemsWithRules noShrinking = new MutableItemsWithRules();
  private final MutableItemsWithRules softPinned = new MutableItemsWithRules();
  private final Set<DexReference> noObfuscation = Sets.newIdentityHashSet();
  private final LinkedHashMap<DexReference, DexReference> reasonAsked = new LinkedHashMap<>();
  private final LinkedHashMap<DexReference, DexReference> checkDiscarded = new LinkedHashMap<>();
  private final Set<DexMethod> alwaysInline = Sets.newIdentityHashSet();
  private final Set<DexMethod> forceInline = Sets.newIdentityHashSet();
  private final Set<DexMethod> neverInline = Sets.newIdentityHashSet();
  private final Set<DexMethod> bypassClinitforInlining = Sets.newIdentityHashSet();
  private final Set<DexMethod> whyAreYouNotInlining = Sets.newIdentityHashSet();
  private final Set<DexMethod> keepParametersWithConstantValue = Sets.newIdentityHashSet();
  private final Set<DexMethod> keepUnusedArguments = Sets.newIdentityHashSet();
  private final Set<DexMethod> reprocess = Sets.newIdentityHashSet();
  private final Set<DexMethod> neverReprocess = Sets.newIdentityHashSet();
  private final PredicateSet<DexType> alwaysClassInline = new PredicateSet<>();
  private final Set<DexType> neverClassInline = Sets.newIdentityHashSet();
  private final Set<DexType> noVerticalClassMerging = Sets.newIdentityHashSet();
  private final Set<DexType> noHorizontalClassMerging = Sets.newIdentityHashSet();
  private final Set<DexType> noStaticClassMerging = Sets.newIdentityHashSet();
  private final Set<DexReference> neverPropagateValue = Sets.newIdentityHashSet();
  private final Map<DexReference, MutableItemsWithRules> dependentNoShrinking =
      new IdentityHashMap<>();
  private final Map<DexReference, MutableItemsWithRules> dependentSoftPinned =
      new IdentityHashMap<>();
  private final Map<DexType, Set<ProguardKeepRuleBase>> dependentKeepClassCompatRule =
      new IdentityHashMap<>();
  private final Map<DexReference, ProguardMemberRule> mayHaveSideEffects = new IdentityHashMap<>();
  private final Map<DexMember<?, ?>, ProguardMemberRule> noSideEffects = new IdentityHashMap<>();
  private final Map<DexMember<?, ?>, ProguardMemberRule> assumedValues = new IdentityHashMap<>();
  private final Set<DexReference> identifierNameStrings = Sets.newIdentityHashSet();
  private final Queue<DelayedRootSetActionItem> delayedRootSetActionItems =
      new ConcurrentLinkedQueue<>();
  private final InternalOptions options;

  private final DexStringCache dexStringCache = new DexStringCache();
  private final Set<ProguardIfRule> ifRules = Sets.newIdentityHashSet();

  private final Map<OriginWithPosition, Set<DexMethod>> assumeNoSideEffectsWarnings =
      new LinkedHashMap<>();

  public RootSetBuilder(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      SubtypingInfo subtypingInfo,
      Iterable<? extends ProguardConfigurationRule> rules) {
    this.appView = appView;
    this.subtypingInfo = subtypingInfo;
    this.application = appView.appInfo().app().asDirect();
    this.rules = rules;
    this.options = appView.options();
  }

  public RootSetBuilder(
      AppView<? extends AppInfoWithClassHierarchy> appView, SubtypingInfo subtypingInfo) {
    this(appView, subtypingInfo, null);
  }

  void handleMatchedAnnotation(AnnotationMatchResult annotation) {
    // Intentionally empty.
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
    AnnotationMatchResult annotationMatchResult = satisfyAnnotation(rule, clazz);
    if (annotationMatchResult == null) {
      return;
    }
    handleMatchedAnnotation(annotationMatchResult);
    // In principle it should make a difference whether the user specified in a class
    // spec that a class either extends or implements another type. However, proguard
    // seems not to care, so users have started to use this inconsistently. We are thus
    // inconsistent, as well, but tell them.
    // TODO(herhut): One day make this do what it says.
    if (rule.hasInheritanceClassName() && !satisfyInheritanceRule(clazz, rule)) {
      return;
    }

    if (!rule.getClassNames().matches(clazz.type)) {
      return;
    }

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
          markMatchingVisibleMethods(
              clazz, memberKeepRules, rule, preconditionSupplier, false, ifRule);
          markMatchingVisibleFields(
              clazz, memberKeepRules, rule, preconditionSupplier, false, ifRule);
          break;
        case KEEP_CLASSES_WITH_MEMBERS:
          if (!allRulesSatisfied(memberKeepRules, clazz)) {
            break;
          }
          // fall through;
        case KEEP:
          markClass(clazz, rule, ifRule);
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
          markMatchingVisibleMethods(
              clazz, memberKeepRules, rule, preconditionSupplier, false, ifRule);
          markMatchingVisibleFields(
              clazz, memberKeepRules, rule, preconditionSupplier, false, ifRule);
          break;
        case CONDITIONAL:
          throw new Unreachable("-if rule will be evaluated separately, not here.");
      }
      return;
    }
    // Only the ordinary keep rules are supported in a conditional rule.
    assert ifRule == null;
    if (rule instanceof ProguardIfRule) {
      throw new Unreachable("-if rule will be evaluated separately, not here.");
    } else if (rule instanceof ProguardCheckDiscardRule) {
      if (memberKeepRules.isEmpty()) {
        markClass(clazz, rule, ifRule);
      } else {
        preconditionSupplier = ImmutableMap.of((definition -> true), clazz);
        markMatchingVisibleMethods(
            clazz, memberKeepRules, rule, preconditionSupplier, true, ifRule);
        markMatchingVisibleFields(clazz, memberKeepRules, rule, preconditionSupplier, true, ifRule);
      }
    } else if (rule instanceof ProguardWhyAreYouKeepingRule) {
      markClass(clazz, rule, ifRule);
      markMatchingVisibleMethods(clazz, memberKeepRules, rule, null, true, ifRule);
      markMatchingVisibleFields(clazz, memberKeepRules, rule, null, true, ifRule);
    } else if (rule instanceof ProguardAssumeMayHaveSideEffectsRule
        || rule instanceof ProguardAssumeNoSideEffectRule
        || rule instanceof ProguardAssumeValuesRule) {
      markMatchingVisibleMethods(clazz, memberKeepRules, rule, null, true, ifRule);
      markMatchingOverriddenMethods(
          appView.appInfo(), clazz, memberKeepRules, rule, null, true, ifRule);
      markMatchingVisibleFields(clazz, memberKeepRules, rule, null, true, ifRule);
    } else if (rule instanceof InlineRule
        || rule instanceof ConstantArgumentRule
        || rule instanceof UnusedArgumentRule
        || rule instanceof ReprocessMethodRule
        || rule instanceof WhyAreYouNotInliningRule) {
      markMatchingMethods(clazz, memberKeepRules, rule, null, ifRule);
    } else if (rule instanceof ClassInlineRule
        || rule instanceof NoVerticalClassMergingRule
        || rule instanceof NoHorizontalClassMergingRule
        || rule instanceof NoStaticClassMergingRule
        || rule instanceof ReprocessClassInitializerRule) {
      if (allRulesSatisfied(memberKeepRules, clazz)) {
        markClass(clazz, rule, ifRule);
      }
    } else if (rule instanceof MemberValuePropagationRule) {
      markMatchingVisibleMethods(clazz, memberKeepRules, rule, null, true, ifRule);
      markMatchingVisibleFields(clazz, memberKeepRules, rule, null, true, ifRule);
    } else {
      assert rule instanceof ProguardIdentifierNameStringRule;
      markMatchingFields(clazz, memberKeepRules, rule, null, ifRule);
      markMatchingMethods(clazz, memberKeepRules, rule, null, ifRule);
    }
  }

  void runPerRule(
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
      return;
    }

    futures.add(
        executorService.submit(
            () -> {
              for (DexProgramClass clazz :
                  rule.relevantCandidatesForRule(appView, subtypingInfo, application.classes())) {
                process(clazz, rule, ifRule);
              }
              if (rule.applyToNonProgramClasses()) {
                for (DexLibraryClass clazz : application.libraryClasses()) {
                  process(clazz, rule, ifRule);
                }
              }
            }));
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
    generateAssumeNoSideEffectsWarnings();
    if (!noSideEffects.isEmpty() || !assumedValues.isEmpty()) {
      BottomUpClassHierarchyTraversal.forAllClasses(appView, subtypingInfo)
          .visit(appView.appInfo().classes(), this::propagateAssumeRules);
    }
    if (appView.options().protoShrinking().enableGeneratedMessageLiteBuilderShrinking) {
      GeneratedMessageLiteBuilderShrinker.addInliningHeuristicsForBuilderInlining(
          appView,
          subtypingInfo,
          alwaysClassInline,
          noVerticalClassMerging,
          noHorizontalClassMerging,
          noStaticClassMerging,
          alwaysInline,
          bypassClinitforInlining);
    }
    assert Sets.intersection(neverInline, alwaysInline).isEmpty()
            && Sets.intersection(neverInline, forceInline).isEmpty()
        : "A method cannot be marked as both -neverinline and -forceinline/-alwaysinline.";
    assert appView.options().isMinificationEnabled() || noObfuscation.isEmpty();
    return new RootSet(
        noShrinking,
        softPinned,
        noObfuscation,
        ImmutableList.copyOf(reasonAsked.values()),
        ImmutableList.copyOf(checkDiscarded.values()),
        alwaysInline,
        forceInline,
        neverInline,
        bypassClinitforInlining,
        whyAreYouNotInlining,
        keepParametersWithConstantValue,
        keepUnusedArguments,
        reprocess,
        neverReprocess,
        alwaysClassInline,
        neverClassInline,
        noVerticalClassMerging,
        noHorizontalClassMerging,
        noStaticClassMerging,
        neverPropagateValue,
        mayHaveSideEffects,
        noSideEffects,
        assumedValues,
        dependentNoShrinking,
        dependentSoftPinned,
        dependentKeepClassCompatRule,
        identifierNameStrings,
        ifRules,
        Lists.newArrayList(delayedRootSetActionItems));
  }

  private void propagateAssumeRules(DexClass clazz) {
    Set<DexType> subTypes = subtypingInfo.allImmediateSubtypes(clazz.type);
    if (subTypes.isEmpty()) {
      return;
    }
    for (DexEncodedMethod encodedMethod : clazz.virtualMethods()) {
      // If the method has a body, it may have side effects. Don't do bottom-up propagation.
      if (encodedMethod.hasCode()) {
        assert !encodedMethod.shouldNotHaveCode();
        continue;
      }
      propagateAssumeRules(clazz.type, encodedMethod.method, subTypes, noSideEffects);
      propagateAssumeRules(clazz.type, encodedMethod.method, subTypes, assumedValues);
    }
  }

  private void propagateAssumeRules(
      DexType type,
      DexMethod reference,
      Set<DexType> subTypes,
      Map<DexMember<?, ?>, ProguardMemberRule> assumeRulePool) {
    ProguardMemberRule ruleToBePropagated = null;
    for (DexType subType : subTypes) {
      DexMethod referenceInSubType =
          appView.dexItemFactory().createMethod(subType, reference.proto, reference.name);
      // Those rules are bound to definitions, not references. If the current subtype does not
      // override the method, and when the retrieval of bound rule fails, it is unclear whether it
      // is due to the lack of the definition or it indeed means no matching rules. Similar to how
      // we apply those assume rules, here we use a resolved target.
      DexEncodedMethod target =
          appView.appInfo().unsafeResolveMethodDueToDexFormat(referenceInSubType).getSingleTarget();
      // But, the resolution should not be landed on the current type we are visiting.
      if (target == null || target.holder() == type) {
        continue;
      }
      ProguardMemberRule ruleInSubType = assumeRulePool.get(target.method);
      // We are looking for the greatest lower bound of assume rules from all sub types.
      // If any subtype doesn't have a matching assume rule, the lower bound is literally nothing.
      if (ruleInSubType == null) {
        ruleToBePropagated = null;
        break;
      }
      if (ruleToBePropagated == null) {
        ruleToBePropagated = ruleInSubType;
      } else {
        // TODO(b/133208961): Introduce comparison/meet of assume rules.
        if (!ruleToBePropagated.equals(ruleInSubType)) {
          ruleToBePropagated = null;
          break;
        }
      }
    }
    if (ruleToBePropagated != null) {
      assumeRulePool.put(reference, ruleToBePropagated);
    }
  }

  ConsequentRootSet buildConsequentRootSet() {
    return new ConsequentRootSet(
        neverInline,
        neverClassInline,
        noShrinking,
        softPinned,
        noObfuscation,
        dependentNoShrinking,
        dependentSoftPinned,
        dependentKeepClassCompatRule,
        Lists.newArrayList(delayedRootSetActionItems));
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
      boolean includeLibraryClasses,
      ProguardIfRule ifRule) {
    Set<Wrapper<DexMethod>> methodsMarked =
        options.forceProguardCompatibility ? null : new HashSet<>();
    Stack<DexClass> worklist = new Stack<>();
    worklist.add(clazz);
    while (!worklist.isEmpty()) {
      DexClass currentClass = worklist.pop();
      if (!includeLibraryClasses && currentClass.isNotProgramClass()) {
        break;
      }
      // In compat mode traverse all direct methods in the hierarchy.
      if (currentClass == clazz || options.forceProguardCompatibility) {
        currentClass
            .directMethods()
            .forEach(
                method -> {
                  DexDefinition precondition = testAndGetPrecondition(method, preconditionSupplier);
                  markMethod(method, memberKeepRules, methodsMarked, rule, precondition, ifRule);
                });
      }
      currentClass
          .virtualMethods()
          .forEach(
              method -> {
                DexDefinition precondition = testAndGetPrecondition(method, preconditionSupplier);
                markMethod(method, memberKeepRules, methodsMarked, rule, precondition, ifRule);
              });
      if (currentClass.superType != null) {
        DexClass dexClass = application.definitionFor(currentClass.superType);
        if (dexClass != null) {
          worklist.add(dexClass);
        }
      }
    }
    // TODO(b/143643942): Generalize the below approach to also work for subtyping hierarchies in
    //  fullmode.
    if (clazz.isProgramClass()
        && rule.isProguardKeepRule()
        && !rule.asProguardKeepRule().getModifiers().allowsShrinking) {
      new SynthesizeMissingInterfaceMethodsForMemberRules(
              clazz.asProgramClass(), memberKeepRules, rule, preconditionSupplier, ifRule)
          .run();
    }
  }

  /**
   * Utility class for visiting all super interfaces to ensure we keep method definitions specified
   * by proguard rules. If possible, we generate a forwarding bridge to the resolved target. If not,
   * we specifically synthesize a keep rule for the interface method.
   */
  private class SynthesizeMissingInterfaceMethodsForMemberRules {
    private final DexProgramClass originalClazz;
    private final Collection<ProguardMemberRule> memberKeepRules;
    private final ProguardConfigurationRule context;
    private final Map<Predicate<DexDefinition>, DexDefinition> preconditionSupplier;
    private final ProguardIfRule ifRule;
    private final Set<Wrapper<DexMethod>> seenMethods = Sets.newHashSet();
    private final Set<DexType> seenTypes = Sets.newIdentityHashSet();

    private SynthesizeMissingInterfaceMethodsForMemberRules(
        DexProgramClass originalClazz,
        Collection<ProguardMemberRule> memberKeepRules,
        ProguardConfigurationRule context,
        Map<Predicate<DexDefinition>, DexDefinition> preconditionSupplier,
        ProguardIfRule ifRule) {
      assert context.isProguardKeepRule();
      assert !context.asProguardKeepRule().getModifiers().allowsShrinking;
      this.originalClazz = originalClazz;
      this.memberKeepRules = memberKeepRules;
      this.context = context;
      this.preconditionSupplier = preconditionSupplier;
      this.ifRule = ifRule;
    }

    void handleMatchedAnnotation(AnnotationMatchResult annotationMatchResult) {
      // Intentionally empty.
    }

    void run() {
      visitAllSuperInterfaces(originalClazz.type);
    }

    private void visitAllSuperInterfaces(DexType type) {
      DexClass clazz = appView.definitionFor(type);
      if (clazz == null || clazz.isNotProgramClass() || !seenTypes.add(type)) {
        return;
      }
      for (DexType iface : clazz.interfaces.values) {
        visitAllSuperInterfaces(iface);
      }
      if (!clazz.isInterface()) {
        visitAllSuperInterfaces(clazz.superType);
        return;
      }
      if (originalClazz == clazz) {
        return;
      }
      for (DexEncodedMethod method : clazz.virtualMethods()) {
        // Check if we already added this.
        Wrapper<DexMethod> wrapped = MethodSignatureEquivalence.get().wrap(method.method);
        if (!seenMethods.add(wrapped)) {
          continue;
        }
        for (ProguardMemberRule rule : memberKeepRules) {
          if (rule.matches(method, appView, this::handleMatchedAnnotation, dexStringCache)) {
            tryAndKeepMethodOnClass(method, rule);
          }
        }
      }
    }

    private void tryAndKeepMethodOnClass(DexEncodedMethod method, ProguardMemberRule rule) {
      SingleResolutionResult resolutionResult =
          appView.appInfo().resolveMethodOn(originalClazz, method.method).asSingleResolution();
      if (resolutionResult == null || !resolutionResult.isVirtualTarget()) {
        return;
      }
      if (resolutionResult.getResolvedHolder() == originalClazz
          || resolutionResult.getResolvedHolder().isNotProgramClass()) {
        return;
      }
      if (!resolutionResult.getResolvedHolder().isInterface()) {
        // TODO(b/143643942): For fullmode, this check should probably be removed.
        return;
      }
      ProgramMethod resolutionMethod =
          new ProgramMethod(
              resolutionResult.getResolvedHolder().asProgramClass(),
              resolutionResult.getResolvedMethod());
      ProgramMethod methodToKeep =
          canInsertForwardingMethod(originalClazz, resolutionMethod.getDefinition())
              ? new ProgramMethod(
                  originalClazz,
                  resolutionMethod.getDefinition().toForwardingMethod(originalClazz, appView))
              : resolutionMethod;

      delayedRootSetActionItems.add(
          new InterfaceMethodSyntheticBridgeAction(
              methodToKeep,
              resolutionMethod,
              (rootSetBuilder) -> {
                if (Log.ENABLED) {
                  Log.verbose(
                      getClass(),
                      "Marking method `%s` due to `%s { %s }`.",
                      methodToKeep,
                      context,
                      rule);
                }
                DexDefinition precondition =
                    testAndGetPrecondition(methodToKeep.getDefinition(), preconditionSupplier);
                rootSetBuilder.addItemToSets(
                    methodToKeep.getDefinition(), context, rule, precondition, ifRule);
              }));
    }
  }

  private boolean canInsertForwardingMethod(DexClass holder, DexEncodedMethod target) {
    return appView.options().isGeneratingDex()
        || ArrayUtils.contains(holder.interfaces.values, target.holder());
  }

  private void markMatchingOverriddenMethods(
      AppInfoWithClassHierarchy appInfoWithSubtyping,
      DexClass clazz,
      Collection<ProguardMemberRule> memberKeepRules,
      ProguardConfigurationRule rule,
      Map<Predicate<DexDefinition>, DexDefinition> preconditionSupplier,
      boolean onlyIncludeProgramClasses,
      ProguardIfRule ifRule) {
    Set<DexType> visited = new HashSet<>();
    Deque<DexType> worklist = new ArrayDeque<>();
    // Intentionally skip the current `clazz`, assuming it's covered by markMatchingVisibleMethods.
    worklist.addAll(subtypingInfo.allImmediateSubtypes(clazz.type));

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
                markMethod(method, memberKeepRules, null, rule, precondition, ifRule);
              });
      worklist.addAll(subtypingInfo.allImmediateSubtypes(currentClazz.type));
    }
  }

  private void markMatchingMethods(
      DexClass clazz,
      Collection<ProguardMemberRule> memberKeepRules,
      ProguardConfigurationRule rule,
      Map<Predicate<DexDefinition>, DexDefinition> preconditionSupplier,
      ProguardIfRule ifRule) {
    clazz.forEachMethod(
        method -> {
          DexDefinition precondition = testAndGetPrecondition(method, preconditionSupplier);
          markMethod(method, memberKeepRules, null, rule, precondition, ifRule);
        });
  }

  private void markMatchingVisibleFields(
      DexClass clazz,
      Collection<ProguardMemberRule> memberKeepRules,
      ProguardConfigurationRule rule,
      Map<Predicate<DexDefinition>, DexDefinition> preconditionSupplier,
      boolean includeLibraryClasses,
      ProguardIfRule ifRule) {
    while (clazz != null) {
      if (!includeLibraryClasses && clazz.isNotProgramClass()) {
        return;
      }
      clazz.forEachField(
          field -> {
            DexDefinition precondition = testAndGetPrecondition(field, preconditionSupplier);
            markField(field, memberKeepRules, rule, precondition, ifRule);
          });
      clazz = clazz.superType == null ? null : application.definitionFor(clazz.superType);
    }
  }

  private void markMatchingFields(
      DexClass clazz,
      Collection<ProguardMemberRule> memberKeepRules,
      ProguardConfigurationRule rule,
      Map<Predicate<DexDefinition>, DexDefinition> preconditionSupplier,
      ProguardIfRule ifRule) {
    clazz.forEachField(
        field -> {
          DexDefinition precondition = testAndGetPrecondition(field, preconditionSupplier);
          markField(field, memberKeepRules, rule, precondition, ifRule);
        });
  }

  // TODO(b/67934426): Test this code.
  public static void writeSeeds(
      AppInfoWithLiveness appInfo, PrintStream out, Predicate<DexType> include) {
    appInfo
        .getKeepInfo()
        .forEachPinnedType(
            type -> {
              if (include.test(type)) {
                out.println(type.toSourceString());
              }
            });
    appInfo
        .getKeepInfo()
        .forEachPinnedField(
            field -> {
              if (include.test(field.holder)) {
                out.println(
                    field.holder.toSourceString()
                        + ": "
                        + field.type.toSourceString()
                        + " "
                        + field.name.toSourceString());
              }
            });
    appInfo
        .getKeepInfo()
        .forEachPinnedMethod(
            method -> {
              if (!include.test(method.holder)) {
                return;
              }
              DexProgramClass holder = asProgramClassOrNull(appInfo.definitionForHolder(method));
              DexEncodedMethod definition = method.lookupOnClass(holder);
              if (definition == null) {
                assert false;
                return;
              }
              out.print(method.holder.toSourceString() + ": ");
              if (definition.isClassInitializer()) {
                out.print(Constants.CLASS_INITIALIZER_NAME);
              } else if (definition.isInstanceInitializer()) {
                String holderName = method.holder.toSourceString();
                String constrName = holderName.substring(holderName.lastIndexOf('.') + 1);
                out.print(constrName);
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
            });
    out.close();
  }

  static boolean satisfyClassType(ProguardConfigurationRule rule, DexClass clazz) {
    return rule.getClassType().matches(clazz) != rule.getClassTypeNegated();
  }

  static boolean satisfyAccessFlag(ProguardConfigurationRule rule, DexClass clazz) {
    return rule.getClassAccessFlags().containsAll(clazz.accessFlags)
        && rule.getNegatedClassAccessFlags().containsNone(clazz.accessFlags);
  }

  static AnnotationMatchResult satisfyAnnotation(ProguardConfigurationRule rule, DexClass clazz) {
    return containsAllAnnotations(rule.getClassAnnotations(), clazz);
  }

  boolean satisfyInheritanceRule(DexClass clazz, ProguardConfigurationRule rule) {
    if (satisfyExtendsRule(clazz, rule)) {
      return true;
    }

    return satisfyImplementsRule(clazz, rule);
  }

  boolean satisfyExtendsRule(DexClass clazz, ProguardConfigurationRule rule) {
    if (anySuperTypeMatchesExtendsRule(clazz.superType, rule)) {
      return true;
    }
    // It is possible that this class used to inherit from another class X, but no longer does it,
    // because X has been merged into `clazz`.
    return anySourceMatchesInheritanceRuleDirectly(clazz, rule, false);
  }

  boolean anySuperTypeMatchesExtendsRule(DexType type, ProguardConfigurationRule rule) {
    while (type != null) {
      DexClass clazz = application.definitionFor(type);
      if (clazz == null) {
        // TODO(herhut): Warn about broken supertype chain?
        return false;
      }
      // TODO(b/110141157): Should the vertical class merger move annotations from the source to
      // the target class? If so, it is sufficient only to apply the annotation-matcher to the
      // annotations of `class`.
      if (rule.getInheritanceClassName().matches(clazz.type, appView)) {
        AnnotationMatchResult annotationMatchResult =
            containsAllAnnotations(rule.getInheritanceAnnotations(), clazz);
        if (annotationMatchResult != null) {
          handleMatchedAnnotation(annotationMatchResult);
          return true;
        }
      }
      type = clazz.superType;
    }
    return false;
  }

  boolean satisfyImplementsRule(DexClass clazz, ProguardConfigurationRule rule) {
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
      if (rule.getInheritanceClassName().matches(iface, appView)) {
        AnnotationMatchResult annotationMatchResult =
            containsAllAnnotations(rule.getInheritanceAnnotations(), ifaceClass);
        if (annotationMatchResult != null) {
          handleMatchedAnnotation(annotationMatchResult);
          return true;
        }
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

  boolean ruleSatisfiedByMethods(ProguardMemberRule rule, Iterable<DexEncodedMethod> methods) {
    if (rule.getRuleType().includesMethods()) {
      for (DexEncodedMethod method : methods) {
        if (rule.matches(method, appView, this::handleMatchedAnnotation, dexStringCache)) {
          return true;
        }
      }
    }
    return false;
  }

  boolean ruleSatisfiedByFields(ProguardMemberRule rule, Iterable<DexEncodedField> fields) {
    if (rule.getRuleType().includesFields()) {
      for (DexEncodedField field : fields) {
        if (rule.matches(field, appView, this::handleMatchedAnnotation, dexStringCache)) {
          return true;
        }
      }
    }
    return false;
  }

  static AnnotationMatchResult containsAllAnnotations(
      List<ProguardTypeMatcher> annotationMatchers, DexClass clazz) {
    return containsAllAnnotations(annotationMatchers, clazz.annotations());
  }

  static <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
      boolean containsAllAnnotations(
          List<ProguardTypeMatcher> annotationMatchers,
          DexEncodedMember<D, R> member,
          Consumer<AnnotationMatchResult> matchedAnnotationsConsumer) {
    AnnotationMatchResult annotationMatchResult =
        containsAllAnnotations(annotationMatchers, member.annotations());
    if (annotationMatchResult != null) {
      matchedAnnotationsConsumer.accept(annotationMatchResult);
      return true;
    }
    if (member.isDexEncodedMethod()) {
      DexEncodedMethod method = member.asDexEncodedMethod();
      for (int i = 0; i < method.parameterAnnotationsList.size(); i++) {
        annotationMatchResult =
            containsAllAnnotations(annotationMatchers, method.parameterAnnotationsList.get(i));
        if (annotationMatchResult != null) {
          matchedAnnotationsConsumer.accept(annotationMatchResult);
          return true;
        }
      }
    }
    return false;
  }

  private static AnnotationMatchResult containsAllAnnotations(
      List<ProguardTypeMatcher> annotationMatchers, DexAnnotationSet annotations) {
    if (annotationMatchers.isEmpty()) {
      return AnnotationsIgnoredMatchResult.getInstance();
    }
    List<DexAnnotation> matchedAnnotations = new ArrayList<>();
    for (ProguardTypeMatcher annotationMatcher : annotationMatchers) {
      DexAnnotation matchedAnnotation =
          getFirstAnnotationThatMatches(annotationMatcher, annotations);
      if (matchedAnnotation == null) {
        return null;
      }
      matchedAnnotations.add(matchedAnnotation);
    }
    return new ConcreteAnnotationMatchResult(matchedAnnotations);
  }

  private static DexAnnotation getFirstAnnotationThatMatches(
      ProguardTypeMatcher annotationMatcher, DexAnnotationSet annotations) {
    for (DexAnnotation annotation : annotations.annotations) {
      if (annotationMatcher.matches(annotation.getAnnotationType())) {
        return annotation;
      }
    }
    return null;
  }

  private void markMethod(
      DexEncodedMethod method,
      Collection<ProguardMemberRule> rules,
      Set<Wrapper<DexMethod>> methodsMarked,
      ProguardConfigurationRule context,
      DexDefinition precondition,
      ProguardIfRule ifRule) {
    if (methodsMarked != null
        && methodsMarked.contains(MethodSignatureEquivalence.get().wrap(method.method))) {
      // Ignore, method is overridden in sub class.
      return;
    }
    for (ProguardMemberRule rule : rules) {
      if (rule.matches(method, appView, this::handleMatchedAnnotation, dexStringCache)) {
        if (Log.ENABLED) {
          Log.verbose(getClass(), "Marking method `%s` due to `%s { %s }`.", method, context,
              rule);
        }
        if (methodsMarked != null) {
          methodsMarked.add(MethodSignatureEquivalence.get().wrap(method.method));
        }
        addItemToSets(method, context, rule, precondition, ifRule);
      }
    }
  }

  private void markField(
      DexEncodedField field,
      Collection<ProguardMemberRule> rules,
      ProguardConfigurationRule context,
      DexDefinition precondition,
      ProguardIfRule ifRule) {
    for (ProguardMemberRule rule : rules) {
      if (rule.matches(field, appView, this::handleMatchedAnnotation, dexStringCache)) {
        if (Log.ENABLED) {
          Log.verbose(getClass(), "Marking field `%s` due to `%s { %s }`.", field, context,
              rule);
        }
        addItemToSets(field, context, rule, precondition, ifRule);
      }
    }
  }

  private void markClass(DexClass clazz, ProguardConfigurationRule rule, ProguardIfRule ifRule) {
    if (Log.ENABLED) {
      Log.verbose(getClass(), "Marking class `%s` due to `%s`.", clazz.type, rule);
    }
    addItemToSets(clazz, rule, null, null, ifRule);
  }

  private void includeDescriptor(DexDefinition item, DexType type, ProguardKeepRuleBase context) {
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
        .computeIfAbsent(item.toReference(), x -> new MutableItemsWithRules())
        .addClassWithRule(type, context);
    // Unconditionally add to no-obfuscation, as that is only checked for surviving items.
    if (appView.options().isMinificationEnabled()) {
      noObfuscation.add(type);
    }
  }

  private void includeDescriptorClasses(DexDefinition item, ProguardKeepRuleBase context) {
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
      DexDefinition precondition,
      ProguardIfRule ifRule) {
    if (context instanceof ProguardKeepRule) {
      if (item.isDexEncodedField()) {
        DexEncodedField encodedField = item.asDexEncodedField();
        if (encodedField.getOptimizationInfo().cannotBeKept()) {
          // We should only ever get here with if rules.
          assert ifRule != null;
          return;
        }
      } else if (item.isDexEncodedMethod()) {
        DexEncodedMethod encodedMethod = item.asDexEncodedMethod();
        if (encodedMethod.isClassInitializer() && !options.debug) {
          // Don't keep class initializers.
          return;
        }
        if (encodedMethod.getOptimizationInfo().cannotBeKept()) {
          // We should only ever get here with if rules.
          assert ifRule != null;
          return;
        }
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
          DexClass holder = appView.definitionFor(encodedMethod.holder());
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

      // The reason for keeping should link to the conditional rule as a whole, if present.
      ProguardKeepRuleBase keepRule = ifRule != null ? ifRule : (ProguardKeepRuleBase) context;
      // The modifiers are specified on the actual keep rule (ie, the consequent/context).
      ProguardKeepRuleModifiers modifiers = ((ProguardKeepRule) context).getModifiers();
      // In compatibility mode, for a match on instance members a referenced class becomes live.
      if (options.forceProguardCompatibility
          && !modifiers.allowsShrinking
          && precondition != null
          && precondition.isDexClass()) {
        if (!item.isDexClass() && !item.isStaticMember()) {
          dependentKeepClassCompatRule
              .computeIfAbsent(precondition.asDexClass().getType(), i -> new HashSet<>())
              .add(keepRule);
          context.markAsUsed();
        }
      }
      if (!modifiers.allowsShrinking) {
        if (precondition != null) {
          dependentNoShrinking
              .computeIfAbsent(precondition.toReference(), x -> new MutableItemsWithRules())
              .addReferenceWithRule(item.toReference(), keepRule);
        } else {
          noShrinking.addReferenceWithRule(item.toReference(), keepRule);
        }
        context.markAsUsed();
      } else if (!modifiers.allowsOptimization) {
        if (precondition != null) {
          dependentSoftPinned
              .computeIfAbsent(precondition.toReference(), x -> new MutableItemsWithRules())
              .addReferenceWithRule(item.toReference(), keepRule);
        } else {
          softPinned.addReferenceWithRule(item.toReference(), keepRule);
        }
      }
      if (!modifiers.allowsOptimization) {
        // The -dontoptimize flag has only effect through the keep all rule, but we still
        // need to mark the rule as used.
        context.markAsUsed();
      }

      if (appView.options().isMinificationEnabled() && !modifiers.allowsObfuscation) {
        noObfuscation.add(item.toReference());
        context.markAsUsed();
      }
      if (modifiers.includeDescriptorClasses) {
        includeDescriptorClasses(item, keepRule);
        context.markAsUsed();
      }
    } else if (context instanceof ProguardAssumeMayHaveSideEffectsRule) {
      mayHaveSideEffects.put(item.toReference(), rule);
      context.markAsUsed();
    } else if (context instanceof ProguardAssumeNoSideEffectRule) {
      if (item.isDexEncodedMember()) {
        DexEncodedMember<?, ?> member = item.asDexEncodedMember();
        if (member.holder() == appView.dexItemFactory().objectType) {
          assert member.isDexEncodedMethod();
          reportAssumeNoSideEffectsWarningForJavaLangClassMethod(
              member.asDexEncodedMethod(), (ProguardAssumeNoSideEffectRule) context);
        } else {
          noSideEffects.put(member.toReference(), rule);
        }
        context.markAsUsed();
      }
    } else if (context instanceof ProguardWhyAreYouKeepingRule) {
      reasonAsked.computeIfAbsent(item.toReference(), i -> i);
      context.markAsUsed();
    } else if (context instanceof ProguardAssumeValuesRule) {
      if (item.isDexEncodedMember()) {
        assumedValues.put(item.asDexEncodedMember().toReference(), rule);
        context.markAsUsed();
      }
    } else if (context instanceof ProguardCheckDiscardRule) {
      checkDiscarded.computeIfAbsent(item.toReference(), i -> i);
      context.markAsUsed();
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
        context.markAsUsed();
      }
    } else if (context instanceof WhyAreYouNotInliningRule) {
      if (!item.isDexEncodedMethod()) {
        throw new Unreachable();
      }
      whyAreYouNotInlining.add(item.asDexEncodedMethod().method);
      context.markAsUsed();
    } else if (context.isClassInlineRule()) {
      ClassInlineRule classInlineRule = context.asClassInlineRule();
      DexClass clazz = item.asDexClass();
      if (clazz == null) {
        throw new IllegalStateException(
            "Unexpected -"
                + classInlineRule.typeString()
                + " rule for a non-class type: `"
                + item.toReference().toSourceString()
                + "`");
      }
      switch (classInlineRule.getType()) {
        case ALWAYS:
          alwaysClassInline.addElement(item.asDexClass().type);
          break;
        case NEVER:
          neverClassInline.add(item.asDexClass().type);
          break;
        default:
          throw new Unreachable();
      }
      context.markAsUsed();
    } else if (context instanceof NoVerticalClassMergingRule) {
      noVerticalClassMerging.add(item.asDexClass().type);
      context.markAsUsed();
    } else if (context instanceof NoHorizontalClassMergingRule) {
      noHorizontalClassMerging.add(item.asDexClass().type);
      context.markAsUsed();
    } else if (context instanceof NoStaticClassMergingRule) {
      noStaticClassMerging.add(item.asDexClass().type);
      context.markAsUsed();
    } else if (context instanceof MemberValuePropagationRule) {
      switch (((MemberValuePropagationRule) context).getType()) {
        case NEVER:
          // Only add members from propgram classes to `neverPropagateValue` since class member
          // values from library types are not propagated by default.
          if (item.isDexEncodedField()) {
            DexEncodedField field = item.asDexEncodedField();
            if (field.isProgramField(appView)) {
              neverPropagateValue.add(item.asDexEncodedField().field);
              context.markAsUsed();
            }
          } else if (item.isDexEncodedMethod()) {
            DexEncodedMethod method = item.asDexEncodedMethod();
            if (method.isProgramMethod(appView)) {
              neverPropagateValue.add(item.asDexEncodedMethod().method);
              context.markAsUsed();
            }
          }
          break;
        default:
          throw new Unreachable();
      }
    } else if (context instanceof ProguardIdentifierNameStringRule) {
      if (item.isDexEncodedField()) {
        identifierNameStrings.add(item.asDexEncodedField().field);
        context.markAsUsed();
      } else if (item.isDexEncodedMethod()) {
        identifierNameStrings.add(item.asDexEncodedMethod().method);
        context.markAsUsed();
      }
    } else if (context instanceof ConstantArgumentRule) {
      if (item.isDexEncodedMethod()) {
        keepParametersWithConstantValue.add(item.asDexEncodedMethod().method);
        context.markAsUsed();
      }
    } else if (context instanceof ReprocessClassInitializerRule) {
      DexProgramClass clazz = item.asProgramClass();
      if (clazz != null && clazz.hasClassInitializer()) {
        switch (context.asReprocessClassInitializerRule().getType()) {
          case ALWAYS:
            reprocess.add(clazz.getClassInitializer().method);
            break;
          case NEVER:
            neverReprocess.add(clazz.getClassInitializer().method);
            break;
          default:
            throw new Unreachable();
        }
        context.markAsUsed();
      }
    } else if (context.isReprocessMethodRule()) {
      if (item.isDexEncodedMethod()) {
        DexEncodedMethod method = item.asDexEncodedMethod();
        switch (context.asReprocessMethodRule().getType()) {
          case ALWAYS:
            reprocess.add(method.method);
            break;
          case NEVER:
            neverReprocess.add(method.method);
            break;
          default:
            throw new Unreachable();
        }
        context.markAsUsed();
      }
    } else if (context instanceof UnusedArgumentRule) {
      if (item.isDexEncodedMethod()) {
        keepUnusedArguments.add(item.asDexEncodedMethod().method);
        context.markAsUsed();
      }
    } else {
      throw new Unreachable();
    }
  }

  abstract static class RootSetBase {

    final Set<DexMethod> neverInline;
    final Set<DexType> neverClassInline;
    final MutableItemsWithRules noShrinking;
    final MutableItemsWithRules softPinned;
    final Set<DexReference> noObfuscation;
    final Map<DexReference, MutableItemsWithRules> dependentNoShrinking;
    final Map<DexReference, MutableItemsWithRules> dependentSoftPinned;
    final Map<DexType, Set<ProguardKeepRuleBase>> dependentKeepClassCompatRule;
    final List<DelayedRootSetActionItem> delayedRootSetActionItems;

    RootSetBase(
        Set<DexMethod> neverInline,
        Set<DexType> neverClassInline,
        MutableItemsWithRules noShrinking,
        MutableItemsWithRules softPinned,
        Set<DexReference> noObfuscation,
        Map<DexReference, MutableItemsWithRules> dependentNoShrinking,
        Map<DexReference, MutableItemsWithRules> dependentSoftPinned,
        Map<DexType, Set<ProguardKeepRuleBase>> dependentKeepClassCompatRule,
        List<DelayedRootSetActionItem> delayedRootSetActionItems) {
      this.neverInline = neverInline;
      this.neverClassInline = neverClassInline;
      this.noShrinking = noShrinking;
      this.softPinned = softPinned;
      this.noObfuscation = noObfuscation;
      this.dependentNoShrinking = dependentNoShrinking;
      this.dependentSoftPinned = dependentSoftPinned;
      this.dependentKeepClassCompatRule = dependentKeepClassCompatRule;
      this.delayedRootSetActionItems = delayedRootSetActionItems;
    }

    public void forEachClassWithDependentItems(
        DexDefinitionSupplier definitions, Consumer<DexProgramClass> consumer) {
      for (DexReference reference : dependentNoShrinking.keySet()) {
        if (reference.isDexType()) {
          DexType type = reference.asDexType();
          DexProgramClass clazz = asProgramClassOrNull(definitions.definitionFor(type));
          if (clazz != null) {
            consumer.accept(clazz);
          }
        }
      }
    }

    public void forEachMemberWithDependentItems(
        DexDefinitionSupplier definitions,
        BiConsumer<DexEncodedMember<?, ?>, ItemsWithRules> consumer) {
      dependentNoShrinking.forEach(
          (reference, dependentItems) -> {
            if (reference.isDexMember()) {
              DexMember<?, ?> member = reference.asDexMember();
              DexProgramClass holder =
                  asProgramClassOrNull(definitions.definitionForHolder(member));
              if (holder != null) {
                DexEncodedMember<?, ?> definition = holder.lookupMember(member);
                if (definition != null) {
                  consumer.accept(definition, dependentItems);
                }
              }
            }
          });
    }

    public void forEachDependentInstanceConstructor(
        DexProgramClass clazz,
        AppView<?> appView,
        BiConsumer<ProgramMethod, Set<ProguardKeepRuleBase>> fn) {
      getDependentItems(clazz)
          .forEachMethod(
              (reference, reasons) -> {
                DexProgramClass holder =
                    asProgramClassOrNull(appView.definitionForHolder(reference));
                if (holder != null) {
                  ProgramMethod method = holder.lookupProgramMethod(reference);
                  if (method != null && method.getDefinition().isInstanceInitializer()) {
                    fn.accept(method, reasons);
                  }
                }
              });
    }

    public void forEachDependentMember(
        DexDefinition item,
        AppView<?> appView,
        Consumer3<DexDefinition, DexEncodedMember<?, ?>, Set<ProguardKeepRuleBase>> fn) {
      getDependentItems(item)
          .forEachMember(
              (reference, reasons) -> {
                DexProgramClass holder =
                    asProgramClassOrNull(appView.definitionForHolder(reference));
                if (holder != null) {
                  DexEncodedMember<?, ?> member = holder.lookupMember(reference);
                  if (member != null) {
                    fn.accept(item, member, reasons);
                  }
                }
              });
    }

    public void forEachDependentNonStaticMember(
        DexDefinition item,
        AppView<?> appView,
        Consumer3<DexDefinition, DexEncodedMember<?, ?>, Set<ProguardKeepRuleBase>> fn) {
      forEachDependentMember(
          item,
          appView,
          (precondition, member, reasons) -> {
            if (!member.isStatic()) {
              fn.accept(precondition, member, reasons);
            }
          });
    }

    public void forEachDependentStaticMember(
        DexDefinition item,
        AppView<?> appView,
        Consumer3<DexDefinition, DexEncodedMember<?, ?>, Set<ProguardKeepRuleBase>> fn) {
      forEachDependentMember(
          item,
          appView,
          (precondition, member, reasons) -> {
            if (member.isStatic()) {
              fn.accept(precondition, member, reasons);
            }
          });
    }

    ItemsWithRules getDependentItems(DexDefinition item) {
      ItemsWithRules found = dependentNoShrinking.get(item.toReference());
      return found != null ? found : ItemsWithRules.empty();
    }

    Set<ProguardKeepRuleBase> getDependentKeepClassCompatRule(DexType type) {
      return dependentKeepClassCompatRule.get(type);
    }
  }

  abstract static class ItemsWithRules {

    public static ItemsWithRules empty() {
      return MutableItemsWithRules.EMPTY;
    }

    public abstract boolean containsClass(DexType type);

    public abstract boolean containsField(DexField field);

    public abstract boolean containsMethod(DexMethod method);

    public final boolean containsReference(DexReference reference) {
      return reference.apply(this::containsClass, this::containsField, this::containsMethod);
    }

    public abstract void forEachClass(Consumer<? super DexType> consumer);

    public abstract void forEachClass(
        BiConsumer<? super DexType, Set<ProguardKeepRuleBase>> consumer);

    public abstract void forEachField(Consumer<? super DexField> consumer);

    public abstract void forEachField(
        BiConsumer<? super DexField, Set<ProguardKeepRuleBase>> consumer);

    public abstract void forEachMember(Consumer<? super DexMember<?, ?>> consumer);

    public abstract void forEachMember(
        BiConsumer<? super DexMember<?, ?>, Set<ProguardKeepRuleBase>> consumer);

    public abstract void forEachMethod(Consumer<? super DexMethod> consumer);

    public abstract void forEachMethod(
        BiConsumer<? super DexMethod, Set<ProguardKeepRuleBase>> consumer);

    public abstract Set<ProguardKeepRuleBase> getRulesForClass(DexType type);

    public abstract Set<ProguardKeepRuleBase> getRulesForField(DexField field);

    public abstract Set<ProguardKeepRuleBase> getRulesForMethod(DexMethod method);

    public final Set<ProguardKeepRuleBase> getRulesForReference(DexReference reference) {
      return reference.apply(
          this::getRulesForClass, this::getRulesForField, this::getRulesForMethod);
    }
  }

  static class MutableItemsWithRules extends ItemsWithRules {

    private static final ItemsWithRules EMPTY =
        new MutableItemsWithRules(
            Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());

    final Map<DexType, Set<ProguardKeepRuleBase>> classesWithRules;
    final Map<DexField, Set<ProguardKeepRuleBase>> fieldsWithRules;
    final Map<DexMethod, Set<ProguardKeepRuleBase>> methodsWithRules;

    MutableItemsWithRules() {
      this(new IdentityHashMap<>(), new IdentityHashMap<>(), new IdentityHashMap<>());
    }

    private MutableItemsWithRules(
        Map<DexType, Set<ProguardKeepRuleBase>> classesWithRules,
        Map<DexField, Set<ProguardKeepRuleBase>> fieldsWithRules,
        Map<DexMethod, Set<ProguardKeepRuleBase>> methodsWithRules) {
      this.classesWithRules = classesWithRules;
      this.fieldsWithRules = fieldsWithRules;
      this.methodsWithRules = methodsWithRules;
    }

    public void addAll(ItemsWithRules items) {
      items.forEachClass(this::addClassWithRules);
      items.forEachField(this::addFieldWithRules);
      items.forEachMethod(this::addMethodWithRules);
    }

    public void addClassWithRule(DexType type, ProguardKeepRuleBase rule) {
      classesWithRules.computeIfAbsent(type, ignore -> new HashSet<>()).add(rule);
    }

    public void addClassWithRules(DexType type, Set<ProguardKeepRuleBase> rules) {
      classesWithRules.computeIfAbsent(type, ignore -> new HashSet<>()).addAll(rules);
    }

    public void addFieldWithRule(DexField field, ProguardKeepRuleBase rule) {
      fieldsWithRules.computeIfAbsent(field, ignore -> new HashSet<>()).add(rule);
    }

    public void addFieldWithRules(DexField field, Set<ProguardKeepRuleBase> rules) {
      fieldsWithRules.computeIfAbsent(field, ignore -> new HashSet<>()).addAll(rules);
    }

    public void addMethodWithRule(DexMethod method, ProguardKeepRuleBase rule) {
      methodsWithRules.computeIfAbsent(method, ignore -> new HashSet<>()).add(rule);
    }

    public void addMethodWithRules(DexMethod method, Set<ProguardKeepRuleBase> rules) {
      methodsWithRules.computeIfAbsent(method, ignore -> new HashSet<>()).addAll(rules);
    }

    public void addReferenceWithRule(DexReference reference, ProguardKeepRuleBase rule) {
      reference.accept(
          this::addClassWithRule, this::addFieldWithRule, this::addMethodWithRule, rule);
    }

    public void addReferenceWithRules(DexReference reference, Set<ProguardKeepRuleBase> rules) {
      reference.accept(
          this::addClassWithRules, this::addFieldWithRules, this::addMethodWithRules, rules);
    }

    @Override
    public boolean containsClass(DexType type) {
      return classesWithRules.containsKey(type);
    }

    @Override
    public boolean containsField(DexField field) {
      return fieldsWithRules.containsKey(field);
    }

    @Override
    public boolean containsMethod(DexMethod method) {
      return methodsWithRules.containsKey(method);
    }

    public void forEachReference(Consumer<DexReference> consumer) {
      forEachClass(consumer);
      forEachMember(consumer);
    }

    @Override
    public void forEachClass(Consumer<? super DexType> consumer) {
      classesWithRules.keySet().forEach(consumer);
    }

    @Override
    public void forEachClass(BiConsumer<? super DexType, Set<ProguardKeepRuleBase>> consumer) {
      classesWithRules.forEach(consumer);
    }

    @Override
    public void forEachField(Consumer<? super DexField> consumer) {
      fieldsWithRules.keySet().forEach(consumer);
    }

    @Override
    public void forEachField(BiConsumer<? super DexField, Set<ProguardKeepRuleBase>> consumer) {
      fieldsWithRules.forEach(consumer);
    }

    @Override
    public void forEachMember(Consumer<? super DexMember<?, ?>> consumer) {
      forEachField(consumer);
      forEachMethod(consumer);
    }

    @Override
    public void forEachMember(
        BiConsumer<? super DexMember<?, ?>, Set<ProguardKeepRuleBase>> consumer) {
      forEachField(consumer);
      forEachMethod(consumer);
    }

    @Override
    public void forEachMethod(Consumer<? super DexMethod> consumer) {
      methodsWithRules.keySet().forEach(consumer);
    }

    @Override
    public void forEachMethod(BiConsumer<? super DexMethod, Set<ProguardKeepRuleBase>> consumer) {
      methodsWithRules.forEach(consumer);
    }

    @Override
    public Set<ProguardKeepRuleBase> getRulesForClass(DexType type) {
      return classesWithRules.get(type);
    }

    @Override
    public Set<ProguardKeepRuleBase> getRulesForField(DexField field) {
      return fieldsWithRules.get(field);
    }

    @Override
    public Set<ProguardKeepRuleBase> getRulesForMethod(DexMethod method) {
      return methodsWithRules.get(method);
    }

    public void removeClass(DexType type) {
      classesWithRules.remove(type);
    }

    public void removeField(DexField field) {
      fieldsWithRules.remove(field);
    }

    public void removeMethod(DexMethod method) {
      methodsWithRules.remove(method);
    }

    public void removeReference(DexReference reference) {
      reference.accept(this::removeClass, this::removeField, this::removeMethod);
    }

    public void putAll(ItemsWithRules items) {
      items.forEachClass(this::putClassWithRules);
      items.forEachField(this::putFieldWithRules);
      items.forEachMethod(this::putMethodWithRules);
    }

    public void putClassWithRules(DexType type, Set<ProguardKeepRuleBase> rules) {
      classesWithRules.put(type, rules);
    }

    public void putFieldWithRules(DexField field, Set<ProguardKeepRuleBase> rules) {
      fieldsWithRules.put(field, rules);
    }

    public void putMethodWithRules(DexMethod method, Set<ProguardKeepRuleBase> rules) {
      methodsWithRules.put(method, rules);
    }

    public void putReferenceWithRules(DexReference reference, Set<ProguardKeepRuleBase> rules) {
      reference.accept(
          this::putClassWithRules, this::putFieldWithRules, this::putMethodWithRules, rules);
    }

    public int size() {
      return classesWithRules.size() + fieldsWithRules.size() + methodsWithRules.size();
    }
  }

  private void reportAssumeNoSideEffectsWarningForJavaLangClassMethod(
      DexEncodedMethod method, ProguardAssumeNoSideEffectRule context) {
    assert method.getHolderType() == options.dexItemFactory().objectType;
    OriginWithPosition key = new OriginWithPosition(context.getOrigin(), context.getPosition());
    assumeNoSideEffectsWarnings
        .computeIfAbsent(key, ignore -> new TreeSet<>(DexMethod::slowCompareTo))
        .add(method.getReference());
  }

  private boolean isWaitOrNotifyMethod(DexMethod method) {
    return method.name == options.itemFactory.waitMethodName
        || method.name == options.itemFactory.notifyMethodName
        || method.name == options.itemFactory.notifyAllMethodName;
  }

  private void generateAssumeNoSideEffectsWarnings() {
    ProguardClassFilter dontWarnPatterns =
        options.getProguardConfiguration() != null
            ? options.getProguardConfiguration().getDontWarnPatterns()
            : ProguardClassFilter.empty();
    if (dontWarnPatterns.matches(options.itemFactory.objectType)) {
      // Don't report any warnings since we don't apply -assumenosideeffects rules to notify() or
      // wait() anyway.
      return;
    }

    assumeNoSideEffectsWarnings.forEach(
        (originWithPosition, methods) -> {
          boolean matchesWaitOrNotifyMethods =
              methods.stream().anyMatch(this::isWaitOrNotifyMethod);
          if (!matchesWaitOrNotifyMethods) {
            // We model the remaining methods on java.lang.Object, and thus there should be no need
            // to warn in this case.
            return;
          }
          options.reporter.warning(
              new AssumeNoSideEffectsRuleForObjectMembersDiagnostic.Builder()
                  .addMatchedMethods(methods)
                  .setOrigin(originWithPosition.getOrigin())
                  .setPosition(originWithPosition.getPosition())
                  .build());
        });
  }

  public static class RootSet extends RootSetBase {

    public final ImmutableList<DexReference> reasonAsked;
    public final ImmutableList<DexReference> checkDiscarded;
    public final Set<DexMethod> alwaysInline;
    public final Set<DexMethod> forceInline;
    public final Set<DexMethod> bypassClinitForInlining;
    public final Set<DexMethod> whyAreYouNotInlining;
    public final Set<DexMethod> keepConstantArguments;
    public final Set<DexMethod> keepUnusedArguments;
    public final Set<DexMethod> reprocess;
    public final Set<DexMethod> neverReprocess;
    public final PredicateSet<DexType> alwaysClassInline;
    public final Set<DexType> noVerticalClassMerging;
    public final Set<DexType> noHorizontalClassMerging;
    public final Set<DexType> noStaticClassMerging;
    public final Set<DexReference> neverPropagateValue;
    public final Map<DexReference, ProguardMemberRule> mayHaveSideEffects;
    public final Map<DexMember<?, ?>, ProguardMemberRule> noSideEffects;
    public final Map<DexMember<?, ?>, ProguardMemberRule> assumedValues;
    public final Set<DexReference> identifierNameStrings;
    public final Set<ProguardIfRule> ifRules;

    private RootSet(
        MutableItemsWithRules noShrinking,
        MutableItemsWithRules softPinned,
        Set<DexReference> noObfuscation,
        ImmutableList<DexReference> reasonAsked,
        ImmutableList<DexReference> checkDiscarded,
        Set<DexMethod> alwaysInline,
        Set<DexMethod> forceInline,
        Set<DexMethod> neverInline,
        Set<DexMethod> bypassClinitForInlining,
        Set<DexMethod> whyAreYouNotInlining,
        Set<DexMethod> keepConstantArguments,
        Set<DexMethod> keepUnusedArguments,
        Set<DexMethod> reprocess,
        Set<DexMethod> neverReprocess,
        PredicateSet<DexType> alwaysClassInline,
        Set<DexType> neverClassInline,
        Set<DexType> noVerticalClassMerging,
        Set<DexType> noHorizontalClassMerging,
        Set<DexType> noStaticClassMerging,
        Set<DexReference> neverPropagateValue,
        Map<DexReference, ProguardMemberRule> mayHaveSideEffects,
        Map<DexMember<?, ?>, ProguardMemberRule> noSideEffects,
        Map<DexMember<?, ?>, ProguardMemberRule> assumedValues,
        Map<DexReference, MutableItemsWithRules> dependentNoShrinking,
        Map<DexReference, MutableItemsWithRules> dependentSoftPinned,
        Map<DexType, Set<ProguardKeepRuleBase>> dependentKeepClassCompatRule,
        Set<DexReference> identifierNameStrings,
        Set<ProguardIfRule> ifRules,
        List<DelayedRootSetActionItem> delayedRootSetActionItems) {
      super(
          neverInline,
          neverClassInline,
          noShrinking,
          softPinned,
          noObfuscation,
          dependentNoShrinking,
          dependentSoftPinned,
          dependentKeepClassCompatRule,
          delayedRootSetActionItems);
      this.reasonAsked = reasonAsked;
      this.checkDiscarded = checkDiscarded;
      this.alwaysInline = alwaysInline;
      this.forceInline = forceInline;
      this.bypassClinitForInlining = bypassClinitForInlining;
      this.whyAreYouNotInlining = whyAreYouNotInlining;
      this.keepConstantArguments = keepConstantArguments;
      this.keepUnusedArguments = keepUnusedArguments;
      this.reprocess = reprocess;
      this.neverReprocess = neverReprocess;
      this.alwaysClassInline = alwaysClassInline;
      this.noVerticalClassMerging = noVerticalClassMerging;
      this.noHorizontalClassMerging = noHorizontalClassMerging;
      this.noStaticClassMerging = noStaticClassMerging;
      this.neverPropagateValue = neverPropagateValue;
      this.mayHaveSideEffects = mayHaveSideEffects;
      this.noSideEffects = noSideEffects;
      this.assumedValues = assumedValues;
      this.identifierNameStrings = Collections.unmodifiableSet(identifierNameStrings);
      this.ifRules = Collections.unmodifiableSet(ifRules);
    }

    public void checkAllRulesAreUsed(InternalOptions options) {
      List<ProguardConfigurationRule> rules = options.getProguardConfiguration().getRules();
      if (rules != null) {
        for (ProguardConfigurationRule rule : rules) {
          if (!rule.isUsed()) {
            String message =
                "Proguard configuration rule does not match anything: `" + rule.toString() + "`";
            StringDiagnostic diagnostic = new StringDiagnostic(message, rule.getOrigin());
            if (options.testing.reportUnusedProguardConfigurationRules) {
              options.reporter.info(diagnostic);
            }
          }
        }
      }
    }

    void addConsequentRootSet(ConsequentRootSet consequentRootSet, boolean addNoShrinking) {
      neverInline.addAll(consequentRootSet.neverInline);
      neverClassInline.addAll(consequentRootSet.neverClassInline);
      noObfuscation.addAll(consequentRootSet.noObfuscation);
      if (addNoShrinking) {
        noShrinking.addAll(consequentRootSet.noShrinking);
      }
      addDependentItems(consequentRootSet.dependentNoShrinking, dependentNoShrinking);
      addDependentItems(consequentRootSet.dependentSoftPinned, dependentSoftPinned);
      consequentRootSet.dependentKeepClassCompatRule.forEach(
          (type, rules) ->
              dependentKeepClassCompatRule.computeIfAbsent(
                  type, k -> new HashSet<>()).addAll(rules));
      delayedRootSetActionItems.addAll(consequentRootSet.delayedRootSetActionItems);
    }

    // Add dependent items that depend on -if rules.
    private static void addDependentItems(
        Map<DexReference, ? extends ItemsWithRules> dependentItemsToAdd,
        Map<DexReference, MutableItemsWithRules> dependentItemsToAddTo) {
      dependentItemsToAdd.forEach(
          (reference, dependence) ->
              dependentItemsToAddTo
                  .computeIfAbsent(reference, x -> new MutableItemsWithRules())
                  .putAll(dependence));
    }

    public void copy(DexReference original, DexReference rewritten) {
      if (noShrinking.containsReference(original)) {
        noShrinking.putReferenceWithRules(rewritten, noShrinking.getRulesForReference(original));
      }
      if (noObfuscation.contains(original)) {
        noObfuscation.add(rewritten);
      }
      if (original.isDexMember()) {
        assert rewritten.isDexMember();
        DexMember<?, ?> originalMember = original.asDexMember();
        if (noSideEffects.containsKey(originalMember)) {
          noSideEffects.put(rewritten.asDexMember(), noSideEffects.get(originalMember));
        }
        if (assumedValues.containsKey(originalMember)) {
          assumedValues.put(rewritten.asDexMember(), assumedValues.get(originalMember));
        }
      }
    }

    public void prune(DexReference reference) {
      noShrinking.removeReference(reference);
      noObfuscation.remove(reference);
      noSideEffects.remove(reference);
      assumedValues.remove(reference);
    }

    public void pruneDeadItems(DexDefinitionSupplier definitions, Enqueuer enqueuer) {
      pruneDeadReferences(noVerticalClassMerging, definitions, enqueuer);
      pruneDeadReferences(noHorizontalClassMerging, definitions, enqueuer);
      pruneDeadReferences(noStaticClassMerging, definitions, enqueuer);
      pruneDeadReferences(alwaysInline, definitions, enqueuer);
      pruneDeadReferences(noSideEffects.keySet(), definitions, enqueuer);
    }

    private static void pruneDeadReferences(
        Set<? extends DexReference> references,
        DexDefinitionSupplier definitions,
        Enqueuer enqueuer) {
      references.removeIf(
          reference -> {
            if (reference.isDexType()) {
              DexClass definition = definitions.definitionFor(reference.asDexType());
              return definition == null || !enqueuer.isTypeLive(definition);
            }

            assert reference.isDexMember();

            DexMember<?, ?> member = reference.asDexMember();
            DexClass holder = definitions.definitionForHolder(member);
            DexEncodedMember<?, ?> definition = member.lookupOnClass(holder);
            if (definition == null) {
              return true;
            }
            if (holder.isProgramClass()) {
              if (definition.isDexEncodedField()) {
                DexEncodedField field = definition.asDexEncodedField();
                return !enqueuer.isFieldReferenced(field);
              }
              assert definition.isDexEncodedMethod();
              DexEncodedMethod method = definition.asDexEncodedMethod();
              return !enqueuer.isMethodLive(method) && !enqueuer.isMethodTargeted(method);
            }
            return !enqueuer.isNonProgramTypeLive(holder);
          });
    }

    public void move(DexReference original, DexReference rewritten) {
      copy(original, rewritten);
      prune(original);
    }

    void shouldNotBeMinified(DexReference reference) {
      noObfuscation.add(reference);
    }

    public boolean verifyKeptFieldsAreAccessedAndLive(AppInfoWithLiveness appInfo) {
      noShrinking.forEachField(
          reference -> {
            DexClass holder = appInfo.definitionForHolder(reference);
            DexEncodedField field = reference.lookupOnClass(holder);
            if (field != null
                && (field.isStatic() || isKeptDirectlyOrIndirectly(field.holder(), appInfo))) {
              assert appInfo.isFieldRead(field)
                  : "Expected kept field `" + field.toSourceString() + "` to be read";
              assert appInfo.isFieldWritten(field)
                  : "Expected kept field `" + field.toSourceString() + "` to be written";
            }
          });
      return true;
    }

    public boolean verifyKeptMethodsAreTargetedAndLive(AppInfoWithLiveness appInfo) {
      noShrinking.forEachMethod(
          reference -> {
            assert appInfo.targetedMethods.contains(reference)
                : "Expected kept method `" + reference.toSourceString() + "` to be targeted";
            DexEncodedMethod method =
                appInfo.definitionForHolder(reference).lookupMethod(reference);
            if (!method.isAbstract() && isKeptDirectlyOrIndirectly(method.holder(), appInfo)) {
              assert appInfo.liveMethods.contains(reference)
                  : "Expected non-abstract kept method `"
                      + reference.toSourceString()
                      + "` to be live";
            }
          });
      return true;
    }

    public boolean verifyKeptTypesAreLive(AppInfoWithLiveness appInfo) {
      noShrinking.forEachClass(
          type -> {
            assert appInfo.isLiveProgramType(type)
                : "Expected kept type `" + type.toSourceString() + "` to be live";
          });
      return true;
    }

    private boolean isKeptDirectlyOrIndirectly(DexType type, AppInfoWithLiveness appInfo) {
      if (noShrinking.containsClass(type)) {
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

    public boolean verifyKeptItemsAreKept(AppView<? extends AppInfoWithClassHierarchy> appView) {
      AppInfoWithClassHierarchy appInfo = appView.appInfo();
      GraphLens lens = appView.graphLens();
      // Create a mapping from each required type to the set of required members on that type.
      Map<DexType, Set<DexMember<?, ?>>> requiredMembersPerType = new IdentityHashMap<>();
      noShrinking.forEachClass(
          type -> {
            DexType rewrittenType = lens.lookupType(type);
            assert !appInfo.hasLiveness() || appInfo.withLiveness().isPinned(rewrittenType)
                : "Expected reference `" + rewrittenType.toSourceString() + "` to be pinned";
            requiredMembersPerType.computeIfAbsent(rewrittenType, key -> Sets.newIdentityHashSet());
          });
      noShrinking.forEachMember(
          member -> {
            DexMember<?, ?> rewrittenMember = lens.getRenamedMemberSignature(member);
            assert !appInfo.hasLiveness() || appInfo.withLiveness().isPinned(rewrittenMember)
                : "Expected reference `" + rewrittenMember.toSourceString() + "` to be pinned";
            requiredMembersPerType
                .computeIfAbsent(rewrittenMember.holder, key -> Sets.newIdentityHashSet())
                .add(rewrittenMember);
          });

      // Run through each class in the program and check that it has members it must have.
      for (DexProgramClass clazz : appView.appInfo().classes()) {
        Set<DexMember<?, ?>> requiredMembers =
            requiredMembersPerType.getOrDefault(clazz.type, ImmutableSet.of());

        Set<DexField> fields = null;
        Set<DexMethod> methods = null;

        for (DexMember<?, ?> requiredMember : requiredMembers) {
          if (requiredMember.isDexField()) {
            DexField requiredField = requiredMember.asDexField();
            if (fields == null) {
              // Create a Set of the fields to avoid quadratic behavior.
              fields =
                  Streams.stream(clazz.fields())
                      .map(DexEncodedField::toReference)
                      .collect(Collectors.toSet());
            }
            assert fields.contains(requiredField)
                : "Expected field `"
                    + requiredField.toSourceString()
                    + "` from the root set to be present";
          } else {
            DexMethod requiredMethod = requiredMember.asDexMethod();
            if (methods == null) {
              // Create a Set of the methods to avoid quadratic behavior.
              methods =
                  Streams.stream(clazz.methods())
                      .map(DexEncodedMethod::toReference)
                      .collect(Collectors.toSet());
            }
            assert methods.contains(requiredMethod)
                : "Expected method `"
                    + requiredMethod.toSourceString()
                    + "` from the root set to be present";
          }
        }
        requiredMembersPerType.remove(clazz.type);
      }

      // If the map is non-empty, then a type in the root set was not in the application.
      if (!requiredMembersPerType.isEmpty()) {
        DexType type = requiredMembersPerType.keySet().iterator().next();
        DexClass clazz = appView.definitionFor(type);
        assert clazz == null || clazz.isProgramClass()
            : "Unexpected library type in root set: `" + type + "`";
        assert requiredMembersPerType.isEmpty()
            : "Expected type `" + type.toSourceString() + "` to be present";
      }

      return true;
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      builder.append("RootSet");
      builder.append("\nnoShrinking: " + noShrinking.size());
      builder.append("\nnoObfuscation: " + noObfuscation.size());
      builder.append("\nreasonAsked: " + reasonAsked.size());
      builder.append("\ncheckDiscarded: " + checkDiscarded.size());
      builder.append("\nnoSideEffects: " + noSideEffects.size());
      builder.append("\nassumedValues: " + assumedValues.size());
      builder.append("\ndependentNoShrinking: " + dependentNoShrinking.size());
      builder.append("\nidentifierNameStrings: " + identifierNameStrings.size());
      builder.append("\nifRules: " + ifRules.size());
      return builder.toString();
    }
  }

  // A partial RootSet that becomes live due to the enabled -if rule or the addition of interface
  // keep rules.
  public static class ConsequentRootSet extends RootSetBase {

    ConsequentRootSet(
        Set<DexMethod> neverInline,
        Set<DexType> neverClassInline,
        MutableItemsWithRules noShrinking,
        MutableItemsWithRules softPinned,
        Set<DexReference> noObfuscation,
        Map<DexReference, MutableItemsWithRules> dependentNoShrinking,
        Map<DexReference, MutableItemsWithRules> dependentSoftPinned,
        Map<DexType, Set<ProguardKeepRuleBase>> dependentKeepClassCompatRule,
        List<DelayedRootSetActionItem> delayedRootSetActionItems) {
      super(
          neverInline,
          neverClassInline,
          noShrinking,
          softPinned,
          noObfuscation,
          dependentNoShrinking,
          dependentSoftPinned,
          dependentKeepClassCompatRule,
          delayedRootSetActionItems);
    }
  }
}
