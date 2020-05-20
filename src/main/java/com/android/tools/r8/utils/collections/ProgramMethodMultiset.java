// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.ProgramMethodEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;
import java.util.function.ObjIntConsumer;

public class ProgramMethodMultiset {

  private final Multiset<Wrapper<ProgramMethod>> backing;

  private ProgramMethodMultiset(Multiset<Wrapper<ProgramMethod>> backing) {
    this.backing = backing;
  }

  public static ProgramMethodMultiset createHash() {
    return new ProgramMethodMultiset(HashMultiset.create());
  }

  public void createAndAdd(DexProgramClass holder, DexEncodedMethod method, int occurrences) {
    backing.add(wrap(new ProgramMethod(holder, method)), occurrences);
  }

  public void forEachEntry(ObjIntConsumer<ProgramMethod> consumer) {
    backing.forEachEntry((wrapper, occurrences) -> consumer.accept(wrapper.get(), occurrences));
  }

  private static Wrapper<ProgramMethod> wrap(ProgramMethod method) {
    return ProgramMethodEquivalence.get().wrap(method);
  }
}
