// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.proto;

import static com.android.tools.r8.utils.MapUtils.ignoreKey;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import com.google.common.collect.Sets;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

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
    if (options.testing.enableExperimentalProtoNormalization) {
      timing.time("Proto normalization", () -> run(executorService));
    }
  }

  private void run(ExecutorService executorService) throws ExecutionException {
    GlobalReservationState globalReservationState = computeGlobalReservationState(executorService);

    // TODO(b/173398086): This uses a single LocalReservationState for the entire program. This
    //  should process the strongly connected program components in parallel, each with their own
    //  LocalReservationState.
    LocalReservationState localReservationState = new LocalReservationState();
    ProtoNormalizerGraphLens.Builder lensBuilder = ProtoNormalizerGraphLens.builder(appView);
    for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) {
      clazz
          .getMethodCollection()
          .replaceMethods(
              method -> {
                DexMethodSignature methodSignature = method.getSignature();
                DexMethodSignature newMethodSignature =
                    localReservationState.getNewMethodSignature(
                        methodSignature, dexItemFactory, globalReservationState);
                if (methodSignature.equals(newMethodSignature)) {
                  return method;
                }
                DexMethod newMethodReference = newMethodSignature.withHolder(clazz, dexItemFactory);
                lensBuilder.recordNewMethodSignature(method, newMethodReference);
                // TODO(b/195112263): Fixup any optimization info and parameter annotations.
                return method.toTypeSubstitutedMethod(newMethodReference);
              });
    }

    if (!lensBuilder.isEmpty()) {
      appView.rewriteWithLens(lensBuilder.build());
    }
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

    ThreadUtils.processMethods(
        appView,
        method ->
            computeReservationsFromMethod(
                method, optimizableParameterLists, reservedParameterLists, unoptimizableSignatures),
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
      Map<DexTypeList, Set<DexTypeList>> optimizableParameterLists,
      Map<DexTypeList, Set<DexTypeList>> reservedParameterLists,
      DexMethodSignatureSet unoptimizableSignatures) {
    if (isUnoptimizable(method)) {
      // Record that other optimizable methods with the same set of parameter types should be
      // rewritten to have the same parameter list as this method.
      reservedParameterLists
          .computeIfAbsent(
              method.getParameters().getSorted(), ignoreKey(Sets::newConcurrentHashSet))
          .add(method.getParameters());

      // Mark signature as unoptimizable.
      unoptimizableSignatures.add(method);
    } else {
      // Record that the method's parameter list can be rewritten into any permutation.
      optimizableParameterLists
          .computeIfAbsent(
              method.getParameters().getSorted(), ignoreKey(Sets::newConcurrentHashSet))
          .add(method.getParameters());
    }
  }

  private void computeExtraReservationsFromMethod(
      ProgramMethod method,
      Set<DexTypeList> unoptimizableParameterLists,
      DexMethodSignatureSet unoptimizableSignatures) {
    if (unoptimizableParameterLists.contains(method.getParameters())) {
      unoptimizableSignatures.add(method.getMethodSignature());
    }
  }

  private boolean isUnoptimizable(ProgramMethod method) {
    // TODO(b/195112263): This is incomplete.
    return appView.getKeepInfo(method).isPinned(options)
        || method.getDefinition().isLibraryMethodOverride().isPossiblyTrue();
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

    MutableBidirectionalOneToOneMap<DexMethodSignature, DexMethodSignature> newMethodSignatures =
        new BidirectionalOneToOneHashMap<>();

    // TODO: avoid sorting multiple times.
    DexMethodSignature getNewMethodSignature(
        DexMethodSignature methodSignature,
        DexItemFactory dexItemFactory,
        GlobalReservationState globalReservationState) {
      if (globalReservationState.isUnoptimizable(methodSignature)) {
        assert !newMethodSignatures.containsKey(methodSignature);
        return methodSignature;
      }
      DexMethodSignature reservedSignature = newMethodSignatures.get(methodSignature);
      if (reservedSignature != null) {
        assert reservedSignature
            .getParameters()
            .equals(globalReservationState.getReservedParameters(methodSignature));
        return reservedSignature;
      }
      DexTypeList reservedParameters =
          globalReservationState.getReservedParameters(methodSignature);
      DexMethodSignature newMethodSignature =
          methodSignature.withParameters(reservedParameters, dexItemFactory);
      if (newMethodSignatures.containsValue(newMethodSignature)) {
        int index = 1;
        String newMethodBaseName = methodSignature.getName().toString();
        do {
          DexString newMethodName = dexItemFactory.createString(newMethodBaseName + "$" + index);
          newMethodSignature = newMethodSignature.withName(newMethodName);
        } while (newMethodSignatures.containsValue(newMethodSignature));
      }
      newMethodSignatures.put(methodSignature, newMethodSignature);
      return newMethodSignature;
    }
  }
}
