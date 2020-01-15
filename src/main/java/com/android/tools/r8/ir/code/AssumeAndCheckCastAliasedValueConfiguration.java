// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

import com.android.tools.r8.utils.ListUtils;

public class AssumeAndCheckCastAliasedValueConfiguration implements AliasedValueConfiguration {

  private static final AssumeAndCheckCastAliasedValueConfiguration INSTANCE =
      new AssumeAndCheckCastAliasedValueConfiguration();

  private AssumeAndCheckCastAliasedValueConfiguration() {}

  public static AssumeAndCheckCastAliasedValueConfiguration getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isIntroducingAnAlias(Instruction instruction) {
    return instruction.isAssume() || instruction.isCheckCast();
  }

  @Override
  public Value getAliasForOutValue(Instruction instruction) {
    assert instruction.isAssume() || instruction.isCheckCast();
    return ListUtils.first(instruction.inValues());
  }
}
