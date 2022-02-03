// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodSignature;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.DexMethodSignatureSet;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import java.util.ArrayList;
import java.util.List;
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

  public void run(ExecutorService executorService, Timing timing) {
    if (options.testing.enableExperimentalProtoNormalization) {
      timing.time("Proto normalization", () -> run(executorService));
    }
  }

  // TODO(b/195112263): Parallelize using executor service.
  private void run(ExecutorService executorService) {
    // Compute mapping from method signatures to new method signatures.
    BidirectionalOneToOneMap<DexMethodSignature, DexMethodSignature> normalization =
        computeNormalization();

    ProtoNormalizerGraphLens.Builder lensBuilder = ProtoNormalizerGraphLens.builder(appView);
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz
          .getMethodCollection()
          .replaceMethods(
              method -> {
                DexMethodSignature methodSignature = method.getSignature();
                assert normalization.containsKey(methodSignature);
                DexMethodSignature normalizedMethodSignature = normalization.get(methodSignature);
                if (methodSignature.equals(normalizedMethodSignature)) {
                  return method;
                }
                DexMethod normalizedMethodReference =
                    normalizedMethodSignature.withHolder(clazz, dexItemFactory);
                lensBuilder.recordNewMethodSignature(method, normalizedMethodReference);
                // TODO(b/195112263): Fixup any optimization info and parameter annotations.
                return method.toTypeSubstitutedMethod(normalizedMethodReference);
              });
    }

    if (!lensBuilder.isEmpty()) {
      appView.rewriteWithLens(lensBuilder.build());
    }
  }

  // TODO(b/195112263): This naively maps each method signatures to their normalized method
  //  signature if it is not already reserved by another method. This means that we will rewrite
  //  foo(A,B), bar(B,A), and baz(B,A) into foo(A,B), bar(A,B), and baz(A,B), such that all of the
  //  method signatures share the same parameter type list. However, if there is a method foo(A,B)
  //  and foo(B,A) then this does not rewrite foo(B,A) into foo(A,B). If foo(B,A) is not in the same
  //  hierarchy as foo(A,B), this would be possible, however.
  // TODO(b/195112263): Do not optimize foo(B, A) into foo(A, B) if this won't lead to any parameter
  //  type lists being shared (e.g., if foo(B, A) is the only method where sorted(parameters) is
  //  [A, B]).
  private BidirectionalOneToOneMap<DexMethodSignature, DexMethodSignature> computeNormalization() {
    // Reserve the signatures of unoptimizable methods to avoid collisions.
    MutableBidirectionalOneToOneMap<DexMethodSignature, DexMethodSignature> normalization =
        new BidirectionalOneToOneHashMap<>();
    DexMethodSignatureSet optimizableMethodSignatures = DexMethodSignatureSet.create();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      clazz.forEachProgramMethod(
          method -> {
            DexMethodSignature methodSignature = method.getMethodSignature();
            if (isUnoptimizable(method)) {
              normalization.put(methodSignature, methodSignature);
            } else if (!normalization.containsKey(methodSignature)) {
              optimizableMethodSignatures.add(methodSignature);
            }
          });
    }
    optimizableMethodSignatures.removeAll(normalization.keySet());

    // Normalize each signature that is subject to optimization.
    List<DexMethodSignature> sortedOptimizableMethodSignatures =
        new ArrayList<>(optimizableMethodSignatures);
    sortedOptimizableMethodSignatures.sort(DexMethodSignature::compareTo);

    for (DexMethodSignature signature : sortedOptimizableMethodSignatures) {
      assert !normalization.containsKey(signature);
      assert !normalization.containsValue(signature);
      DexMethodSignature normalizedSignature =
          signature.withProto(
              dexItemFactory.createProto(
                  signature.getReturnType(), signature.getParameters().getSorted()));
      if (normalization.containsValue(normalizedSignature)) {
        normalization.put(signature, signature);
      } else {
        normalization.put(signature, normalizedSignature);
      }
    }

    return normalization;
  }

  private boolean isUnoptimizable(ProgramMethod method) {
    // TODO(b/195112263): This is incomplete.
    return appView.getKeepInfo(method).isPinned(options);
  }
}
