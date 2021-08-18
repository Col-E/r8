// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;

public interface DesugaredLibraryRetargeterSynthesizerEventConsumer {

  default DesugaredLibraryRetargeterL8SynthesizerEventConsumer asProgramSynthesizer() {
    assert false;
    return null;
  }

  default DesugaredLibraryRetargeterInstructionEventConsumer asClasspathSynthesizer() {
    assert false;
    return null;
  }

  interface DesugaredLibraryRetargeterL8SynthesizerEventConsumer
      extends DesugaredLibraryRetargeterSynthesizerEventConsumer {

    @Override
    default DesugaredLibraryRetargeterL8SynthesizerEventConsumer asProgramSynthesizer() {
      return this;
    }

    void acceptDesugaredLibraryRetargeterDispatchProgramClass(DexProgramClass clazz);
  }

  interface DesugaredLibraryRetargeterInstructionEventConsumer
      extends DesugaredLibraryRetargeterSynthesizerEventConsumer {

    @Override
    default DesugaredLibraryRetargeterInstructionEventConsumer asClasspathSynthesizer() {
      return this;
    }

    void acceptDesugaredLibraryRetargeterDispatchClasspathClass(DexClasspathClass clazz);
  }

  interface DesugaredLibraryRetargeterPostProcessingEventConsumer
      extends DesugaredLibraryRetargeterInstructionEventConsumer {

    void acceptInterfaceInjection(DexProgramClass clazz, DexClass newInterface);

    void acceptForwardingMethod(ProgramMethod method);
  }
}
