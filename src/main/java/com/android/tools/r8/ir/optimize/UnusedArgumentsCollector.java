// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ArgumentUse;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.NestedGraphLens;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfoCollection;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.RemovedArgumentInfo;
import com.android.tools.r8.ir.optimize.MemberPoolCollection.MemberPool;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.android.tools.r8.utils.SymbolGenerationUtils;
import com.android.tools.r8.utils.SymbolGenerationUtils.MixedCasing;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Streams;
import java.util.BitSet;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

public class UnusedArgumentsCollector {

  private static final MethodSignatureEquivalence equivalence = MethodSignatureEquivalence.get();

  private final AppView<AppInfoWithLiveness> appView;
  private final MethodPoolCollection methodPoolCollection;

  private final BiMap<DexMethod, DexMethod> methodMapping = HashBiMap.create();
  private final Map<DexMethod, ArgumentInfoCollection> removedArguments = new IdentityHashMap<>();

  public static class UnusedArgumentsGraphLens extends NestedGraphLens {

    private final Map<DexMethod, ArgumentInfoCollection> removedArguments;

    UnusedArgumentsGraphLens(
        Map<DexType, DexType> typeMap,
        Map<DexMethod, DexMethod> methodMap,
        Map<DexField, DexField> fieldMap,
        BiMap<DexField, DexField> originalFieldSignatures,
        BiMap<DexMethod, DexMethod> originalMethodSignatures,
        GraphLens previousLens,
        DexItemFactory dexItemFactory,
        Map<DexMethod, ArgumentInfoCollection> removedArguments) {
      super(
          typeMap,
          methodMap,
          fieldMap,
          originalFieldSignatures,
          originalMethodSignatures,
          previousLens,
          dexItemFactory);
      this.removedArguments = removedArguments;
    }

    @Override
    protected RewrittenPrototypeDescription internalDescribePrototypeChanges(
        RewrittenPrototypeDescription prototypeChanges, DexMethod method) {
      return prototypeChanges.withRemovedArguments(
          removedArguments.getOrDefault(method, ArgumentInfoCollection.empty()));
    }
  }

  public UnusedArgumentsCollector(
      AppView<AppInfoWithLiveness> appView, MethodPoolCollection methodPoolCollection) {
    this.appView = appView;
    this.methodPoolCollection = methodPoolCollection;
  }

  public UnusedArgumentsGraphLens run(ExecutorService executorService, Timing timing)
      throws ExecutionException {
    ThreadUtils.awaitFutures(
        Streams.stream(appView.appInfo().classes())
            .map(this::runnableForClass)
            .map(executorService::submit)
            // Materialize list such that all runnables are submitted to the executor service
            // before calling awaitFutures().
            .collect(Collectors.toList()));

    // Build method pool collection to enable unused argument removal for virtual methods.
    methodPoolCollection.buildAll(executorService, timing);

    // Visit classes in deterministic order to ensure deterministic output.
    appView.appInfo().classesWithDeterministicOrder().forEach(this::processVirtualMethods);

    if (!methodMapping.isEmpty()) {
      return new UnusedArgumentsGraphLens(
          ImmutableMap.of(),
          methodMapping,
          ImmutableMap.of(),
          ImmutableBiMap.of(),
          methodMapping.inverse(),
          appView.graphLens(),
          appView.dexItemFactory(),
          removedArguments);
    }

    return null;
  }

  private class UsedSignatures {

    private final MethodSignatureEquivalence equivalence = MethodSignatureEquivalence.get();
    private final Set<Wrapper<DexMethod>> usedSignatures = new HashSet<>();

    private boolean isMethodSignatureAvailable(DexMethod method) {
      return !usedSignatures.contains(equivalence.wrap(method));
    }

    private void markSignatureAsUsed(DexMethod method) {
      usedSignatures.add(equivalence.wrap(method));
    }

    DexMethod getNewSignature(DexEncodedMethod method, DexProto newProto) {
      DexMethod newSignature;
      int count = 0;
      DexString newName = null;
      do {
        if (newName == null) {
          newName = method.method.name;
        } else if (!appView.dexItemFactory().isConstructor(method.method)) {
          newName =
              appView
                  .dexItemFactory()
                  .createString(
                      SymbolGenerationUtils.numberToIdentifier(
                          count,
                          MixedCasing.USE_MIXED_CASE,
                          method.method.name.toSourceString().toCharArray()));
        } else {
          // Constructors must be named `<init>`.
          return null;
        }
        newSignature = appView.dexItemFactory().createMethod(method.holder(), newProto, newName);
        count++;
      } while (!isMethodSignatureAvailable(newSignature));
      return newSignature;
    }

    DexEncodedMethod removeArguments(
        DexEncodedMethod method, DexMethod newSignature, ArgumentInfoCollection unused) {
      boolean removed = usedSignatures.remove(equivalence.wrap(method.method));
      assert removed;

      markSignatureAsUsed(newSignature);

      return method.toTypeSubstitutedMethod(
          newSignature, unused.createParameterAnnotationsRemover(method));
    }
  }

  private class GloballyUsedSignatures {

    private final MemberPool<DexMethod> methodPool;

    GloballyUsedSignatures(MemberPool<DexMethod> methodPool) {
      this.methodPool = methodPool;
    }

    DexMethod getNewSignature(DexEncodedMethod method, DexProto newProto) {
      DexMethod newSignature;
      int count = 0;
      DexString newName = null;
      do {
        if (newName == null) {
          newName = method.method.name;
        } else if (!appView.dexItemFactory().isConstructor(method.method)) {
          newName =
              appView.dexItemFactory().createString(method.method.name.toSourceString() + count);
        } else {
          // Constructors must be named `<init>`.
          return null;
        }
        newSignature = appView.dexItemFactory().createMethod(method.holder(), newProto, newName);
        count++;
      } while (methodPool.hasSeen(equivalence.wrap(newSignature)));
      return newSignature;
    }

