// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.FieldPut;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Return;

public class EmptyInterfaceTypeToClassTypeLensCodeRewriterHelper
    extends InterfaceTypeToClassTypeLensCodeRewriterHelper {

  @Override
  public void insertCastsForOperandsIfNeeded(
      InvokeMethod originalInvoke,
      InvokeMethod rewrittenInvoke,
      MethodLookupResult lookupResult,
      BasicBlockIterator blockIterator,
      BasicBlock block,
      InstructionListIterator instructionIterator) {
    // Intentionally empty.
  }

  @Override
  public void insertCastsForOperandsIfNeeded(
      Return rewrittenReturn,
      BasicBlockIterator blockIterator,
      BasicBlock block,
      InstructionListIterator instructionIterator) {
    // Intentionally empty.
  }

  @Override
  public void insertCastsForOperandsIfNeeded(
      FieldPut originalFieldPut,
      InvokeStatic rewrittenFieldPut,
      BasicBlockIterator blockIterator,
      BasicBlock block,
      InstructionListIterator instructionIterator) {
    // Intentionally empty.
  }

  @Override
  public void insertCastsForOperandsIfNeeded(
      FieldPut originalFieldPut,
      FieldPut rewrittenFieldPut,
      BasicBlockIterator blockIterator,
      BasicBlock block,
      InstructionListIterator instructionIterator) {
    // Intentionally empty.
  }

  @Override
  public void processWorklist() {
    // Intentionally empty.
  }
}
