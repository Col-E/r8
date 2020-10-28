// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.staticizer;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;
import static com.android.tools.r8.utils.PredicateUtils.not;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodProcessingId;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.OneTimeMethodProcessor;
import com.android.tools.r8.ir.optimize.AssumeInserter;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.CodeRewriter;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.staticizer.ClassStaticizer.CandidateInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.collections.LongLivedProgramMethodSetBuilder;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.collections.SortedProgramMethodSet;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.collect.Streams;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.stream.Collectors;

// TODO(b/140766440): Use PostProcessor, instead of having its own post processing.
final class StaticizingProcessor {

  private final AppView<AppInfoWithLiveness> appView;
  private final ClassStaticizer classStaticizer;
  private final IRConverter converter;

  private final SortedProgramMethodSet methodsToReprocess = SortedProgramMethodSet.create();

  // Optimization order matters, hence a collection that preserves orderings.
  private final Map<DexEncodedMethod, ImmutableList.Builder<BiConsumer<IRCode, MethodProcessor>>>
      processingQueue = new IdentityHashMap<>();

  private final ProgramMethodSet referencingExtraMethods = ProgramMethodSet.create();
  private final Map<DexEncodedMethod, CandidateInfo> hostClassInits = new IdentityHashMap<>();
  private final ProgramMethodSet methodsToBeStaticized = ProgramMethodSet.create();
  private final Map<DexField, CandidateInfo> singletonFields = new IdentityHashMap<>();
  private final Map<DexMethod, CandidateInfo> singletonGetters = new IdentityHashMap<>();
  private final Map<DexType, DexType> candidateToHostMapping = new IdentityHashMap<>();

  StaticizingProcessor(
      AppView<AppInfoWithLiveness> appView,
      ClassStaticizer classStaticizer,
      IRConverter converter) {
    this.appView = appView;
    this.classStaticizer = classStaticizer;
    this.converter = converter;
  }

  final void run(OptimizationFeedback feedback, ExecutorService executorService)
      throws ExecutionException {
    // Filter out candidates based on the information we collected while examining methods.
    Map<CandidateInfo, ProgramMethodSet> materializedReferencedFromCollections =
        finalEligibilityCheck();

    // Prepare interim data.
    prepareCandidates(materializedReferencedFromCollections);

    // Enqueue all host class initializers (only remove instantiations).
    ProgramMethodSet hostClassInitMethods = ProgramMethodSet.create();
    hostClassInits
        .values()
        .forEach(
            candidateInfo ->
                hostClassInitMethods.add(candidateInfo.hostClass().getProgramClassInitializer()));
    enqueueMethodsWithCodeOptimizations(
        hostClassInitMethods,
        optimizations ->
            optimizations
                .add(this::removeCandidateInstantiation)
                .add(this::insertAssumeInstructions)
                .add(collectOptimizationInfo(feedback)));

    // Enqueue instance methods to be staticized (only remove references to 'this'). Intentionally
    // not collecting optimization info for these methods, since they will be reprocessed again
    // below once staticized.
    enqueueMethodsWithCodeOptimizations(
        methodsToBeStaticized, optimizations -> optimizations.add(this::removeReferencesToThis));

    // Process queued methods with associated optimizations
    processMethodsConcurrently(feedback, executorService);

    // TODO(b/140767158): Merge the remaining part below.
    // Convert instance methods into static methods with an extra parameter.
    ProgramMethodSet methods = staticizeMethodSymbols();

    // Process all other methods that may reference singleton fields and call methods on them.
    // (Note that we exclude the former instance methods, but include new static methods created as
    // a result of staticizing.)
    methods.addAll(referencingExtraMethods);
    methods.addAll(hostClassInitMethods);
    enqueueMethodsWithCodeOptimizations(
        methods,
        optimizations ->
            optimizations
                .add(this::rewriteReferences)
                .add(this::insertAssumeInstructions)
                .add(collectOptimizationInfo(feedback)));

    // Process queued methods with associated optimizations
    processMethodsConcurrently(feedback, executorService);

    // Clear all candidate information now that all candidates have been staticized.
    classStaticizer.candidates.clear();
  }

