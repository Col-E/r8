// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.CfState.Slot;
import com.android.tools.r8.ir.conversion.IRBuilder;
import java.util.ListIterator;

public class CfSafeCheckCast extends CfCheckCast {

  public CfSafeCheckCast(DexType type) {
    super(type);
  }

  @Override
  void addCheckCast(IRBuilder builder, Slot object) {
    builder.addSafeCheckCast(object.register, getType());
  }

  @Override
  void internalRegisterUse(
      UseRegistry registry, DexClassAndMethod context, ListIterator<CfInstruction> iterator) {
    registry.registerSafeCheckCast(getType());
  }

  @Override
  public CfInstruction withType(DexType newType) {
    return new CfSafeCheckCast(newType);
  }
}
