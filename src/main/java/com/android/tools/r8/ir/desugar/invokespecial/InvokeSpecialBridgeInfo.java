// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.invokespecial;

import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.ProgramMethod;

public class InvokeSpecialBridgeInfo implements Comparable<InvokeSpecialBridgeInfo> {

  private final ProgramMethod newDirectMethod;
  private final ProgramMethod virtualMethod;
  private final CfCode virtualMethodCode;

  InvokeSpecialBridgeInfo(
      ProgramMethod newDirectMethod, ProgramMethod virtualMethod, CfCode virtualMethodCode) {
    this.newDirectMethod = newDirectMethod;
    this.virtualMethod = virtualMethod;
    this.virtualMethodCode = virtualMethodCode;
  }

  public ProgramMethod getNewDirectMethod() {
    return newDirectMethod;
  }

  public ProgramMethod getVirtualMethod() {
    return virtualMethod;
  }

  public CfCode getVirtualMethodCode() {
    return virtualMethodCode;
  }

  @Override
  public int compareTo(InvokeSpecialBridgeInfo info) {
    return getNewDirectMethod().getReference().compareTo(info.getNewDirectMethod().getReference());
  }
}