  private Map<CandidateInfo, ProgramMethodSet> finalEligibilityCheck() {
    Map<CandidateInfo, ProgramMethodSet> materializedReferencedFromCollections =
        new IdentityHashMap<>();
    Set<Phi> visited = Sets.newIdentityHashSet();
    Set<Phi> trivialPhis = Sets.newIdentityHashSet();
    Iterator<Entry<DexType, CandidateInfo>> it = classStaticizer.candidates.entrySet().iterator();
    while (it.hasNext()) {
      Entry<DexType, CandidateInfo> entry = it.next();
      DexType candidateType = entry.getKey();
      CandidateInfo info = entry.getValue();
      DexProgramClass candidateClass = info.candidate;
      DexType candidateHostType = info.hostType();
      DexEncodedMethod constructorUsed = info.constructor.get();

      int instancesCreated = info.instancesCreated.get();
      assert instancesCreated == info.fieldWrites.get();
      assert instancesCreated <= 1;
      assert (instancesCreated == 0) == (constructorUsed == null);

      // CHECK: One instance, one singleton field, known constructor
      if (instancesCreated == 0) {
        // Give up on the candidate, if there are any reads from instance
        // field the user should read null.
        it.remove();
        continue;
      }

      // CHECK: instance initializer used to create an instance is trivial.
      // NOTE: Along with requirement that candidate does not have instance
      // fields this should guarantee that the constructor is empty.
      assert candidateClass.instanceFields().size() == 0;
      assert constructorUsed.isProcessed();
      if (constructorUsed.getOptimizationInfo().mayHaveSideEffects()) {
        it.remove();
        continue;
      }

      // CHECK: class initializer should only be present if candidate itself is its own host.
      DexEncodedMethod classInitializer = candidateClass.getClassInitializer();
      assert classInitializer != null || candidateType != candidateHostType;
      if (classInitializer != null && candidateType != candidateHostType) {
        it.remove();
        continue;
      }

      // CHECK: no abstract or native instance methods.
      if (Streams.stream(candidateClass.methods()).anyMatch(
          method -> !method.isStatic() && (method.shouldNotHaveCode()))) {
        it.remove();
        continue;
      }

      // CHECK: references to 'this' in instance methods are fixable.
      TraversalContinuation fixableThisPointer =
          candidateClass.traverseProgramMethods(
              method -> {
                IRCode code = method.buildIR(appView);
                assert code != null;
                Value thisValue = code.getThis();
                assert thisValue != null;
                visited.clear();
                trivialPhis.clear();
                boolean onlyHasTrivialPhis =
                    testAndCollectPhisComposedOfThis(
                        visited, thisValue.uniquePhiUsers(), thisValue, trivialPhis);
                if (thisValue.hasPhiUsers() && !onlyHasTrivialPhis) {
                  return TraversalContinuation.BREAK;
                }
                return TraversalContinuation.CONTINUE;
              },
              definition -> !definition.isStatic() && !definition.isInstanceInitializer());
      if (fixableThisPointer.shouldBreak()) {
        it.remove();
        continue;
      }

      ProgramMethodSet referencedFrom;
      if (classStaticizer.referencedFrom.containsKey(info)) {
        LongLivedProgramMethodSetBuilder<?> referencedFromBuilder =
            classStaticizer.referencedFrom.remove(info);
        assert referencedFromBuilder != null;
        referencedFrom = referencedFromBuilder.build(appView);
        materializedReferencedFromCollections.put(info, referencedFrom);
      } else {
        referencedFrom = ProgramMethodSet.empty();
      }

      // CHECK: references to field read usages are fixable.
      boolean fixableFieldReads = true;
      for (ProgramMethod method : referencedFrom) {
        IRCode code = method.buildIR(appView);
        assert code != null;
        List<Instruction> singletonUsers =
            Streams.stream(code.instructionIterator())
                .filter(
                    instruction -> {
                      if (instruction.isStaticGet()
                          && instruction.asStaticGet().getField() == info.singletonField.field) {
                        return true;
                      }
                      DexEncodedMethod getter = info.getter.get();
                      return getter != null
                          && instruction.isInvokeStatic()
                          && instruction.asInvokeStatic().getInvokedMethod() == getter.method;
                    })
                .collect(Collectors.toList());
        boolean fixableFieldReadsPerUsage = true;
        for (Instruction user : singletonUsers) {
          if (user.outValue() == null) {
            continue;
          }
          Value dest = user.outValue();
          visited.clear();
          trivialPhis.clear();
          assert user.isInvokeStatic() || user.isStaticGet();
          DexMember member =
              user.isStaticGet()
                  ? user.asStaticGet().getField()
                  : user.asInvokeStatic().getInvokedMethod();
          boolean onlyHasTrivialPhis =
              testAndCollectPhisComposedOfSameMember(
                  visited, dest.uniquePhiUsers(), member, trivialPhis);
          if (dest.hasPhiUsers() && !onlyHasTrivialPhis) {
            fixableFieldReadsPerUsage = false;
            break;
          }
        }
        if (!fixableFieldReadsPerUsage) {
          fixableFieldReads = false;
          break;
        }
      }
      if (!fixableFieldReads) {
        it.remove();
        continue;
      }
    }
    return materializedReferencedFromCollections;
  }

