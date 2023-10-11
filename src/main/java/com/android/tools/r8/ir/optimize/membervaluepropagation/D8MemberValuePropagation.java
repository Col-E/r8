// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.membervaluepropagation;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ArrayGet;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.membervaluepropagation.assume.AssumeInfo;
import java.util.Set;

public class D8MemberValuePropagation extends MemberValuePropagation<AppInfo> {

  public D8MemberValuePropagation(AppView<AppInfo> appView) {
    super(appView);
  }

  @Override
  void rewriteArrayGet(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      ArrayGet arrayGet) {
    // Intentionally empty.
  }

  @Override
  InstructionListIterator rewriteInstanceGet(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      InstanceGet current) {
    return iterator;
  }

  @Override
  void rewriteInstancePut(IRCode code, InstructionListIterator iterator, InstancePut current) {
    // Intentionally empty.
  }

  @Override
  InstructionListIterator rewriteInvokeMethod(
      IRCode code,
      ProgramMethod context,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      InvokeMethod invoke) {
    return iterator;
  }

  @Override
  InstructionListIterator rewriteStaticGet(
      IRCode code,
      Set<Value> affectedValues,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      StaticGet current) {
    AssumeInfo assumeInfo = appView.getAssumeInfoCollection().get(current.getField());
    applyAssumeInfo(code, affectedValues, blocks, iterator, current, assumeInfo);
    return iterator;
  }

  @Override
  void rewriteStaticPut(IRCode code, InstructionListIterator iterator, StaticPut current) {
    // Intentionally empty.
  }
}
