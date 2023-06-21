// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.CollectionUtils;
import com.android.tools.r8.utils.ForEachable;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class ProgramMethodSet extends DexClassAndMethodSetBase<ProgramMethod> {

  private static final ProgramMethodSet EMPTY = new EmptyProgramMethodSet();

  ProgramMethodSet() {
    super();
  }

  ProgramMethodSet(Map<DexMethod, ProgramMethod> backing) {
    super(backing);
  }

  ProgramMethodSet(int capacity) {
    super(capacity);
  }

  public static ProgramMethodSet create() {
    return new IdentityProgramMethodSet();
  }

  public static ProgramMethodSet create(int capacity) {
    return new IdentityProgramMethodSet(capacity);
  }

  public static ProgramMethodSet create(ProgramMethod element) {
    ProgramMethodSet result = create(1);
    result.add(element);
    return result;
  }

  public static ProgramMethodSet create(ForEachable<ProgramMethod> methods) {
    ProgramMethodSet result = create();
    methods.forEach(result::add);
    return result;
  }

  public static ProgramMethodSet create(ProgramMethodSet methodSet) {
    ProgramMethodSet newMethodSet = create(methodSet.size());
    newMethodSet.addAll(methodSet);
    return newMethodSet;
  }

  public static ProgramMethodSet createConcurrent() {
    return new ConcurrentProgramMethodSet();
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
    GraphLens appliedLens = GraphLens.getIdentityLens();
    List<ProgramMethod> elementsToRemove = null;
    ProgramMethodSet rewrittenMethods = null;
    for (ProgramMethod method : this) {
      ProgramMethod rewrittenMethod = method.rewrittenWithLens(lens, appliedLens, definitions);
      if (rewrittenMethod == null) {
        assert lens.isEnumUnboxerLens();
        // If everything has been unchanged up until now, then record that we should remove this
        // method.
        if (rewrittenMethods == null) {
          if (elementsToRemove == null) {
            elementsToRemove = new ArrayList<>();
          }
          elementsToRemove.add(method);
        }
        continue;
      }
      if (rewrittenMethod == method) {
        if (rewrittenMethods != null) {
          rewrittenMethods.add(rewrittenMethod);
        }
      } else {
        if (rewrittenMethods == null) {
          rewrittenMethods = ProgramMethodSet.create(size());
          CollectionUtils.<ProgramMethod>forEachUntilExclusive(
              this, rewrittenMethods::add, method::isStructurallyEqualTo);
          if (elementsToRemove != null) {
            rewrittenMethods.removeAll(elementsToRemove);
            elementsToRemove = null;
          }
        }
        rewrittenMethods.add(rewrittenMethod);
      }
    }
    if (rewrittenMethods != null) {
      rewrittenMethods.trimCapacityIfSizeLessThan(size());
      return rewrittenMethods;
    } else {
      if (elementsToRemove != null) {
        removeAll(elementsToRemove);
      }
      return this;
    }
  }

  public ProgramMethodSet withoutPrunedItems(PrunedItems prunedItems) {
    removeIf(method -> prunedItems.isRemoved(method.getReference()));
    return this;
  }

  private static class ConcurrentProgramMethodSet extends ProgramMethodSet {

    @Override
    Map<DexMethod, ProgramMethod> createBacking() {
      return new ConcurrentHashMap<>();
    }

    @Override
    Map<DexMethod, ProgramMethod> createBacking(int capacity) {
      return new ConcurrentHashMap<>(capacity);
    }
  }

  private static class EmptyProgramMethodSet extends ProgramMethodSet {

    @Override
    Map<DexMethod, ProgramMethod> createBacking() {
      return ImmutableMap.of();
    }

    @Override
    Map<DexMethod, ProgramMethod> createBacking(int capacity) {
      return ImmutableMap.of();
    }
  }

  private static class IdentityProgramMethodSet extends ProgramMethodSet {

    IdentityProgramMethodSet() {
      super();
    }

    IdentityProgramMethodSet(int capacity) {
      super(capacity);
    }

    @Override
    Map<DexMethod, ProgramMethod> createBacking() {
      return new IdentityHashMap<>();
    }

    @Override
    Map<DexMethod, ProgramMethod> createBacking(int capacity) {
      return new IdentityHashMap<>(capacity);
    }
  }
}