  private void prepareCandidates(
      Map<CandidateInfo, ProgramMethodSet> materializedReferencedFromCollections) {
    Set<DexEncodedMethod> removedInstanceMethods = Sets.newIdentityHashSet();

    for (CandidateInfo candidate : classStaticizer.candidates.values()) {
      DexProgramClass candidateClass = candidate.candidate;
      // Host class initializer
      DexClass hostClass = candidate.hostClass();
      DexEncodedMethod hostClassInitializer = hostClass.getClassInitializer();
      assert hostClassInitializer != null;
      CandidateInfo previous = hostClassInits.put(hostClassInitializer, candidate);
      assert previous == null;

      // Collect instance methods to be staticized.
      candidateClass.forEachProgramMethodMatching(
          definition -> {
            if (!definition.isStatic()) {
              removedInstanceMethods.add(definition);
              return !definition.isInstanceInitializer();
            }
            return false;
          },
          methodsToBeStaticized::add);
      singletonFields.put(candidate.singletonField.field, candidate);
      DexEncodedMethod getter = candidate.getter.get();
      if (getter != null) {
        singletonGetters.put(getter.method, candidate);
      }
      ProgramMethodSet referencedFrom =
          materializedReferencedFromCollections.getOrDefault(candidate, ProgramMethodSet.empty());
      assert validMethods(referencedFrom);
      referencingExtraMethods.addAll(referencedFrom);
    }

    removedInstanceMethods.forEach(referencingExtraMethods::remove);
  }

  private boolean validMethods(ProgramMethodSet referencedFrom) {
    for (ProgramMethod method : referencedFrom) {
      DexClass clazz = appView.definitionForHolder(method.getReference());
      assert clazz != null;
      assert clazz.lookupMethod(method.getReference()) == method.getDefinition();
    }
    return true;
  }

  private void enqueueMethodsWithCodeOptimizations(
      Iterable<ProgramMethod> methods,
      Consumer<ImmutableList.Builder<BiConsumer<IRCode, MethodProcessor>>> extension) {
    for (ProgramMethod method : methods) {
      methodsToReprocess.add(method);
      extension.accept(
          processingQueue.computeIfAbsent(
              method.getDefinition(), ignore -> ImmutableList.builder()));
    }
  }

  /**
   * Processes the given methods concurrently using the given strategy.
   *
   * <p>Note that, when the strategy {@link #rewriteReferences(IRCode, MethodProcessor)} is being
   * applied, it is important that we never inline a method from `methods` which has still not been
   * reprocessed. This could lead to broken code, because the strategy that rewrites the broken
   * references is applied *before* inlining (because the broken references in the inlinee are never
   * rewritten). We currently avoid this situation by processing all the methods concurrently
   * (inlining of a method that is processed concurrently is not allowed).
   */
  private void processMethodsConcurrently(
      OptimizationFeedback feedback, ExecutorService executorService) throws ExecutionException {
    OneTimeMethodProcessor methodProcessor =
        OneTimeMethodProcessor.create(methodsToReprocess, appView);
    methodProcessor.forEachWave(
        (method, methodProcessingId) ->
            forEachMethod(
                method,
                processingQueue.get(method.getDefinition()).build(),
                feedback,
                methodProcessor,
                methodProcessingId),
        executorService);
    // TODO(b/140767158): No need to clear if we can do every thing in one go.
    methodsToReprocess.clear();
    processingQueue.clear();
  }

