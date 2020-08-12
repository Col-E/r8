// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.IntFunction;

public class LongLivedProgramMethodSetBuilder<T extends ProgramMethodSet> {

  private final IntFunction<T> factory;
  private final Set<DexMethod> methods;

  private LongLivedProgramMethodSetBuilder(IntFunction<T> factory, Set<DexMethod> methods) {
    this.factory = factory;
    this.methods = methods;
  }

  public static LongLivedProgramMethodSetBuilder<?> createForIdentitySet() {
    return new LongLivedProgramMethodSetBuilder<>(
        ProgramMethodSet::create, Sets.newIdentityHashSet());
  }

  public static LongLivedProgramMethodSetBuilder<SortedProgramMethodSet> createForSortedSet() {
    return new LongLivedProgramMethodSetBuilder<>(
        ignore -> SortedProgramMethodSet.create(), Sets.newIdentityHashSet());
  }

  public static LongLivedProgramMethodSetBuilder<?> createConcurrentForIdentitySet() {
    return new LongLivedProgramMethodSetBuilder<>(
        ignore -> ProgramMethodSet.create(), Sets.newConcurrentHashSet());
  }

  public void add(ProgramMethod method) {
    methods.add(method.getReference());
  }

  public void addAll(Iterable<ProgramMethod> methods) {
    methods.forEach(this::add);
  }

  public void rewrittenWithLens(AppView<AppInfoWithLiveness> appView, GraphLens applied) {
    Set<DexMethod> newMethods = Sets.newIdentityHashSet();
    for (DexMethod method : methods) {
      newMethods.add(appView.graphLens().getRenamedMethodSignature(method, applied));
    }
    methods.clear();
    methods.addAll(newMethods);
  }

  public T build(AppView<AppInfoWithLiveness> appView) {
    return build(appView, null);
  }

  public T build(AppView<AppInfoWithLiveness> appView, GraphLens applied) {
    T result = factory.apply(methods.size());
    for (DexMethod oldMethod : methods) {
      DexMethod method = appView.graphLens().getRenamedMethodSignature(oldMethod, applied);
      DexProgramClass holder = appView.definitionForHolder(method).asProgramClass();
      result.createAndAdd(holder, holder.lookupMethod(method));
    }
    return result;
  }

  public boolean isEmpty() {
    return methods.isEmpty();
  }
}
