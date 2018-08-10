// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InvokeMethod;
import java.util.ListIterator;

interface InliningStrategy {
  boolean exceededAllowance();

  void markInlined(IRCode inlinee);

  void ensureMethodProcessed(DexEncodedMethod target, IRCode inlinee);

  boolean isValidTarget(InvokeMethod invoke, DexEncodedMethod target, IRCode inlinee);

  void updateTypeInformationIfNeeded(
      IRCode inlinee, ListIterator<BasicBlock> blockIterator, BasicBlock block);

  DexType getReceiverTypeIfKnown(InvokeMethod invoke);
}