  // TODO(b/140766440): Should be part or variant of PostProcessor.
  private void forEachMethod(
      ProgramMethod method,
      Collection<BiConsumer<IRCode, MethodProcessor>> codeOptimizations,
      OptimizationFeedback feedback,
      OneTimeMethodProcessor methodProcessor,
      MethodProcessingId methodProcessingId) {
    IRCode code = method.buildIR(appView);
    codeOptimizations.forEach(codeOptimization -> codeOptimization.accept(code, methodProcessor));
    CodeRewriter.removeAssumeInstructions(appView, code);
    converter.removeDeadCodeAndFinalizeIR(method, code, feedback, Timing.empty());
  }

  private void insertAssumeInstructions(IRCode code, MethodProcessor methodProcessor) {
    AssumeInserter assumeInserter = converter.assumeInserter;
    if (assumeInserter != null) {
      assumeInserter.insertAssumeInstructions(code, Timing.empty());
    }
  }

  private BiConsumer<IRCode, MethodProcessor> collectOptimizationInfo(
      OptimizationFeedback feedback) {
    return (code, methodProcessor) ->
        converter.collectOptimizationInfo(
            code.context(),
            code,
            ClassInitializerDefaultsResult.empty(),
            feedback,
            methodProcessor,
            Timing.empty());
  }

  private void removeCandidateInstantiation(IRCode code, MethodProcessor methodProcessor) {
    CandidateInfo candidateInfo = hostClassInits.get(code.method());
    assert candidateInfo != null;

    // Find and remove instantiation and its users.
    for (NewInstance newInstance : code.<NewInstance>instructions(Instruction::isNewInstance)) {
      if (newInstance.clazz == candidateInfo.candidate.type) {
        // Remove all usages
        // NOTE: requiring (a) the instance initializer to be trivial, (b) not allowing
        //       candidates with instance fields and (c) requiring candidate to directly
        //       extend java.lang.Object guarantees that the constructor is actually
        //       empty and does not need to be inlined.
        assert candidateInfo.candidate.superType == factory().objectType;
        assert candidateInfo.candidate.instanceFields().size() == 0;

        Value singletonValue = newInstance.outValue();
        assert singletonValue != null;

        InvokeDirect uniqueConstructorInvoke =
            newInstance.getUniqueConstructorInvoke(appView.dexItemFactory());
        assert uniqueConstructorInvoke != null;
        uniqueConstructorInvoke.removeOrReplaceByDebugLocalRead(code);

        StaticPut uniqueStaticPut = null;
        for (Instruction user : singletonValue.uniqueUsers()) {
          if (user.isStaticPut()) {
            assert uniqueStaticPut == null;
            uniqueStaticPut = user.asStaticPut();
          }
        }
        assert uniqueStaticPut != null;
        uniqueStaticPut.removeOrReplaceByDebugLocalRead(code);

        if (newInstance.outValue().hasAnyUsers()) {
          TypeElement type = TypeElement.fromDexType(newInstance.clazz, maybeNull(), appView);
          newInstance.replace(
              new StaticGet(code.createValue(type), candidateInfo.singletonField.field), code);
        } else {
          newInstance.removeOrReplaceByDebugLocalRead(code);
        }
        return;
      }
    }

    assert false : "Must always be able to find and remove the instantiation";
  }

  private void removeReferencesToThis(IRCode code, MethodProcessor methodProcessor) {
    fixupStaticizedThisUsers(code, code.getThis());
  }

