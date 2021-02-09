// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.nest;

import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.MethodProcessor;

public class D8NestBridgeConsumer extends NestBridgeConsumer {

  private final MethodProcessor methodProcessor;

  public D8NestBridgeConsumer(MethodProcessor methodProcessor) {
    this.methodProcessor = methodProcessor;
  }

  @Override
  public void acceptFieldGetBridge(ProgramField target, ProgramMethod bridge) {
    methodProcessor.scheduleDesugaredMethodForProcessing(bridge);
  }

  @Override
  public void acceptFieldPutBridge(ProgramField target, ProgramMethod bridge) {
    methodProcessor.scheduleDesugaredMethodForProcessing(bridge);
  }

  @Override
  public void acceptMethodBridge(ProgramMethod target, ProgramMethod bridge) {
    methodProcessor.scheduleDesugaredMethodForProcessing(bridge);
  }
}
