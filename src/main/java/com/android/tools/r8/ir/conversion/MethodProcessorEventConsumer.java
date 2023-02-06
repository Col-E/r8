// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.optimize.UtilityMethodsForCodeOptimizationsEventConsumer;

public abstract class MethodProcessorEventConsumer
    implements UtilityMethodsForCodeOptimizationsEventConsumer {

  public static MethodProcessorEventConsumer empty() {
    return EmptyMethodProcessorEventConsumer.getInstance();
  }

  private static class EmptyMethodProcessorEventConsumer extends MethodProcessorEventConsumer {

    private static final EmptyMethodProcessorEventConsumer INSTANCE =
        new EmptyMethodProcessorEventConsumer();

    private EmptyMethodProcessorEventConsumer() {}

    static EmptyMethodProcessorEventConsumer getInstance() {
      return INSTANCE;
    }

    @Override
    public void acceptUtilityToStringIfNotNullMethod(ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptUtilityThrowClassCastExceptionIfNotNullMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptUtilityThrowIllegalAccessErrorMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptUtilityThrowIncompatibleClassChangeErrorMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptUtilityThrowNoSuchMethodErrorMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }

    @Override
    public void acceptUtilityThrowRuntimeExceptionWithMessageMethod(
        ProgramMethod method, ProgramMethod context) {
      // Intentionally empty.
    }
  }
}
