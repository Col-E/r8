// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.google.common.hash.HashCode;
import com.google.common.hash.Hasher;
import com.google.common.hash.Hashing;
import java.util.Comparator;

/**
 * Definition of a synthetic method item.
 *
 * <p>This class is internal to the synthetic items collection, thus package-protected.
 */
class SyntheticMethodDefinition extends SyntheticDefinition
    implements Comparable<SyntheticMethodDefinition> {

  private final ProgramMethod method;

  SyntheticMethodDefinition(SynthesizingContext context, ProgramMethod method) {
    super(context);
    this.method = method;
  }

  public ProgramMethod getMethod() {
    return method;
  }

  @Override
  SyntheticReference toReference() {
    return new SyntheticMethodReference(getContext(), method.getReference());
  }

  @Override
  DexProgramClass getHolder() {
    return method.getHolder();
  }

  @Override
  HashCode computeHash() {
    Hasher hasher = Hashing.sha256().newHasher();
    method.getDefinition().hashSyntheticContent(hasher);
    return hasher.hash();
  }

  @Override
  boolean isEquivalentTo(SyntheticDefinition other) {
    if (!(other instanceof SyntheticMethodDefinition)) {
      return false;
    }
    SyntheticMethodDefinition o = (SyntheticMethodDefinition) other;
    return method.getDefinition().isSyntheticContentEqual(o.method.getDefinition());
  }

  // Since methods are sharable they must define an order from which representatives can be found.
  @Override
  public int compareTo(SyntheticMethodDefinition other) {
    return Comparator.comparing(SyntheticMethodDefinition::getContext)
        .thenComparing(m -> m.method.getDefinition(), DexEncodedMethod::syntheticCompareTo)
        .compare(this, other);
  }

  @Override
  public String toString() {
    return "SyntheticMethodDefinition{" + method + '}';
  }
}
