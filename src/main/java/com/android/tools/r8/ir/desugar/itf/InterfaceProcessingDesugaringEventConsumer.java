// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodResolutionResult.FailedResolutionResult;
import com.android.tools.r8.graph.ProgramMethod;

public interface InterfaceProcessingDesugaringEventConsumer {

  void acceptInterfaceMethodDesugaringForwardingMethod(
      ProgramMethod method, DexClassAndMethod baseMethod);

  void acceptEmulatedInterfaceMarkerInterface(
      DexProgramClass clazz, DexClasspathClass newInterface);

  void acceptThrowingMethod(
      ProgramMethod method, DexType errorType, FailedResolutionResult resolutionResult);

  void warnMissingInterface(
      DexProgramClass context, DexType missing, InterfaceDesugaringSyntheticHelper helper);
}
