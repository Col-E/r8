// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.IntFunction;

public class LongLivedProgramMethodSetBuilder {

  private Set<DexMethod> methods = Sets.newIdentityHashSet();

  public LongLivedProgramMethodSetBuilder() {}

  public void add(ProgramMethod method) {
    methods.add(method.getReference());
  }

  public void addAll(Iterable<ProgramMethod> methods) {
    methods.forEach(this::add);
  }

  public ProgramMethodSet build(AppView<AppInfoWithLiveness> appView) {
    return build(appView, ProgramMethodSet::create);
  }

  public <T extends ProgramMethodSet> T build(
      AppView<AppInfoWithLiveness> appView, IntFunction<T> factory) {
    T result = factory.apply(methods.size());
    for (DexMethod oldMethod : methods) {
      DexMethod method = appView.graphLense().getRenamedMethodSignature(oldMethod);
      DexProgramClass holder = appView.definitionForHolder(method).asProgramClass();
      result.createAndAdd(holder, holder.lookupMethod(method));
    }
    return result;
  }

  @Deprecated
  public ProgramMethodSet buildRaw(AppView<AppInfoWithLiveness> appView) {
    ProgramMethodSet result = ProgramMethodSet.create();
    for (DexMethod method : methods) {
      DexProgramClass holder = appView.definitionForHolder(method).asProgramClass();
      result.createAndAdd(holder, holder.lookupMethod(method));
    }
    return result;
  }

  public boolean isEmpty() {
    return methods.isEmpty();
  }
}
