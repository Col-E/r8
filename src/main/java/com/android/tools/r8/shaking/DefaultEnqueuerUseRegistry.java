// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;

public class DefaultEnqueuerUseRegistry extends UseRegistry {

  private final ProgramMethod context;
  protected final Enqueuer enqueuer;

  public DefaultEnqueuerUseRegistry(AppView<?> appView, ProgramMethod context, Enqueuer enqueuer) {
    super(appView.dexItemFactory());
    this.context = context;
    this.enqueuer = enqueuer;
  }

  public ProgramMethod getContext() {
    return context;
  }

  public DexProgramClass getContextHolder() {
    return context.getHolder();
  }

  public DexEncodedMethod getContextMethod() {
    return context.getDefinition();
  }

  @Override
  public boolean registerInitClass(DexType clazz) {
    return enqueuer.traceInitClass(clazz, context);
  }

  @Override
  public boolean registerInvokeVirtual(DexMethod invokedMethod) {
    return enqueuer.traceInvokeVirtual(invokedMethod, context);
  }

  @Override
  public boolean registerInvokeDirect(DexMethod invokedMethod) {
    return enqueuer.traceInvokeDirect(invokedMethod, context);
  }

  @Override
  public boolean registerInvokeStatic(DexMethod invokedMethod) {
    return enqueuer.traceInvokeStatic(invokedMethod, context);
  }

  @Override
  public boolean registerInvokeInterface(DexMethod invokedMethod) {
    return enqueuer.traceInvokeInterface(invokedMethod, context);
  }

  @Override
  public boolean registerInvokeSuper(DexMethod invokedMethod) {
    return enqueuer.traceInvokeSuper(invokedMethod, context);
  }

  @Override
  public boolean registerInstanceFieldRead(DexField field) {
    return enqueuer.traceInstanceFieldRead(field, context);
  }

  @Override
  public boolean registerInstanceFieldReadFromMethodHandle(DexField field) {
    return enqueuer.traceInstanceFieldReadFromMethodHandle(field, context);
  }

  @Override
  public boolean registerInstanceFieldWrite(DexField field) {
    return enqueuer.traceInstanceFieldWrite(field, context);
  }

  @Override
  public boolean registerInstanceFieldWriteFromMethodHandle(DexField field) {
    return enqueuer.traceInstanceFieldWriteFromMethodHandle(field, context);
  }

  @Override
  public boolean registerNewInstance(DexType type) {
    return enqueuer.traceNewInstance(type, context);
  }

  @Override
  public boolean registerStaticFieldRead(DexField field) {
    return enqueuer.traceStaticFieldRead(field, context);
  }

  @Override
  public boolean registerStaticFieldReadFromMethodHandle(DexField field) {
    return enqueuer.traceStaticFieldReadFromMethodHandle(field, context);
  }

  @Override
  public boolean registerStaticFieldWrite(DexField field) {
    return enqueuer.traceStaticFieldWrite(field, context);
  }

  @Override
  public boolean registerStaticFieldWriteFromMethodHandle(DexField field) {
    return enqueuer.traceStaticFieldWriteFromMethodHandle(field, context);
  }

  @Override
  public boolean registerConstClass(DexType type) {
    return enqueuer.traceConstClass(type, context);
  }

  @Override
  public boolean registerCheckCast(DexType type) {
    return enqueuer.traceCheckCast(type, context);
  }

  @Override
  public boolean registerTypeReference(DexType type) {
    return enqueuer.traceTypeReference(type, context);
  }

  @Override
  public void registerMethodHandle(DexMethodHandle methodHandle, MethodHandleUse use) {
    super.registerMethodHandle(methodHandle, use);
    enqueuer.traceMethodHandle(methodHandle, use, context);
  }

  @Override
  public void registerCallSite(DexCallSite callSite) {
    super.registerCallSite(callSite);
    enqueuer.traceCallSite(callSite, context);
  }
}
