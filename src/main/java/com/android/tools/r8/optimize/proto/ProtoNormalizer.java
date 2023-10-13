// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.proto;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.shaking.KeepMethodInfo;
import com.android.tools.r8.utils.DepthFirstSearchWorkListBase.StatefulDepthFirstSearchWorkList;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.WorkList;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;

public class ProtoNormalizer {

  private final AppView<AppInfoWithLiveness> appView;
  private final DexItemFactory dexItemFactory;
  private final InternalOptions options;

  public ProtoNormalizer(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    this.options = appView.options();
  }

  public void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("Proto normalization");
    GlobalReservationState globalReservationState = computeGlobalReservationState(executorService);

    // To ensure we do not add collisions of method signatures when creating the new method
    // signatures we keep a map of methods in the type hierarchy, similar to what we do for
    // minification.
    ProtoNormalizerGraphLens.Builder lensBuilder = ProtoNormalizerGraphLens.builder(appView);
    new StatefulDepthFirstSearchWorkList<DexClass, LocalReservationState, Void>() {

      @Override
      @SuppressWarnings("ReturnValueIgnored")
      protected TraversalContinuation<Void, LocalReservationState> process(
          DFSNodeWithState<DexClass, LocalReservationState> node,
          Function<DexClass, DFSNodeWithState<DexClass, LocalReservationState>> childNodeConsumer) {
        DexClass clazz = node.getNode();
        node.setState(new LocalReservationState());
        if (clazz.getSuperType() != null) {
          appView
              .contextIndependentDefinitionForWithResolutionResult(clazz.getSuperType())
              .forEachClassResolutionResult(childNodeConsumer::apply);
        }
        return TraversalContinuation.doContinue();
      }

      @Override
      @SuppressWarnings("ReferenceEquality")
      protected TraversalContinuation<Void, LocalReservationState> joiner(
          DFSNodeWithState<DexClass, LocalReservationState> node,
          List<DFSNodeWithState<DexClass, LocalReservationState>> childStates) {
        DexClass clazz = node.getNode();
        assert childStates.size() >= 1
            || clazz.getType() == dexItemFactory.objectType
            || clazz.hasMissingSuperType(appView.appInfo());
        // We can have multiple child states if there are multiple definitions of a class.
        assert childStates.size() <= 1 || options.loadAllClassDefinitions;
        LocalReservationState localReservationState = node.getState();
        if (!childStates.isEmpty()) {
          localReservationState.linkParent(childStates.get(0).getState());
        }
        Map<DexMethodSignature, DexMethodSignature> newInstanceInitializerSignatures =
            clazz.isProgramClass()
                ? computeNewInstanceInitializerSignatures(
                    clazz.asProgramClass(), localReservationState, globalReservationState)
                : null;
        clazz
            .getMethodCollection()
            .replaceMethods(
                method -> {
                  DexMethodSignature methodSignature = method.getSignature();
                  if (!clazz.isProgramClass()) {
                    // We cannot change the signature of the method. Record it to ensure we do not
                    // change a program signature in a sub class to override this.
                    localReservationState.recordNoSignatureChange(methodSignature, dexItemFactory);
                    return method;
                  } else {
                    assert newInstanceInitializerSignatures != null;
                    DexMethodSignature newMethodSignature =
                        method.isInstanceInitializer()
                            ? newInstanceInitializerSignatures.get(methodSignature)
                            : localReservationState.getAndReserveNewMethodSignature(
                                methodSignature, dexItemFactory, globalReservationState);
                    if (methodSignature.equals(newMethodSignature)) {
                      // This method could not be optimized, record it as identity mapping to ensure
                      // we keep it going forward.
                      localReservationState.recordNoSignatureChange(
                          methodSignature, dexItemFactory);
                      return method;
                    }
                    DexMethod newMethodReference =
                        newMethodSignature.withHolder(clazz.asProgramClass(), dexItemFactory);
                    RewrittenPrototypeDescription prototypeChanges =
                        lensBuilder.recordNewMethodSignature(method, newMethodReference);
                    // TODO(b/195112263): Assert that the method does not have optimization info.
                    // If/when enabling proto normalization after the final round of tree shaking,
                    // this should simply clear the optimization info, or replace it by a
                    // ThrowingMethodOptimizationInfo since we should never use the optimization
                    // info after this point.
                    return method.toTypeSubstitutedMethodAsInlining(
                        newMethodReference,
                        dexItemFactory,
                        builder -> {
                          if (!prototypeChanges.isEmpty()) {
                            builder
                                .apply(prototypeChanges.createParameterAnnotationsRemover(method))
                                .setGenericSignature(MethodTypeSignature.noSignature());
                          }
                        });
                  }
                });
        return TraversalContinuation.doContinue(localReservationState);
      }
    }.run(appView.appInfo().classesWithDeterministicOrder());

