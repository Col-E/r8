// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.EmulatedDispatchMethodDescriptor;

public interface DesugaredLibraryRetargeterSynthesizerEventConsumer {

  interface DesugaredLibraryRetargeterL8SynthesizerEventConsumer {
    void acceptDesugaredLibraryRetargeterDispatchProgramClass(DexProgramClass clazz);
  }

  interface DesugaredLibraryRetargeterInstructionEventConsumer {
    void acceptDesugaredLibraryRetargeterDispatchClasspathClass(DexClasspathClass clazz);

    void acceptCovariantRetargetMethod(ProgramMethod method, ProgramMethod context);
  }

  interface DesugaredLibraryRetargeterPostProcessingEventConsumer
      extends DesugaredLibraryRetargeterInstructionEventConsumer {
    void acceptInterfaceInjection(DexProgramClass clazz, DexClass newInterface);

    void acceptDesugaredLibraryRetargeterForwardingMethod(
        ProgramMethod method, EmulatedDispatchMethodDescriptor descriptor);
  }
}
