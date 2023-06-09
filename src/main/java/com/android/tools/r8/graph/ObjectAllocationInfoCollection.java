// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.desugar.LambdaDescriptor;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Provides immutable access to {@link ObjectAllocationInfoCollectionImpl}, which stores the set of
 * instantiated classes along with their allocation sites.
 */
public interface ObjectAllocationInfoCollection {

  void forEachClassWithKnownAllocationSites(
      BiConsumer<DexProgramClass, Set<DexEncodedMethod>> consumer);

  boolean hasInstantiatedStrictSubtype(DexProgramClass clazz);

  boolean isAllocationSitesKnown(DexProgramClass clazz);

  boolean isInstantiatedDirectly(DexProgramClass clazz);

  boolean isInstantiatedDirectlyOrHasInstantiatedSubtype(DexProgramClass clazz);

  boolean isInterfaceWithUnknownSubtypeHierarchy(DexProgramClass clazz);

  boolean isImmediateInterfaceOfInstantiatedLambda(DexProgramClass clazz);

  void forEachInstantiatedLambdaInterfaces(Consumer<DexType> consumer);

  TraversalContinuation<?, ?> traverseInstantiatedSubtypes(
      DexType type,
      Function<DexProgramClass, TraversalContinuation<?, ?>> onClass,
      Function<LambdaDescriptor, TraversalContinuation<?, ?>> onLambda,
      AppInfo appInfo);

  ObjectAllocationInfoCollection rewrittenWithLens(
      DexDefinitionSupplier definitions, GraphLens lens, Timing timing);
}
