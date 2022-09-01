// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.optimize;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class InvokeSingleTargetExtractor extends UseRegistry<ProgramMethod> {

  private InvokeKind kind = InvokeKind.NONE;
  private DexMethod target;

  public InvokeSingleTargetExtractor(AppView<AppInfoWithLiveness> appView, ProgramMethod context) {
    super(appView, context);
  }

  private void setTarget(DexMethod target, InvokeKind kind) {
    if (this.kind != InvokeKind.NONE) {
      this.kind = InvokeKind.ILLEGAL;
      this.target = null;
    } else {
      assert this.target == null;
      this.target = target;
      this.kind = kind;
    }
  }

  private void invalid() {
    kind = InvokeKind.ILLEGAL;
  }

  public DexMethod getTarget() {
    return target;
  }

  public InvokeKind getKind() {
    return kind;
  }

  @Override
  public void registerInitClass(DexType clazz) {
    invalid();
  }

  @Override
  public void registerInvokeVirtual(DexMethod method) {
    setTarget(method, InvokeKind.VIRTUAL);
  }

  @Override
  public void registerInvokeDirect(DexMethod method) {
    setTarget(method, InvokeKind.DIRECT);
  }

  @Override
  public void registerInvokeStatic(DexMethod method) {
    setTarget(method, InvokeKind.STATIC);
  }

  @Override
  public void registerInvokeInterface(DexMethod method) {
    invalid();
  }

  @Override
  public void registerInvokeSuper(DexMethod method) {
    setTarget(method, InvokeKind.SUPER);
  }

  @Override
  public void registerInstanceFieldWrite(DexField field) {
    invalid();
  }

  @Override
  public void registerInstanceFieldRead(DexField field) {
    invalid();
  }

  @Override
  public void registerNewInstance(DexType type) {
    invalid();
  }

  @Override
  public void registerStaticFieldRead(DexField field) {
    invalid();
  }

  @Override
  public void registerStaticFieldWrite(DexField field) {
    invalid();
  }

  @Override
  public void registerTypeReference(DexType type) {
    invalid();
  }

  @Override
  public void registerInstanceOf(DexType type) {
    invalid();
  }

  public enum InvokeKind {
    DIRECT,
    VIRTUAL,
    STATIC,
    SUPER,
    ILLEGAL,
    NONE
  }
}
