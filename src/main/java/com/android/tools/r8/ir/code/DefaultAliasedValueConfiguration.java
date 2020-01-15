// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

public class DefaultAliasedValueConfiguration implements AliasedValueConfiguration {

  private static final DefaultAliasedValueConfiguration INSTANCE =
      new DefaultAliasedValueConfiguration();

  private DefaultAliasedValueConfiguration() {}

  public static DefaultAliasedValueConfiguration getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isIntroducingAnAlias(Instruction instruction) {
    return instruction.isAssume();
  }

  @Override
  public Value getAliasForOutValue(Instruction instruction) {
    assert instruction.isAssume();
    return instruction.asAssume().src();
  }
}
