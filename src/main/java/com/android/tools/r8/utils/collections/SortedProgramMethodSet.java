// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.ForEachable;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;

public abstract class SortedProgramMethodSet extends ProgramMethodSet {

  private static final SortedProgramMethodSet EMPTY = new EmptySortedProgramMethodSet();

  private SortedProgramMethodSet() {
    super();
  }

  public static SortedProgramMethodSet create() {
    return new TreeSortedProgramMethodSet();
  }

  public static SortedProgramMethodSet create(ProgramMethod method) {
    SortedProgramMethodSet result = create();
    result.add(method);
    return result;
  }

  public static SortedProgramMethodSet create(ForEachable<ProgramMethod> methods) {
    SortedProgramMethodSet result = create();
    methods.forEach(result::add);
    return result;
  }

  public static SortedProgramMethodSet createConcurrent() {
    return new ConcurrentSortedProgramMethodSet();
  }

  public static SortedProgramMethodSet empty() {
    return EMPTY;
  }

  @Override
  Map<DexMethod, ProgramMethod> createBacking(int capacity) {
    return createBacking();
  }

  @Override
  public SortedProgramMethodSet rewrittenWithLens(
      DexDefinitionSupplier definitions, GraphLens lens) {
    GraphLens appliedLens = GraphLens.getIdentityLens();
    return create(
        consumer ->
            forEach(
                method ->
                    consumer.accept(method.rewrittenWithLens(lens, appliedLens, definitions))));
  }

  @Override
  public Set<DexEncodedMethod> toDefinitionSet() {
    Comparator<DexEncodedMethod> comparator = Comparator.comparing(DexEncodedMethod::getReference);
    Set<DexEncodedMethod> definitions = new TreeSet<>(comparator);
    forEach(method -> definitions.add(method.getDefinition()));
    return definitions;
  }

  private static class ConcurrentSortedProgramMethodSet extends SortedProgramMethodSet {

    @Override
    Map<DexMethod, ProgramMethod> createBacking() {
      return new ConcurrentSkipListMap<>(DexMethod::compareTo);
    }
  }

  private static class EmptySortedProgramMethodSet extends SortedProgramMethodSet {

    @Override
    Map<DexMethod, ProgramMethod> createBacking() {
      return Collections.emptyMap();
    }
  }

  private static class TreeSortedProgramMethodSet extends SortedProgramMethodSet {

    @Override
    Map<DexMethod, ProgramMethod> createBacking() {
      return new TreeMap<>(DexMethod::compareTo);
    }
  }
}
