// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.IntFunction;

public class LongLivedProgramMethodSetBuilder<T extends ProgramMethodSet> {

  private final IntFunction<T> factory;
  private final Set<DexMethod> methods = Sets.newIdentityHashSet();

  private LongLivedProgramMethodSetBuilder(IntFunction<T> factory) {
    this.factory = factory;
  }

  public static LongLivedProgramMethodSetBuilder<?> create() {
    return new LongLivedProgramMethodSetBuilder<>(ProgramMethodSet::create);
  }

  public static LongLivedProgramMethodSetBuilder<SortedProgramMethodSet> createSorted() {
    return new LongLivedProgramMethodSetBuilder<>(ignore -> SortedProgramMethodSet.create());
  }

  public void add(ProgramMethod method) {
    methods.add(method.getReference());
  }

  public void addAll(Iterable<ProgramMethod> methods) {
    methods.forEach(this::add);
  }

  public void rewrittenWithLens(AppView<AppInfoWithLiveness> appView, GraphLense applied) {
    Set<DexMethod> newMethods = Sets.newIdentityHashSet();
    for (DexMethod method : methods) {
      newMethods.add(appView.graphLense().getRenamedMethodSignature(method, applied));
    }
    methods.clear();
    methods.addAll(newMethods);
  }

  public T build(AppView<AppInfoWithLiveness> appView) {
    return build(appView, null);
  }

  public T build(AppView<AppInfoWithLiveness> appView, GraphLense applied) {
    T result = factory.apply(methods.size());
    for (DexMethod oldMethod : methods) {
      DexMethod method = appView.graphLense().getRenamedMethodSignature(oldMethod, applied);
      DexProgramClass holder = appView.definitionForHolder(method).asProgramClass();
      result.createAndAdd(holder, holder.lookupMethod(method));
    }
    return result;
  }

  public boolean isEmpty() {
    return methods.isEmpty();
  }
}