  private void rewriteReferences(IRCode code, MethodProcessor methodProcessor) {
    // Fetch all instructions that reference singletons to avoid concurrent modifications to the
    // instruction list that can arise from doing it directly in the iterator.
    List<Instruction> singletonUsers =
        Streams.stream(code.instructionIterator())
            .filter(
                instruction ->
                    (instruction.isStaticGet()
                            && singletonFields.containsKey(
                                instruction.asFieldInstruction().getField()))
                        || (instruction.isInvokeStatic()
                            && singletonGetters.containsKey(
                                instruction.asInvokeStatic().getInvokedMethod())))
            .collect(Collectors.toList());
    for (Instruction singletonUser : singletonUsers) {
      CandidateInfo candidateInfo;
      DexMember member;
      if (singletonUser.isStaticGet()) {
        candidateInfo = singletonFields.get(singletonUser.asStaticGet().getField());
        member = singletonUser.asStaticGet().getField();
      } else {
        assert singletonUser.isInvokeStatic();
        candidateInfo = singletonGetters.get(singletonUser.asInvokeStatic().getInvokedMethod());
        member = singletonUser.asInvokeStatic().getInvokedMethod();
      }
      Value value = singletonUser.outValue();
      if (value != null) {
        fixupStaticizedFieldUsers(code, value, member);
      }
      if (!candidateInfo.preserveRead.get()) {
        singletonUser.removeOrReplaceByDebugLocalRead(code);
      }
    }
    if (!candidateToHostMapping.isEmpty()) {
      remapMovedCandidates(code);
    }
  }

  private boolean testAndCollectPhisComposedOfThis(
      Set<Phi> visited, Set<Phi> phisToCheck, Value thisValue, Set<Phi> trivialPhis) {
    for (Phi phi : phisToCheck) {
      if (!visited.add(phi)) {
        continue;
      }
      Set<Phi> chainedPhis = Sets.newIdentityHashSet();
      for (Value operand : phi.getOperands()) {
        Value v = operand.getAliasedValue();
        if (v.isPhi()) {
          chainedPhis.add(operand.asPhi());
        } else {
          if (v != thisValue) {
            return false;
          }
        }
      }
      if (!chainedPhis.isEmpty()) {
        if (!testAndCollectPhisComposedOfThis(visited, chainedPhis, thisValue, trivialPhis)) {
          return false;
        }
      }
      trivialPhis.add(phi);
    }
    return true;
  }

  // Fixup `this` usages: rewrites all method calls so that they point to static methods.
  private void fixupStaticizedThisUsers(IRCode code, Value thisValue) {
    assert thisValue != null && thisValue == thisValue.getAliasedValue();
    // Depending on other optimizations, e.g., inlining, `this` can be flown to phis.
    Set<Phi> trivialPhis = Sets.newIdentityHashSet();
    boolean onlyHasTrivialPhis = testAndCollectPhisComposedOfThis(
        Sets.newIdentityHashSet(), thisValue.uniquePhiUsers(), thisValue, trivialPhis);
    assert !thisValue.hasPhiUsers() || onlyHasTrivialPhis;
    assert trivialPhis.isEmpty() || onlyHasTrivialPhis;

    Set<Instruction> users = SetUtils.newIdentityHashSet(thisValue.aliasedUsers());
    // If that is the case, method calls we want to fix up include users of those phis.
    for (Phi phi : trivialPhis) {
      users.addAll(phi.aliasedUsers());
    }

    fixupStaticizedValueUsers(code, users);

    // We can't directly use Phi#removeTrivialPhi because they still refer to different operands.
    trivialPhis.forEach(Phi::removeDeadPhi);

    // No matter what, number of phi users should be zero too.
    assert !thisValue.hasUsers() && !thisValue.hasPhiUsers();
  }

