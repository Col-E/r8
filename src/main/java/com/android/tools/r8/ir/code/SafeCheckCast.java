// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.cf.code.CfSafeCheckCast;
import com.android.tools.r8.dex.code.DexCheckCast;
import com.android.tools.r8.dex.code.DexSafeCheckCast;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.CfBuilder;
import com.android.tools.r8.lightir.LirBuilder;

public class SafeCheckCast extends CheckCast {

  public SafeCheckCast(Value dest, Value value, DexType type) {
    super(dest, value, type);
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public void buildCf(CfBuilder builder) {
    builder.add(new CfSafeCheckCast(getType()), this);
  }

  @Override
  public void buildLir(LirBuilder<Value, ?> builder) {
    builder.addSafeCheckCast(getType(), object());
  }

  @Override
  DexCheckCast createCheckCast(int register) {
    return new DexSafeCheckCast(register, getType());
  }

  @Override
  public boolean instructionInstanceCanThrow(AppView<?> appView, ProgramMethod context) {
    return false;
  }

  @Override
  public boolean isSafeCheckCast() {
    return true;
  }

  @Override
  public SafeCheckCast asSafeCheckCast() {
    return this;
  }

  public static class Builder extends CheckCast.Builder {

    @Override
    public CheckCast build() {
      return amend(new SafeCheckCast(outValue, object, castType));
    }
  }
}
