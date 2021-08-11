// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.analysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClasspathClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryAPIConverter.Mode;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import java.util.function.Consumer;

public class DesugaredLibraryConversionWrapperAnalysis extends EnqueuerAnalysis
    implements EnqueuerInvokeAnalysis {

  private final AppView<?> appView;
  private final DesugaredLibraryAPIConverter converter;

  public DesugaredLibraryConversionWrapperAnalysis(AppView<?> appView) {
    this.appView = appView;
    this.converter =
        new DesugaredLibraryAPIConverter(appView, Mode.GENERATE_CALLBACKS_AND_WRAPPERS);
  }

  @Override
  public void processNewlyLiveMethod(ProgramMethod method, ProgramDefinition context) {
    converter.registerCallbackIfRequired(method);
  }

  private void traceInvoke(DexMethod invokedMethod, Type invokeType, ProgramMethod context) {
    converter.registerWrappersForLibraryInvokeIfRequired(invokedMethod, invokeType, context);
  }

  @Override
  public void traceInvokeStatic(DexMethod invokedMethod, ProgramMethod context) {
    this.traceInvoke(invokedMethod, Type.STATIC, context);
  }

  @Override
  public void traceInvokeDirect(DexMethod invokedMethod, ProgramMethod context) {
    this.traceInvoke(invokedMethod, Type.DIRECT, context);
  }

  @Override
  public void traceInvokeInterface(DexMethod invokedMethod, ProgramMethod context) {
    this.traceInvoke(invokedMethod, Type.INTERFACE, context);
  }

  @Override
  public void traceInvokeSuper(DexMethod invokedMethod, ProgramMethod context) {
    this.traceInvoke(invokedMethod, Type.SUPER, context);
  }

  @Override
  public void traceInvokeVirtual(DexMethod invokedMethod, ProgramMethod context) {
    this.traceInvoke(invokedMethod, Invoke.Type.VIRTUAL, context);
  }

  public ProgramMethodSet generateCallbackMethods() {
    return converter.generateCallbackMethods();
  }

  public void generateWrappers(Consumer<DexClasspathClass> synthesizedCallback) {
    converter.synthesizeWrappers(synthesizedCallback);
  }
}
