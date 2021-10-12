// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.code.CfOrDexInstruction;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.util.ListIterator;

public class DefaultEnqueuerUseRegistry extends UseRegistry<ProgramMethod> {

  protected final AppView<? extends AppInfoWithClassHierarchy> appView;
  protected final Enqueuer enqueuer;
  private final AndroidApiLevelCompute computeApiLevel;
  private AndroidApiLevel maxApiReferenceLevel;

  public DefaultEnqueuerUseRegistry(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      ProgramMethod context,
      Enqueuer enqueuer,
      AndroidApiLevelCompute computeApiLevel) {
    super(appView, context);
    this.appView = appView;
    this.enqueuer = enqueuer;
    this.computeApiLevel = computeApiLevel;
    this.maxApiReferenceLevel = appView.options().minApiLevel;
  }

  public DexProgramClass getContextHolder() {
    return getContext().getHolder();
  }

  public DexEncodedMethod getContextMethod() {
    return getContext().getDefinition();
  }

  @Override
  public void registerInitClass(DexType clazz) {
    enqueuer.traceInitClass(clazz, getContext());
  }

  @Override
  public void registerInvokeVirtual(DexMethod invokedMethod) {
    setMaxApiReferenceLevel(invokedMethod);
    enqueuer.traceInvokeVirtual(invokedMethod, getContext());
  }

  @Override
  public void registerInvokeDirect(DexMethod invokedMethod) {
    setMaxApiReferenceLevel(invokedMethod);
    enqueuer.traceInvokeDirect(invokedMethod, getContext());
  }

  @Override
  public void registerInvokeStatic(DexMethod invokedMethod) {
    setMaxApiReferenceLevel(invokedMethod);
    enqueuer.traceInvokeStatic(invokedMethod, getContext());
  }

  @Override
  public void registerInvokeInterface(DexMethod invokedMethod) {
    setMaxApiReferenceLevel(invokedMethod);
    enqueuer.traceInvokeInterface(invokedMethod, getContext());
  }

  @Override
  public void registerInvokeSuper(DexMethod invokedMethod) {
    setMaxApiReferenceLevel(invokedMethod);
    enqueuer.traceInvokeSuper(invokedMethod, getContext());
  }

  @Override
  public void registerInstanceFieldRead(DexField field) {
    setMaxApiReferenceLevel(field);
    enqueuer.traceInstanceFieldRead(field, getContext());
  }

  @Override
  public void registerInstanceFieldReadFromMethodHandle(DexField field) {
    setMaxApiReferenceLevel(field);
    enqueuer.traceInstanceFieldReadFromMethodHandle(field, getContext());
  }

  @Override
  public void registerInstanceFieldWrite(DexField field) {
    setMaxApiReferenceLevel(field);
    enqueuer.traceInstanceFieldWrite(field, getContext());
  }

  @Override
  public void registerInstanceFieldWriteFromMethodHandle(DexField field) {
    setMaxApiReferenceLevel(field);
    enqueuer.traceInstanceFieldWriteFromMethodHandle(field, getContext());
  }

  @Override
  public void registerNewInstance(DexType type) {
    setMaxApiReferenceLevel(type);
    enqueuer.traceNewInstance(type, getContext());
  }

  @Override
  public void registerStaticFieldRead(DexField field) {
    setMaxApiReferenceLevel(field);
    enqueuer.traceStaticFieldRead(field, getContext());
  }

  @Override
  public void registerStaticFieldReadFromMethodHandle(DexField field) {
    setMaxApiReferenceLevel(field);
    enqueuer.traceStaticFieldReadFromMethodHandle(field, getContext());
  }

  @Override
  public void registerStaticFieldWrite(DexField field) {
    setMaxApiReferenceLevel(field);
    enqueuer.traceStaticFieldWrite(field, getContext());
  }

  @Override
  public void registerStaticFieldWriteFromMethodHandle(DexField field) {
    setMaxApiReferenceLevel(field);
    enqueuer.traceStaticFieldWriteFromMethodHandle(field, getContext());
  }

  @Override
  public void registerConstClass(
      DexType type,
      ListIterator<? extends CfOrDexInstruction> iterator,
      boolean ignoreCompatRules) {
    enqueuer.traceConstClass(type, getContext(), iterator, ignoreCompatRules);
  }

  @Override
  public void registerCheckCast(DexType type, boolean ignoreCompatRules) {
    enqueuer.traceCheckCast(type, getContext(), ignoreCompatRules);
  }

  @Override
  public void registerSafeCheckCast(DexType type) {
    enqueuer.traceSafeCheckCast(type, getContext());
  }

  @Override
  public void registerTypeReference(DexType type) {
    enqueuer.traceTypeReference(type, getContext());
  }

  @Override
  public void registerInstanceOf(DexType type) {
    enqueuer.traceInstanceOf(type, getContext());
  }

  @Override
  public void registerExceptionGuard(DexType guard) {
    enqueuer.traceExceptionGuard(guard, getContext());
  }

  @Override
  public void registerMethodHandle(DexMethodHandle methodHandle, MethodHandleUse use) {
    super.registerMethodHandle(methodHandle, use);
    enqueuer.traceMethodHandle(methodHandle, use, getContext());
  }

  @Override
  public void registerCallSite(DexCallSite callSite) {
    super.registerCallSite(callSite);
    enqueuer.traceCallSite(callSite, getContext());
  }

  private void setMaxApiReferenceLevel(DexReference reference) {
    if (reference.isDexMember()) {
      maxApiReferenceLevel =
          maxApiReferenceLevel.max(
              computeApiLevel.computeApiLevelForDefinition(
                  reference.asDexMember(), appView.dexItemFactory()));
    }
    maxApiReferenceLevel =
        maxApiReferenceLevel.max(computeApiLevel.computeApiLevelForLibraryReference(reference));
  }

  public AndroidApiLevel getMaxApiReferenceLevel() {
    return maxApiReferenceLevel;
  }
}
