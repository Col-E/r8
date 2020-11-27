// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.library.StatelessLibraryMethodModelCollection.State;
import java.util.Set;

public abstract class StatelessLibraryMethodModelCollection
    implements LibraryMethodModelCollection<State> {

  @Override
  public final State createInitialState() {
    return null;
  }

  public abstract void optimize(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues);

  @Override
  public final void optimize(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues,
      State state) {
    assert state == null;
    optimize(code, instructionIterator, invoke, singleTarget, affectedValues);
  }

  static class State implements LibraryMethodModelCollection.State {}
}
