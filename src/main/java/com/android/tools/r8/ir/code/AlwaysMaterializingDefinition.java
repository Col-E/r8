// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.LoadStoreHelper;
import com.android.tools.r8.code.Const16;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.utils.InternalOptions;

public class AlwaysMaterializingDefinition extends ConstInstruction {

  public AlwaysMaterializingDefinition(Value out) {
    super(out);
  }

  @Override
  public boolean canBeDeadCode(IRCode code, InternalOptions options) {
    // This instruction may never be considered dead as it must remain.
    return false;
  }

  @Override
  public void buildDex(DexBuilder builder) {
    int register = builder.allocatedRegister(outValue, getNumber());
    builder.add(
        this, (register & 0xf) == register ? new Const4(register, 0) : new Const16(register, 0));
  }

  @Override
  public void buildCf(CfBuilder builder) {
    throw new Unreachable();
  }

  @Override
  public boolean identicalNonValueNonPositionParts(Instruction other) {
    return false;
  }

  @Override
  public int maxInValueRegister() {
    throw new Unreachable();
  }

  @Override
  public int maxOutValueRegister() {
    return Constants.U8BIT_MAX;
  }

  @Override
  public void insertLoadAndStores(InstructionListIterator it, LoadStoreHelper helper) {
    throw new Unreachable();
  }
}
