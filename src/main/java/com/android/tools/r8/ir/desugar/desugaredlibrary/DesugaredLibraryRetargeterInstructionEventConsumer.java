// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;

public interface DesugaredLibraryRetargeterInstructionEventConsumer {

  void acceptDesugaredLibraryRetargeterDispatchProgramClass(DexProgramClass clazz);

  void acceptDesugaredLibraryRetargeterDispatchClasspathClass(DexClasspathClass clazz);

  void acceptInterfaceInjection(DexProgramClass clazz, DexClass newInterface);

  interface DesugaredLibraryRetargeterPostProcessingEventConsumer
      extends DesugaredLibraryRetargeterInstructionEventConsumer {

    void acceptForwardingMethod(ProgramMethod method);
  }
}
