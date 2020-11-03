// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.ComparatorUtils;
import com.android.tools.r8.utils.ForEachable;
import com.android.tools.r8.utils.ForEachableUtils;
import java.util.Comparator;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.function.Supplier;

public class SortedProgramMethodSet extends ProgramMethodSet {

  private static final SortedProgramMethodSet EMPTY =
      new SortedProgramMethodSet(() -> new TreeMap<>(ComparatorUtils.unreachableComparator()));

  private SortedProgramMethodSet(Supplier<SortedMap<DexMethod, ProgramMethod>> backingFactory) {
    super(backingFactory);
  }

  public static SortedProgramMethodSet create() {
    return create(ForEachableUtils.empty());
  }

  public static SortedProgramMethodSet create(ProgramMethod method) {
    SortedProgramMethodSet result = create();
    result.add(method);
    return result;
  }

  public static SortedProgramMethodSet create(ForEachable<ProgramMethod> methods) {
    SortedProgramMethodSet result =
        new SortedProgramMethodSet(() -> new TreeMap<>(DexMethod::slowCompareTo));
    methods.forEach(result::add);
    return result;
  }

  public static SortedProgramMethodSet createConcurrent() {
    return new SortedProgramMethodSet(() -> new ConcurrentSkipListMap<>(DexMethod::slowCompareTo));
  }

  public static SortedProgramMethodSet empty() {
    return EMPTY;
  }

  @Override
  public SortedProgramMethodSet rewrittenWithLens(
      DexDefinitionSupplier definitions, GraphLens lens) {
    return create(
        consumer -> forEach(method -> consumer.accept(lens.mapProgramMethod(method, definitions))));
  }

  @Override
  public Set<DexEncodedMethod> toDefinitionSet() {
    Comparator<DexEncodedMethod> comparator =
        (x, y) -> x.getReference().slowCompareTo(y.getReference());
    Set<DexEncodedMethod> definitions = new TreeSet<>(comparator);
    forEach(method -> definitions.add(method.getDefinition()));
    return definitions;
  }
}
