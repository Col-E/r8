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
import com.android.tools.r8.ir.desugar.TwrCloseResourceRewriter;

class NeedsIRDesugarUseRegistry extends UseRegistry {

  private boolean needsDesugarging = false;
  private final AppView appView;
  private final BackportedMethodRewriter backportedMethodRewriter;

  public NeedsIRDesugarUseRegistry(
      AppView appView, BackportedMethodRewriter backportedMethodRewriter) {
    super(appView.dexItemFactory());
    this.appView = appView;
    this.backportedMethodRewriter = backportedMethodRewriter;
  }

  public boolean needsDesugaring() {
    return needsDesugarging;
  }

  @Override
  public void registerInitClass(DexType type) {}

  @Override
  public void registerInvokeVirtual(DexMethod method) {
    if (backportedMethodRewriter.needsDesugaring(method)) {
      needsDesugarging = true;
    }
  }

  @Override
  public void registerInvokeDirect(DexMethod method) {}

  @Override
  public void registerInvokeStatic(DexMethod method) {
    if (TwrCloseResourceRewriter.isSynthesizedCloseResourceMethod(method, appView)) {
      needsDesugarging = true;
    }

    if (backportedMethodRewriter.needsDesugaring(method)) {
      needsDesugarging = true;
    }
  }

  @Override
  public void registerInvokeInterface(DexMethod method) {}

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
  public void registerInvokeSuper(DexMethod method) {}

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
