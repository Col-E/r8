// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.code;

public abstract class IRMetadata {

  public static IRMetadata unknown() {
    return UnknownIRMetadata.getInstance();
  }

  public boolean isUpdatableIRMetadata() {
    return false;
  }

  public UpdatableIRMetadata asUpdatableIRMetadata() {
    return null;
  }

  public boolean isUnknownIRMetadata() {
    return false;
  }

  public abstract void record(Instruction instruction);

  public abstract void merge(IRMetadata metadata);

  public abstract boolean mayHaveConstString();

  public abstract boolean mayHaveDebugPosition();

  public abstract boolean mayHaveDexItemBasedConstString();

  public abstract boolean mayHaveMonitorInstruction();

  public abstract boolean mayHaveStringSwitch();
}
