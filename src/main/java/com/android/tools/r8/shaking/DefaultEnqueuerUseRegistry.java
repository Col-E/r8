// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.code.CfOrDexInstruction;
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
import java.util.ListIterator;

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
  public void registerInitClass(DexType clazz) {
    enqueuer.traceInitClass(clazz, context);
  }

  @Override
  public void registerInvokeVirtual(DexMethod invokedMethod) {
    enqueuer.traceInvokeVirtual(invokedMethod, context);
  }

  @Override
  public void registerInvokeDirect(DexMethod invokedMethod) {
    enqueuer.traceInvokeDirect(invokedMethod, context);
  }

  @Override
  public void registerInvokeStatic(DexMethod invokedMethod) {
    enqueuer.traceInvokeStatic(invokedMethod, context);
  }

  @Override
  public void registerInvokeInterface(DexMethod invokedMethod) {
    enqueuer.traceInvokeInterface(invokedMethod, context);
  }

  @Override
  public void registerInvokeSuper(DexMethod invokedMethod) {
    enqueuer.traceInvokeSuper(invokedMethod, context);
  }

  @Override
  public void registerInstanceFieldRead(DexField field) {
    enqueuer.traceInstanceFieldRead(field, context);
  }

  @Override
  public void registerInstanceFieldReadFromMethodHandle(DexField field) {
    enqueuer.traceInstanceFieldReadFromMethodHandle(field, context);
  }

  @Override
  public void registerInstanceFieldWrite(DexField field) {
    enqueuer.traceInstanceFieldWrite(field, context);
  }

  @Override
  public void registerInstanceFieldWriteFromMethodHandle(DexField field) {
    enqueuer.traceInstanceFieldWriteFromMethodHandle(field, context);
  }

  @Override
  public void registerNewInstance(DexType type) {
    enqueuer.traceNewInstance(type, context);
  }

  @Override
  public void registerStaticFieldRead(DexField field) {
    enqueuer.traceStaticFieldRead(field, context);
  }

  @Override
  public void registerStaticFieldReadFromMethodHandle(DexField field) {
    enqueuer.traceStaticFieldReadFromMethodHandle(field, context);
  }

  @Override
  public void registerStaticFieldWrite(DexField field) {
    enqueuer.traceStaticFieldWrite(field, context);
  }

  @Override
  public void registerStaticFieldWriteFromMethodHandle(DexField field) {
    enqueuer.traceStaticFieldWriteFromMethodHandle(field, context);
  }

  @Override
  public void registerConstClass(
      DexType type, ListIterator<? extends CfOrDexInstruction> iterator) {
    enqueuer.traceConstClass(type, context, iterator);
  }

  @Override
  public void registerCheckCast(DexType type) {
    enqueuer.traceCheckCast(type, context);
  }

  @Override
  public void registerTypeReference(DexType type) {
    enqueuer.traceTypeReference(type, context);
  }

  @Override
  public void registerInstanceOf(DexType type) {
    enqueuer.traceInstanceOf(type, context);
  }

  @Override
  public void registerExceptionGuard(DexType guard) {
    enqueuer.traceExceptionGuard(guard, context);
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
