// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

public class LongLivedProgramMethodMultisetBuilder {

  private final Multiset<DexMethod> backing = HashMultiset.create();

  private LongLivedProgramMethodMultisetBuilder() {}

  public static LongLivedProgramMethodMultisetBuilder create() {
    return new LongLivedProgramMethodMultisetBuilder();
  }

  public void add(ProgramMethod method) {
    backing.add(method.getReference());
  }

  public int size() {
    return backing.size();
  }

  public ProgramMethodMultiset build(AppView<AppInfoWithLiveness> appView) {
    ProgramMethodMultiset result = ProgramMethodMultiset.createHash();
    backing.forEachEntry(
        (oldMethod, occurrences) -> {
          DexMethod method = appView.graphLens().getRenamedMethodSignature(oldMethod);
          DexProgramClass holder = appView.definitionForHolder(method).asProgramClass();
          result.createAndAdd(holder, holder.lookupMethod(method), occurrences);
        });
    return result;
  }
}
