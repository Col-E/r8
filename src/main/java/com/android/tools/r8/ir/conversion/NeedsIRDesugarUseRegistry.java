// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.ir.desugar.BackportedMethodRewriter;
import com.android.tools.r8.ir.desugar.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.DesugaredLibraryRetargeter;
import com.android.tools.r8.ir.desugar.InterfaceMethodRewriter;
import com.android.tools.r8.ir.desugar.TwrCloseResourceRewriter;

class NeedsIRDesugarUseRegistry extends UseRegistry {

  private boolean needsDesugarging = false;
  private final AppView appView;
  private final BackportedMethodRewriter backportedMethodRewriter;
  private final DesugaredLibraryRetargeter desugaredLibraryRetargeter;
  private final InterfaceMethodRewriter interfaceMethodRewriter;
  private final DesugaredLibraryAPIConverter desugaredLibraryAPIConverter;

  public NeedsIRDesugarUseRegistry(
      AppView appView,
      BackportedMethodRewriter backportedMethodRewriter,
      DesugaredLibraryRetargeter desugaredLibraryRetargeter,
      InterfaceMethodRewriter interfaceMethodRewriter,
      DesugaredLibraryAPIConverter desugaredLibraryAPIConverter) {
    super(appView.dexItemFactory());
    this.appView = appView;
    this.backportedMethodRewriter = backportedMethodRewriter;
    this.desugaredLibraryRetargeter = desugaredLibraryRetargeter;
    this.interfaceMethodRewriter = interfaceMethodRewriter;
    this.desugaredLibraryAPIConverter = desugaredLibraryAPIConverter;
  }

  public boolean needsDesugaring() {
    return needsDesugarging;
  }

  @Override
  public void registerInitClass(DexType type) {
    if (!needsDesugarging
        && desugaredLibraryAPIConverter != null
        && desugaredLibraryAPIConverter.canConvert(type)) {
      needsDesugarging = true;
    }
  }

  @Override
  public void registerInvokeVirtual(DexMethod method) {
    registerBackportedMethodRewriting(method);
    registerLibraryRetargeting(method, false);
    registerInterfaceMethodRewriting(method);
    registerDesugaredLibraryAPIConverter(method);
  }

  @Override
  public void registerInvokeDirect(DexMethod method) {
    registerLibraryRetargeting(method, false);
    registerInterfaceMethodRewriting(method);
    registerDesugaredLibraryAPIConverter(method);
  }

  private void registerBackportedMethodRewriting(DexMethod method) {
    if (!needsDesugarging) {
      needsDesugarging = backportedMethodRewriter.needsDesugaring(method);
    }
  }

  private void registerInterfaceMethodRewriting(DexMethod method) {
    if (!needsDesugarging) {
      needsDesugarging =
          interfaceMethodRewriter != null && interfaceMethodRewriter.needsRewriting(method);
    }
  }

  private void registerDesugaredLibraryAPIConverter(DexMethod method) {
    if (!needsDesugarging) {
      needsDesugarging =
          desugaredLibraryAPIConverter != null
              && desugaredLibraryAPIConverter.shouldRewriteInvoke(method);
    }
  }

  private void registerLibraryRetargeting(DexMethod method, boolean b) {
    if (!needsDesugarging) {
      needsDesugarging =
          desugaredLibraryRetargeter != null
              && desugaredLibraryRetargeter.getRetargetedMethod(method, b) != null;
    }
  }

  @Override
  public void registerInvokeStatic(DexMethod method) {
    if (!needsDesugarging) {
      needsDesugarging = TwrCloseResourceRewriter.isSynthesizedCloseResourceMethod(method, appView);
    }
    registerBackportedMethodRewriting(method);
    registerLibraryRetargeting(method, false);
    registerInterfaceMethodRewriting(method);
    registerDesugaredLibraryAPIConverter(method);
  }

  @Override
  public void registerInvokeInterface(DexMethod method) {
    registerLibraryRetargeting(method, true);
    registerInterfaceMethodRewriting(method);
    registerDesugaredLibraryAPIConverter(method);
  }

  @Override
  public void registerInvokeStatic(DexMethod method, boolean itf) {
    if (itf) {
      needsDesugarging = true;
    }
    registerInvokeStatic(method);
  }

  @Override
  public void registerCallSite(DexCallSite callSite) {
    super.registerCallSite(callSite);
    needsDesugarging = true;
  }

  @Override
  public void registerInvokeSuper(DexMethod method) {
    registerLibraryRetargeting(method, false);
    registerInterfaceMethodRewriting(method);
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
