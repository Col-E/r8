// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

public abstract class CfJumpInstruction extends CfInstruction {

  @Override
  public final boolean isJump() {
    return true;
  }

  @Override
  public CfJumpInstruction asJump() {
    return this;
  }

  /**
   * @return true if this jump instruction has fallthrough, i.e., this is {@link CfIf} or {@link
   *     CfIfCmp}.
   */
  public boolean hasFallthrough() {
    return false;
  }
}
