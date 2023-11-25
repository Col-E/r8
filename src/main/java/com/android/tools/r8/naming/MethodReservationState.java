// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.naming.MethodReservationState.InternalReservationState;
import com.android.tools.r8.utils.MethodSignatureEquivalence;
import com.google.common.base.Equivalence.Wrapper;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

class MethodReservationState<KeyType>
    extends MethodNamingStateBase<KeyType, InternalReservationState> {

  private final MethodReservationState<KeyType> parentNamingState;

  private MethodReservationState(
      MethodReservationState<KeyType> parentNamingState,
      Function<DexMethod, KeyType> keyTransform) {
    super(keyTransform);
    this.parentNamingState = parentNamingState;
  }

  public static <KeyType> MethodReservationState<KeyType> createRoot(
      Function<DexMethod, KeyType> keyTransform) {
    return new MethodReservationState<>(null, keyTransform);
  }

  MethodReservationState<KeyType> createChild() {
    return new MethodReservationState<>(this, this.keyTransform);
  }

  void reserveName(DexString reservedName, DexEncodedMethod method) {
    try {
      getOrCreateInternalState(method.getReference()).reserveName(method, reservedName);
    } catch (AssertionError err) {
      throw new RuntimeException(
          String.format(
              "Assertion error when trying to reserve name '%s' for method '%s'",
              reservedName, method),
          err);
    }
  }

  boolean isReserved(DexString name, DexMethod method) {
    InternalReservationState internalState = getInternalState(method);
    if (internalState != null && internalState.isReserved(name)) {
      return true;
    }
    if (parentNamingState != null) {
      return parentNamingState.isReserved(name, method);
    }
    return false;
  }

  Set<DexString> getReservedNamesFor(DexMethod method) {
    InternalReservationState internalState = getInternalState(method);
    Set<DexString> reservedName = null;
    if (internalState != null) {
      reservedName = internalState.getAssignedNamesFor(method);
    }
    if (reservedName == null && parentNamingState != null) {
      reservedName = parentNamingState.getReservedNamesFor(method);
    }
    return reservedName;
  }

  @Override
  InternalReservationState createInternalState(DexMethod method) {
    return new InternalReservationState();
  }

  static class InternalReservationState {
    private Map<Wrapper<DexMethod>, Set<DexString>> originalToReservedNames = null;
    private Set<DexString> reservedNames = null;

    boolean isReserved(DexString name) {
      return reservedNames != null && reservedNames.contains(name);
    }

    Set<DexString> getAssignedNamesFor(DexMethod method) {
      if (originalToReservedNames == null) {
        return null;
      }
      return originalToReservedNames.get(MethodSignatureEquivalence.get().wrap(method));
    }

    void reserveName(DexEncodedMethod method, DexString name) {
      if (reservedNames == null) {
        assert originalToReservedNames == null;
        originalToReservedNames = new HashMap<>();
        reservedNames = new HashSet<>();
      }
      Wrapper<DexMethod> wrapped = MethodSignatureEquivalence.get().wrap(method.getReference());
      originalToReservedNames.computeIfAbsent(wrapped, ignore -> new HashSet<>()).add(name);
      reservedNames.add(name);
    }
  }
}
