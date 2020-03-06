// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.FieldInstruction.Assumption;

public interface InstanceFieldInstruction {

  boolean hasOutValue();

  Value outValue();

  Value object();

  boolean instructionMayHaveSideEffects(AppView<?> appView, DexType context, Assumption assumption);

  FieldInstruction asFieldInstruction();

  boolean isInstanceGet();

  InstanceGet asInstanceGet();

  boolean isInstancePut();

  InstancePut asInstancePut();
}