    if (!lensBuilder.isEmpty()) {
      appView.rewriteWithLens(lensBuilder.build(), executorService, timing);
    }
    appView.notifyOptimizationFinishedForTesting();
    timing.end();
  }

  private GlobalReservationState computeGlobalReservationState(ExecutorService executorService)
      throws ExecutionException {
    // Tracks how many different parameter lists can be optimized into the same parameter list.
    // If only (B, A) can be rewritten into (A, B), then there is no need to rewrite parameter lists
    // on the form (B, A) into (A, B), as that won't lead to any sharing of parameter lists.
    Map<DexTypeList, Set<DexTypeList>> optimizableParameterLists = new ConcurrentHashMap<>();

    // Used to track if a given parameter list should be mapped to a specific permutation instead of
    // just sorting the parameter list. This is used to ensure that we will rewrite parameter lists
    // such as (A, B) into (B, A) if there is an unoptimizable method with parameter list (B, A).
    Map<DexTypeList, Set<DexTypeList>> reservedParameterLists = new ConcurrentHashMap<>();

    // Tracks the set of unoptimizable method signatures. These must remain as-is.
    DexMethodSignatureSet unoptimizableSignatures = DexMethodSignatureSet.createConcurrent();

    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          Map<DexMethodSignature, DexMethodSignatureSet> collisions = new HashMap<>();
          clazz.forEachProgramMethod(
              method -> {
                DexTypeList methodParametersSorted = method.getParameters().getSorted();
                computeReservationsFromMethod(
                    method,
                    methodParametersSorted,
                    optimizableParameterLists,
                    reservedParameterLists,
                    unoptimizableSignatures);

                DexMethodSignature methodSignature = method.getMethodSignature();
                DexMethodSignature methodSignatureWithSortedParameters =
                    methodSignature.withParameters(methodParametersSorted, dexItemFactory);
                collisions
                    .computeIfAbsent(
                        methodSignatureWithSortedParameters,
                        ignoreKey(DexMethodSignatureSet::createLinked))
                    .add(methodSignature);
              });
          collisions.forEach(
              (methodSignatureWithSortedParameters, methodSignatures) -> {
                if (methodSignatures.size() > 1) {
                  methodSignatures.forEach(
                      methodSignature ->
                          addUnoptimizableMethod(
                              methodSignature,
                              methodSignatureWithSortedParameters.getParameters(),
                              reservedParameterLists,
                              unoptimizableSignatures));
                }
              });
        },
        executorService);

    // Reserve parameter lists that won't lead to any sharing after normalization. Any method with
    // such a parameter list must remain as-is.
    Set<DexTypeList> unoptimizableParameterLists = new HashSet<>();
    optimizableParameterLists.forEach(
        (sortedParameters, parameterListsBeforeNormalization) -> {
          int size = parameterListsBeforeNormalization.size();
          if (size != 1) {
            // There are multiple optimizable methods with different parameter lists that can be
            // rewritten into having the same parameter list.
            assert size > 1;
            return;
          }
          DexTypeList parameters = parameterListsBeforeNormalization.iterator().next();
          Set<DexTypeList> reservedParameters =
              reservedParameterLists.getOrDefault(sortedParameters, Collections.emptySet());
          if (!reservedParameters.isEmpty() && !reservedParameters.contains(parameters)) {
            // There is at least one optimizable method that can be rewritten into having the same
            // parameter list as an unoptimizable method.
            return;
          }
          unoptimizableParameterLists.add(parameters);
        });

    ThreadUtils.processMethods(
        appView,
        method ->
            computeExtraReservationsFromMethod(
                method, unoptimizableParameterLists, unoptimizableSignatures),
        executorService);

    return new GlobalReservationState(reservedParameterLists, unoptimizableSignatures);
  }

  private void computeReservationsFromMethod(
      ProgramMethod method,
      DexTypeList methodParametersSorted,
      Map<DexTypeList, Set<DexTypeList>> optimizableParameterLists,
      Map<DexTypeList, Set<DexTypeList>> reservedParameterLists,
      DexMethodSignatureSet unoptimizableSignatures) {
    if (isUnoptimizable(method)) {
      addUnoptimizableMethod(
          method.getMethodSignature(),
          methodParametersSorted,
          reservedParameterLists,
          unoptimizableSignatures);
    } else {
      // Record that the method's parameter list can be rewritten into any permutation.
      optimizableParameterLists
          .computeIfAbsent(methodParametersSorted, ignoreKey(Sets::newConcurrentHashSet))
          .add(method.getParameters());
    }
  }

  private void addUnoptimizableMethod(
      DexMethodSignature method,
      DexTypeList methodParametersSorted,
      Map<DexTypeList, Set<DexTypeList>> reservedParameterLists,
      DexMethodSignatureSet unoptimizableSignatures) {
    // Record that other optimizable methods with the same set of parameter types should be
    // rewritten to have the same parameter list as this method.
    reservedParameterLists
        .computeIfAbsent(methodParametersSorted, ignoreKey(Sets::newConcurrentHashSet))
        .add(method.getParameters());

    // Mark signature as unoptimizable.
    unoptimizableSignatures.add(method);
  }

  private void computeExtraReservationsFromMethod(
      ProgramMethod method,
      Set<DexTypeList> unoptimizableParameterLists,
      DexMethodSignatureSet unoptimizableSignatures) {
    if (unoptimizableParameterLists.contains(method.getParameters())) {
      unoptimizableSignatures.add(method.getMethodSignature());
    }
  }

  Map<DexMethodSignature, DexMethodSignature> computeNewInstanceInitializerSignatures(
      DexProgramClass clazz,
      LocalReservationState localReservationState,
      GlobalReservationState globalReservationState) {
    // Create a map from new method signatures to old method signatures. This produces a one-to-many
    // mapping since multiple instance initializers may normalize to the same signature.
    Map<DexMethodSignature, DexMethodSignatureSet> instanceInitializerCollisions =
        computeInstanceInitializerCollisions(clazz, localReservationState, globalReservationState);

    // Resolve each collision to ensure that the mapping is one-to-one.
    resolveInstanceInitializerCollisions(instanceInitializerCollisions);

    // Inverse the one-to-one map to produce a mapping from old method signatures to new method
    // signatures.
    return MapUtils.transform(
        instanceInitializerCollisions,
        HashMap::new,
        (newMethodSignature, methodSignatures) -> Iterables.getFirst(methodSignatures, null),
        (newMethodSignature, methodSignatures) -> newMethodSignature,
        (newMethodSignature, methodSignature, otherMethodSignature) -> {
          throw new Unreachable();
        });
  }

  private Map<DexMethodSignature, DexMethodSignatureSet> computeInstanceInitializerCollisions(
      DexProgramClass clazz,
      LocalReservationState localReservationState,
      GlobalReservationState globalReservationState) {
    Map<DexMethodSignature, DexMethodSignatureSet> instanceInitializerCollisions = new HashMap<>();
    clazz.forEachProgramInstanceInitializer(
        method -> {
          DexMethodSignature methodSignature = method.getMethodSignature();
          DexMethodSignature newMethodSignature =
              localReservationState.getNewMethodSignature(
                  methodSignature, dexItemFactory, globalReservationState);
          instanceInitializerCollisions
              .computeIfAbsent(newMethodSignature, ignoreKey(DexMethodSignatureSet::create))
              .add(methodSignature);
        });
    return instanceInitializerCollisions;
  }

  private void resolveInstanceInitializerCollisions(
      Map<DexMethodSignature, DexMethodSignatureSet> instanceInitializerCollisions) {
    WorkList<DexMethodSignature> worklist = WorkList.newEqualityWorkList();
    instanceInitializerCollisions.forEach(
        (newMethodSignature, methodSignatures) -> {
          if (methodSignatures.size() > 1) {
            worklist.addIfNotSeen(newMethodSignature);
          }
        });

    while (worklist.hasNext()) {
      DexMethodSignature newMethodSignature = worklist.removeSeen();
      DexMethodSignatureSet methodSignatures =
          instanceInitializerCollisions.get(newMethodSignature);
      assert methodSignatures.size() > 1;

      // Resolve this conflict in a deterministic way.
      DexMethodSignature survivor =
          methodSignatures.contains(newMethodSignature)
              ? newMethodSignature
              : IterableUtils.min(methodSignatures, DexMethodSignature::compareTo);

      // Disallow optimizations of all other methods than the `survivor`.
      for (DexMethodSignature methodSignature : methodSignatures) {
        if (!methodSignature.equals(survivor)) {
          DexMethodSignatureSet originalMethodSignaturesForMethodSignature =
              instanceInitializerCollisions.computeIfAbsent(
                  methodSignature, ignoreKey(DexMethodSignatureSet::create));
          originalMethodSignaturesForMethodSignature.add(methodSignature);
          if (originalMethodSignaturesForMethodSignature.size() > 1) {
            worklist.addIfNotSeen(methodSignature);
          }
        }
      }

      // Remove all pinned methods from the set of original method signatures stored at
      // instanceInitializerCollisions.get(newMethodSignature).
      methodSignatures.clear();
      methodSignatures.add(survivor);
    }
  }

  private boolean isUnoptimizable(ProgramMethod method) {
    KeepMethodInfo keepInfo = appView.getKeepInfo(method);
    if (!keepInfo.isParameterReorderingAllowed(options)
        || method.getDefinition().isLibraryMethodOverride().isPossiblyTrue()) {
      return true;
    }
    AppInfoWithLiveness appInfo = appView.appInfo();
    if (appInfo.isBootstrapMethod(method)) {
      return true;
    }
    // As long as we do not consider interface method and overrides as optimizable we can change
    // method signatures in a top-down traversal.
    return method.getHolder().isInterface()
        && method.getDefinition().isAbstract()
        && appInfo
            .getObjectAllocationInfoCollection()
            .isImmediateInterfaceOfInstantiatedLambda(method.getHolder());
  }

  static class GlobalReservationState {

    // Used to track if a given parameter list should be mapped to a specific permutation instead of
    // just sorting the parameter list. This is used to ensure that we will rewrite parameter lists
    // such as (A, B) into (B, A) if there is an unoptimizable method with parameter list (B, A).
    Map<DexTypeList, DexTypeList> reservedParameters;

    // Tracks the set of unoptimizable method signatures. These must remain as-is.
    DexMethodSignatureSet unoptimizableSignatures;

    GlobalReservationState(
        Map<DexTypeList, Set<DexTypeList>> reservedParameterLists,
        DexMethodSignatureSet unoptimizableSignatures) {
      this.reservedParameters = selectDeterministicTarget(reservedParameterLists);
      this.unoptimizableSignatures = unoptimizableSignatures;
    }

    private static Map<DexTypeList, DexTypeList> selectDeterministicTarget(
        Map<DexTypeList, Set<DexTypeList>> reservedParameterLists) {
      Map<DexTypeList, DexTypeList> result = new HashMap<>();
      reservedParameterLists.forEach(
          (sortedParameters, candidates) -> {
            Iterator<DexTypeList> iterator = candidates.iterator();
            DexTypeList smallestCandidate = iterator.next();
            while (iterator.hasNext()) {
              DexTypeList candidate = iterator.next();
              if (candidate.compareTo(smallestCandidate) < 0) {
                smallestCandidate = candidate;
              }
            }
            result.put(sortedParameters, smallestCandidate);
          });
      return result;
    }

    DexTypeList getReservedParameters(DexMethodSignature methodSignature) {
      DexTypeList sortedParameters = methodSignature.getParameters().getSorted();
      return reservedParameters.getOrDefault(sortedParameters, sortedParameters);
    }

    boolean isUnoptimizable(DexMethodSignature methodSignature) {
      return unoptimizableSignatures.contains(methodSignature);
    }
  }

  static class LocalReservationState {

    private final List<LocalReservationState> parents = new ArrayList<>(2);

    private final MutableBidirectionalOneToOneMap<DexMethodSignature, DexMethodSignature>
        newMethodSignatures = new BidirectionalOneToOneHashMap<>();

    DexMethodSignature getNewMethodSignature(
        DexMethodSignature methodSignature,
        DexItemFactory dexItemFactory,
        GlobalReservationState globalReservationState) {
      return internalGetAndReserveNewMethodSignature(
          methodSignature, dexItemFactory, globalReservationState, false);
    }

    DexMethodSignature getAndReserveNewMethodSignature(
        DexMethodSignature methodSignature,
        DexItemFactory dexItemFactory,
        GlobalReservationState globalReservationState) {
      return internalGetAndReserveNewMethodSignature(
          methodSignature, dexItemFactory, globalReservationState, true);
    }

    private DexMethodSignature internalGetAndReserveNewMethodSignature(
        DexMethodSignature methodSignature,
        DexItemFactory dexItemFactory,
        GlobalReservationState globalReservationState,
        boolean reserve) {
      if (globalReservationState.isUnoptimizable(methodSignature)) {
        assert getReserved(methodSignature) == null
            || methodSignature.equals(getReserved(methodSignature));
        return methodSignature;
      }
      DexMethodSignature reservedSignature = getReserved(methodSignature);
      if (reservedSignature != null) {
        return reservedSignature;
      }
      DexTypeList reservedParameters =
          globalReservationState.getReservedParameters(methodSignature);
      DexMethodSignature newMethodSignature =
          methodSignature.withParameters(reservedParameters, dexItemFactory);
      if (isDestinationTaken(newMethodSignature)) {
        int index = 1;
        String newMethodBaseName = methodSignature.getName().toString();
        do {
          DexString newMethodName = dexItemFactory.createString(newMethodBaseName + "$" + index);
          newMethodSignature = newMethodSignature.withName(newMethodName);
          index++;
        } while (isDestinationTaken(newMethodSignature));
      }
      if (reserve) {
        newMethodSignatures.put(methodSignature, newMethodSignature);
      }
      return newMethodSignature;
    }

    private void linkParent(LocalReservationState parent) {
      this.parents.add(parent);
    }

    private DexMethodSignature getReserved(DexMethodSignature signature) {
      WorkList<LocalReservationState> workList = WorkList.newIdentityWorkList(this);
      while (workList.hasNext()) {
        LocalReservationState localReservationState = workList.next();
        DexMethodSignature reservedSignature =
            localReservationState.newMethodSignatures.get(signature);
        if (reservedSignature != null) {
          return reservedSignature;
        }
        workList.addIfNotSeen(localReservationState.parents);
      }
      return null;
    }

    private boolean isDestinationTaken(DexMethodSignature signature) {
      WorkList<LocalReservationState> workList = WorkList.newIdentityWorkList(this);
      while (workList.hasNext()) {
        LocalReservationState localReservationState = workList.next();
        if (localReservationState.newMethodSignatures.containsValue(signature)) {
          return true;
        }
        workList.addIfNotSeen(localReservationState.parents);
      }
      return false;
    }

    public void recordNoSignatureChange(
        DexMethodSignature methodSignature, DexItemFactory factory) {
      if (!methodSignature.getName().equals(factory.constructorMethodName)) {
        newMethodSignatures.put(methodSignature, methodSignature);
      }
    }
  }
}
