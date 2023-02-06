// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import com.android.tools.r8.graph.ProgramMethod;

public interface UtilityMethodsForCodeOptimizationsEventConsumer {

  void acceptUtilityToStringIfNotNullMethod(ProgramMethod method, ProgramMethod context);

  void acceptUtilityThrowClassCastExceptionIfNotNullMethod(
      ProgramMethod method, ProgramMethod context);

  void acceptUtilityThrowIllegalAccessErrorMethod(ProgramMethod method, ProgramMethod context);

  void acceptUtilityThrowIncompatibleClassChangeErrorMethod(
      ProgramMethod method, ProgramMethod context);

  void acceptUtilityThrowNoSuchMethodErrorMethod(ProgramMethod method, ProgramMethod context);

  void acceptUtilityThrowRuntimeExceptionWithMessageMethod(
      ProgramMethod method, ProgramMethod context);
}
