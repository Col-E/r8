// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.google.common.base.Equivalence;
import com.google.common.base.Equivalence.Wrapper;
import java.util.HashSet;
import java.util.Set;

class ScopedDexMethodSet {

  private static Equivalence<DexMethod> METHOD_EQUIVALENCE = MethodSignatureEquivalence.get();

  private final ScopedDexMethodSet parent;
  private final Set<Wrapper<DexMethod>> items = new HashSet<>();

  public ScopedDexMethodSet() {
    this(null);
  }

  private ScopedDexMethodSet(ScopedDexMethodSet parent) {
    this.parent = parent;
  }

  public ScopedDexMethodSet newNestedScope() {
    return new ScopedDexMethodSet(this);
  }

  private boolean contains(Wrapper<DexMethod> item) {
    return items.contains(item)
        || ((parent != null) && parent.contains(item));
  }

  public boolean addMethod(DexMethod method) {
    Wrapper<DexMethod> wrapped = METHOD_EQUIVALENCE.wrap(method);
    return !contains(wrapped) && items.add(wrapped);
  }

  public ScopedDexMethodSet getParent() {
    return parent;
  }
}
