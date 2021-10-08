// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.code;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.OffsetToObjectMapping;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class SafeCheckCast extends CheckCast {

  SafeCheckCast(int high, BytecodeStream stream, OffsetToObjectMapping mapping) {
    super(high, stream, mapping);
  }

  public SafeCheckCast(int valueRegister, DexType type) {
    super(valueRegister, type);
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addSafeCheckCast(AA, getType());
  }

  @Override
  public void registerUse(UseRegistry<?> registry) {
    registry.registerSafeCheckCast(getType());
  }
}
