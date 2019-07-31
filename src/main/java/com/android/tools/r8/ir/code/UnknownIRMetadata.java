// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

public class UnknownIRMetadata extends IRMetadata {

  private static UnknownIRMetadata INSTANCE = new UnknownIRMetadata();

  private UnknownIRMetadata() {}

  static UnknownIRMetadata getInstance() {
    return INSTANCE;
  }

  @Override
  public boolean isUnknownIRMetadata() {
    return true;
  }

  @Override
  public void record(Instruction instruction) {
    // Nothing to do.
  }

  @Override
  public void merge(IRMetadata metadata) {
    // Nothing to do.
  }

  @Override
  public boolean mayHaveConstString() {
    return true;
  }

  @Override
  public boolean mayHaveDebugPosition() {
    return true;
  }

  @Override
  public boolean mayHaveDexItemBasedConstString() {
    return true;
  }

  @Override
  public boolean mayHaveMonitorInstruction() {
    return true;
  }

  @Override
  public boolean mayHaveStringSwitch() {
    return true;
  }
}
