// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.ForEachable;
import com.google.common.collect.ImmutableMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class ProgramMethodSet extends DexClassAndMethodSetBase<ProgramMethod> {

  private static final ProgramMethodSet EMPTY = new ProgramMethodSet(ImmutableMap::of);

  protected ProgramMethodSet(Supplier<? extends Map<DexMethod, ProgramMethod>> backingFactory) {
    super(backingFactory);
  }

  protected ProgramMethodSet(
      Supplier<? extends Map<DexMethod, ProgramMethod>> backingFactory,
      Map<DexMethod, ProgramMethod> backing) {
    super(backingFactory, backing);
  }

  public static ProgramMethodSet create() {
    return new ProgramMethodSet(IdentityHashMap::new);
  }

  public static ProgramMethodSet create(int capacity) {
    return new ProgramMethodSet(IdentityHashMap::new, new IdentityHashMap<>(capacity));
  }

  public static ProgramMethodSet create(ProgramMethod element) {
    ProgramMethodSet result = create();
    result.add(element);
    return result;
  }

  public static ProgramMethodSet create(ForEachable<ProgramMethod> methods) {
    ProgramMethodSet result = create();
    methods.forEach(result::add);
    return result;
  }

  public static ProgramMethodSet create(ProgramMethodSet methodSet) {
    ProgramMethodSet newMethodSet = create();
    newMethodSet.addAll(methodSet);
    return newMethodSet;
  }

  public static ProgramMethodSet createConcurrent() {
    return new ProgramMethodSet(ConcurrentHashMap::new);
  }

  public static LinkedProgramMethodSet createLinked() {
    return new LinkedProgramMethodSet();
  }

  public static LinkedProgramMethodSet createLinked(int capacity) {
    return new LinkedProgramMethodSet(capacity);
  }

  public static ProgramMethodSet empty() {
    return EMPTY;
  }

  public void addAll(ProgramMethodSet methods) {
    backing.putAll(methods.backing);
  }

  public boolean createAndAdd(DexProgramClass clazz, DexEncodedMethod definition) {
    return add(new ProgramMethod(clazz, definition));
  }

  public ProgramMethodSet rewrittenWithLens(DexDefinitionSupplier definitions, GraphLens lens) {
    ProgramMethodSet rewritten = new ProgramMethodSet(backingFactory);
    forEach(
        method -> {
          ProgramMethod newMethod = lens.mapProgramMethod(method, definitions);
          if (newMethod != null) {
            rewritten.add(newMethod);
          }
        });
    return rewritten;
  }
}
