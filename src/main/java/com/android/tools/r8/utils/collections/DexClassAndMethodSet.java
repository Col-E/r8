// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.google.common.collect.ImmutableMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

public class DexClassAndMethodSet extends DexClassAndMethodSetBase<DexClassAndMethod> {

  private static final DexClassAndMethodSet EMPTY = new DexClassAndMethodSet(ImmutableMap::of);

  protected DexClassAndMethodSet(
      Supplier<? extends Map<DexMethod, DexClassAndMethod>> backingFactory) {
    super(backingFactory);
  }

  protected DexClassAndMethodSet(
      Supplier<? extends Map<DexMethod, DexClassAndMethod>> backingFactory,
      Map<DexMethod, DexClassAndMethod> backing) {
    super(backingFactory, backing);
  }

  public static DexClassAndMethodSet create() {
    return new DexClassAndMethodSet(IdentityHashMap::new);
  }

  public static DexClassAndMethodSet create(int capacity) {
    return new DexClassAndMethodSet(IdentityHashMap::new, new IdentityHashMap<>(capacity));
  }

  public static DexClassAndMethodSet create(DexClassAndMethod element) {
    DexClassAndMethodSet result = create();
    result.add(element);
    return result;
  }

  public static DexClassAndMethodSet create(DexClassAndMethodSet methodSet) {
    DexClassAndMethodSet newMethodSet = create();
    newMethodSet.addAll(methodSet);
    return newMethodSet;
  }

  public static DexClassAndMethodSet createConcurrent() {
    return new DexClassAndMethodSet(ConcurrentHashMap::new);
  }

  public static DexClassAndMethodSet createLinked() {
    return new DexClassAndMethodSet(LinkedHashMap::new);
  }

  public static DexClassAndMethodSet empty() {
    return EMPTY;
  }

  public void addAll(DexClassAndMethodSet methods) {
    backing.putAll(methods.backing);
  }
}
