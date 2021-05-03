// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.synthesis;

import com.android.tools.r8.FeatureSplit;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens.NonIdentityGraphLens;
import java.util.Set;

public class LegacySyntheticReference implements Rewritable<LegacySyntheticReference> {
  private final DexType type;
  private final Set<DexType> contexts;
  private final FeatureSplit featureSplit;

  public LegacySyntheticReference(DexType type, Set<DexType> contexts, FeatureSplit featureSplit) {
    this.type = type;
    this.contexts = contexts;
    this.featureSplit = featureSplit;
  }

  @Override
  public DexType getHolder() {
    return type;
  }

  public Set<DexType> getContexts() {
    return contexts;
  }

  public FeatureSplit getFeatureSplit() {
    return featureSplit;
  }

  @Override
  public LegacySyntheticReference rewrite(NonIdentityGraphLens lens) {
    DexType rewrittenType = lens.lookupType(type);
    Set<DexType> rewrittenContexts = lens.rewriteTypes(getContexts());
    if (type == rewrittenType && contexts.equals(rewrittenContexts)) {
      return this;
    }
    return new LegacySyntheticReference(rewrittenType, rewrittenContexts, featureSplit);
  }
}
