// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.conversion.D8MethodProcessor;
import com.android.tools.r8.ir.desugar.records.RecordDesugaringEventConsumer;

public abstract class CfClassDesugaringEventConsumer implements RecordDesugaringEventConsumer {

  public static D8CfClassDesugaringEventConsumer createForD8(D8MethodProcessor methodProcessor) {
    return new D8CfClassDesugaringEventConsumer(methodProcessor);
  }

  public static class D8CfClassDesugaringEventConsumer extends CfClassDesugaringEventConsumer {

    private final D8MethodProcessor methodProcessor;

    public D8CfClassDesugaringEventConsumer(D8MethodProcessor methodProcessor) {
      this.methodProcessor = methodProcessor;
    }

    @Override
    public void acceptRecordClass(DexProgramClass recordClass) {
      methodProcessor.scheduleDesugaredMethodsForProcessing(recordClass.programMethods());
    }

    @Override
    public void acceptRecordMethod(ProgramMethod method) {
      assert false;
    }
  }

  // TODO(b/): Implement R8CfClassDesugaringEventConsumer
}
