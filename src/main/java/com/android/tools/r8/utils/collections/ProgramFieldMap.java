// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.utils.ProgramFieldEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class ProgramFieldMap<V> extends ProgramMemberMap<ProgramField, V> {

  private ProgramFieldMap(Supplier<Map<Wrapper<ProgramField>, V>> backingFactory) {
    super(backingFactory);
  }

  public static <V> ProgramFieldMap<V> create() {
    return new ProgramFieldMap<>(HashMap::new);
  }

  @Override
  Wrapper<ProgramField> wrap(ProgramField method) {
    return ProgramFieldEquivalence.get().wrap(method);
  }
}
