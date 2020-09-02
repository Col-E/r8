// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import java.util.Set;
import java.util.function.BiConsumer;

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

  ObjectAllocationInfoCollection rewrittenWithLens(
      DexDefinitionSupplier definitions, GraphLens lens);
}
