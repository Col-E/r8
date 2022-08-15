// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

public class IgnoreDebugLocalWriteAliasedValueConfiguration implements AliasedValueConfiguration {

  private static final IgnoreDebugLocalWriteAliasedValueConfiguration INSTANCE =
      new IgnoreDebugLocalWriteAliasedValueConfiguration();

  private IgnoreDebugLocalWriteAliasedValueConfiguration() {}

  public static IgnoreDebugLocalWriteAliasedValueConfiguration getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isIntroducingAnAlias(Instruction instruction) {
    return instruction.isAssume() || instruction.isDebugLocalWrite();
  }

  @Override
  public Value getAliasForOutValue(Instruction instruction) {
    assert instruction.isAssume() || instruction.isDebugLocalWrite();

    return instruction.isAssume()
        ? instruction.asAssume().src()
        : instruction.asDebugLocalWrite().src();
  }
}
