// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.itf;

import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;

// TODO(b/183998768): Consider forcing the processing of interface methods in D8 akin to R8.
//  That would avoid the need to reiterate the interface methods to collect info and this
//  could avoid the "base" methods.
public interface InterfaceProcessingDesugaringEventConsumer
    extends InterfaceMethodDesugaringBaseEventConsumer {

  void acceptForwardingMethod(ProgramMethod method);

  void acceptEmulatedInterfaceMarkerInterface(
      DexProgramClass clazz, DexClasspathClass newInterface);
}
