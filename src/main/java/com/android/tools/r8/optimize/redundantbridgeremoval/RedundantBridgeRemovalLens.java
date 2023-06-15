// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.redundantbridgeremoval;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.DefaultNonIdentityGraphLens;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class RedundantBridgeRemovalLens extends DefaultNonIdentityGraphLens {

  private final Set<DexType> interfaces;
  private final Map<DexMethod, DexMethod> methodMap;

  public RedundantBridgeRemovalLens(
      AppView<?> appView, Set<DexType> interfaces, Map<DexMethod, DexMethod> methodMap) {
    super(appView);
    this.interfaces = interfaces;
    this.methodMap = methodMap;
  }

  // Methods.

  @Override
  public DexMethod getNextMethodSignature(DexMethod method) {
    return methodMap.getOrDefault(method, method);
  }

  @Override
  protected MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    if (methodMap.containsKey(previous.getReference())) {
      DexMethod newReference = previous.getReference();
      do {
        newReference = methodMap.get(newReference);
      } while (methodMap.containsKey(newReference));
      boolean holderTypeIsInterface = interfaces.contains(newReference.getHolderType());
      if (previous.getType().isSuper() && holderTypeIsInterface) {
        return previous;
      }
      return MethodLookupResult.builder(this)
          .setReference(newReference)
          .setReboundReference(newReference)
          .setPrototypeChanges(previous.getPrototypeChanges())
          .setType(
              holderTypeIsInterface && previous.getType().isVirtual()
                  ? InvokeType.INTERFACE
                  : previous.getType())
          .build();
    }
    return previous;
  }

  public static class Builder {

    private final Set<DexType> interfaces = Sets.newIdentityHashSet();
    private final Map<DexMethod, DexMethod> methodMap = new IdentityHashMap<>();

    public synchronized Builder map(ProgramMethod from, DexClassAndMethod to) {
      methodMap.put(from.getReference(), to.getReference());
      if (to.getHolder().isInterface()) {
        interfaces.add(to.getHolderType());
      }
      return this;
    }

    public boolean isEmpty() {
      return methodMap.isEmpty();
    }

    public RedundantBridgeRemovalLens build(AppView<?> appView) {
      return new RedundantBridgeRemovalLens(appView, interfaces, methodMap);
    }
  }
}
