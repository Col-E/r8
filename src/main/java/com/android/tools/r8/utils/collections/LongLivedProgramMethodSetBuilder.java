// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DeterminismChecker.LineCallback;
import com.android.tools.r8.utils.SetUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.IntFunction;
import java.util.function.Predicate;

public class LongLivedProgramMethodSetBuilder<T extends ProgramMethodSet> {

  // Factory for creating the final ProgramMethodSet.
  private final IntFunction<T> factory;

  // Factory for creating a Set<DexMethod>.
  private final IntFunction<Set<DexMethod>> factoryForBuilder;

  // The graph lens that this collection has been rewritten up until.
  private GraphLens appliedGraphLens;

  // The methods in this collection.
  private Set<DexMethod> methods;

  private LongLivedProgramMethodSetBuilder(
      GraphLens currentGraphLens,
      IntFunction<T> factory,
      IntFunction<Set<DexMethod>> factoryBuilder) {
    this.appliedGraphLens = currentGraphLens;
    this.factory = factory;
    this.factoryForBuilder = factoryBuilder;
    this.methods = factoryBuilder.apply(2);
  }

  public static LongLivedProgramMethodSetBuilder<ProgramMethodSet> createForIdentitySet(
      GraphLens currentGraphLens) {
    return new LongLivedProgramMethodSetBuilder<>(
        currentGraphLens, ProgramMethodSet::create, SetUtils::newIdentityHashSet);
  }

  public static LongLivedProgramMethodSetBuilder<ProgramMethodSet> createConcurrentForIdentitySet(
      GraphLens currentGraphLens) {
    return new LongLivedProgramMethodSetBuilder<>(
        currentGraphLens, ProgramMethodSet::create, SetUtils::newConcurrentHashSet);
  }

  @Deprecated
  public void add(ProgramMethod method) {
    methods.add(method.getReference());
  }

  public void add(ProgramMethod method, GraphLens currentGraphLens) {
    // All methods in a long lived program method set should be rewritten up until the same graph
    // lens.
    assert verifyIsRewrittenWithLens(currentGraphLens);
    methods.add(method.getReference());
  }

  public void addAll(Iterable<ProgramMethod> methodsToAdd, GraphLens currentGraphLens) {
    assert verifyIsRewrittenWithLens(currentGraphLens);
    methodsToAdd.forEach(method -> add(method, currentGraphLens));
  }

  public void clear() {
    methods.clear();
  }

  public boolean contains(ProgramMethod method, GraphLens currentGraphLens) {
    // We can only query a long lived program method set that is fully lens rewritten.
    assert verifyIsRewrittenWithLens(currentGraphLens);
    return methods.contains(method.getReference());
  }

  public boolean isRewrittenWithLens(GraphLens graphLens) {
    return appliedGraphLens == graphLens;
  }

  public LongLivedProgramMethodSetBuilder<T> merge(LongLivedProgramMethodSetBuilder<T> builder) {
    // Check that the two builders are rewritten up until the same lens (if not we could rewrite the
    // methods in the given builder up until the applied graph lens of this builder, but it must be
    // such that this builder has the same or a newer graph lens than the given builder).
    if (isRewrittenWithLens(builder.appliedGraphLens)) {
      methods.addAll(builder.methods);
    } else {
      // Rewrite the methods in the given builder up until the applied graph lens of this builder.
      // Note that this builder must have a newer graph lens than the given builder.
      assert verifyIsRewrittenWithNewerLens(builder.appliedGraphLens);
      for (DexMethod method : builder.methods) {
        methods.add(appliedGraphLens.getRenamedMethodSignature(method, builder.appliedGraphLens));
      }
    }
    return this;
  }

  @Deprecated
  public void remove(DexMethod method) {
    methods.remove(method);
  }

  public void remove(DexMethod method, GraphLens currentGraphLens) {
    assert isEmpty() || verifyIsRewrittenWithLens(currentGraphLens);
    methods.remove(method);
  }

  public LongLivedProgramMethodSetBuilder<T> removeAll(Iterable<DexMethod> methods) {
    methods.forEach(this::remove);
    return this;
  }

  public LongLivedProgramMethodSetBuilder<T> removeIf(
      DexDefinitionSupplier definitions, Predicate<ProgramMethod> predicate) {
    methods.removeIf(
        method -> {
          DexProgramClass holder =
              asProgramClassOrNull(definitions.definitionFor(method.getHolderType()));
          ProgramMethod definition = method.lookupOnProgramClass(holder);
          if (definition == null) {
            assert false;
            return true;
          }
          return predicate.test(definition);
        });
    return this;
  }

  public LongLivedProgramMethodSetBuilder<T> rewrittenWithLens(
      AppView<AppInfoWithLiveness> appView) {
    return rewrittenWithLens(appView.graphLens());
  }

  public LongLivedProgramMethodSetBuilder<T> rewrittenWithLens(GraphLens newGraphLens) {
    // Check if the graph lens has changed (otherwise lens rewriting is not needed).
    if (newGraphLens == appliedGraphLens) {
      return this;
    }

    // Rewrite the backing.
    Set<DexMethod> newMethods = factoryForBuilder.apply(methods.size());
    for (DexMethod method : methods) {
      newMethods.add(newGraphLens.getRenamedMethodSignature(method, appliedGraphLens));
    }
    methods = newMethods;

    // Record that this collection is now rewritten up until the given graph lens.
    appliedGraphLens = newGraphLens;
    return this;
  }

  public T build(AppView<AppInfoWithLiveness> appView) {
    T result = factory.apply(methods.size());
    for (DexMethod method : methods) {
      DexMethod rewrittenMethod =
          appView.graphLens().getRenamedMethodSignature(method, appliedGraphLens);
      DexProgramClass holder = appView.definitionForHolder(rewrittenMethod).asProgramClass();
      result.createAndAdd(holder, holder.lookupMethod(rewrittenMethod));
    }
    return result;
  }

  public boolean isEmpty() {
    return methods.isEmpty();
  }

  public boolean verifyIsRewrittenWithLens(GraphLens graphLens) {
    assert isRewrittenWithLens(graphLens);
    return true;
  }

  public boolean verifyIsRewrittenWithNewerLens(GraphLens graphLens) {
    assert appliedGraphLens != graphLens;
    assert appliedGraphLens.isNonIdentityLens();
    assert graphLens.isIdentityLens()
        || appliedGraphLens.asNonIdentityLens().findPrevious(previous -> previous == graphLens)
            != null;
    return true;
  }

  public void dump(LineCallback lineCallback) throws IOException {
    List<DexMethod> sortedMethods = new ArrayList<>(methods);
    sortedMethods.sort(DexMethod::compareTo);
    for (DexMethod method : sortedMethods) {
      lineCallback.onLine(method.toSourceString());
    }
  }
}
