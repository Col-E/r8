// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.code;

import com.android.tools.r8.ir.code.MonitorType;
import com.android.tools.r8.ir.conversion.IRBuilder;

public class DexMonitorEnter extends DexFormat11x {

  public static final int OPCODE = 0x1d;
  public static final String NAME = "MonitorEnter";
  public static final String SMALI_NAME = "monitor-enter";

  DexMonitorEnter(int high, BytecodeStream stream) {
    super(high, stream);
  }

  public DexMonitorEnter(int register) {
    super(register);
  }

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public String getSmaliName() {
    return SMALI_NAME;
  }

  @Override
  public int getOpcode() {
    return OPCODE;
  }

  @Override
  public void buildIR(IRBuilder builder) {
    builder.addMonitor(MonitorType.ENTER, AA);
  }

  @Override
  public boolean canThrow() {
    return true;
  }
}
