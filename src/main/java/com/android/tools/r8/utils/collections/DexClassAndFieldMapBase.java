// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.utils.DexClassAndFieldEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import java.util.Map;
import java.util.function.Supplier;

public abstract class DexClassAndFieldMapBase<K extends DexClassAndField, V>
    extends DexClassAndMemberMap<K, V> {

  DexClassAndFieldMapBase(Supplier<Map<Wrapper<K>, V>> backingFactory) {
    super(backingFactory);
  }

  @Override
  Wrapper<K> wrap(K field) {
    return DexClassAndFieldEquivalence.get().wrap(field);
  }
}