    DexEncodedMethod removeArguments(
        DexEncodedMethod method, DexMethod newSignature, ArgumentInfoCollection unused) {
      methodPool.seen(equivalence.wrap(newSignature));
      return method.toTypeSubstitutedMethod(
          newSignature, unused.createParameterAnnotationsRemover(method));
    }
  }

  private Runnable runnableForClass(DexProgramClass clazz) {
    return () -> this.processDirectMethods(clazz);
  }

  private void processDirectMethods(DexProgramClass clazz) {
    UsedSignatures signatures = new UsedSignatures();
    for (DexEncodedMethod method : clazz.methods()) {
      signatures.markSignatureAsUsed(method.method);
    }

    clazz
        .getMethodCollection()
        .replaceDirectMethods(
            method -> {

              // If this is a method with known resolution issues, then don't remove any unused
              // arguments.
              if (appView.appInfo().isFailedResolutionTarget(method.method)) {
                return method;
              }

              ArgumentInfoCollection unused = collectUnusedArguments(method);
              if (unused != null && unused.hasRemovedArguments()) {
                DexProto newProto = createProtoWithRemovedArguments(method, unused);
                DexMethod newSignature = signatures.getNewSignature(method, newProto);
                if (newSignature == null) {
                  assert appView.dexItemFactory().isConstructor(method.method);
                  return method;
                }
                DexEncodedMethod newMethod =
                    signatures.removeArguments(method, newSignature, unused);
                synchronized (this) {
                  methodMapping.put(method.method, newMethod.method);
                  removedArguments.put(newMethod.method, unused);
                }
                return newMethod;
              }
              return method;
            });
  }

  private void processVirtualMethods(DexProgramClass clazz) {
    MemberPool<DexMethod> methodPool = methodPoolCollection.get(clazz);
    GloballyUsedSignatures signatures = new GloballyUsedSignatures(methodPool);

    clazz
        .getMethodCollection()
        .replaceVirtualMethods(
            method -> {
              ArgumentInfoCollection unused = collectUnusedArguments(method, methodPool);
              if (unused != null && unused.hasRemovedArguments()) {
                DexProto newProto = createProtoWithRemovedArguments(method, unused);
                DexMethod newSignature = signatures.getNewSignature(method, newProto);

                // Double-check that the new method signature is in fact available.
                assert !methodPool.hasSeenStrictlyAbove(equivalence.wrap(newSignature));
                assert !methodPool.hasSeenStrictlyBelow(equivalence.wrap(newSignature));

                DexEncodedMethod newMethod =
                    signatures.removeArguments(
                        method, signatures.getNewSignature(method, newProto), unused);

                methodMapping.put(method.method, newMethod.method);
                removedArguments.put(newMethod.method, unused);
                return newMethod;
              }
              return method;
            });
  }

  private ArgumentInfoCollection collectUnusedArguments(DexEncodedMethod method) {
    return collectUnusedArguments(method, null);
  }

  private ArgumentInfoCollection collectUnusedArguments(
      DexEncodedMethod method, MemberPool<DexMethod> methodPool) {
    if (ArgumentRemovalUtils.isPinned(method, appView)
        || appView.appInfo().keepUnusedArguments.contains(method.method)) {
      return null;
    }
    // Only process classfile code objects.
    if (method.getCode() == null || !method.getCode().isCfCode()) {
      return null;
    }
    if (method.isNonPrivateVirtualMethod()) {
      // Abort if the method overrides another method, or if the method is overridden. In both cases
      // an unused argument cannot be removed unless it is unused in all of the related methods in
      // the hierarchy.
      assert methodPool != null;
      Wrapper<DexMethod> wrapper = equivalence.wrap(method.method);
      if (methodPool.hasSeenStrictlyAbove(wrapper) || methodPool.hasSeenStrictlyBelow(wrapper)) {
        return null;
      }
    }
    int offset = method.accessFlags.isStatic() ? 0 : 1;
    int argumentCount = method.method.proto.parameters.size() + offset;
    CollectUsedArguments collector = new CollectUsedArguments();
    if (!method.accessFlags.isStatic()) {
      // TODO(65810338): The receiver cannot be removed without transforming the method to being
      // static.
      collector.register(0);
    }
    method.getCode().registerArgumentReferences(method, collector);
    BitSet used = collector.getUsedArguments();
    if (used.cardinality() < argumentCount) {
      ArgumentInfoCollection.Builder argInfosBuilder = ArgumentInfoCollection.builder();
      for (int argumentIndex = 0; argumentIndex < argumentCount; argumentIndex++) {
        if (!used.get(argumentIndex)) {
          RemovedArgumentInfo removedArg =
              RemovedArgumentInfo.builder()
                  .setType(method.method.proto.parameters.values[argumentIndex - offset])
                  .build();
          argInfosBuilder.addArgumentInfo(argumentIndex, removedArg);
        }
      }
      return argInfosBuilder.build();
    }
    return null;
  }

  private DexProto createProtoWithRemovedArguments(
      DexEncodedMethod encodedMethod, ArgumentInfoCollection unused) {
    DexType[] parameters = unused.rewriteParameters(encodedMethod);
    return appView.dexItemFactory().createProto(encodedMethod.method.proto.returnType, parameters);
  }

  private static class CollectUsedArguments extends ArgumentUse {

    private final BitSet used = new BitSet();

    BitSet getUsedArguments() {
      return used;
    }

    @Override
    public boolean register(int argument) {
      used.set(argument);
      return true;
    }
  }
}
