// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.structural.RepresentativeMap;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;

/**
 * Base type for the definition of a synthetic item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
abstract class SyntheticDefinition<
    R extends SyntheticReference<R, D, C>,
    D extends SyntheticDefinition<R, D, C>,
    C extends DexClass> {

  private final SyntheticKind kind;
  private final SynthesizingContext context;

  SyntheticDefinition(SyntheticKind kind, SynthesizingContext context) {
    assert kind != null;
    assert context != null;
    this.kind = kind;
    this.context = context;
  }

  public boolean isClasspathDefinition() {
    return false;
  }

  public SyntheticClasspathDefinition asClasspathDefinition() {
    return null;
  }

  public boolean isProgramDefinition() {
    return false;
  }

  public SyntheticProgramDefinition asProgramDefinition() {
    return null;
  }

  abstract R toReference();

  final SyntheticKind getKind() {
    return kind;
  }

  final SynthesizingContext getContext() {
    return context;
  }

  public abstract C getHolder();

  final HashCode computeHash(RepresentativeMap map, boolean intermediate) {
    Hasher hasher = Hashing.murmur3_128().newHasher();
    if (intermediate) {
      // If in intermediate mode, include the context type as sharing is restricted to within a
      // single context.
      getContext().getSynthesizingContextType().hashWithTypeEquivalence(hasher, map);
    }
    internalComputeHash(hasher, map);
    return hasher.hash();
  }

  abstract void internalComputeHash(Hasher hasher, RepresentativeMap map);

  final boolean isEquivalentTo(D other, boolean includeContext, GraphLens graphLens) {
    return compareTo(other, includeContext, graphLens) == 0;
  }

  int compareTo(D other, boolean includeContext, GraphLens graphLens) {
    if (includeContext) {
      int order = getContext().compareTo(other.getContext());
      if (order != 0) {
        return order;
      }
    }
    DexType thisType = getHolder().getType();
    DexType otherType = other.getHolder().getType();
    RepresentativeMap map = null;
    // If the synthetics have been moved include the original types in the equivalence.
    if (graphLens.isNonIdentityLens()) {
      DexType thisOrigType = graphLens.getOriginalType(thisType);
      DexType otherOrigType = graphLens.getOriginalType(otherType);
      if (thisType != thisOrigType || otherType != otherOrigType) {
        map =
            t -> {
              if (t == otherType || t == thisOrigType || t == otherOrigType) {
                return thisType;
              }
              return t;
            };
      }
    }
    if (map == null) {
      map = t -> t == otherType ? thisType : t;
    }
    return internalCompareTo(other, map);
  }

  abstract int internalCompareTo(D other, RepresentativeMap map);

  public abstract boolean isValid();
}
