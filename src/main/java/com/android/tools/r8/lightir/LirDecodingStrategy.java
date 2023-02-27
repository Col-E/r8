// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.lightir;

import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.Phi;

/** Abstraction for how to decode SSA values (and basic blocks) when reading LIR. */
public abstract class LirDecodingStrategy<V, EV> {

  public abstract V getValue(EV encodedValue);

  public abstract V getValueDefinitionForInstructionIndex(
      int instructionIndex, TypeElement type, DebugLocalInfo localInfo);

  public abstract Phi getPhiDefinitionForInstructionIndex(
      int instructionIndex, BasicBlock block, TypeElement type, DebugLocalInfo localInfo);
}
