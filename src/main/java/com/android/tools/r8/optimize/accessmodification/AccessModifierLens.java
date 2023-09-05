// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.accessmodification;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.lens.DefaultNonIdentityGraphLens;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneHashMap;
import com.android.tools.r8.utils.collections.BidirectionalOneToOneMap;
import com.android.tools.r8.utils.collections.MutableBidirectionalOneToOneMap;
import java.util.Set;

public class AccessModifierLens extends DefaultNonIdentityGraphLens {

  private final BidirectionalOneToOneMap<DexMethod, DexMethod> methodMap;

  // Private interface methods that have been publicized. Invokes targeting these methods must be
  // rewritten from invoke-direct to invoke-interface.
  private final Set<DexMethod> publicizedPrivateInterfaceMethods;

  // Private class methods that have been publicized. Invokes targeting these methods must be
  // rewritten from invoke-direct to invoke-virtual.
  private final Set<DexMethod> publicizedPrivateVirtualMethods;

  AccessModifierLens(
      AppView<AppInfoWithLiveness> appView,
      BidirectionalOneToOneMap<DexMethod, DexMethod> methodMap,
      Set<DexMethod> publicizedPrivateInterfaceMethods,
      Set<DexMethod> publicizedPrivateVirtualMethods) {
    super(appView);
    this.methodMap = methodMap;
    this.publicizedPrivateInterfaceMethods = publicizedPrivateInterfaceMethods;
    this.publicizedPrivateVirtualMethods = publicizedPrivateVirtualMethods;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public DexMethod getNextMethodSignature(DexMethod method) {
    return methodMap.getOrDefault(method, method);
  }

  @Override
  public DexMethod getPreviousMethodSignature(DexMethod method) {
    return methodMap.getRepresentativeKeyOrDefault(method, method);
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public MethodLookupResult internalDescribeLookupMethod(
      MethodLookupResult previous, DexMethod context, GraphLens codeLens) {
    assert !previous.hasReboundReference();
    DexMethod newMethod = getNextMethodSignature(previous.getReference());
    InvokeType newInvokeType = previous.getType();
    if (previous.getType() == InvokeType.DIRECT) {
      if (publicizedPrivateInterfaceMethods.contains(newMethod)) {
        newInvokeType = InvokeType.INTERFACE;
      } else if (publicizedPrivateVirtualMethods.contains(newMethod)) {
        newInvokeType = InvokeType.VIRTUAL;
      }
    }
    if (newInvokeType != previous.getType() || newMethod != previous.getReference()) {
      return MethodLookupResult.builder(this)
          .setReference(newMethod)
          .setPrototypeChanges(previous.getPrototypeChanges())
          .setType(newInvokeType)
          .build();
    }
    return previous;
  }

  public static class Builder {

    private final MutableBidirectionalOneToOneMap<DexMethod, DexMethod> methodMap =
        new BidirectionalOneToOneHashMap<>();
    private final Set<DexMethod> publicizedPrivateInterfaceMethods =
        SetUtils.newConcurrentHashSet();
    private final Set<DexMethod> publicizedPrivateVirtualMethods = SetUtils.newConcurrentHashSet();

    public Builder addPublicizedPrivateVirtualMethod(DexProgramClass holder, DexMethod method) {
      if (holder.isInterface()) {
        publicizedPrivateInterfaceMethods.add(method);
      } else {
        publicizedPrivateVirtualMethods.add(method);
      }
      return this;
    }

    @SuppressWarnings("ReferenceEquality")
    public Builder recordMove(DexMethod from, DexMethod to) {
      assert from != to;
      synchronized (methodMap) {
        methodMap.put(from, to);
      }
      return this;
    }

    public boolean isEmpty() {
      return methodMap.isEmpty()
          && publicizedPrivateInterfaceMethods.isEmpty()
          && publicizedPrivateVirtualMethods.isEmpty();
    }

    public AccessModifierLens build(AppView<AppInfoWithLiveness> appView) {
      assert !isEmpty();
      return new AccessModifierLens(
          appView, methodMap, publicizedPrivateInterfaceMethods, publicizedPrivateVirtualMethods);
    }
  }
}
