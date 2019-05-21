// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.naming.ReservedFieldNamingState.InternalState;
import com.google.common.collect.Sets;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class ReservedFieldNamingState extends FieldNamingStateBase<InternalState> {

  public ReservedFieldNamingState(AppView<?> appView) {
    super(appView, new IdentityHashMap<>());
  }

  public boolean isReserved(DexString name, DexType type) {
    InternalState internalState = getInternalState(type);
    return internalState != null && internalState.isReserved(name);
  }

  public void markReservedDirectly(DexString name, DexType type) {
    getOrCreateInternalState(type).markReservedDirectly(name);
  }

  public void includeReservations(ReservedFieldNamingState reservedNames) {
    for (Map.Entry<DexType, InternalState> entry : reservedNames.internalStates.entrySet()) {
      getOrCreateInternalState(entry.getKey()).includeReservations(entry.getValue());
    }
  }

  public void includeReservationsFromBelow(ReservedFieldNamingState reservedNames) {
    for (Map.Entry<DexType, InternalState> entry : reservedNames.internalStates.entrySet()) {
      getOrCreateInternalState(entry.getKey()).includeReservationsFromBelow(entry.getValue());
    }
  }

  @Override
  InternalState createInternalState() {
    return new InternalState();
  }

  static class InternalState {

    private Set<DexString> reservedNamesDirect = Sets.newIdentityHashSet();
    private Set<DexString> reservedNamesBelow = Sets.newIdentityHashSet();

    public boolean isReserved(DexString name) {
      return reservedNamesDirect.contains(name) || reservedNamesBelow.contains(name);
    }

    public void markReservedDirectly(DexString name) {
      reservedNamesDirect.add(name);
    }

    public void includeReservations(InternalState state) {
      reservedNamesDirect.addAll(state.reservedNamesDirect);
    }

    public void includeReservationsFromBelow(InternalState state) {
      reservedNamesBelow.addAll(state.reservedNamesDirect);
      reservedNamesBelow.addAll(state.reservedNamesBelow);
    }
  }
}
