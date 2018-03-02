// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.lambda;

import com.android.tools.r8.ApiLevelException;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.conversion.CallSiteInformation;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.Outliner;
import com.android.tools.r8.ir.optimize.lambda.CodeProcessor.Strategy;
import com.android.tools.r8.ir.optimize.lambda.LambdaGroup.LambdaInfo;
import com.android.tools.r8.ir.optimize.lambda.LambdaGroup.LambdaStructureError;
import com.android.tools.r8.ir.optimize.lambda.kstyle.KStyleLambdaGroupIdFactory;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.ThrowingConsumer;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

// Merging lambda classes into single lambda group classes. There are three flavors
// of lambdas we are dealing with:
//   (a) lambda classes synthesized in desugaring, handles java lambdas
//   (b) k-style lambda classes synthesized by kotlin compiler
//   (c) j-style lambda classes synthesized by kotlin compiler
//
// Lambda merging is potentially applicable to all three of them, but
// current implementation only deals with k-style lambdas.
//
// In general we merge lambdas in 5 phases:
//   1. collect all lambdas and compute group candidates. we do it synchronously
//      and ensure that the order of lambda groups and lambdas inside each group
//      is stable.
//   2. analyze usages of lambdas and exclude lambdas with unexpected usage
//      NOTE: currently we consider *all* usages outside the code invalid
//      so we only need to patch method code when replacing the lambda class.
//   3. exclude (invalidate) all lambda classes with usages we don't understand
//      or support, compact the remaining lambda groups, remove trivial groups
//      with less that 2 lambdas.
//   4. replace lambda valid/supported class constructions with references to
//      lambda group classes.
//   5. synthesize group lambda classes.
//
public final class LambdaMerger {
  // Maps lambda into a group, only contains lambdas we decided to merge.
  // NOTE: needs synchronization.
  private final Map<DexType, LambdaGroup> lambdas = new IdentityHashMap<>();
  // We use linked map to ensure stable ordering of the groups
  // when they are processed sequentially.
  // NOTE: needs synchronization.
  private final Map<LambdaGroupId, LambdaGroup> groups = new LinkedHashMap<>();

  // Since invalidating lambdas may happen concurrently we don't remove invalidated lambdas
  // from groups (and `lambdas`) right away since the ordering may be important. Instead we
  // collect invalidated lambdas and remove them from groups after analysis is done.
  private final Set<DexType> invalidatedLambdas = Sets.newConcurrentHashSet();

  // Methods which need to be patched to reference lambda group classes instead of the
  // original lambda classes. The number of methods is expected to be small since there
  // is a 1:1 relation between lambda and method it is defined in (unless such a method
  // was inlined by either kotlinc or r8).
  //
  // Note that we don't track precisely lambda -> method mapping, so it may happen that
  // we mark a method for further processing, and then invalidate the only lambda referenced
  // from it. In this case we will reprocess method that does not need patching, but it
  // should not be happening very frequently and we ignore possible overhead.
  private final Set<DexEncodedMethod> methodsToReprocess = Sets.newIdentityHashSet();

  private final DexItemFactory factory;
  private final Kotlin kotlin;
  private final DiagnosticsHandler reporter;

  private BiFunction<DexEncodedMethod, IRCode, CodeProcessor> strategyFactory = null;

  // Lambda visitor invalidating lambdas it sees.
  private final LambdaTypeVisitor lambdaInvalidator;
  // Lambda visitor throwing Unreachable on each lambdas it sees.
  private final LambdaTypeVisitor lambdaChecker;

  public LambdaMerger(DexItemFactory factory, DiagnosticsHandler reporter) {
    this.factory = factory;
    this.kotlin = factory.kotlin;
    this.reporter = reporter;

    this.lambdaInvalidator = new LambdaTypeVisitor(factory, this::isMergeableLambda,
        this::invalidateLambda);
    this.lambdaChecker = new LambdaTypeVisitor(factory, this::isMergeableLambda,
        type -> {
          throw new Unreachable("Unexpected lambda " + type.toSourceString());
        });
  }

  private void invalidateLambda(DexType lambda) {
    invalidatedLambdas.add(lambda);
  }

  private synchronized boolean isMergeableLambda(DexType lambda) {
    return lambdas.containsKey(lambda);
  }

  private synchronized LambdaGroup getLambdaGroup(DexType lambda) {
    return lambdas.get(lambda);
  }

  private synchronized void queueForProcessing(DexEncodedMethod method) {
    methodsToReprocess.add(method);
  }