  // Re-processing finalized code may create slightly different IR code than what the examining
  // phase has seen. For example,
  //
  //  b1:
  //    s1 <- static-get singleton
  //    ...
  //    invoke-virtual { s1, ... } mtd1
  //    goto Exit
  //  b2:
  //    s2 <- invoke-static getter()
  //    ...
  //    invoke-virtual { s2, ... } mtd1
  //    goto Exit
  //  ...
  //  Exit: ...
  //
  // ~>
  //
  //  b1:
  //    s1 <- static-get singleton
  //    ...
  //    goto Exit
  //  b2:
  //    s2 <- invoke-static getter()
  //    ...
  //    goto Exit
  //  Exit:
  //    sp <- phi(s1, s2)
  //    invoke-virtual { sp, ... } mtd1
  //    ...
  //
  // From staticizer's viewpoint, `sp` is trivial in the sense that it is composed of values that
  // refer to the same singleton field. If so, we can safely relax the assertion; remove uses of
  // field reads; remove quasi-trivial phis; and then remove original field reads.
  private boolean testAndCollectPhisComposedOfSameMember(
      Set<Phi> visited, Set<Phi> phisToCheck, DexMember dexMember, Set<Phi> trivialPhis) {
    for (Phi phi : phisToCheck) {
      if (!visited.add(phi)) {
        continue;
      }
      Set<Phi> chainedPhis = Sets.newIdentityHashSet();
      for (Value operand : phi.getOperands()) {
        Value v = operand.getAliasedValue();
        if (v.isPhi()) {
          chainedPhis.add(operand.asPhi());
        } else {
          Instruction definition = v.definition;
          if (!definition.isStaticGet() && !definition.isInvokeStatic()) {
            return false;
          }
          if (definition.isStaticGet() && definition.asStaticGet().getField() != dexMember) {
            return false;
          } else if (definition.isInvokeStatic()
              && definition.asInvokeStatic().getInvokedMethod() != dexMember) {
            return false;
          }
        }
      }
      chainedPhis.addAll(phi.uniquePhiUsers());
      if (!chainedPhis.isEmpty()) {
        if (!testAndCollectPhisComposedOfSameMember(visited, chainedPhis, dexMember, trivialPhis)) {
          return false;
        }
      }
      trivialPhis.add(phi);
    }
    return true;
  }

  // Fixup field read usages. Same as {@link #fixupStaticizedThisUsers} except this one determines
  // quasi-trivial phis, based on the original field.
  private void fixupStaticizedFieldUsers(IRCode code, Value dest, DexMember member) {
    assert dest != null;
    // During the examine phase, field reads with any phi users have been invalidated, hence zero.
    // However, it may be not true if re-processing introduces phis after optimizing common suffix.
    Set<Phi> trivialPhis = Sets.newIdentityHashSet();
    boolean onlyHasTrivialPhis =
        testAndCollectPhisComposedOfSameMember(
            Sets.newIdentityHashSet(), dest.uniquePhiUsers(), member, trivialPhis);
    assert !dest.hasPhiUsers() || onlyHasTrivialPhis;
    assert trivialPhis.isEmpty() || onlyHasTrivialPhis;

    Set<Instruction> users = SetUtils.newIdentityHashSet(dest.aliasedUsers());
    // If that is the case, method calls we want to fix up include users of those phis.
    for (Phi phi : trivialPhis) {
      users.addAll(phi.aliasedUsers());
    }

    fixupStaticizedValueUsers(code, users);

    // We can't directly use Phi#removeTrivialPhi because they still refer to different operands.
    trivialPhis.forEach(Phi::removeDeadPhi);

    // No matter what, number of phi users should be zero too.
    assert !dest.hasUsers() && !dest.hasPhiUsers();
  }

  private void fixupStaticizedValueUsers(IRCode code, Set<Instruction> users) {
    for (Instruction user : users) {
      if (user.isAssume()) {
        continue;
      }
      assert user.isInvokeVirtual() || user.isInvokeDirect();
      InvokeMethodWithReceiver invoke = user.asInvokeMethodWithReceiver();
      Value newValue = null;
      Value outValue = invoke.outValue();
      if (outValue != null) {
        newValue = code.createValue(outValue.getType());
        DebugLocalInfo localInfo = outValue.getLocalInfo();
        if (localInfo != null) {
          newValue.setLocalInfo(localInfo);
        }
      }
      List<Value> args = invoke.inValues();
      invoke.replace(
          new InvokeStatic(invoke.getInvokedMethod(), newValue, args.subList(1, args.size())),
          code);
    }
  }

