// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.utils.ForEachable;
import com.android.tools.r8.utils.ForEachableUtils;
import java.util.Comparator;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

public class SortedProgramMethodSet extends ProgramMethodSet {

  private SortedProgramMethodSet(TreeMap<DexMethod, ProgramMethod> backing) {
    super(backing);
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
        new SortedProgramMethodSet(new TreeMap<>(DexMethod::slowCompareTo));
    methods.forEach(result::add);
    return result;
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
