// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import java.util.Set;

public final class ObjectsMethodRewrites {

  public static void rewriteToArraysHashCode(
      InvokeMethod invoke,
      InstructionListIterator iterator,
      DexItemFactory factory,
      Set<Value> affectedValues) {
    DexType arraysType = factory.createType(factory.arraysDescriptor);
    DexMethod hashCodeMethod =
        factory.createMethod(arraysType, invoke.getInvokedMethod().proto, "hashCode");
    InvokeStatic arraysHashCode =
        new InvokeStatic(hashCodeMethod, invoke.outValue(), invoke.inValues(), false);
    iterator.replaceCurrentInstruction(arraysHashCode);
  }

  public static void rewriteRequireNonNull(
      InvokeMethod invoke,
      InstructionListIterator iterator,
      DexItemFactory factory,
      Set<Value> affectedValues) {
    InvokeVirtual getClass =
        new InvokeVirtual(factory.objectMembers.getClass, null, invoke.inValues());
    if (invoke.hasOutValue()) {
      affectedValues.addAll(invoke.outValue().affectedValues());
      invoke.outValue().replaceUsers(invoke.inValues().get(0));
      invoke.setOutValue(null);
    }
    iterator.replaceCurrentInstruction(getClass);
  }
}
