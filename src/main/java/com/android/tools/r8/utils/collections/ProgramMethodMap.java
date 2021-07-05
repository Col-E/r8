// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.ProgramMethodEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ProgramMethodMap<V> extends ProgramMemberMap<ProgramMethod, V> {

  private ProgramMethodMap(Supplier<Map<Wrapper<ProgramMethod>, V>> backingFactory) {
    super(backingFactory);
  }

  public static <V> ProgramMethodMap<V> create() {
    return new ProgramMethodMap<>(HashMap::new);
  }

  public static <V> ProgramMethodMap<V> createConcurrent() {
    return new ProgramMethodMap<>(ConcurrentHashMap::new);
  }

  @Override
  Wrapper<ProgramMethod> wrap(ProgramMethod method) {
    return ProgramMethodEquivalence.get().wrap(method);
  }
}
