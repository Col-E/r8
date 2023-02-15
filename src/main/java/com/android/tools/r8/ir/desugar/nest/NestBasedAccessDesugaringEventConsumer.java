// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.nest;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;

public interface NestBasedAccessDesugaringEventConsumer {

  void acceptNestConstructorBridge(
      ProgramMethod target,
      ProgramMethod bridge,
      DexProgramClass argumentClass,
      DexClassAndMethod context);

  void acceptNestFieldGetBridge(
      ProgramField target, ProgramMethod bridge, DexClassAndMethod context);

  void acceptNestFieldPutBridge(
      ProgramField target, ProgramMethod bridge, DexClassAndMethod context);

  void acceptNestMethodBridge(
      ProgramMethod target, ProgramMethod bridge, DexClassAndMethod context);

  static EmptyNestBasedAccessDesugaringEventConsumer empty() {
    return EmptyNestBasedAccessDesugaringEventConsumer.getInstance();
  }

  class EmptyNestBasedAccessDesugaringEventConsumer
      implements NestBasedAccessDesugaringEventConsumer {

    private static final EmptyNestBasedAccessDesugaringEventConsumer INSTANCE =
        new EmptyNestBasedAccessDesugaringEventConsumer();

    private EmptyNestBasedAccessDesugaringEventConsumer() {}

    static EmptyNestBasedAccessDesugaringEventConsumer getInstance() {
      return INSTANCE;
    }

    @Override
    public void acceptNestConstructorBridge(
        ProgramMethod target,
        ProgramMethod bridge,
        DexProgramClass argumentClass,
        DexClassAndMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptNestFieldGetBridge(
        ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptNestFieldPutBridge(
        ProgramField target, ProgramMethod bridge, DexClassAndMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptNestMethodBridge(
        ProgramMethod target, ProgramMethod bridge, DexClassAndMethod context) {
      // Intentionally empty.
    }
  }
}