  // Collect all group candidates and assign unique lambda ids inside each group.
  // We do this before methods are being processed to guarantee stable order of
  // lambdas inside each group.
  public final void collectGroupCandidates(
      DexApplication app, AppInfoWithLiveness infoWithLiveness, InternalOptions options) {
    assert infoWithLiveness != null;
    // Collect lambda groups.
    app.classes().stream()
        .filter(cls -> !infoWithLiveness.isPinned(cls.type))
        .filter(cls -> cls.hasKotlinInfo() &&
            cls.getKotlinInfo().isSyntheticClass() &&
            cls.getKotlinInfo().asSyntheticClass().isKotlinStyleLambda())
        .sorted((a, b) -> a.type.slowCompareTo(b.type)) // Ensure stable ordering.
        .forEachOrdered(lambda -> {
          try {
            LambdaGroupId id = KStyleLambdaGroupIdFactory.create(kotlin, lambda, options);
            LambdaGroup group = groups.computeIfAbsent(id, LambdaGroupId::createGroup);
            group.add(lambda);
            lambdas.put(lambda.type, group);
          } catch (LambdaStructureError error) {
            reporter.warning(
                new StringDiagnostic("Invalid Kotlin-style lambda [" +
                    lambda.type.toSourceString() + "]: " + error.getMessage()));
          }
        });

    // Remove trivial groups.
    removeTrivialLambdaGroups();

    assert strategyFactory == null;
    strategyFactory = AnalysisStrategy::new;
  }

  // Is called by IRConverter::rewriteCode, performs different actions
  // depending on phase:
  //   - in ANALYZE phase just analyzes invalid usages of lambda classes
  //     inside the method code, invalidated such lambda classes,
  //     collects methods that need to be patched.
  //   - in APPLY phase patches the code to use lambda group classes, also
  //     asserts that there are no more invalid lambda class references.
  public final void processMethodCode(DexEncodedMethod method, IRCode code) {
    if (strategyFactory != null) {
      strategyFactory.apply(method, code).processCode();
    }
  }

  public final void applyLambdaClassMapping(DexApplication app,
      IRConverter converter, OptimizationFeedback feedback, Builder<?> builder,
      ExecutorService executorService) throws ExecutionException, ApiLevelException {
    if (lambdas.isEmpty()) {
      return;
    }

    // Analyse references from program classes. We assume that this optimization
    // is only used for full program analysis and there are no classpath classes.
    analyzeReferencesInProgramClasses(app, executorService);

    // Analyse more complex aspects of lambda classes including method code.
    assert converter.appInfo.hasSubtyping();
    analyzeLambdaClassesStructure(converter.appInfo.withSubtyping(), executorService);

    // Remove invalidated lambdas, compact groups to ensure
    // sequential lambda ids, create group lambda classes.
    Map<LambdaGroup, DexProgramClass> lambdaGroupsClasses = finalizeLambdaGroups();

    // Switch to APPLY strategy.
    this.strategyFactory = ApplyStrategy::new;

    // Add synthesized lambda group classes to the builder.
    for (Entry<LambdaGroup, DexProgramClass> entry : lambdaGroupsClasses.entrySet()) {
      converter.optimizeSynthesizedClass(entry.getValue());
      builder.addSynthesizedClass(entry.getValue(),
          entry.getKey().shouldAddToMainDex(converter.appInfo));
    }

    // Rewrite lambda class references into lambda group class
    // references inside methods from the processing queue.
    rewriteLambdaReferences(converter, feedback);
    this.strategyFactory = null;
  }

  private void analyzeReferencesInProgramClasses(
      DexApplication app, ExecutorService service) throws ExecutionException {
    List<Future<?>> futures = new ArrayList<>();
    for (DexProgramClass clazz : app.classes()) {
      futures.add(service.submit(() -> analyzeClass(clazz)));
    }
    ThreadUtils.awaitFutures(futures);
  }

  private void analyzeLambdaClassesStructure(
      AppInfoWithSubtyping appInfo, ExecutorService service) throws ExecutionException {
    List<Future<?>> futures = new ArrayList<>();
    for (LambdaGroup group : groups.values()) {
      ThrowingConsumer<DexClass, LambdaStructureError> validator =
          group.lambdaClassValidator(kotlin, appInfo);
      for (LambdaInfo lambda : group.lambdas()) {
        futures.add(service.submit(() -> {
          try {
            validator.accept(lambda.clazz);
          } catch (LambdaStructureError error) {
            reporter.info(
                new StringDiagnostic("Unexpected Kotlin-style lambda structure [" +
                    lambda.clazz.type.toSourceString() + "]: " + error.getMessage())
            );
            invalidateLambda(lambda.clazz.type);
          }
        }));
      }
    }
    ThreadUtils.awaitFutures(futures);
  }

  private synchronized Map<LambdaGroup, DexProgramClass> finalizeLambdaGroups() {
    for (DexType lambda : invalidatedLambdas) {
      LambdaGroup group = lambdas.get(lambda);
      assert group != null;
      lambdas.remove(lambda);
      group.remove(lambda);
    }
    invalidatedLambdas.clear();

    // Remove new trivial lambdas.
    removeTrivialLambdaGroups();

    // Compact lambda groups, synthesize lambda group classes.
    Map<LambdaGroup, DexProgramClass> result = new LinkedHashMap<>();
    for (LambdaGroup group : groups.values()) {
      assert !group.isTrivial() : "No trivial group is expected here.";
      group.compact();
      result.put(group, group.synthesizeClass(factory));
    }
    return result;
  }

