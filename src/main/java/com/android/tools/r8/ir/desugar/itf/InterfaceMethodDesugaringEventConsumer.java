// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.ProgramMethod;

public interface InterfaceMethodDesugaringEventConsumer
    extends InterfaceMethodDesugaringBaseEventConsumer {

  void acceptInvokeStaticInterfaceOutliningMethod(ProgramMethod method, ProgramMethod context);

  static EmptyInterfaceMethodDesugaringEventConsumer emptyInterfaceMethodDesugaringEventConsumer() {
    return EmptyInterfaceMethodDesugaringEventConsumer.INSTANCE;
  }

  class EmptyInterfaceMethodDesugaringEventConsumer
      implements InterfaceMethodDesugaringEventConsumer {

    static EmptyInterfaceMethodDesugaringEventConsumer INSTANCE =
        new EmptyInterfaceMethodDesugaringEventConsumer();

    private EmptyInterfaceMethodDesugaringEventConsumer() {}

    @Override
    public void acceptCompanionClassClinit(ProgramMethod method, ProgramMethod companionMethod) {
      // Intentionally empty.
    }

    @Override
    public void acceptDefaultAsCompanionMethod(
        ProgramMethod method, ProgramMethod companionMethod) {
      // Intentionally empty.
    }

    @Override
    public void acceptInvokeStaticInterfaceOutliningMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptPrivateAsCompanionMethod(
        ProgramMethod method, ProgramMethod companionMethod) {
      // Intentionally empty.
    }

    @Override
    public void acceptStaticAsCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
      // Intentionally empty.
    }
  }
}
