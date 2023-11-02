// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.utils.LensUtils.rewriteAndApplyIfNotPrimitiveType;
import static com.google.common.base.Predicates.alwaysTrue;
import static java.util.Collections.emptyMap;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.AssumeNoSideEffectsRuleForObjectMembersDiagnostic;
import com.android.tools.r8.errors.AssumeValuesMissingStaticFieldDiagnostic;
import com.android.tools.r8.errors.InlinableStaticFinalFieldPreconditionDiagnostic;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.errors.UnusedProguardKeepRuleDiagnostic;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.BottomUpClassHierarchyTraversal;
import com.android.tools.r8.graph.Definition;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotation.AnnotatedKind;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.graph.DexClassAndMethod;
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
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMember;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.SubtypingInfo;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.proto.GeneratedMessageLiteBuilderShrinker;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.ir.desugar.itf.InterfaceDesugaringSyntheticHelper;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfo;
import com.android.tools.r8.profile.rewriting.ProfileCollectionAdditions;
import com.android.tools.r8.repackaging.RepackagingUtils;
import com.android.tools.r8.shaking.AnnotationMatchResult.AnnotationsIgnoredMatchResult;
import com.android.tools.r8.shaking.AnnotationMatchResult.ConcreteAnnotationMatchResult;
import com.android.tools.r8.shaking.AnnotationMatchResult.MatchedAnnotation;
import com.android.tools.r8.shaking.DelayedRootSetActionItem.InterfaceMethodSyntheticBridgeAction;
import com.android.tools.r8.shaking.EnqueuerEvent.InstantiatedClassEnqueuerEvent;
import com.android.tools.r8.shaking.EnqueuerEvent.LiveClassEnqueuerEvent;
import com.android.tools.r8.shaking.EnqueuerEvent.UnconditionalKeepInfoEvent;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.android.tools.r8.threading.TaskCollection;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.OriginWithPosition;
import com.android.tools.r8.utils.PredicateSet;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
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
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class RootSetUtils {

  public static class RootSetBuilder {

    private final AppView<? extends AppInfoWithClassHierarchy> appView;
    private AssumeInfoCollection.Builder assumeInfoCollectionBuilder;
    private final RootSetBuilderEventConsumer eventConsumer;
    private final SubtypingInfo subtypingInfo;
    private final DirectMappedDexApplication application;
    private final Iterable<? extends ProguardConfigurationRule> rules;
    private final DependentMinimumKeepInfoCollection dependentMinimumKeepInfo =
        DependentMinimumKeepInfoCollection.createConcurrent();
    private final LinkedHashMap<DexReference, DexReference> reasonAsked = new LinkedHashMap<>();
    private final Set<DexMethod> alwaysInline = Sets.newIdentityHashSet();
    private final Set<DexMethod> neverInlineDueToSingleCaller = Sets.newIdentityHashSet();
    private final Set<DexMethod> bypassClinitforInlining = Sets.newIdentityHashSet();
    private final Set<DexMethod> whyAreYouNotInlining = Sets.newIdentityHashSet();
    private final Set<DexMethod> reprocess = Sets.newIdentityHashSet();
    private final Set<DexMethod> neverReprocess = Sets.newIdentityHashSet();
    private final PredicateSet<DexType> alwaysClassInline = new PredicateSet<>();
    private final Set<DexType> neverClassInline = Sets.newIdentityHashSet();
    private final Set<DexType> noUnusedInterfaceRemoval = Sets.newIdentityHashSet();
    private final Set<DexType> noVerticalClassMerging = Sets.newIdentityHashSet();
    private final Set<DexType> noHorizontalClassMerging = Sets.newIdentityHashSet();
    private final Set<DexMember<?, ?>> neverPropagateValue = Sets.newIdentityHashSet();
    private final Map<DexType, Set<ProguardKeepRuleBase>> dependentKeepClassCompatRule =
        new IdentityHashMap<>();
    private final Map<DexReference, ProguardMemberRule> mayHaveSideEffects =
        new IdentityHashMap<>();
    private final Set<DexMember<?, ?>> identifierNameStrings = Sets.newIdentityHashSet();
    private final Map<DexMethod, ProgramMethod> keptMethodBridges = new ConcurrentHashMap<>();
    private final Queue<DelayedRootSetActionItem> delayedRootSetActionItems =
        new ConcurrentLinkedQueue<>();
    private final InternalOptions options;

    private final DexStringCache dexStringCache = new DexStringCache();
    private final Set<ProguardIfRule> ifRules = Sets.newIdentityHashSet();

    private final Map<OriginWithPosition, Set<DexMethod>> assumeNoSideEffectsWarnings =
        new LinkedHashMap<>();
    private final Set<DexProgramClass> classesWithCheckDiscardedMembers = Sets.newIdentityHashSet();

    private final OptimizationFeedbackSimple feedback = OptimizationFeedbackSimple.getInstance();

    private final InterfaceDesugaringSyntheticHelper interfaceDesugaringSyntheticHelper;
    private final ProgramMethodMap<ProgramMethod> pendingMethodMoveInverse =
        ProgramMethodMap.create();

    private RootSetBuilder(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        RootSetBuilderEventConsumer eventConsumer,
        SubtypingInfo subtypingInfo,
        Iterable<? extends ProguardConfigurationRule> rules) {
      this.appView = appView;
      this.eventConsumer = eventConsumer;
      this.subtypingInfo = subtypingInfo;
      this.application = appView.appInfo().app().asDirect();
      this.rules = rules;
      this.options = appView.options();
      interfaceDesugaringSyntheticHelper =
          options.isInterfaceMethodDesugaringEnabled()
              ? new InterfaceDesugaringSyntheticHelper(appView)
              : null;
    }

    private RootSetBuilder(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        Enqueuer enqueuer,
        SubtypingInfo subtypingInfo) {
      this(
          appView,
          RootSetBuilderEventConsumer.create(enqueuer.getProfileCollectionAdditions()),
          subtypingInfo,
          null);
    }

    boolean isMainDexRootSetBuilder() {
      return false;
    }

    void handleMatchedAnnotation(AnnotationMatchResult annotation) {
      // Intentionally empty.
    }

    public RootSetBuilder setAssumeInfoCollectionBuilder(
        AssumeInfoCollection.Builder assumeInfoCollectionBuilder) {
      this.assumeInfoCollectionBuilder = assumeInfoCollectionBuilder;
      return this;
    }

    // Process a class with the keep rule.
    private void process(DexClass clazz, ProguardConfigurationRule rule, ProguardIfRule ifRule) {
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
      Map<Predicate<DexDefinition>, DexProgramClass> preconditionSupplier;
      if (rule instanceof ProguardKeepRule) {
        if (clazz.isNotProgramClass()) {
          return;
        }
        switch (((ProguardKeepRule) rule).getType()) {
          case KEEP_CLASS_MEMBERS:
            // Members mentioned at -keepclassmembers always depend on their holder.
            preconditionSupplier = ImmutableMap.of(definition -> true, clazz.asProgramClass());
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
              preconditionSupplier.put(
                  definition -> !definition.isStaticMember(), clazz.asProgramClass());
            } else {
              // Members mentioned at -keep should always be pinned as long as that -keep rule is
              // not triggered conditionally.
              preconditionSupplier.put(alwaysTrue(), null);
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
      } else if (rule.isProguardCheckDiscardRule()) {
        evaluateCheckDiscardRule(clazz, rule.asProguardCheckDiscardRule());
      } else if (rule instanceof CheckEnumUnboxedRule) {
        evaluateCheckEnumUnboxedRule(clazz, (CheckEnumUnboxedRule) rule);
      } else if (rule instanceof NoAccessModificationRule
          || rule instanceof ProguardWhyAreYouKeepingRule) {
        markClass(clazz, rule, ifRule);
        markMatchingVisibleMethods(clazz, memberKeepRules, rule, null, true, ifRule);
        markMatchingVisibleFields(clazz, memberKeepRules, rule, null, true, ifRule);
      } else if (rule instanceof ProguardAssumeMayHaveSideEffectsRule) {
        markMatchingVisibleMethods(clazz, memberKeepRules, rule, null, true, ifRule);
        markMatchingOverriddenMethods(clazz, memberKeepRules, rule, null, true, ifRule);
        markMatchingVisibleFields(clazz, memberKeepRules, rule, null, true, ifRule);
      } else if (rule instanceof ProguardAssumeNoSideEffectRule
          || rule instanceof ProguardAssumeValuesRule) {
        if (assumeInfoCollectionBuilder != null) {
          markMatchingVisibleMethods(clazz, memberKeepRules, rule, null, true, ifRule);
          markMatchingOverriddenMethods(clazz, memberKeepRules, rule, null, true, ifRule);
          markMatchingVisibleFields(clazz, memberKeepRules, rule, null, true, ifRule);
        }
      } else if (rule instanceof NoFieldTypeStrengtheningRule
          || rule instanceof NoRedundantFieldLoadEliminationRule) {
        markMatchingFields(clazz, memberKeepRules, rule, null, ifRule);
      } else if (rule instanceof InlineRule
          || rule instanceof KeepConstantArgumentRule
          || rule instanceof KeepUnusedReturnValueRule
          || rule instanceof NoMethodStaticizingRule
          || rule instanceof NoParameterReorderingRule
          || rule instanceof NoParameterTypeStrengtheningRule
          || rule instanceof NoReturnTypeStrengtheningRule
          || rule instanceof KeepUnusedArgumentRule
          || rule instanceof ReprocessMethodRule
          || rule instanceof WhyAreYouNotInliningRule
          || rule.isMaximumRemovedAndroidLogLevelRule()) {
        markMatchingMethods(clazz, memberKeepRules, rule, null, ifRule);
      } else if (rule instanceof ClassInlineRule
          || rule instanceof NoUnusedInterfaceRemovalRule
          || rule instanceof NoVerticalClassMergingRule
          || rule instanceof NoHorizontalClassMergingRule
          || rule instanceof ReprocessClassInitializerRule) {
        if (allRulesSatisfied(memberKeepRules, clazz)) {
          markClass(clazz, rule, ifRule);
        }
      } else if (rule instanceof NoValuePropagationRule) {
        markMatchingVisibleMethods(clazz, memberKeepRules, rule, null, true, ifRule);
        markMatchingVisibleFields(clazz, memberKeepRules, rule, null, true, ifRule);
      } else if (rule instanceof ProguardIdentifierNameStringRule) {
        markMatchingFields(clazz, memberKeepRules, rule, null, ifRule);
        markMatchingMethods(clazz, memberKeepRules, rule, null, ifRule);
      } else {
        assert rule instanceof ConvertCheckNotNullRule;
        markMatchingMethods(clazz, memberKeepRules, rule, null, ifRule);
      }
    }

    void runPerRule(TaskCollection<?> tasks, ProguardConfigurationRule rule, ProguardIfRule ifRule)
        throws ExecutionException {
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

      tasks.submit(
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
          });
    }

    public RootSet build(ExecutorService executorService) throws ExecutionException {
      application.timing.begin("Build root set...");
      try {
        TaskCollection<?> tasks = new TaskCollection<>(options, executorService);
        // Mark all the things explicitly listed in keep rules.
        if (rules != null) {
          for (ProguardConfigurationRule rule : rules) {
            if (rule instanceof ProguardIfRule) {
              ProguardIfRule ifRule = (ProguardIfRule) rule;
              ifRules.add(ifRule);
            } else {
              runPerRule(tasks, rule, null);
            }
          }
          tasks.await();
        }
      } finally {
        application.timing.end();
      }
      finalizeCheckDiscardedInformation();
      generateAssumeNoSideEffectsWarnings();
      if (assumeInfoCollectionBuilder != null && !assumeInfoCollectionBuilder.isEmpty()) {
        BottomUpClassHierarchyTraversal.forAllClasses(appView, subtypingInfo)
            .visit(appView.appInfo().classes(), this::propagateAssumeRules);
      }
      appView.withGeneratedMessageLiteShrinker(
          shrinker -> shrinker.extendRootSet(dependentMinimumKeepInfo));
      appView.withGeneratedMessageLiteBuilderShrinker(
          shrinker -> shrinker.extendRootSet(dependentMinimumKeepInfo));
      if (appView.options().protoShrinking().enableGeneratedMessageLiteBuilderShrinking) {
        GeneratedMessageLiteBuilderShrinker.addInliningHeuristicsForBuilderInlining(
            appView,
            subtypingInfo,
            alwaysClassInline,
            noVerticalClassMerging,
            noHorizontalClassMerging,
            alwaysInline,
            bypassClinitforInlining);
      }
      return new RootSet(
          dependentMinimumKeepInfo,
          ImmutableList.copyOf(reasonAsked.values()),
          alwaysInline,
          neverInlineDueToSingleCaller,
          bypassClinitforInlining,
          whyAreYouNotInlining,
          reprocess,
          neverReprocess,
          alwaysClassInline,
          neverClassInline,
          noUnusedInterfaceRemoval,
          noVerticalClassMerging,
          noHorizontalClassMerging,
          neverPropagateValue,
          mayHaveSideEffects,
          dependentKeepClassCompatRule,
          identifierNameStrings,
          ifRules,
          Lists.newArrayList(delayedRootSetActionItems),
          pendingMethodMoveInverse);
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
        propagateAssumeRules(clazz, encodedMethod.getReference(), subTypes);
      }
    }

    private void propagateAssumeRules(DexClass clazz, DexMethod reference, Set<DexType> subTypes) {
      AssumeInfo infoToBePropagated = null;
      for (DexType subType : subTypes) {
        DexMethod referenceInSubType =
            appView.dexItemFactory().createMethod(subType, reference.proto, reference.name);
        // Those rules are bound to definitions, not references. If the current subtype does not
        // override the method, and when the retrieval of bound rule fails, it is unclear whether it
        // is due to the lack of the definition or it indeed means no matching rules. Similar to how
        // we apply those assume rules, here we use a resolved target.
        DexClassAndMethod target =
            appView
                .appInfo()
                .unsafeResolveMethodDueToDexFormatLegacy(referenceInSubType)
                .getResolutionPair();
        // But, the resolution should not be landed on the current type we are visiting.
        if (target == null || target.getHolder() == clazz) {
          continue;
        }
        AssumeInfo ruleInSubType = assumeInfoCollectionBuilder.buildInfo(target);
        // We are looking for the greatest lower bound of assume rules from all sub types.
        // If any subtype doesn't have a matching assume rule, the lower bound is literally nothing.
        if (ruleInSubType == null) {
          infoToBePropagated = null;
          break;
        }
        if (infoToBePropagated == null) {
          infoToBePropagated = ruleInSubType;
        } else {
          // TODO(b/133208961): Introduce comparison/meet of assume rules.
          if (!infoToBePropagated.equals(ruleInSubType)) {
            infoToBePropagated = null;
            break;
          }
        }
      }
      if (infoToBePropagated != null) {
        assumeInfoCollectionBuilder.meet(reference, infoToBePropagated);
      }
    }

    ConsequentRootSet buildConsequentRootSet() {
      return new ConsequentRootSet(
          neverInlineDueToSingleCaller,
          neverClassInline,
          dependentMinimumKeepInfo,
          dependentKeepClassCompatRule,
          Lists.newArrayList(delayedRootSetActionItems),
          pendingMethodMoveInverse);
    }

    private static DexProgramClass testAndGetPrecondition(
        DexDefinition definition,
        Map<Predicate<DexDefinition>, DexProgramClass> preconditionSupplier) {
      if (preconditionSupplier == null) {
        return null;
      }
      DexProgramClass precondition = null;
      boolean conditionEverMatched = false;
      for (Entry<Predicate<DexDefinition>, DexProgramClass> entry :
          preconditionSupplier.entrySet()) {
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
        Map<Predicate<DexDefinition>, DexProgramClass> preconditionSupplier,
        boolean includeLibraryClasses,
        ProguardIfRule ifRule) {
      Set<Wrapper<DexMethod>> methodsMarked =
          options.forceProguardCompatibility ? null : new HashSet<>();
      Deque<DexClass> worklist = new ArrayDeque<>();
      worklist.add(clazz);
      while (!worklist.isEmpty()) {
        DexClass currentClass = worklist.pop();
        if (!includeLibraryClasses && currentClass.isNotProgramClass()) {
          break;
        }
        // In compat mode traverse all direct methods in the hierarchy.
        currentClass.forEachClassMethodMatching(
            method ->
                method.belongsToVirtualPool()
                    || currentClass == clazz
                    || (method.isStatic() && !method.isPrivate() && !method.isInitializer())
                    || options.forceProguardCompatibility,
            method -> {
              DexProgramClass precondition =
                  testAndGetPrecondition(method.getDefinition(), preconditionSupplier);
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
          && !rule.asProguardKeepRule().getModifiers().allowsShrinking
          && !isMainDexRootSetBuilder()) {
        new SynthesizeMissingInterfaceMethodsForMemberRules(
                clazz.asProgramClass(), memberKeepRules, rule, preconditionSupplier, ifRule)
            .run();
      }
    }

    /**
     * Utility class for visiting all super interfaces to ensure we keep method definitions
     * specified by proguard rules. If possible, we generate a forwarding bridge to the resolved
     * target. If not, we specifically synthesize a keep rule for the interface method.
     */
    private class SynthesizeMissingInterfaceMethodsForMemberRules {

      private final DexProgramClass originalClazz;
      private final Collection<ProguardMemberRule> memberKeepRules;
      private final ProguardConfigurationRule context;
      private final Map<Predicate<DexDefinition>, DexProgramClass> preconditionSupplier;
      private final ProguardIfRule ifRule;
      private final Set<Wrapper<DexMethod>> seenMethods = Sets.newHashSet();
      private final Set<DexType> seenTypes = Sets.newIdentityHashSet();

      private SynthesizeMissingInterfaceMethodsForMemberRules(
          DexProgramClass originalClazz,
          Collection<ProguardMemberRule> memberKeepRules,
          ProguardConfigurationRule context,
          Map<Predicate<DexDefinition>, DexProgramClass> preconditionSupplier,
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
          if (clazz.superType != null) {
            visitAllSuperInterfaces(clazz.superType);
          }
          return;
        }
        if (originalClazz == clazz) {
          return;
        }
        clazz.forEachClassMethodMatching(
            DexEncodedMethod::belongsToVirtualPool,
            method -> {
              // Check if we already added this.
              Wrapper<DexMethod> wrapped =
                  MethodSignatureEquivalence.get().wrap(method.getReference());
              if (!seenMethods.add(wrapped)) {
                return;
              }
              for (ProguardMemberRule rule : memberKeepRules) {
                if (rule.matches(method, appView, this::handleMatchedAnnotation, dexStringCache)) {
                  tryAndKeepMethodOnClass(method, rule);
                }
              }
            });
      }

      private void tryAndKeepMethodOnClass(DexClassAndMethod method, ProguardMemberRule rule) {
        SingleResolutionResult<?> resolutionResult =
            appView
                .appInfo()
                .resolveMethodOnLegacy(originalClazz, method.getReference())
                .asSingleResolution();
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
        ProgramMethod resolutionMethod = resolutionResult.getResolvedProgramMethod();
        ProgramMethod methodToKeep;
        if (canInsertForwardingMethod(originalClazz, resolutionMethod.getDefinition())) {
          DexMethod methodToKeepReference =
              resolutionMethod.getReference().withHolder(originalClazz, appView.dexItemFactory());
          methodToKeep =
              keptMethodBridges.computeIfAbsent(
                  methodToKeepReference,
                  k ->
                      new ProgramMethod(
                          originalClazz,
                          resolutionMethod
                              .getDefinition()
                              .toForwardingMethod(originalClazz, appView)));
          assert methodToKeepReference.equals(methodToKeep.getReference());
        } else {
          methodToKeep = resolutionMethod;
        }

        delayedRootSetActionItems.add(
            new InterfaceMethodSyntheticBridgeAction(
                methodToKeep,
                resolutionMethod,
                (rootSetBuilder) -> {
                  DexProgramClass precondition =
                      testAndGetPrecondition(methodToKeep.getDefinition(), preconditionSupplier);
                  rootSetBuilder.addItemToSets(methodToKeep, context, rule, precondition, ifRule);
                }));
      }
    }

    private boolean canInsertForwardingMethod(DexClass holder, DexEncodedMethod target) {
      return appView.options().isGeneratingDex()
          || ArrayUtils.contains(holder.interfaces.values, target.getHolderType());
    }

    private void markMatchingOverriddenMethods(
        DexClass clazz,
        Collection<ProguardMemberRule> memberKeepRules,
        ProguardConfigurationRule rule,
        Map<Predicate<DexDefinition>, DexProgramClass> preconditionSupplier,
        boolean onlyIncludeProgramClasses,
        ProguardIfRule ifRule) {
      Set<DexType> visited = new HashSet<>();
      Deque<DexType> worklist = new ArrayDeque<>();
      // Intentionally skip the current `clazz`, assuming it's covered by
      // markMatchingVisibleMethods.
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
        currentClazz.forEachClassMethodMatching(
            DexEncodedMethod::belongsToVirtualPool,
            method -> {
              DexProgramClass precondition =
                  testAndGetPrecondition(method.getDefinition(), preconditionSupplier);
              markMethod(method, memberKeepRules, null, rule, precondition, ifRule);
            });
        worklist.addAll(subtypingInfo.allImmediateSubtypes(currentClazz.type));
      }
    }

    private void markMatchingMethods(
        DexClass clazz,
        Collection<ProguardMemberRule> memberKeepRules,
        ProguardConfigurationRule rule,
        Map<Predicate<DexDefinition>, DexProgramClass> preconditionSupplier,
        ProguardIfRule ifRule) {
      clazz.forEachClassMethod(
          method -> {
            DexProgramClass precondition =
                testAndGetPrecondition(method.getDefinition(), preconditionSupplier);
            markMethod(method, memberKeepRules, null, rule, precondition, ifRule);
          });
    }

    private void markMatchingVisibleFields(
        DexClass clazz,
        Collection<ProguardMemberRule> memberKeepRules,
        ProguardConfigurationRule rule,
        Map<Predicate<DexDefinition>, DexProgramClass> preconditionSupplier,
        boolean includeLibraryClasses,
        ProguardIfRule ifRule) {
      while (clazz != null) {
        if (!includeLibraryClasses && clazz.isNotProgramClass()) {
          return;
        }
        clazz.forEachClassField(
            field -> {
              DexProgramClass precondition =
                  testAndGetPrecondition(field.getDefinition(), preconditionSupplier);
              markField(field, memberKeepRules, rule, precondition, ifRule);
            });
        clazz = clazz.superType == null ? null : application.definitionFor(clazz.superType);
      }
    }

    private void markMatchingFields(
        DexClass clazz,
        Collection<ProguardMemberRule> memberKeepRules,
        ProguardConfigurationRule rule,
        Map<Predicate<DexDefinition>, DexProgramClass> preconditionSupplier,
        ProguardIfRule ifRule) {
      clazz.forEachClassField(
          field -> {
            DexProgramClass precondition =
                testAndGetPrecondition(field.getDefinition(), preconditionSupplier);
            markField(field, memberKeepRules, rule, precondition, ifRule);
          });
    }

    // TODO(b/67934426): Test this code.
    public static void writeSeeds(
        AppInfoWithLiveness appInfo, PrintStream out, Predicate<DexType> include) {
      InternalOptions options = appInfo.app().options;
      appInfo
          .getKeepInfo()
          .forEachPinnedType(
              type -> {
                if (include.test(type)) {
                  out.println(type.toSourceString());
                }
              },
              options);
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
              },
              options);
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
                  assert method.match(appInfo.dexItemFactory().deserializeLambdaMethod);
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
                      method.proto.returnType.toSourceString()
                          + " "
                          + method.name.toSourceString());
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
              },
              options);
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

    private boolean allRulesSatisfied(
        Collection<ProguardMemberRule> memberKeepRules, DexClass clazz) {
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
      return ruleSatisfiedByMethods(rule, clazz.classMethods())
          || ruleSatisfiedByFields(rule, clazz.classFields());
    }

    boolean ruleSatisfiedByMethods(ProguardMemberRule rule, Iterable<DexClassAndMethod> methods) {
      if (rule.getRuleType().includesMethods()) {
        for (DexClassAndMethod method : methods) {
          if (rule.matches(method, appView, this::handleMatchedAnnotation, dexStringCache)) {
            return true;
          }
        }
      }
      return false;
    }

    boolean ruleSatisfiedByFields(ProguardMemberRule rule, Iterable<DexClassAndField> fields) {
      if (rule.getRuleType().includesFields()) {
        for (DexClassAndField field : fields) {
          if (rule.matches(field, appView, this::handleMatchedAnnotation, dexStringCache)) {
            return true;
          }
        }
      }
      return false;
    }

    boolean sideEffectFreeIsRuleSatisfiedByField(ProguardMemberRule rule, DexClassAndField field) {
      return rule.matches(field, appView, ignore -> {}, dexStringCache);
    }

    static AnnotationMatchResult containsAllAnnotations(
        List<ProguardTypeMatcher> annotationMatchers, DexClass clazz) {
      return containsAllAnnotations(
          annotationMatchers, clazz, clazz.annotations(), AnnotatedKind.TYPE);
    }

    static <D extends DexEncodedMember<D, R>, R extends DexMember<D, R>>
        boolean containsAllAnnotations(
            List<ProguardTypeMatcher> annotationMatchers,
            DexClassAndMember<D, R> member,
            Consumer<AnnotationMatchResult> matchedAnnotationsConsumer) {
      AnnotationMatchResult annotationMatchResult =
          containsAllAnnotations(
              annotationMatchers,
              member,
              member.getAnnotations(),
              member.isField() ? AnnotatedKind.FIELD : AnnotatedKind.METHOD);
      if (annotationMatchResult != null) {
        matchedAnnotationsConsumer.accept(annotationMatchResult);
        return true;
      }
      if (member.isMethod()) {
        DexClassAndMethod method = member.asMethod();
        for (int i = 0; i < method.getParameterAnnotations().size(); i++) {
          annotationMatchResult =
              containsAllAnnotations(
                  annotationMatchers,
                  method,
                  method.getParameterAnnotation(i),
                  AnnotatedKind.PARAMETER);
          if (annotationMatchResult != null) {
            matchedAnnotationsConsumer.accept(annotationMatchResult);
            return true;
          }
        }
      }
      return false;
    }

    private static AnnotationMatchResult containsAllAnnotations(
        List<ProguardTypeMatcher> annotationMatchers,
        Definition annotatedItem,
        DexAnnotationSet annotations,
        AnnotatedKind annotatedKind) {
      if (annotationMatchers.isEmpty()) {
        return AnnotationsIgnoredMatchResult.getInstance();
      }
      List<MatchedAnnotation> matchedAnnotations = new ArrayList<>();
      for (ProguardTypeMatcher annotationMatcher : annotationMatchers) {
        DexAnnotation matchedAnnotation =
            getFirstAnnotationThatMatches(annotationMatcher, annotations);
        if (matchedAnnotation == null) {
          return null;
        }
        if (annotatedItem.isProgramDefinition()) {
          matchedAnnotations.add(
              new MatchedAnnotation(
                  annotatedItem.asProgramDefinition(), matchedAnnotation, annotatedKind));
        }
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
        DexClassAndMethod method,
        Collection<ProguardMemberRule> rules,
        Set<Wrapper<DexMethod>> methodsMarked,
        ProguardConfigurationRule context,
        DexProgramClass precondition,
        ProguardIfRule ifRule) {
      if (methodsMarked != null
          && methodsMarked.contains(MethodSignatureEquivalence.get().wrap(method.getReference()))) {
        // Ignore, method is overridden in sub class.
        return;
      }
      for (ProguardMemberRule rule : rules) {
        if (rule.matches(method, appView, this::handleMatchedAnnotation, dexStringCache)) {
          if (methodsMarked != null) {
            methodsMarked.add(MethodSignatureEquivalence.get().wrap(method.getReference()));
          }
          addItemToSets(method, context, rule, precondition, ifRule);
        }
      }
    }

    private void markField(
        DexClassAndField field,
        Collection<ProguardMemberRule> rules,
        ProguardConfigurationRule context,
        DexProgramClass precondition,
        ProguardIfRule ifRule) {
      for (ProguardMemberRule rule : rules) {
        if (rule.matches(field, appView, this::handleMatchedAnnotation, dexStringCache)) {
          addItemToSets(field, context, rule, precondition, ifRule);
        }
      }
    }

    private void markClass(DexClass clazz, ProguardConfigurationRule rule, ProguardIfRule ifRule) {
      addItemToSets(clazz, rule, null, null, ifRule);
    }

    private void includeDescriptor(
        DexType type, ProguardKeepRuleBase rule, EnqueuerEvent preconditionEvent) {
      if (type.isVoidType()) {
        return;
      }
      if (type.isArrayType()) {
        type = type.toBaseType(appView.dexItemFactory());
      }
      if (type.isPrimitiveType()) {
        return;
      }
      DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
      if (clazz == null) {
        return;
      }

      // Keep the type if the item is also kept.
      ProguardKeepRuleModifiers modifiers = rule.getModifiers();
      if (appView.options().isShrinking() && !modifiers.allowsShrinking) {
        dependentMinimumKeepInfo
            .getOrCreateMinimumKeepInfoFor(preconditionEvent, clazz.getReference())
            .addRule(rule)
            .disallowShrinking();
      }

      // Disable minification for the type.
      if (appView.options().isMinificationEnabled() && !modifiers.allowsObfuscation) {
        dependentMinimumKeepInfo
            .getOrCreateMinimumKeepInfoFor(preconditionEvent, clazz.getReference())
            .disallowMinification()
            .asClassJoiner()
            .disallowRepackaging();
      }
    }

    private void includeDescriptorClasses(
        ProgramDefinition item, ProguardKeepRuleBase rule, EnqueuerEvent preconditionEvent) {
      if (item.isMethod()) {
        ProgramMethod method = item.asProgramMethod();
        includeDescriptor(method.getReturnType(), rule, preconditionEvent);
        for (DexType value : method.getParameters()) {
          includeDescriptor(value, rule, preconditionEvent);
        }
      } else if (item.isField()) {
        ProgramField field = item.asProgramField();
        includeDescriptor(field.getType(), rule, preconditionEvent);
      } else {
        assert item.isClass();
      }
    }

    private synchronized void addItemToSets(
        Definition item,
        ProguardConfigurationRule context,
        ProguardMemberRule rule,
        DexProgramClass precondition,
        ProguardIfRule ifRule) {
      if (context.isProguardKeepRule()) {
        if (!item.isProgramDefinition()) {
          // Keep rules do not apply to non-program items.
          return;
        }
        evaluateKeepRule(
            item.asProgramDefinition(), context.asProguardKeepRule(), precondition, ifRule);
      } else if (context instanceof ProguardAssumeMayHaveSideEffectsRule) {
        mayHaveSideEffects.put(item.getReference(), rule);
        context.markAsUsed();
      } else if (context instanceof ProguardAssumeNoSideEffectRule) {
        evaluateAssumeNoSideEffectsRule(item, (ProguardAssumeNoSideEffectRule) context, rule);
      } else if (context instanceof ProguardAssumeValuesRule) {
        evaluateAssumeValuesRule(item, (ProguardAssumeValuesRule) context, rule);
      } else if (context instanceof ProguardWhyAreYouKeepingRule) {
        reasonAsked.computeIfAbsent(item.getReference(), i -> i);
        context.markAsUsed();
      } else if (context.isProguardCheckDiscardRule()) {
        assert item.isProgramMember();
        evaluateCheckDiscardMemberRule(
            item.asProgramMember(), context.asProguardCheckDiscardRule());
      } else if (context instanceof InlineRule) {
        if (item.isMethod()) {
          DexMethod reference = item.asMethod().getReference();
          switch (((InlineRule) context).getType()) {
            case ALWAYS:
              alwaysInline.add(reference);
              break;
            case NEVER:
              dependentMinimumKeepInfo
                  .getOrCreateUnconditionalMinimumKeepInfoFor(item.getReference())
                  .asMethodJoiner()
                  .disallowInlining();
              break;
            case NEVER_CLASS_INLINE:
              dependentMinimumKeepInfo
                  .getOrCreateUnconditionalMinimumKeepInfoFor(item.getReference())
                  .asMethodJoiner()
                  .disallowClassInlining();
              break;
            case NEVER_SINGLE_CALLER:
              neverInlineDueToSingleCaller.add(reference);
              break;
            default:
              throw new Unreachable();
          }
          context.markAsUsed();
        }
      } else if (context instanceof WhyAreYouNotInliningRule) {
        if (!item.isMethod()) {
          throw new Unreachable();
        }
        whyAreYouNotInlining.add(item.asMethod().getReference());
        context.markAsUsed();
      } else if (context.isClassInlineRule()) {
        ClassInlineRule classInlineRule = context.asClassInlineRule();
        DexClass clazz = item.asClass();
        if (clazz == null) {
          throw new IllegalStateException(
              "Unexpected -"
                  + classInlineRule.typeString()
                  + " rule for a non-class type: `"
                  + item.getReference().toSourceString()
                  + "`");
        }
        switch (classInlineRule.getType()) {
          case ALWAYS:
            alwaysClassInline.addElement(item.asClass().getType());
            break;
          case NEVER:
            neverClassInline.add(item.asClass().getType());
            break;
          default:
            throw new Unreachable();
        }
        context.markAsUsed();
      } else if (context instanceof NoAccessModificationRule) {
        assert item.isProgramDefinition();
        dependentMinimumKeepInfo
            .getOrCreateUnconditionalMinimumKeepInfoFor(item.getReference())
            .disallowAccessModificationForTesting();
        context.markAsUsed();
      } else if (context instanceof NoFieldTypeStrengtheningRule) {
        assert item.isProgramField();
        dependentMinimumKeepInfo
            .getOrCreateUnconditionalMinimumKeepInfoFor(item.getReference())
            .asFieldJoiner()
            .disallowFieldTypeStrengthening();
        context.markAsUsed();
      } else if (context instanceof NoRedundantFieldLoadEliminationRule) {
        assert item.isProgramField();
        dependentMinimumKeepInfo
            .getOrCreateUnconditionalMinimumKeepInfoFor(item.getReference())
            .asFieldJoiner()
            .disallowRedundantFieldLoadElimination();
        context.markAsUsed();
      } else if (context instanceof NoUnusedInterfaceRemovalRule) {
        noUnusedInterfaceRemoval.add(item.asClass().type);
        context.markAsUsed();
      } else if (context instanceof NoVerticalClassMergingRule) {
        noVerticalClassMerging.add(item.asClass().type);
        context.markAsUsed();
      } else if (context instanceof NoHorizontalClassMergingRule) {
        noHorizontalClassMerging.add(item.asClass().type);
        context.markAsUsed();
      } else if (context instanceof NoMethodStaticizingRule) {
        assert item.isProgramMethod();
        dependentMinimumKeepInfo
            .getOrCreateUnconditionalMinimumKeepInfoFor(item.getReference())
            .asMethodJoiner()
            .disallowMethodStaticizing();
        context.markAsUsed();
      } else if (context instanceof NoParameterReorderingRule) {
        assert item.isProgramMethod();
        dependentMinimumKeepInfo
            .getOrCreateUnconditionalMinimumKeepInfoFor(item.getReference())
            .asMethodJoiner()
            .disallowParameterReordering();
        context.markAsUsed();
      } else if (context instanceof NoParameterTypeStrengtheningRule) {
        assert item.isProgramMethod();
        dependentMinimumKeepInfo
            .getOrCreateUnconditionalMinimumKeepInfoFor(item.getReference())
            .asMethodJoiner()
            .disallowParameterTypeStrengthening();
        context.markAsUsed();
      } else if (context instanceof NoReturnTypeStrengtheningRule) {
        assert item.isProgramMethod();
        dependentMinimumKeepInfo
            .getOrCreateUnconditionalMinimumKeepInfoFor(item.getReference())
            .asMethodJoiner()
            .disallowReturnTypeStrengthening();
        context.markAsUsed();
      } else if (context instanceof NoValuePropagationRule) {
        // Only add members from propgram classes to `neverPropagateValue` since class member values
        // from library types are not propagated by default.
        if (item.isField()) {
          DexClassAndField field = item.asField();
          if (field.isProgramField()) {
            neverPropagateValue.add(field.getReference());
            context.markAsUsed();
          }
        } else if (item.isMethod()) {
          DexClassAndMethod method = item.asMethod();
          if (method.isProgramMethod()) {
            neverPropagateValue.add(method.getReference());
            context.markAsUsed();
          }
        }
      } else if (context instanceof ProguardIdentifierNameStringRule) {
        evaluateIdentifierNameStringRule(item, context, ifRule);
      } else if (context instanceof ReprocessClassInitializerRule) {
        DexProgramClass clazz = item.asProgramClass();
        if (clazz != null && clazz.hasClassInitializer()) {
          switch (context.asReprocessClassInitializerRule().getType()) {
            case ALWAYS:
              reprocess.add(clazz.getClassInitializer().getReference());
              break;
            case NEVER:
              neverReprocess.add(clazz.getClassInitializer().getReference());
              break;
            default:
              throw new Unreachable();
          }
          context.markAsUsed();
        }
      } else if (context.isReprocessMethodRule()) {
        if (item.isMethod()) {
          DexClassAndMethod method = item.asMethod();
          switch (context.asReprocessMethodRule().getType()) {
            case ALWAYS:
              reprocess.add(method.getReference());
              break;
            case NEVER:
              neverReprocess.add(method.getReference());
              break;
            default:
              throw new Unreachable();
          }
          context.markAsUsed();
        }
      } else if (context instanceof KeepConstantArgumentRule) {
        assert item.isProgramMethod();
        dependentMinimumKeepInfo
            .getOrCreateUnconditionalMinimumKeepInfoFor(item.getReference())
            .asMethodJoiner()
            .disallowConstantArgumentOptimization();
        context.markAsUsed();
      } else if (context instanceof KeepUnusedArgumentRule) {
        assert item.isProgramMethod();
        dependentMinimumKeepInfo
            .getOrCreateUnconditionalMinimumKeepInfoFor(item.getReference())
            .asMethodJoiner()
            .disallowUnusedArgumentOptimization();
        context.markAsUsed();
      } else if (context instanceof KeepUnusedReturnValueRule) {
        assert item.isProgramMethod();
        dependentMinimumKeepInfo
            .getOrCreateUnconditionalMinimumKeepInfoFor(item.getReference())
            .asMethodJoiner()
            .disallowUnusedReturnValueOptimization();
        context.markAsUsed();
      } else if (context instanceof ConvertCheckNotNullRule) {
        assert item.isMethod();
        feedback.setConvertCheckNotNull(item.asMethod());
        if (item.isProgramMethod()) {
          // Disallow optimization to prevent inlining.
          dependentMinimumKeepInfo
              .getOrCreateUnconditionalMinimumKeepInfoFor(item.getReference())
              .asMethodJoiner()
              .disallowOptimization();
        }
        context.markAsUsed();
      } else if (context.isMaximumRemovedAndroidLogLevelRule()) {
        evaluateMaximumRemovedAndroidLogLevelRule(
            item, context.asMaximumRemovedAndroidLogLevelRule());
      } else {
        throw new Unreachable();
      }
    }

    private void evaluateCheckDiscardRule(DexClass clazz, ProguardCheckDiscardRule rule) {
      if (clazz.isProgramClass()) {
        evaluateCheckDiscardRule(clazz.asProgramClass(), rule.asProguardCheckDiscardRule());
      } else {
        StringDiagnostic warning =
            new StringDiagnostic("The rule `" + rule + "` matches a class not in the program.");
        appView.reporter().warning(warning);
      }
    }

    private synchronized void evaluateCheckDiscardRule(
        DexProgramClass clazz, ProguardCheckDiscardRule rule) {
      if (rule.getMemberRules().isEmpty()) {
        evaluateCheckDiscardClassAndAllMembersRule(clazz, rule);
      } else if (clazz.hasFields() || clazz.hasMethods()) {
        markMatchingFields(clazz, rule.getMemberRules(), rule, null, null);
        markMatchingMethods(clazz, rule.getMemberRules(), rule, null, null);
        classesWithCheckDiscardedMembers.add(clazz);
      }
    }

    private void evaluateCheckDiscardClassAndAllMembersRule(
        DexProgramClass clazz, ProguardCheckDiscardRule rule) {
      setCheckDiscarded(clazz);
      clazz.forEachProgramMember(this::setCheckDiscarded);
      rule.markAsUsed();
    }

    private void evaluateCheckDiscardMemberRule(
        ProgramMember<?, ?> member, ProguardCheckDiscardRule rule) {
      setCheckDiscarded(member);
      rule.markAsUsed();
    }

    private void setCheckDiscarded(ProgramDefinition definition) {
      dependentMinimumKeepInfo
          .getOrCreateUnconditionalMinimumKeepInfo()
          .getOrCreateMinimumKeepInfoFor(definition.getReference())
          .setCheckDiscarded();
    }

    private void finalizeCheckDiscardedInformation() {
      MinimumKeepInfoCollection unconditionalKeepInfo =
          dependentMinimumKeepInfo.getUnconditionalMinimumKeepInfoOrDefault(
              MinimumKeepInfoCollection.empty());
      for (DexProgramClass clazz : classesWithCheckDiscardedMembers) {
        TraversalContinuation<?, ?> continueIfAllMembersMarkedAsCheckDiscarded =
            clazz.traverseProgramMembers(
                member ->
                    TraversalContinuation.continueIf(
                        unconditionalKeepInfo.hasMinimumKeepInfoThatMatches(
                            member.getReference(), Joiner::isCheckDiscardedEnabled)));
        if (continueIfAllMembersMarkedAsCheckDiscarded.shouldContinue()) {
          setCheckDiscarded(clazz);
        }
      }
    }

    @SuppressWarnings("ReferenceEquality")
    private void evaluateAssumeNoSideEffectsRule(
        Definition item, ProguardAssumeNoSideEffectRule context, ProguardMemberRule rule) {
      assert assumeInfoCollectionBuilder != null;
      if (!item.isMember()) {
        return;
      }
      DexClassAndMember<?, ?> member = item.asMember();
      if (member.getHolderType() == appView.dexItemFactory().objectType) {
        assert member.isMethod();
        reportAssumeNoSideEffectsWarningForJavaLangClassMethod(member.asMethod(), context);
      } else {
        DexType valueType =
            member.getReference().apply(DexField::getType, DexMethod::getReturnType);
        assumeInfoCollectionBuilder
            .applyIf(
                rule.hasReturnValue(),
                builder -> {
                  DynamicType assumeType = rule.getReturnValue().toDynamicType(appView, valueType);
                  AbstractValue assumeValue =
                      rule.getReturnValue().toAbstractValue(appView, valueType);
                  builder.meetAssumeType(member, assumeType).meetAssumeValue(member, assumeValue);
                  reportAssumeValuesWarningForMissingReturnField(context, rule, assumeValue);
                })
            .setIsSideEffectFree(member);
        if (member.isMethod()) {
          DexClassAndMethod method = member.asMethod();
          if (method.getDefinition().isClassInitializer()) {
            feedback.classInitializerMayBePostponed(method.getDefinition());
          }
        }
      }
      context.markAsUsed();
    }

    private void evaluateAssumeValuesRule(
        Definition item, ProguardAssumeValuesRule context, ProguardMemberRule rule) {
      assert assumeInfoCollectionBuilder != null;
      if (!item.isMember() || !rule.hasReturnValue()) {
        return;
      }
      DexClassAndMember<?, ?> member = item.asMember();
      DexType valueType = member.getReference().apply(DexField::getType, DexMethod::getReturnType);
      DynamicType assumeType = rule.getReturnValue().toDynamicType(appView, valueType);
      AbstractValue assumeValue = rule.getReturnValue().toAbstractValue(appView, valueType);
      assumeInfoCollectionBuilder
          .meetAssumeType(member, assumeType)
          .meetAssumeValue(member, assumeValue);
      reportAssumeValuesWarningForMissingReturnField(context, rule, assumeValue);
      context.markAsUsed();
    }

    private void evaluateCheckEnumUnboxedRule(DexClass clazz, CheckEnumUnboxedRule rule) {
      if (clazz.isProgramClass()) {
        if (clazz.isEnum()) {
          dependentMinimumKeepInfo
              .getOrCreateUnconditionalMinimumKeepInfo()
              .getOrCreateMinimumKeepInfoFor(clazz.getType())
              .asClassJoiner()
              .setCheckEnumUnboxed();
        } else {
          StringDiagnostic warning =
              new StringDiagnostic(
                  "The rule `"
                      + rule
                      + "` matches the non-enum class "
                      + clazz.getTypeName()
                      + ".");
          appView.reporter().warning(warning);
        }
      } else {
        StringDiagnostic warning =
            new StringDiagnostic(
                "The rule `"
                    + rule
                    + "` matches the non-program class "
                    + clazz.getTypeName()
                    + ".");
        appView.reporter().warning(warning);
      }
    }

    @SuppressWarnings("UnusedVariable")
    private void evaluateKeepRule(
        ProgramDefinition item,
        ProguardKeepRule context,
        DexProgramClass precondition,
        ProguardIfRule ifRule) {
      if (item.isField()) {
        ProgramField field = item.asProgramField();
        if (field.getOptimizationInfo().cannotBeKept()) {
          // We should only ever get here with if rules.
          assert ifRule != null;
          return;
        }
      } else if (item.isMethod()) {
        ProgramMethod method = item.asProgramMethod();
        if (method.getDefinition().isClassInitializer() && !options.debug) {
          // Don't keep class initializers.
          return;
        }
        if (method.getOptimizationInfo().cannotBeKept()) {
          // We should only ever get here with if rules.
          assert ifRule != null;
          return;
        }
        if (options.isGeneratingDex()
            && method.getReference().isLambdaDeserializeMethod(appView.dexItemFactory())) {
          // Don't keep lambda deserialization methods.
          return;
        }
      }

      // The reason for keeping should link to the conditional rule as a whole, if present.
      ProguardKeepRuleBase keepRule = ifRule != null ? ifRule : context;

      // The modifiers are specified on the actual keep rule (ie, the consequent/context).
      ProguardKeepRuleModifiers modifiers = context.getModifiers();
      if (modifiers.isBottom()) {
        // This rule is a no-op.
        return;
      }

      // In compatibility mode, for a match on instance members a referenced class becomes live.
      if (options.forceProguardCompatibility
          && !modifiers.allowsShrinking
          && precondition != null
          && precondition.isDexClass()) {
        if (!item.isClass() && !item.getAccessFlags().isStatic()) {
          dependentKeepClassCompatRule
              .computeIfAbsent(precondition.asDexClass().getType(), i -> new HashSet<>())
              .add(keepRule);
          context.markAsUsed();
        }
      }

      EnqueuerEvent preconditionEvent;
      if (precondition != null) {
        preconditionEvent =
            item.getAccessFlags().isStatic()
                    || (item.isMethod() && item.asMethod().getDefinition().isInstanceInitializer())
                ? new LiveClassEnqueuerEvent(precondition)
                : new InstantiatedClassEnqueuerEvent(precondition);
      } else {
        preconditionEvent = UnconditionalKeepInfoEvent.get();
      }

      if (isInterfaceMethodNeedingDesugaring(item)) {
        ProgramMethod method = item.asMethod();
        ProgramMethod companion =
            interfaceDesugaringSyntheticHelper.ensureMethodOfProgramCompanionClassStub(
                method, eventConsumer);
        // Add the method to the inverse map as tracing will now directly target the CC method.
        pendingMethodMoveInverse.put(companion, method);
        // Only shrinking and optimization are transferred for interface companion methods.
        if (appView.options().isOptimizationEnabled() && !modifiers.allowsOptimization) {
          dependentMinimumKeepInfo
              .getOrCreateMinimumKeepInfoFor(preconditionEvent, companion.getReference())
              .disallowOptimization();
          context.markAsUsed();
        }
        if (appView.options().isShrinking() && !modifiers.allowsShrinking) {
          dependentMinimumKeepInfo
              .getOrCreateMinimumKeepInfoFor(preconditionEvent, companion.getReference())
              .addRule(keepRule)
              .disallowShrinking();
          context.markAsUsed();
        }
        if (!item.asMethod().isDefaultMethod()) {
          // Static and private methods do not apply to the original item.
          return;
        }
      }

      if (appView.options().isAccessModificationEnabled() && !modifiers.allowsAccessModification) {
        dependentMinimumKeepInfo
            .getOrCreateMinimumKeepInfoFor(preconditionEvent, item.getReference())
            .disallowAccessModification();
        context.markAsUsed();
      }

      if (appView.options().isAnnotationRemovalEnabled() && !modifiers.allowsAnnotationRemoval) {
        dependentMinimumKeepInfo
            .getOrCreateMinimumKeepInfoFor(preconditionEvent, item.getReference())
            .disallowAnnotationRemoval();
        context.markAsUsed();
      }

      if (appView.options().isKeepAttributesSignatureEnabled()) {
        dependentMinimumKeepInfo
            .getOrCreateMinimumKeepInfoFor(preconditionEvent, item.getReference())
            .disallowSignatureRemoval();
        context.markAsUsed();
      }

      if (appView.options().isMinificationEnabled() && !modifiers.allowsObfuscation) {
        dependentMinimumKeepInfo
            .getOrCreateMinimumKeepInfoFor(preconditionEvent, item.getReference())
            .disallowMinification();
        context.markAsUsed();
      }

      if (appView.options().isRepackagingEnabled() && isRepackagingDisallowed(item, modifiers)) {
        dependentMinimumKeepInfo
            .getOrCreateMinimumKeepInfoFor(preconditionEvent, item.getReference())
            .applyIf(item.isProgramClass(), joiner -> joiner.asClassJoiner().disallowRepackaging());
        context.markAsUsed();
      }

      if (appView.options().isOptimizationEnabled() && !modifiers.allowsOptimization) {
        dependentMinimumKeepInfo
            .getOrCreateMinimumKeepInfoFor(preconditionEvent, item.getReference())
            .disallowOptimization();
        context.markAsUsed();
      }

      if ((appView.options().isShrinking() || isMainDexRootSetBuilder())
          && !modifiers.allowsShrinking) {
        KeepInfo.Joiner<?, ?, ?> minimumKeepInfoForItem =
            dependentMinimumKeepInfo
                .getOrCreateMinimumKeepInfoFor(preconditionEvent, item.getReference())
                .addRule(keepRule)
                .disallowShrinking();
        context.markAsUsed();
      }

      if (modifiers.includeDescriptorClasses) {
        includeDescriptorClasses(item, keepRule, preconditionEvent);
        context.markAsUsed();
      }

      if (item.isProgramClass()
          && appView.options().isKeepPermittedSubclassesEnabled()
          && !modifiers.allowsPermittedSubclassesRemoval) {
        dependentMinimumKeepInfo
            .getOrCreateMinimumKeepInfoFor(preconditionEvent, item.getReference())
            .asClassJoiner()
            .disallowPermittedSubclassesRemoval();
        context.markAsUsed();
      }
    }

    private boolean isRepackagingDisallowed(
        ProgramDefinition item, ProguardKeepRuleModifiers modifiers) {
      if (!modifiers.allowsRepackaging) {
        return true;
      }
      return RepackagingUtils.isPackageNameKept(item.getContextClass(), options);
    }

    private void evaluateIdentifierNameStringRule(
        Definition item, ProguardConfigurationRule context, ProguardIfRule ifRule) {
      // Main dex rules should not contain -identifiernamestring rules.
      assert !isMainDexRootSetBuilder();

      // We don't expect -identifiernamestring rules to be used as the subsequent of -if rules.
      assert ifRule == null;

      if (item.isClass()) {
        return;
      }

      if (item.isField()) {
        DexClassAndField field = item.asField();
        if (field.getDefinition().getOrComputeIsInlinableByJavaC(appView.dexItemFactory())) {
          Reporter reporter = appView.reporter();
          reporter.warning(
              new StringDiagnostic(
                  "Rule matches the static final field `"
                      + field.toSourceString()
                      + "`, which may have been inlined: "
                      + context.toString(),
                  context.getOrigin()));
        }
      }

      identifierNameStrings.add(item.asMember().getReference());
      context.markAsUsed();
    }

    private void evaluateMaximumRemovedAndroidLogLevelRule(
        Definition item, MaximumRemovedAndroidLogLevelRule context) {
      assert item.isProgramMethod();
      feedback.joinMaxRemovedAndroidLogLevel(
          item.asProgramMethod(), context.getMaxRemovedAndroidLogLevel());
      context.markAsUsed();
    }

    private boolean isInterfaceMethodNeedingDesugaring(ProgramDefinition item) {
      return !isMainDexRootSetBuilder()
          && options.isInterfaceMethodDesugaringEnabled()
          && item.isMethod()
          && item.asMethod().getHolder().isInterface()
          && !item.asMethod().getDefinition().isClassInitializer()
          && item.asMethod().getDefinition().hasCode();
    }

    @SuppressWarnings("ReferenceEquality")
    private void reportAssumeNoSideEffectsWarningForJavaLangClassMethod(
        DexClassAndMethod method, ProguardAssumeNoSideEffectRule context) {
      assert method.getHolderType() == options.dexItemFactory().objectType;
      OriginWithPosition key = new OriginWithPosition(context.getOrigin(), context.getPosition());
      assumeNoSideEffectsWarnings
          .computeIfAbsent(key, ignore -> new TreeSet<>(DexMethod::compareTo))
          .add(method.getReference());
    }

    @SuppressWarnings("ReferenceEquality")
    private boolean isWaitOrNotifyMethod(DexMethod method) {
      return method.name == options.itemFactory.waitMethodName
          || method.name == options.itemFactory.notifyMethodName
          || method.name == options.itemFactory.notifyAllMethodName;
    }

    private void generateAssumeNoSideEffectsWarnings() {
      if (appView.getDontWarnConfiguration().matches(options.itemFactory.objectType)) {
        // Don't report any warnings since we don't apply -assumenosideeffects rules to notify() or
        // wait() anyway.
        return;
      }

      assumeNoSideEffectsWarnings.forEach(
          (originWithPosition, methods) -> {
            boolean matchesWaitOrNotifyMethods =
                methods.stream().anyMatch(this::isWaitOrNotifyMethod);
            if (!matchesWaitOrNotifyMethods) {
              // We model the remaining methods on java.lang.Object, and thus there should be no
              // need
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

    private void reportAssumeValuesWarningForMissingReturnField(
        ProguardConfigurationRule context, ProguardMemberRule rule, AbstractValue assumeValue) {
      if (rule.hasReturnValue() && rule.getReturnValue().isField()) {
        assert assumeValue.isSingleFieldValue() || assumeValue.isUnknown();
        if (assumeValue.isUnknown()) {
          ProguardMemberRuleReturnValue returnValue = rule.getReturnValue();
          options.reporter.warning(
              new AssumeValuesMissingStaticFieldDiagnostic.Builder()
                  .setField(returnValue.getFieldHolder(), returnValue.getFieldName())
                  .setOrigin(context.getOrigin())
                  .setPosition(context.getPosition())
                  .build());
        }
      }
    }
  }

  abstract static class RootSetBase {

    final Set<DexMethod> neverInlineDueToSingleCaller;
    final Set<DexType> neverClassInline;
    private final DependentMinimumKeepInfoCollection dependentMinimumKeepInfo;
    final Map<DexType, Set<ProguardKeepRuleBase>> dependentKeepClassCompatRule;
    final List<DelayedRootSetActionItem> delayedRootSetActionItems;
    public final ProgramMethodMap<ProgramMethod> pendingMethodMoveInverse;

    RootSetBase(
        Set<DexMethod> neverInlineDueToSingleCaller,
        Set<DexType> neverClassInline,
        DependentMinimumKeepInfoCollection dependentMinimumKeepInfo,
        Map<DexType, Set<ProguardKeepRuleBase>> dependentKeepClassCompatRule,
        List<DelayedRootSetActionItem> delayedRootSetActionItems,
        ProgramMethodMap<ProgramMethod> pendingMethodMoveInverse) {
      this.neverInlineDueToSingleCaller = neverInlineDueToSingleCaller;
      this.neverClassInline = neverClassInline;
      this.dependentMinimumKeepInfo = dependentMinimumKeepInfo;
      this.dependentKeepClassCompatRule = dependentKeepClassCompatRule;
      this.delayedRootSetActionItems = delayedRootSetActionItems;
      this.pendingMethodMoveInverse = pendingMethodMoveInverse;
    }

    Set<ProguardKeepRuleBase> getDependentKeepClassCompatRule(DexType type) {
      return dependentKeepClassCompatRule.get(type);
    }

    public DependentMinimumKeepInfoCollection getDependentMinimumKeepInfo() {
      return dependentMinimumKeepInfo;
    }
  }

  public static class RootSet extends RootSetBase {

    public final ImmutableList<DexReference> reasonAsked;
    public final Set<DexMethod> alwaysInline;
    public final Set<DexMethod> bypassClinitForInlining;
    public final Set<DexMethod> whyAreYouNotInlining;
    public final Set<DexMethod> reprocess;
    public final Set<DexMethod> neverReprocess;
    public final PredicateSet<DexType> alwaysClassInline;
    public final Set<DexType> noUnusedInterfaceRemoval;
    public final Set<DexType> noVerticalClassMerging;
    public final Set<DexType> noHorizontalClassMerging;
    public final Set<DexMember<?, ?>> neverPropagateValue;
    public final Map<DexReference, ProguardMemberRule> mayHaveSideEffects;
    public final Set<DexMember<?, ?>> identifierNameStrings;
    public final Set<ProguardIfRule> ifRules;

    private RootSet(
        DependentMinimumKeepInfoCollection dependentMinimumKeepInfo,
        ImmutableList<DexReference> reasonAsked,
        Set<DexMethod> alwaysInline,
        Set<DexMethod> neverInlineDueToSingleCaller,
        Set<DexMethod> bypassClinitForInlining,
        Set<DexMethod> whyAreYouNotInlining,
        Set<DexMethod> reprocess,
        Set<DexMethod> neverReprocess,
        PredicateSet<DexType> alwaysClassInline,
        Set<DexType> neverClassInline,
        Set<DexType> noUnusedInterfaceRemoval,
        Set<DexType> noVerticalClassMerging,
        Set<DexType> noHorizontalClassMerging,
        Set<DexMember<?, ?>> neverPropagateValue,
        Map<DexReference, ProguardMemberRule> mayHaveSideEffects,
        Map<DexType, Set<ProguardKeepRuleBase>> dependentKeepClassCompatRule,
        Set<DexMember<?, ?>> identifierNameStrings,
        Set<ProguardIfRule> ifRules,
        List<DelayedRootSetActionItem> delayedRootSetActionItems,
        ProgramMethodMap<ProgramMethod> pendingMethodMoveInverse) {
      super(
          neverInlineDueToSingleCaller,
          neverClassInline,
          dependentMinimumKeepInfo,
          dependentKeepClassCompatRule,
          delayedRootSetActionItems,
          pendingMethodMoveInverse);
      this.reasonAsked = reasonAsked;
      this.alwaysInline = alwaysInline;
      this.bypassClinitForInlining = bypassClinitForInlining;
      this.whyAreYouNotInlining = whyAreYouNotInlining;
      this.reprocess = reprocess;
      this.neverReprocess = neverReprocess;
      this.alwaysClassInline = alwaysClassInline;
      this.noUnusedInterfaceRemoval = noUnusedInterfaceRemoval;
      this.noVerticalClassMerging = noVerticalClassMerging;
      this.noHorizontalClassMerging = noHorizontalClassMerging;
      this.neverPropagateValue = neverPropagateValue;
      this.mayHaveSideEffects = mayHaveSideEffects;
      this.identifierNameStrings = Collections.unmodifiableSet(identifierNameStrings);
      this.ifRules = Collections.unmodifiableSet(ifRules);
    }

    public void checkAllRulesAreUsed(InternalOptions options) {
      List<ProguardConfigurationRule> rules = options.getProguardConfiguration().getRules();
      if (rules == null) {
        return;
      }
      for (ProguardConfigurationRule rule : rules) {
        if (rule.isProguardIfRule()) {
          ProguardIfRule ifRule = rule.asProguardIfRule();
          Set<DexField> unorderedFields = ifRule.getAndClearInlinableFieldsMatchingPrecondition();
          if (!unorderedFields.isEmpty()) {
            List<DexField> fields = new ArrayList<>(unorderedFields);
            fields.sort(DexField::compareTo);
            options.reporter.warning(
                new InlinableStaticFinalFieldPreconditionDiagnostic(ifRule, fields));
            continue;
          }
        }
        if (!rule.isUsed()) {
          options.reporter.info(new UnusedProguardKeepRuleDiagnostic(rule));
        }
      }
    }

    void addConsequentRootSet(ConsequentRootSet consequentRootSet) {
      neverInlineDueToSingleCaller.addAll(consequentRootSet.neverInlineDueToSingleCaller);
      neverClassInline.addAll(consequentRootSet.neverClassInline);
      consequentRootSet.dependentKeepClassCompatRule.forEach(
          (type, rules) ->
              dependentKeepClassCompatRule
                  .computeIfAbsent(type, k -> new HashSet<>())
                  .addAll(rules));
      delayedRootSetActionItems.addAll(consequentRootSet.delayedRootSetActionItems);
    }

    public boolean isShrinkingDisallowedUnconditionally(
        ProgramDefinition definition, InternalOptions options) {
      if (!options.isShrinking()) {
        return true;
      }
      return getDependentMinimumKeepInfo()
          .getOrDefault(UnconditionalKeepInfoEvent.get(), MinimumKeepInfoCollection.empty())
          .hasMinimumKeepInfoThatMatches(
              definition.getReference(),
              minimumKeepInfoForDefinition -> !minimumKeepInfoForDefinition.isShrinkingAllowed());
    }

    public void pruneDeadItems(
        DexDefinitionSupplier definitions, Enqueuer enqueuer, Timing timing) {
      timing.begin("Prune keep info");
      getDependentMinimumKeepInfo().pruneDeadItems(definitions, enqueuer);
      timing.end();
      timing.begin("Prune others");
      pruneDeadReferences(noUnusedInterfaceRemoval, definitions, enqueuer);
      pruneDeadReferences(noVerticalClassMerging, definitions, enqueuer);
      pruneDeadReferences(noHorizontalClassMerging, definitions, enqueuer);
      pruneDeadReferences(alwaysInline, definitions, enqueuer);
      timing.end();
    }

    private static void pruneDeadReferences(
        Set<? extends DexReference> references,
        DexDefinitionSupplier definitions,
        Enqueuer enqueuer) {
      references.removeIf(
          reference -> {
            Definition definition =
                reference.apply(
                    definitions::definitionFor,
                    field ->
                        field.lookupMemberOnClass(definitions.definitionFor(field.getHolderType())),
                    method ->
                        method.lookupMemberOnClass(
                            definitions.definitionFor(method.getHolderType())));
            return definition == null || !enqueuer.isReachable(definition);
          });
    }

    public void pruneItems(PrunedItems prunedItems, Timing timing) {
      timing.begin("Prune RootSet");
      MinimumKeepInfoCollection unconditionalMinimumKeepInfo =
          getDependentMinimumKeepInfo().getUnconditionalMinimumKeepInfoOrDefault(null);
      if (unconditionalMinimumKeepInfo != null) {
        unconditionalMinimumKeepInfo.pruneItems(prunedItems);
        if (unconditionalMinimumKeepInfo.isEmpty()) {
          getDependentMinimumKeepInfo().remove(UnconditionalKeepInfoEvent.get());
        }
      }
      timing.end();
    }

    public RootSet rewrittenWithLens(GraphLens graphLens, Timing timing) {
      timing.begin("Rewrite RootSet");
      RootSet rewrittenRootSet;
      if (graphLens.isIdentityLens()) {
        rewrittenRootSet = this;
      } else {
        // TODO(b/164019179): If rules can now reference dead items. These should be pruned or
        //  rewritten
        ifRules.forEach(ProguardIfRule::canReferenceDeadTypes);
        rewrittenRootSet =
            new RootSet(
                getDependentMinimumKeepInfo().rewrittenWithLens(graphLens, timing),
                reasonAsked,
                alwaysInline,
                neverInlineDueToSingleCaller,
                bypassClinitForInlining,
                whyAreYouNotInlining,
                reprocess,
                neverReprocess,
                alwaysClassInline,
                neverClassInline,
                noUnusedInterfaceRemoval,
                noVerticalClassMerging,
                noHorizontalClassMerging,
                neverPropagateValue,
                mayHaveSideEffects,
                dependentKeepClassCompatRule,
                identifierNameStrings,
                ifRules,
                delayedRootSetActionItems,
                pendingMethodMoveInverse);
      }
      timing.end();
      return rewrittenRootSet;
    }

    void shouldNotBeMinified(ProgramDefinition definition) {
      getDependentMinimumKeepInfo()
          .getOrCreateUnconditionalMinimumKeepInfoFor(definition.getReference())
          .disallowMinification()
          .applyIf(
              definition.isProgramClass(), joiner -> joiner.asClassJoiner().disallowRepackaging());
    }

    public boolean verifyKeptFieldsAreAccessedAndLive(AppView<AppInfoWithLiveness> appView) {
      getDependentMinimumKeepInfo()
          .getUnconditionalMinimumKeepInfoOrDefault(MinimumKeepInfoCollection.empty())
          .forEachThatMatches(
              (reference, minimumKeepInfo) ->
                  reference.isDexField() && !minimumKeepInfo.isShrinkingAllowed(),
              (reference, minimumKeepInfo) -> {
                DexField fieldReference = reference.asDexField();
                DexProgramClass holder =
                    asProgramClassOrNull(appView.definitionForHolder(fieldReference));
                ProgramField field = fieldReference.lookupOnProgramClass(holder);
                if (field != null
                    && (field.getAccessFlags().isStatic()
                        || isKeptDirectlyOrIndirectly(field.getHolderType(), appView))) {
                  assert appView.appInfo().isFieldRead(field)
                      : "Expected kept field `" + fieldReference.toSourceString() + "` to be read";
                  assert appView.appInfo().isFieldWritten(field)
                      : "Expected kept field `"
                          + fieldReference.toSourceString()
                          + "` to be written";
                }
              });
      return true;
    }

    public boolean verifyKeptMethodsAreTargetedAndLive(AppView<AppInfoWithLiveness> appView) {
      getDependentMinimumKeepInfo()
          .getUnconditionalMinimumKeepInfoOrDefault(MinimumKeepInfoCollection.empty())
          .forEachThatMatches(
              (reference, minimumKeepInfo) ->
                  reference.isDexMethod() && !minimumKeepInfo.isShrinkingAllowed(),
              (reference, minimumKeepInfo) -> {
                DexMethod methodReference = reference.asDexMethod();
                assert appView.appInfo().isTargetedMethod(methodReference)
                    : "Expected kept method `" + reference.toSourceString() + "` to be targeted";
                DexEncodedMethod method =
                    appView.definitionForHolder(methodReference).lookupMethod(methodReference);
                if (!method.isAbstract()
                    && isKeptDirectlyOrIndirectly(methodReference.getHolderType(), appView)) {
                  assert appView.appInfo().isLiveMethod(methodReference)
                      : "Expected non-abstract kept method `"
                          + reference.toSourceString()
                          + "` to be live";
                }
              });
      return true;
    }

    public boolean verifyKeptTypesAreLive(AppView<AppInfoWithLiveness> appView) {
      getDependentMinimumKeepInfo()
          .getUnconditionalMinimumKeepInfoOrDefault(MinimumKeepInfoCollection.empty())
          .forEachThatMatches(
              (reference, minimumKeepInfo) ->
                  reference.isDexType() && !minimumKeepInfo.isShrinkingAllowed(),
              (reference, minimumKeepInfo) -> {
                DexType type = reference.asDexType();
                assert appView.appInfo().isLiveProgramType(type)
                    : "Expected kept type `" + type.toSourceString() + "` to be live";
              });
      return true;
    }

    private boolean isKeptDirectlyOrIndirectly(DexType type, AppView<AppInfoWithLiveness> appView) {
      DexProgramClass clazz = asProgramClassOrNull(appView.definitionFor(type));
      if (clazz == null) {
        return false;
      }
      if (isShrinkingDisallowedUnconditionally(clazz, appView.options())) {
        return true;
      }
      if (clazz.superType != null) {
        return isKeptDirectlyOrIndirectly(clazz.superType, appView);
      }
      return false;
    }

    public boolean verifyKeptItemsAreKept(AppView<? extends AppInfoWithClassHierarchy> appView) {
      AppInfoWithClassHierarchy appInfo = appView.appInfo();
      // Create a mapping from each required type to the set of required members on that type.
      Map<DexType, Set<DexMember<?, ?>>> requiredMembersPerType = new IdentityHashMap<>();
      getDependentMinimumKeepInfo()
          .getUnconditionalMinimumKeepInfoOrDefault(MinimumKeepInfoCollection.empty())
          .forEachThatMatches(
              (reference, minimumKeepInfo) -> !minimumKeepInfo.isShrinkingAllowed(),
              (reference, minimumKeepInfo) -> {
                if (reference.isDexType()) {
                  DexType type = reference.asDexType();
                  assert !appInfo.hasLiveness()
                          || appInfo.withLiveness().isPinnedWithDefinitionLookup(type)
                      : "Expected reference `" + type.toSourceString() + "` to be pinned";
                  requiredMembersPerType.computeIfAbsent(type, key -> Sets.newIdentityHashSet());
                } else {
                  DexMember<?, ?> member = reference.asDexMember();
                  assert !appInfo.hasLiveness()
                          || appInfo.withLiveness().isPinnedWithDefinitionLookup(member)
                      : "Expected reference `" + member.toSourceString() + "` to be pinned";
                  requiredMembersPerType
                      .computeIfAbsent(member.holder, key -> Sets.newIdentityHashSet())
                      .add(member);
                }
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
                      .map(DexEncodedField::getReference)
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
                      .map(DexEncodedMethod::getReference)
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
      builder.append("\nreasonAsked: " + reasonAsked.size());
      builder.append("\nidentifierNameStrings: " + identifierNameStrings.size());
      builder.append("\nifRules: " + ifRules.size());
      return builder.toString();
    }

    public static RootSetBuilder builder(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        Enqueuer enqueuer,
        SubtypingInfo subtypingInfo) {
      return new RootSetBuilder(appView, enqueuer, subtypingInfo);
    }

    public static RootSetBuilder builder(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        ProfileCollectionAdditions profileCollectionAdditions,
        SubtypingInfo subtypingInfo,
        Iterable<? extends ProguardConfigurationRule> rules) {
      return new RootSetBuilder(
          appView,
          RootSetBuilderEventConsumer.create(profileCollectionAdditions),
          subtypingInfo,
          rules);
    }
  }

  static class ConsequentRootSetBuilder extends RootSetBuilder {

    private final Enqueuer enqueuer;

    private ConsequentRootSetBuilder(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        Enqueuer enqueuer,
        SubtypingInfo subtypingInfo) {
      super(
          appView,
          RootSetBuilderEventConsumer.create(enqueuer.getProfileCollectionAdditions()),
          subtypingInfo,
          null);
      this.enqueuer = enqueuer;
    }

    @Override
    void handleMatchedAnnotation(AnnotationMatchResult annotationMatchResult) {
      if (enqueuer.getMode().isInitialTreeShaking()
          && annotationMatchResult.isConcreteAnnotationMatchResult()) {
        enqueuer.retainAnnotationForFinalTreeShaking(
            annotationMatchResult.asConcreteAnnotationMatchResult().getMatchedAnnotations());
      }
    }
  }

  // A partial RootSet that becomes live due to the enabled -if rule or the addition of interface
  // keep rules.
  public static class ConsequentRootSet extends RootSetBase {

    ConsequentRootSet(
        Set<DexMethod> neverInlineDueToSingleCaller,
        Set<DexType> neverClassInline,
        DependentMinimumKeepInfoCollection dependentMinimumKeepInfo,
        Map<DexType, Set<ProguardKeepRuleBase>> dependentKeepClassCompatRule,
        List<DelayedRootSetActionItem> delayedRootSetActionItems,
        ProgramMethodMap<ProgramMethod> pendingMethodMoveInverse) {
      super(
          neverInlineDueToSingleCaller,
          neverClassInline,
          dependentMinimumKeepInfo,
          dependentKeepClassCompatRule,
          delayedRootSetActionItems,
          pendingMethodMoveInverse);
    }

    static ConsequentRootSetBuilder builder(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        Enqueuer enqueuer,
        SubtypingInfo subtypingInfo) {
      return new ConsequentRootSetBuilder(appView, enqueuer, subtypingInfo);
    }
  }

  public static class MainDexRootSetBuilder extends RootSetBuilder {

    private MainDexRootSetBuilder(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        ProfileCollectionAdditions profileCollectionAdditions,
        SubtypingInfo subtypingInfo,
        Iterable<? extends ProguardConfigurationRule> rules) {
      super(
          appView,
          RootSetBuilderEventConsumer.create(profileCollectionAdditions),
          subtypingInfo,
          rules);
    }

    @Override
    boolean isMainDexRootSetBuilder() {
      return true;
    }

    @Override
    public MainDexRootSet build(ExecutorService executorService) throws ExecutionException {
      // Call the super builder to have if-tests calculated automatically.
      RootSet rootSet = super.build(executorService);
      return new MainDexRootSet(
          rootSet.getDependentMinimumKeepInfo(),
          rootSet.reasonAsked,
          rootSet.ifRules,
          rootSet.delayedRootSetActionItems);
    }
  }

  public static class MainDexRootSet extends RootSet {

    public MainDexRootSet(
        DependentMinimumKeepInfoCollection dependentMinimumKeepInfo,
        ImmutableList<DexReference> reasonAsked,
        Set<ProguardIfRule> ifRules,
        List<DelayedRootSetActionItem> delayedRootSetActionItems) {
      super(
          dependentMinimumKeepInfo,
          reasonAsked,
          Collections.emptySet(),
          Collections.emptySet(),
          Collections.emptySet(),
          Collections.emptySet(),
          Collections.emptySet(),
          Collections.emptySet(),
          PredicateSet.empty(),
          Collections.emptySet(),
          Collections.emptySet(),
          Collections.emptySet(),
          Collections.emptySet(),
          Collections.emptySet(),
          emptyMap(),
          emptyMap(),
          Collections.emptySet(),
          ifRules,
          delayedRootSetActionItems,
          ProgramMethodMap.empty());
    }

    public static MainDexRootSetBuilder builder(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        ProfileCollectionAdditions profileCollectionAdditions,
        SubtypingInfo subtypingInfo,
        Iterable<? extends ProguardConfigurationRule> rules) {
      return new MainDexRootSetBuilder(appView, profileCollectionAdditions, subtypingInfo, rules);
    }

    @Override
    void shouldNotBeMinified(ProgramDefinition definition) {
      // Do nothing.
    }

    @Override
    public MainDexRootSet rewrittenWithLens(GraphLens graphLens, Timing timing) {
      timing.begin("Rewrite MainDexRootSet");
      MainDexRootSet rewrittenMainDexRootSet;
      if (graphLens.isIdentityLens()) {
        rewrittenMainDexRootSet = this;
      } else {
        ImmutableList.Builder<DexReference> rewrittenReasonAsked = ImmutableList.builder();
        reasonAsked.forEach(
            reference ->
                rewriteAndApplyIfNotPrimitiveType(graphLens, reference, rewrittenReasonAsked::add));
        // TODO(b/164019179): If rules can now reference dead items. These should be pruned or
        //  rewritten
        ifRules.forEach(ProguardIfRule::canReferenceDeadTypes);
        // All delayed root set actions should have been processed at this point.
        assert delayedRootSetActionItems.isEmpty();
        rewrittenMainDexRootSet =
            new MainDexRootSet(
                getDependentMinimumKeepInfo().rewrittenWithLens(graphLens, timing),
                rewrittenReasonAsked.build(),
                ifRules,
                delayedRootSetActionItems);
      }
      timing.end();
      return rewrittenMainDexRootSet;
    }

    public MainDexRootSet withoutPrunedItems(PrunedItems prunedItems, Timing timing) {
      if (prunedItems.isEmpty()) {
        return this;
      }
      timing.begin("Prune MainDexRootSet");
      // TODO(b/164019179): If rules can now reference dead items. These should be pruned or
      //  rewritten.
      ifRules.forEach(ProguardIfRule::canReferenceDeadTypes);
      // All delayed root set actions should have been processed at this point.
      assert delayedRootSetActionItems.isEmpty();
      MainDexRootSet result =
          new MainDexRootSet(
              getDependentMinimumKeepInfo(), reasonAsked, ifRules, delayedRootSetActionItems);
      timing.end();
      return result;
    }
  }
}
