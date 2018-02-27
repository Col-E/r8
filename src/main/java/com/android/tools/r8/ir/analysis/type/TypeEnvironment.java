// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.Value;
import java.util.List;

public interface TypeEnvironment {
  void analyze();
  void analyzeBlocks(List<BasicBlock> blocks);
  void enqueue(Value value);

  TypeLatticeElement getLatticeElement(Value value);
  DexType getRefinedReceiverType(InvokeMethodWithReceiver invoke);
}
