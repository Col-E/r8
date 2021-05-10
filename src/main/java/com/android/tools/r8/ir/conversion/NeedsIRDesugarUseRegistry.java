// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.ir.code.Invoke.Type.DIRECT;
import static com.android.tools.r8.ir.code.Invoke.Type.INTERFACE;
import static com.android.tools.r8.ir.code.Invoke.Type.STATIC;
import static com.android.tools.r8.ir.code.Invoke.Type.SUPER;
import static com.android.tools.r8.ir.code.Invoke.Type.VIRTUAL;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.code.Invoke.Type;
import com.android.tools.r8.ir.desugar.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.DesugaredLibraryRetargeter;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodRewriter;

class NeedsIRDesugarUseRegistry extends UseRegistry {

  private boolean needsDesugaring = false;
  private final ProgramMethod context;
  private final DesugaredLibraryRetargeter desugaredLibraryRetargeter;
  private final InterfaceMethodRewriter interfaceMethodRewriter;
  private final DesugaredLibraryAPIConverter desugaredLibraryAPIConverter;

  public NeedsIRDesugarUseRegistry(
      ProgramMethod method,
      AppView<?> appView,
      DesugaredLibraryRetargeter desugaredLibraryRetargeter,
      InterfaceMethodRewriter interfaceMethodRewriter,
      DesugaredLibraryAPIConverter desugaredLibraryAPIConverter) {
    super(appView.dexItemFactory());
    this.context = method;
    this.desugaredLibraryRetargeter = desugaredLibraryRetargeter;
    this.interfaceMethodRewriter = interfaceMethodRewriter;
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
    registerLibraryRetargeting(method, false);
    registerInterfaceMethodRewriting(method, VIRTUAL);
    registerDesugaredLibraryAPIConverter(method);
  }

  @Override
  public void registerInvokeDirect(DexMethod method) {
    registerLibraryRetargeting(method, false);
    registerInterfaceMethodRewriting(method, DIRECT);
    registerDesugaredLibraryAPIConverter(method);
  }

  private void registerInterfaceMethodRewriting(DexMethod method, Type invokeType) {
    if (!needsDesugaring) {
      needsDesugaring =
          interfaceMethodRewriter != null
              && interfaceMethodRewriter.needsRewriting(method, invokeType, context);
    }
  }

  private void registerDesugaredLibraryAPIConverter(DexMethod method) {
    if (!needsDesugaring) {
      needsDesugaring =
          desugaredLibraryAPIConverter != null
              && desugaredLibraryAPIConverter.shouldRewriteInvoke(method);
    }
  }

  private void registerLibraryRetargeting(DexMethod method, boolean b) {
    if (!needsDesugaring) {
      needsDesugaring =
          desugaredLibraryRetargeter != null
              && desugaredLibraryRetargeter.getRetargetedMethod(method, b) != null;
    }
  }

  @Override
  public void registerInvokeStatic(DexMethod method) {
    registerLibraryRetargeting(method, false);
    registerInterfaceMethodRewriting(method, STATIC);
    registerDesugaredLibraryAPIConverter(method);
  }

  @Override
  public void registerInvokeInterface(DexMethod method) {
    registerLibraryRetargeting(method, true);
    registerInterfaceMethodRewriting(method, INTERFACE);
    registerDesugaredLibraryAPIConverter(method);
  }

  @Override
  public void registerInvokeStatic(DexMethod method, boolean itf) {
    if (itf) {
      needsDesugaring = true;
    }
    registerInvokeStatic(method);
  }

  @Override
  public void registerCallSite(DexCallSite callSite) {
    super.registerCallSite(callSite);
    needsDesugaring = true;
  }

  @Override
  public void registerInvokeSuper(DexMethod method) {
    registerLibraryRetargeting(method, false);
    registerInterfaceMethodRewriting(method, SUPER);
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
