// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.nest;

import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.MethodProcessor;

public abstract class NestBridgeConsumer {

  public static D8NestBridgeConsumer createForD8(MethodProcessor methodProcessor) {
    return new D8NestBridgeConsumer(methodProcessor);
  }

  public abstract void acceptFieldGetBridge(ProgramField target, ProgramMethod bridge);

  public abstract void acceptFieldPutBridge(ProgramField target, ProgramMethod bridge);

  public abstract void acceptMethodBridge(ProgramMethod target, ProgramMethod bridge);

  public final void acceptFieldBridge(ProgramField target, ProgramMethod bridge, boolean isGet) {
    if (isGet) {
      acceptFieldGetBridge(target, bridge);
    } else {
      acceptFieldPutBridge(target, bridge);
    }
  }
}
