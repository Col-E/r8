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

public abstract class DexClassAndMethodSet extends DexClassAndMethodSetBase<DexClassAndMethod> {

  private static final DexClassAndMethodSet EMPTY = new EmptyDexClassAndMethodSet();

  DexClassAndMethodSet() {
    super();
  }

  public static DexClassAndMethodSet create() {
    return new IdentityDexClassAndMethodSet();
  }

  public static DexClassAndMethodSet createConcurrent() {
    return new ConcurrentDexClassAndMethodSet();
  }

  public static DexClassAndMethodSet createLinked() {
    return new LinkedDexClassAndMethodSet();
  }

  public static DexClassAndMethodSet empty() {
    return EMPTY;
  }

  public void addAll(DexClassAndMethodSet methods) {
    backing.putAll(methods.backing);
  }

  private static class ConcurrentDexClassAndMethodSet extends DexClassAndMethodSet {

    @Override
    Map<DexMethod, DexClassAndMethod> createBacking() {
      return new ConcurrentHashMap<>();
    }

    @Override
    Map<DexMethod, DexClassAndMethod> createBacking(int capacity) {
      return new ConcurrentHashMap<>(capacity);
    }
  }

  private static class EmptyDexClassAndMethodSet extends DexClassAndMethodSet {

    @Override
    Map<DexMethod, DexClassAndMethod> createBacking() {
      return ImmutableMap.of();
    }

    @Override
    Map<DexMethod, DexClassAndMethod> createBacking(int capacity) {
      return ImmutableMap.of();
    }
  }

  private static class IdentityDexClassAndMethodSet extends DexClassAndMethodSet {

    @Override
    Map<DexMethod, DexClassAndMethod> createBacking() {
      return new IdentityHashMap<>();
    }

    @Override
    Map<DexMethod, DexClassAndMethod> createBacking(int capacity) {
      return new IdentityHashMap<>(capacity);
    }
  }

  private static class LinkedDexClassAndMethodSet extends DexClassAndMethodSet {

    @Override
    Map<DexMethod, DexClassAndMethod> createBacking() {
      return new LinkedHashMap<>();
    }

    @Override
    Map<DexMethod, DexClassAndMethod> createBacking(int capacity) {
      return new LinkedHashMap<>(capacity);
    }
  }
}
