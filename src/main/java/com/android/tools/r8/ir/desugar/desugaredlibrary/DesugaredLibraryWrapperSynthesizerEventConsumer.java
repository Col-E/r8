// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;

public interface DesugaredLibraryWrapperSynthesizerEventConsumer {

  interface DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer {

    void acceptWrapperProgramClass(DexProgramClass clazz);

    void acceptEnumConversionProgramClass(DexProgramClass clazz);
  }

  interface DesugaredLibraryClasspathWrapperSynthesizeEventConsumer {

    void acceptWrapperClasspathClass(DexClasspathClass clazz);

    void acceptEnumConversionClasspathClass(DexClasspathClass clazz);
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