  private void removeTrivialLambdaGroups() {
    Iterator<Entry<LambdaGroupId, LambdaGroup>> iterator = groups.entrySet().iterator();
    while (iterator.hasNext()) {
      Entry<LambdaGroupId, LambdaGroup> group = iterator.next();
      if (group.getValue().isTrivial()) {
        iterator.remove();
        List<LambdaInfo> lambdas = group.getValue().lambdas();
        assert lambdas.size() < 2;
        for (LambdaInfo lambda : lambdas) {
          this.lambdas.remove(lambda.clazz.type);
        }
      }
    }
  }

  private void rewriteLambdaReferences(
      IRConverter converter, OptimizationFeedback feedback) throws ApiLevelException {
    List<DexEncodedMethod> methods =
        methodsToReprocess
            .stream()
            .sorted(DexEncodedMethod::slowCompare)
            .collect(Collectors.toList());
    for (DexEncodedMethod method : methods) {
      converter.processMethod(method, feedback,
          x -> false, CallSiteInformation.empty(), Outliner::noProcessing);
      assert method.isProcessed();
    }
  }

  private void analyzeClass(DexProgramClass clazz) {
    lambdaInvalidator.accept(clazz.superType);
    lambdaInvalidator.accept(clazz.interfaces);
    lambdaInvalidator.accept(clazz.annotations);

    for (DexEncodedField field : clazz.staticFields()) {
      lambdaInvalidator.accept(field.annotations);
      if (field.field.type != clazz.type) {
        // Ignore static fields of the same type.
        lambdaInvalidator.accept(field.field, clazz.type);
      }
    }
    for (DexEncodedField field : clazz.instanceFields()) {
      lambdaInvalidator.accept(field.annotations);
      lambdaInvalidator.accept(field.field, clazz.type);
    }

    for (DexEncodedMethod method : clazz.directMethods()) {
      lambdaInvalidator.accept(method.annotations);
      lambdaInvalidator.accept(method.parameterAnnotations);
      lambdaInvalidator.accept(method.method, clazz.type);
    }
    for (DexEncodedMethod method : clazz.virtualMethods()) {
      lambdaInvalidator.accept(method.annotations);
      lambdaInvalidator.accept(method.parameterAnnotations);
      lambdaInvalidator.accept(method.method, clazz.type);
    }
  }

  private Strategy strategyProvider(DexType type) {
    LambdaGroup group = this.getLambdaGroup(type);
    return group != null ? group.getCodeStrategy() : CodeProcessor.NoOp;
  }

  private final class AnalysisStrategy extends CodeProcessor {
    private AnalysisStrategy(DexEncodedMethod method, IRCode code) {
      super(LambdaMerger.this.factory,
          LambdaMerger.this::strategyProvider, lambdaInvalidator, method, code);
    }

    @Override
    void process(Strategy strategy, InvokeMethod invokeMethod) {
      queueForProcessing(method);
    }

    @Override
    void process(Strategy strategy, NewInstance newInstance) {
      queueForProcessing(method);
    }

    @Override
    void process(Strategy strategy, InstancePut instancePut) {
      queueForProcessing(method);
    }

    @Override
    void process(Strategy strategy, InstanceGet instanceGet) {
      queueForProcessing(method);
    }

    @Override
    void process(Strategy strategy, StaticPut staticPut) {
      queueForProcessing(method);
    }

    @Override
    void process(Strategy strategy, StaticGet staticGet) {
      queueForProcessing(method);
    }
  }

  private final class ApplyStrategy extends CodeProcessor {
    private ApplyStrategy(DexEncodedMethod method, IRCode code) {
      super(LambdaMerger.this.factory,
          LambdaMerger.this::strategyProvider, lambdaChecker, method, code);
    }

    @Override
    void process(Strategy strategy, InvokeMethod invokeMethod) {
      strategy.patch(this, invokeMethod);
    }

    @Override
    void process(Strategy strategy, NewInstance newInstance) {
      strategy.patch(this, newInstance);
    }

    @Override
    void process(Strategy strategy, InstancePut instancePut) {
      strategy.patch(this, instancePut);
    }

    @Override
    void process(Strategy strategy, InstanceGet instanceGet) {
      strategy.patch(this, instanceGet);
    }

    @Override
    void process(Strategy strategy, StaticPut staticPut) {
      strategy.patch(this, staticPut);
    }

    @Override
    void process(Strategy strategy, StaticGet staticGet) {
      strategy.patch(this, staticGet);
    }
  }
}
