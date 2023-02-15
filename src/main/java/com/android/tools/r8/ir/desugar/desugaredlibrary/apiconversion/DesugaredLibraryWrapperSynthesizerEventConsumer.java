// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion;

import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;

public interface DesugaredLibraryWrapperSynthesizerEventConsumer {

  void acceptCollectionConversion(ProgramMethod arrayConversion, ProgramMethod context);

  interface DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer
      extends DesugaredLibraryWrapperSynthesizerEventConsumer {

    void acceptWrapperProgramClass(DexProgramClass clazz);

    void acceptEnumConversionProgramClass(DexProgramClass clazz);
  }

  interface DesugaredLibraryClasspathWrapperSynthesizeEventConsumer
      extends DesugaredLibraryWrapperSynthesizerEventConsumer {

    void acceptWrapperClasspathClass(DexClasspathClass clazz);

    void acceptEnumConversionClasspathClass(DexClasspathClass clazz);

    void acceptGenericApiConversionStub(DexClasspathClass dexClasspathClass);
  }

  interface DesugaredLibraryAPIConverterEventConsumer
      extends DesugaredLibraryClasspathWrapperSynthesizeEventConsumer {

    void acceptAPIConversionOutline(ProgramMethod method, ProgramMethod context);
  }

  interface DesugaredLibraryAPICallbackSynthesizorEventConsumer
      extends DesugaredLibraryClasspathWrapperSynthesizeEventConsumer {

    void acceptAPIConversionCallback(ProgramMethod callbackMethod, ProgramMethod convertedMethod);
  }
}
