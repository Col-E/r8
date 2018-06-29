// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.ir.code.If.Type;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class ParameterUsagesInfo {
  private ImmutableList<ParameterUsage> parametersUsages;

  public ParameterUsagesInfo(List<ParameterUsage> usages) {
    assert !usages.isEmpty();
    parametersUsages = ImmutableList.copyOf(usages);
    assert parametersUsages.size() ==
        parametersUsages.stream().map(usage -> usage.index).collect(Collectors.toSet()).size();
  }

  ParameterUsage getParameterUsage(int parameter) {
    for (ParameterUsage usage : parametersUsages) {
      if (usage.index == parameter) {
        return usage;
      }
    }
    return null;
  }

  public final static class ParameterUsage {
    public final int index;
    public final Set<Type> ifZeroTest;
    public final List<Pair<Invoke.Type, DexMethod>> callsReceiver;
    public final boolean returnValue;

    ParameterUsage(int index, Set<Type> ifZeroTest,
        List<Pair<Invoke.Type, DexMethod>> callsReceiver, boolean returnValue) {
      this.index = index;
      this.ifZeroTest = ifZeroTest.isEmpty()
          ? Collections.emptySet() : ImmutableSet.copyOf(ifZeroTest);
      this.callsReceiver = callsReceiver.isEmpty()
          ? Collections.emptyList() : ImmutableList.copyOf(callsReceiver);
      this.returnValue = returnValue;
    }

    public boolean notUsed() {
      return ifZeroTest == null && callsReceiver == null && !returnValue;
    }
  }

  public static class ParameterUsageBuilder {
    private final int index;
    private final Value arg;
    private final Set<Type> ifZeroTestTypes = new HashSet<>();
    private final List<Pair<Invoke.Type, DexMethod>> callsOnReceiver = new ArrayList<>();
    private boolean returnValue = false;

    public ParameterUsageBuilder(Value arg, int index) {
      this.arg = arg;
      this.index = index;
    }

    // Returns false if the instruction is not supported.
    public boolean note(Instruction instruction) {
      if (instruction.isInvokeMethodWithReceiver() &&
          instruction.inValues().lastIndexOf(arg) == 0) {
        callsOnReceiver.add(new Pair<>(
            instruction.asInvokeMethodWithReceiver().getType(),
            instruction.asInvokeMethodWithReceiver().getInvokedMethod()));
        return true;
      }

      if (instruction.isIf() && instruction.asIf().isZeroTest()) {
        assert instruction.inValues().size() == 1 && instruction.inValues().get(0) == arg;
        ifZeroTestTypes.add(instruction.asIf().getType());
        return true;
      }

      if (instruction.isReturn()) {
        assert instruction.inValues().size() == 1 && instruction.inValues().get(0) == arg;
        returnValue = true;
        return true;
      }

      return false;
    }

    public ParameterUsage build() {
      return new ParameterUsage(index, ifZeroTestTypes, callsOnReceiver, returnValue);
    }
  }
}
