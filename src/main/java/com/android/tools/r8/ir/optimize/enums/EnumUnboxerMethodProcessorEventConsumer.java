// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import com.android.tools.r8.graph.ProgramMethod;

public interface EnumUnboxerMethodProcessorEventConsumer {

  void acceptEnumUnboxerCheckNotZeroContext(ProgramMethod method, ProgramMethod context);

  void acceptEnumUnboxerLocalUtilityClassMethodContext(ProgramMethod method, ProgramMethod context);

  void acceptEnumUnboxerSharedUtilityClassMethodContext(
      ProgramMethod method, ProgramMethod context);
}
