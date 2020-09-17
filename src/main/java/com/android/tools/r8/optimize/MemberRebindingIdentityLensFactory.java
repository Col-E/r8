// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AbstractAccessContexts.ConcreteAccessContexts;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.FieldAccessInfoCollectionImpl;
import com.android.tools.r8.graph.FieldAccessInfoImpl;
import com.android.tools.r8.graph.FieldResolutionResult.SuccessfulFieldResolutionResult;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions.TestingOptions;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.google.common.collect.Sets;
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
    TestingOptions testingOptions = appView.options().testing;
    FieldAccessInfoCollection<?> fieldAccessInfoCollection =
        appView.appInfo().hasLiveness()
                && testingOptions.alwaysUseExistingFieldAccessInfoCollectionInMemberRebinding
            ? appView.appInfo().withLiveness().getFieldAccessInfoCollection()
            : createFieldAccessInfoCollectionForMemberRebinding(appView, executorService);
    return create(fieldAccessInfoCollection, appView.dexItemFactory(), appView.graphLens());
  }

  public static MemberRebindingIdentityLens create(
      FieldAccessInfoCollection<?> fieldAccessInfoCollection,
      DexItemFactory dexItemFactory,
      GraphLens previousLens) {
    MemberRebindingIdentityLens.Builder builder = MemberRebindingIdentityLens.builder();
    fieldAccessInfoCollection.forEach(builder::recordNonReboundFieldAccesses);
    return builder.build(dexItemFactory, previousLens);
  }

  /**
   * Applies {@link NonReboundMemberReferencesRegistry} to all code objects to construct a mapping
   * from non-rebound field references to their definition.
   */
  private static FieldAccessInfoCollection<?> createFieldAccessInfoCollectionForMemberRebinding(
      AppView<? extends AppInfoWithClassHierarchy> appView, ExecutorService executorService)
      throws ExecutionException {
    NonReboundMemberReferencesRegistry registry = new NonReboundMemberReferencesRegistry(appView);
    ThreadUtils.processItems(appView.appInfo()::forEachMethod, registry::accept, executorService);
    return registry.getFieldAccessInfoCollection();
  }

  private static class NonReboundMemberReferencesRegistry extends UseRegistry {

    private final AppInfoWithClassHierarchy appInfo;
    private final FieldAccessInfoCollectionImpl fieldAccessInfoCollection =
        new FieldAccessInfoCollectionImpl(new ConcurrentHashMap<>());
    private final Set<DexField> seenFieldReferences = Sets.newConcurrentHashSet();

    FieldAccessInfoCollection<?> getFieldAccessInfoCollection() {
      return fieldAccessInfoCollection;
    }

    public NonReboundMemberReferencesRegistry(
        AppView<? extends AppInfoWithClassHierarchy> appView) {
      super(appView.dexItemFactory());
      this.appInfo = appView.appInfo();
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
      SuccessfulFieldResolutionResult resolutionResult =
          appInfo.resolveField(field).asSuccessfulResolution();
      if (resolutionResult == null) {
        return;
      }
      DexField reboundReference = resolutionResult.getResolvedField().toReference();
      if (field == reboundReference) {
        // For the purpose of member rebinding, we don't care about already rebound references.
        return;
      }
      FieldAccessInfoImpl fieldAccessInfo =
          fieldAccessInfoCollection.computeIfAbsent(reboundReference, FieldAccessInfoImpl::new);
      synchronized (fieldAccessInfo) {
        // Record the fact that there is a non-rebound access to the given field. We don't
        // distinguish between non-rebound reads and writes, so we just record it as a read.
        ConcreteAccessContexts accessContexts =
            fieldAccessInfo.getReadsWithContexts().isConcrete()
                ? fieldAccessInfo.getReadsWithContexts().asConcrete()
                : new ConcreteAccessContexts();
        // For the purpose of member rebinding, we don't care about the access contexts, so we
        // simply use the empty set.
        accessContexts.getAccessesWithContexts().put(field, ProgramMethodSet.empty());
      }
    }

    @Override
    public void registerInvokeVirtual(DexMethod method) {
      // Intentionally empty.
    }

    @Override
    public void registerInvokeDirect(DexMethod method) {
      // Intentionally empty.
    }

    @Override
    public void registerInvokeStatic(DexMethod method) {
      // Intentionally empty.
    }

    @Override
    public void registerInvokeInterface(DexMethod method) {
      // Intentionally empty.
    }

    @Override
    public void registerInvokeSuper(DexMethod method) {
      // Intentionally empty.
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