  private void remapMovedCandidates(IRCode code) {
    InstructionListIterator it = code.instructionListIterator();
    while (it.hasNext()) {
      Instruction instruction = it.next();

      if (instruction.isStaticGet()) {
        StaticGet staticGet = instruction.asStaticGet();
        DexField field = mapFieldIfMoved(staticGet.getField());
        if (field != staticGet.getField()) {
          Value outValue = staticGet.dest();
          assert outValue != null;
          it.replaceCurrentInstruction(
              new StaticGet(
                  code.createValue(
                      TypeElement.fromDexType(
                          field.type, outValue.getType().nullability(), appView),
                      outValue.getLocalInfo()),
                  field));
        }
        continue;
      }

      if (instruction.isStaticPut()) {
        StaticPut staticPut = instruction.asStaticPut();
        DexField field = mapFieldIfMoved(staticPut.getField());
        if (field != staticPut.getField()) {
          it.replaceCurrentInstruction(new StaticPut(staticPut.value(), field));
        }
        continue;
      }

      if (instruction.isInvokeStatic()) {
        InvokeStatic invoke = instruction.asInvokeStatic();
        DexMethod method = invoke.getInvokedMethod();
        DexType hostType = candidateToHostMapping.get(method.holder);
        if (hostType != null) {
          DexMethod newMethod = factory().createMethod(hostType, method.proto, method.name);
          Value outValue = invoke.outValue();
          DexType returnType = method.proto.returnType;
          Value newOutValue =
              returnType.isVoidType() || outValue == null
                  ? null
                  : code.createValue(
                      TypeElement.fromDexType(
                          returnType, outValue.getType().nullability(), appView),
                      outValue.getLocalInfo());
          it.replaceCurrentInstruction(new InvokeStatic(newMethod, newOutValue, invoke.inValues()));
        }
        continue;
      }
    }
  }

  private DexField mapFieldIfMoved(DexField field) {
    DexType hostType = candidateToHostMapping.get(field.holder);
    if (hostType != null) {
      field = factory().createField(hostType, field.type, field.name);
    }
    hostType = candidateToHostMapping.get(field.type);
    if (hostType != null) {
      field = factory().createField(field.holder, hostType, field.name);
    }
    return field;
  }

  private ProgramMethodSet staticizeMethodSymbols() {
    BiMap<DexMethod, DexMethod> methodMapping = HashBiMap.create();
    BiMap<DexField, DexField> fieldMapping = HashBiMap.create();

    ProgramMethodSet staticizedMethods = ProgramMethodSet.create();
    for (CandidateInfo candidate : classStaticizer.candidates.values()) {
      DexProgramClass candidateClass = candidate.candidate;

      // Move instance methods into static ones.
      List<DexEncodedMethod> newDirectMethods = new ArrayList<>();
      for (DexEncodedMethod method : candidateClass.methods()) {
        if (method.isStatic()) {
          newDirectMethods.add(method);
        } else if (!factory().isConstructor(method.method)) {
          DexEncodedMethod staticizedMethod = method.toStaticMethodWithoutThis();
          newDirectMethods.add(staticizedMethod);
          staticizedMethods.createAndAdd(candidateClass, staticizedMethod);
          methodMapping.put(method.method, staticizedMethod.method);
        }
      }
      candidateClass.setVirtualMethods(DexEncodedMethod.EMPTY_ARRAY);
      candidateClass.setDirectMethods(newDirectMethods.toArray(DexEncodedMethod.EMPTY_ARRAY));

      // Consider moving static members from candidate into host.
      DexType hostType = candidate.hostType();
      if (candidateClass.type != hostType) {
        DexProgramClass hostClass = asProgramClassOrNull(appView.definitionFor(hostType));
        assert hostClass != null;
        if (!classMembersConflict(candidateClass, hostClass)
            && !hasMembersNotStaticized(candidateClass, staticizedMethods)) {
          // Move all members of the candidate class into host class.
          moveMembersIntoHost(staticizedMethods,
              candidateClass, hostType, hostClass, methodMapping, fieldMapping);
        }
      }
    }

    if (!methodMapping.isEmpty() || !fieldMapping.isEmpty()) {
      appView.setGraphLens(new ClassStaticizerGraphLens(appView, fieldMapping, methodMapping));
    }
    return staticizedMethods;
  }

