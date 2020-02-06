// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import java.util.Set;

public final class OptionalMethodRewrites {

  private OptionalMethodRewrites() {}

  public static void rewriteOrElseGet(
      InvokeMethod invoke,
      InstructionListIterator iterator,
      DexItemFactory factory,
      Set<Value> affectedValues) {
    InvokeVirtual getInvoke = new InvokeVirtual(
        factory.createMethod(factory.optionalType, invoke.getInvokedMethod().proto,
            "get"), invoke.outValue(), invoke.inValues());
    iterator.replaceCurrentInstruction(getInvoke);
  }

  public static void rewriteDoubleOrElseGet(
      InvokeMethod invoke,
      InstructionListIterator iterator,
      DexItemFactory factory,
      Set<Value> affectedValues) {
    InvokeVirtual getInvoke = new InvokeVirtual(
        factory.createMethod(factory.optionalDoubleType, invoke.getInvokedMethod().proto,
            "getAsDouble"), invoke.outValue(), invoke.inValues());
    iterator.replaceCurrentInstruction(getInvoke);
  }

  public static void rewriteIntOrElseGet(
      InvokeMethod invoke,
      InstructionListIterator iterator,
      DexItemFactory factory,
      Set<Value> affectedValues) {
    InvokeVirtual getInvoke = new InvokeVirtual(
        factory.createMethod(factory.optionalIntType, invoke.getInvokedMethod().proto,
            "getAsInt"), invoke.outValue(), invoke.inValues());
    iterator.replaceCurrentInstruction(getInvoke);
  }

  public static void rewriteLongOrElseGet(
      InvokeMethod invoke,
      InstructionListIterator iterator,
      DexItemFactory factory,
      Set<Value> affectedValues) {
    InvokeVirtual getInvoke = new InvokeVirtual(
        factory.createMethod(factory.optionalLongType, invoke.getInvokedMethod().proto,
            "getAsLong"), invoke.outValue(), invoke.inValues());
    iterator.replaceCurrentInstruction(getInvoke);
  }
}
