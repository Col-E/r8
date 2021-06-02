// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.desugar.DesugaredLibraryAPIConverter;

class NeedsIRDesugarUseRegistry extends UseRegistry {

  private boolean needsDesugaring = false;
  private final ProgramMethod context;
  private final DesugaredLibraryAPIConverter desugaredLibraryAPIConverter;

  public NeedsIRDesugarUseRegistry(
      ProgramMethod method,
      AppView<?> appView,
      DesugaredLibraryAPIConverter desugaredLibraryAPIConverter) {
    super(appView.dexItemFactory());
    this.context = method;
    this.desugaredLibraryAPIConverter = desugaredLibraryAPIConverter;
  }

  public boolean needsDesugaring() {
    return needsDesugaring;
  }

  @Override
  public void registerInitClass(DexType type) {
    if (!needsDesugaring
        && desugaredLibraryAPIConverter != null
        && desugaredLibraryAPIConverter.canConvert(type)) {
      needsDesugaring = true;
    }
  }

  @Override
  public void registerInvokeVirtual(DexMethod method) {
    registerDesugaredLibraryAPIConverter(method);
  }

  @Override
  public void registerInvokeDirect(DexMethod method) {
    registerDesugaredLibraryAPIConverter(method);
  }

  private void registerDesugaredLibraryAPIConverter(DexMethod method) {
    if (!needsDesugaring) {
      needsDesugaring =
          desugaredLibraryAPIConverter != null
              && desugaredLibraryAPIConverter.shouldRewriteInvoke(method);
    }
  }

  @Override
  public void registerInvokeStatic(DexMethod method) {
    registerDesugaredLibraryAPIConverter(method);
  }

  @Override
  public void registerInvokeInterface(DexMethod method) {
    registerDesugaredLibraryAPIConverter(method);
  }

  @Override
  public void registerInvokeStatic(DexMethod method, boolean itf) {
    registerInvokeStatic(method);
  }

  @Override
  public void registerCallSite(DexCallSite callSite) {
    super.registerCallSite(callSite);
    needsDesugaring = true;
  }

  @Override
  public void registerInvokeSuper(DexMethod method) {
    registerDesugaredLibraryAPIConverter(method);
  }

  @Override
  public void registerInstanceFieldRead(DexField field) {}

  @Override
  public void registerInstanceFieldWrite(DexField field) {}

  @Override
  public void registerNewInstance(DexType type) {}

  @Override
  public void registerStaticFieldRead(DexField field) {}

  @Override
  public void registerStaticFieldWrite(DexField field) {}

  @Override
  public void registerTypeReference(DexType type) {}

  @Override
  public void registerInstanceOf(DexType type) {}
}
