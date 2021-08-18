// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;

public interface DesugaredLibraryWrapperSynthesizerEventConsumer {

  default DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer asProgramWrapperSynthesizer() {
    assert false;
    return null;
  }

  default DesugaredLibraryClasspathWrapperSynthesizeEventConsumer asClasspathWrapperSynthesizer() {
    assert false;
    return null;
  }

  interface DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer
      extends DesugaredLibraryWrapperSynthesizerEventConsumer {

    @Override
    default DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer asProgramWrapperSynthesizer() {
      return this;
    }

    void acceptWrapperProgramClass(DexProgramClass clazz);
  }

  interface DesugaredLibraryClasspathWrapperSynthesizeEventConsumer
      extends DesugaredLibraryWrapperSynthesizerEventConsumer {

    @Override
    default DesugaredLibraryClasspathWrapperSynthesizeEventConsumer
        asClasspathWrapperSynthesizer() {
      return this;
    }

    void acceptWrapperClasspathClass(DexClasspathClass clazz);
  }

  interface DesugaredLibraryAPIConverterEventConsumer
      extends DesugaredLibraryClasspathWrapperSynthesizeEventConsumer {

    void acceptAPIConversion(ProgramMethod method);
  }

  interface DesugaredLibraryAPICallbackSynthesizorEventConsumer
      extends DesugaredLibraryClasspathWrapperSynthesizeEventConsumer {

    void acceptAPIConversionCallback(ProgramMethod method);
  }
}
