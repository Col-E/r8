// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

public class UpdatableIRMetadata extends IRMetadata {

  // TODO(b/122257895): change to bit vector based representation.
  private boolean mayHaveConstString;
  private boolean mayHaveDebugPosition;
  private boolean mayHaveDexItemBasedConstString;
  private boolean mayHaveMonitorInstruction;
  private boolean mayHaveStringSwitch;

  @Override
  public boolean isUpdatableIRMetadata() {
    return true;
  }

  @Override
  public UpdatableIRMetadata asUpdatableIRMetadata() {
    return this;
  }

  @Override
  public void record(Instruction instruction) {
    mayHaveConstString |= instruction.isConstString();
    mayHaveDebugPosition |= instruction.isDebugPosition();
    mayHaveDexItemBasedConstString |= instruction.isDexItemBasedConstString();
    mayHaveMonitorInstruction |= instruction.isMonitor();
    mayHaveStringSwitch |= instruction.isStringSwitch();
  }

  @Override
  public void merge(IRMetadata metadata) {
    this.mayHaveConstString |= metadata.mayHaveConstString();
    this.mayHaveDebugPosition |= metadata.mayHaveDebugPosition();
    this.mayHaveDexItemBasedConstString |= metadata.mayHaveDexItemBasedConstString();
    this.mayHaveMonitorInstruction |= metadata.mayHaveMonitorInstruction();
    this.mayHaveStringSwitch |= metadata.mayHaveStringSwitch();
  }

  @Override
  public boolean mayHaveConstString() {
    return mayHaveConstString;
  }

  @Override
  public boolean mayHaveDebugPosition() {
    return mayHaveDebugPosition;
  }

  @Override
  public boolean mayHaveDexItemBasedConstString() {
    return mayHaveDexItemBasedConstString;
  }

  @Override
  public boolean mayHaveMonitorInstruction() {
    return mayHaveMonitorInstruction;
  }

  @Override
  public boolean mayHaveStringSwitch() {
    return mayHaveStringSwitch;
  }
}
