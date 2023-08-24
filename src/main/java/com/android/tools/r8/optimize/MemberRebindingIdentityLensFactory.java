// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AbstractAccessContexts.ConcreteAccessContexts;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.FieldAccessInfoCollectionImpl;
import com.android.tools.r8.graph.FieldAccessInfoImpl;
import com.android.tools.r8.graph.MethodAccessInfoCollection;
import com.android.tools.r8.graph.MethodResolutionResult.SingleResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class MemberRebindingIdentityLensFactory {

  /**
   * In order to construct an instance of {@link MemberRebindingIdentityLens} we need a mapping from
   * non-rebound field and method references to their definitions.
   *
   * <p>If shrinking or minification is enabled, we retrieve these from {@link AppInfoWithLiveness}.
   * Otherwise we apply the {@link NonReboundMemberReferencesRegistry} below to all code objects to
   * compute the mapping.
   */
  public static MemberRebindingIdentityLens create(
      AppView<? extends AppInfoWithClassHierarchy> appView, ExecutorService executorService)
      throws ExecutionException {
    FieldAccessInfoCollection<?> fieldAccessInfoCollection;
    MethodAccessInfoCollection methodAccessInfoCollection;
    if (appView.appInfo().hasLiveness()
        && appView.options().testing.alwaysUseExistingAccessInfoCollectionsInMemberRebinding) {
      AppInfoWithLiveness appInfo = appView.appInfo().withLiveness();
      fieldAccessInfoCollection = appInfo.getFieldAccessInfoCollection();
      methodAccessInfoCollection = appInfo.getMethodAccessInfoCollection();
    } else {
      FieldAccessInfoCollectionImpl mutableFieldAccessInfoCollection =
          new FieldAccessInfoCollectionImpl(new ConcurrentHashMap<>());
      MethodAccessInfoCollection.ConcurrentBuilder methodAccessInfoCollectionBuilder =
          MethodAccessInfoCollection.concurrentBuilder();
      initializeMemberAccessInfoCollectionsForMemberRebinding(
          appView,
          mutableFieldAccessInfoCollection,
          methodAccessInfoCollectionBuilder,
          executorService);
      fieldAccessInfoCollection = mutableFieldAccessInfoCollection;
      methodAccessInfoCollection = methodAccessInfoCollectionBuilder.build();
    }
    return create(appView, fieldAccessInfoCollection, methodAccessInfoCollection);
  }

  public static MemberRebindingIdentityLens create(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      FieldAccessInfoCollection<?> fieldAccessInfoCollection,
      MethodAccessInfoCollection methodAccessInfoCollection) {
    MemberRebindingIdentityLens.Builder builder = MemberRebindingIdentityLens.builder(appView);
    fieldAccessInfoCollection.forEach(builder::recordNonReboundFieldAccesses);
    methodAccessInfoCollection.forEachMethodReference(builder::recordMethodAccess);
    return builder.build();
  }

  /**
   * Applies {@link NonReboundMemberReferencesRegistry} to all code objects to construct a mapping
   * from non-rebound field references to their definition.
   */
  private static void initializeMemberAccessInfoCollectionsForMemberRebinding(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      FieldAccessInfoCollectionImpl fieldAccessInfoCollection,
      MethodAccessInfoCollection.ConcurrentBuilder methodAccessInfoCollectionBuilder,
      ExecutorService executorService)
      throws ExecutionException {
    Set<DexField> seenFieldReferences = SetUtils.newConcurrentHashSet();
    Set<DexMethod> seenMethodReferences = SetUtils.newConcurrentHashSet();
    ThreadUtils.processItems(
        appView.appInfo()::forEachMethod,
        method ->
            new NonReboundMemberReferencesRegistry(
                    appView,
                    method,
                    fieldAccessInfoCollection,
                    methodAccessInfoCollectionBuilder,
                    seenFieldReferences,
                    seenMethodReferences)
                .accept(method),
        executorService);
  }

  private static class NonReboundMemberReferencesRegistry extends UseRegistry<ProgramMethod> {

    private final AppInfoWithClassHierarchy appInfo;
    private final FieldAccessInfoCollectionImpl fieldAccessInfoCollection;
    private final MethodAccessInfoCollection.ConcurrentBuilder methodAccessInfoCollectionBuilder;
    private final Set<DexField> seenFieldReferences;
    private final Set<DexMethod> seenMethodReferences;

    public NonReboundMemberReferencesRegistry(
        AppView<? extends AppInfoWithClassHierarchy> appView,
        ProgramMethod context,
        FieldAccessInfoCollectionImpl fieldAccessInfoCollection,
        MethodAccessInfoCollection.ConcurrentBuilder methodAccessInfoCollectionBuilder,
        Set<DexField> seenFieldReferences,
        Set<DexMethod> seenMethodReferences) {
      super(appView, context);
      this.appInfo = appView.appInfo();
      this.fieldAccessInfoCollection = fieldAccessInfoCollection;
      this.methodAccessInfoCollectionBuilder = methodAccessInfoCollectionBuilder;
      this.seenFieldReferences = seenFieldReferences;
      this.seenMethodReferences = seenMethodReferences;
    }

    @Override
    public void registerInstanceFieldRead(DexField field) {
      registerFieldAccess(field);
    }

    @Override
    public void registerInstanceFieldWrite(DexField field) {
      registerFieldAccess(field);
    }

    @Override
    public void registerStaticFieldRead(DexField field) {
      registerFieldAccess(field);
    }

    @Override
    public void registerStaticFieldWrite(DexField field) {
      registerFieldAccess(field);
    }

    private void registerFieldAccess(DexField field) {
      if (!seenFieldReferences.add(field)) {
        return;
      }
      appInfo
          .resolveField(field)
          .forEachSuccessfulFieldResolutionResult(
              resolutionResult -> {
                DexField reboundReference = resolutionResult.getResolvedField().getReference();
                if (field == reboundReference) {
                  // For the purpose of member rebinding, we don't care about already rebound
                  // references.
                  return;
                }
                FieldAccessInfoImpl fieldAccessInfo =
                    fieldAccessInfoCollection.computeIfAbsent(
                        reboundReference, FieldAccessInfoImpl::new);
                synchronized (fieldAccessInfo) {
                  // Record the fact that there is a non-rebound access to the given field. We don't
                  // distinguish between non-rebound reads and writes, so we just record it as a
                  // read.
                  if (fieldAccessInfo.getReadsWithContexts().isBottom()) {
                    fieldAccessInfo.setReadsWithContexts(new ConcreteAccessContexts());
                  } else {
                    assert fieldAccessInfo.getReadsWithContexts().isConcrete();
                  }
                  // For the purpose of member rebinding, we don't care about the access contexts,
                  // so we
                  // simply use the empty set.
                  ConcreteAccessContexts accessContexts =
                      fieldAccessInfo.getReadsWithContexts().asConcrete();
                  accessContexts.getAccessesWithContexts().put(field, ProgramMethodSet.empty());
                }
              });
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      registerInvokeMethod(method, methodAccessInfoCollectionBuilder.getDirectInvokes());
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      registerInvokeMethod(method, methodAccessInfoCollectionBuilder.getInterfaceInvokes());
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      registerInvokeMethod(method, methodAccessInfoCollectionBuilder.getStaticInvokes());
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      registerInvokeMethod(method, methodAccessInfoCollectionBuilder.getSuperInvokes());
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      registerInvokeMethod(method, methodAccessInfoCollectionBuilder.getVirtualInvokes());
    }

    private void registerInvokeMethod(DexMethod method, Map<DexMethod, ProgramMethodSet> invokes) {
      if (!seenMethodReferences.add(method)) {
        return;
      }
      if (method.getHolderType().isArrayType()) {
        return;
      }
      DexClass holder = appInfo.definitionFor(method.getHolderType(), getContext());
      if (holder == null) {
        return;
      }
      SingleResolutionResult<?> resolutionResult =
          appInfo.resolveMethodOnLegacy(holder, method).asSingleResolution();
      if (resolutionResult == null) {
        return;
      }
      DexMethod reboundReference = resolutionResult.getResolvedMethod().getReference();
      if (method == reboundReference) {
        // For the purpose of member rebinding, we don't care about already rebound references.
        return;
      }
      // For the purpose of member rebinding, we don't care about the access contexts, so we
      // simply use the empty set.
      invokes.put(method, ProgramMethodSet.empty());
    }

    @Override
    public void registerInitClass(DexType type) {
      // Intentionally empty.
    }

    @Override
    public void registerNewInstance(DexType type) {
      // Intentionally empty.
    }

    @Override
    public void registerTypeReference(DexType type) {
      // Intentionally empty.
    }

    @Override
    public void registerInstanceOf(DexType type) {
      // Intentionally empty.
    }
  }
}