  private boolean classMembersConflict(DexClass a, DexClass b) {
    assert Streams.stream(a.methods()).allMatch(DexEncodedMethod::isStatic);
    assert a.instanceFields().size() == 0;
    return a.staticFields().stream().anyMatch(fld -> b.lookupField(fld.field) != null)
        || Streams.stream(a.methods()).anyMatch(method -> b.lookupMethod(method.method) != null);
  }

  private boolean hasMembersNotStaticized(
      DexProgramClass candidateClass, ProgramMethodSet staticizedMethods) {
    // TODO(b/159174309): Refine the analysis to allow for fields.
    if (candidateClass.hasFields()) {
      return true;
    }
    // TODO(b/158018192): Activate again when picking up all references.
    return candidateClass.methods(not(staticizedMethods::contains)).iterator().hasNext();
  }

  private void moveMembersIntoHost(
      ProgramMethodSet staticizedMethods,
      DexProgramClass candidateClass,
      DexType hostType,
      DexProgramClass hostClass,
      BiMap<DexMethod, DexMethod> methodMapping,
      BiMap<DexField, DexField> fieldMapping) {
    candidateToHostMapping.put(candidateClass.type, hostType);

    // Process static fields.
    int numOfHostStaticFields = hostClass.staticFields().size();
    DexEncodedField[] newFields =
        candidateClass.staticFields().size() > 0
            ? new DexEncodedField[numOfHostStaticFields + candidateClass.staticFields().size()]
            : new DexEncodedField[numOfHostStaticFields];
    List<DexEncodedField> oldFields = hostClass.staticFields();
    for (int i = 0; i < oldFields.size(); i++) {
      DexEncodedField field = oldFields.get(i);
      DexField newField = mapCandidateField(field.field, candidateClass.type, hostType);
      if (newField != field.field) {
        newFields[i] = field.toTypeSubstitutedField(newField);
        fieldMapping.put(field.field, newField);
      } else {
        newFields[i] = field;
      }
    }
    if (candidateClass.staticFields().size() > 0) {
      List<DexEncodedField> extraFields = candidateClass.staticFields();
      for (int i = 0; i < extraFields.size(); i++) {
        DexEncodedField field = extraFields.get(i);
        DexField newField = mapCandidateField(field.field, candidateClass.type, hostType);
        if (newField != field.field) {
          newFields[numOfHostStaticFields + i] = field.toTypeSubstitutedField(newField);
          fieldMapping.put(field.field, newField);
        } else {
          newFields[numOfHostStaticFields + i] = field;
        }
      }
    }
    hostClass.setStaticFields(newFields);

    // Process static methods.
    if (!candidateClass.getMethodCollection().hasDirectMethods()) {
      return;
    }

    Iterable<DexEncodedMethod> extraMethods = candidateClass.directMethods();
    List<DexEncodedMethod> newMethods =
        new ArrayList<>(candidateClass.getMethodCollection().numberOfDirectMethods());
    for (DexEncodedMethod method : extraMethods) {
      DexEncodedMethod newMethod =
          method.toTypeSubstitutedMethod(
              factory().createMethod(hostType, method.method.proto, method.method.name));
      newMethods.add(newMethod);
      // If the old method from the candidate class has been staticized,
      if (staticizedMethods.remove(method)) {
        // Properly update staticized methods to reprocess, i.e., add the corresponding one that
        // has just been migrated to the host class.
        staticizedMethods.createAndAdd(hostClass, newMethod);
      }
      DexMethod originalMethod = methodMapping.inverse().get(method.method);
      if (originalMethod == null) {
        methodMapping.put(method.method, newMethod.method);
      } else {
        methodMapping.put(originalMethod, newMethod.method);
      }
    }
    hostClass.addDirectMethods(newMethods);
  }

  private DexField mapCandidateField(DexField field, DexType candidateType, DexType hostType) {
    return field.holder != candidateType && field.type != candidateType ? field
        : factory().createField(
            field.holder == candidateType ? hostType : field.holder,
            field.type == candidateType ? hostType : field.type,
            field.name);
  }

  private DexItemFactory factory() {
    return appView.dexItemFactory();
  }
}
