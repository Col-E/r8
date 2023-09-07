// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import static com.android.tools.r8.ir.desugar.records.RecordRewriterHelper.isInvokeDynamicOnRecord;

import com.android.tools.r8.androidapi.AndroidApiLevelCompute;
import com.android.tools.r8.dex.code.CfOrDexInstruction;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import java.util.ListIterator;

public class DefaultEnqueuerUseRegistry extends ComputeApiLevelUseRegistry {

  protected final AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy;
  protected final Enqueuer enqueuer;

  public DefaultEnqueuerUseRegistry(
      AppView<? extends AppInfoWithClassHierarchy> appViewWithClassHierarchy,
      ProgramMethod context,
      Enqueuer enqueuer,
      AndroidApiLevelCompute apiLevelCompute) {
    super(appViewWithClassHierarchy, context, apiLevelCompute);
    this.appViewWithClassHierarchy = appViewWithClassHierarchy;
    this.enqueuer = enqueuer;
  }

  public DexProgramClass getContextHolder() {
    return getContext().getHolder();
  }

  public DexEncodedMethod getContextMethod() {
    return getContext().getDefinition();
  }

  @Override
  public void registerInitClass(DexType clazz) {
    super.registerInitClass(clazz);
    enqueuer.traceInitClass(clazz, getContext());
  }

  @Override
  public void registerRecordFieldValues(DexField[] fields) {
    super.registerRecordFieldValues(fields);
    enqueuer.traceRecordFieldValues(fields, getContext());
  }

  @Override
  public void registerInvokeVirtual(DexMethod invokedMethod) {
    super.registerInvokeVirtual(invokedMethod);
    enqueuer.traceInvokeVirtual(invokedMethod, getContext());
  }

  @Override
  public void registerInvokeDirect(DexMethod invokedMethod) {
    super.registerInvokeDirect(invokedMethod);
    enqueuer.traceInvokeDirect(invokedMethod, getContext());
  }

  @Override
  public void registerInvokeStatic(DexMethod invokedMethod) {
    super.registerInvokeStatic(invokedMethod);
    enqueuer.traceInvokeStatic(invokedMethod, getContext());
  }

  @Override
  public void registerInvokeInterface(DexMethod invokedMethod) {
    super.registerInvokeInterface(invokedMethod);
    enqueuer.traceInvokeInterface(invokedMethod, getContext());
  }

  @Override
  public void registerInvokeSuper(DexMethod invokedMethod) {
    super.registerInvokeSuper(invokedMethod);
    enqueuer.traceInvokeSuper(invokedMethod, getContext());
  }

  @Override
  public void registerInstanceFieldRead(DexField field) {
    super.registerInstanceFieldRead(field);
    enqueuer.traceInstanceFieldRead(field, getContext());
  }

  @Override
  public void registerInstanceFieldReadFromMethodHandle(DexField field) {
    super.registerInstanceFieldReadFromMethodHandle(field);
    enqueuer.traceInstanceFieldReadFromMethodHandle(field, getContext());
  }

  private void registerInstanceFieldReadFromRecordMethodHandle(DexField field) {
    super.registerInstanceFieldWriteFromMethodHandle(field);
    enqueuer.traceInstanceFieldReadFromRecordMethodHandle(field, getContext());
  }

  @Override
  public void registerInstanceFieldWrite(DexField field) {
    super.registerInstanceFieldWrite(field);
    enqueuer.traceInstanceFieldWrite(field, getContext());
  }

  @Override
  public void registerInstanceFieldWriteFromMethodHandle(DexField field) {
    super.registerInstanceFieldWriteFromMethodHandle(field);
    enqueuer.traceInstanceFieldWriteFromMethodHandle(field, getContext());
  }

  @Override
  public void registerNewInstance(DexType type) {
    super.registerNewInstance(type);
    enqueuer.traceNewInstance(type, getContext());
  }

  @Override
  public void registerStaticFieldRead(DexField field) {
    super.registerStaticFieldRead(field);
    enqueuer.traceStaticFieldRead(field, getContext());
  }

  @Override
  public void registerStaticFieldReadFromMethodHandle(DexField field) {
    super.registerStaticFieldReadFromMethodHandle(field);
    enqueuer.traceStaticFieldReadFromMethodHandle(field, getContext());
  }

  @Override
  public void registerStaticFieldWrite(DexField field) {
    super.registerStaticFieldWrite(field);
    enqueuer.traceStaticFieldWrite(field, getContext());
  }

  @Override
  public void registerStaticFieldWriteFromMethodHandle(DexField field) {
    super.registerStaticFieldWriteFromMethodHandle(field);
    enqueuer.traceStaticFieldWriteFromMethodHandle(field, getContext());
  }

  @Override
  public void registerConstClass(
      DexType type,
      ListIterator<? extends CfOrDexInstruction> iterator,
      boolean ignoreCompatRules) {
    super.registerConstClass(type, iterator, ignoreCompatRules);
    enqueuer.traceConstClass(type, getContext(), iterator, ignoreCompatRules);
  }

  @Override
  public void registerCheckCast(DexType type, boolean ignoreCompatRules) {
    super.registerCheckCast(type, ignoreCompatRules);
    enqueuer.traceCheckCast(type, getContext(), ignoreCompatRules);
  }

  @Override
  public void registerSafeCheckCast(DexType type) {
    super.registerSafeCheckCast(type);
    enqueuer.traceSafeCheckCast(type, getContext());
  }

  @Override
  public void registerTypeReference(DexType type) {
    super.registerTypeReference(type);
    enqueuer.traceTypeReference(type, getContext());
  }

  @Override
  public void registerInstanceOf(DexType type) {
    super.registerInstanceOf(type);
    enqueuer.traceInstanceOf(type, getContext());
  }

  @Override
  public void registerExceptionGuard(DexType guard) {
    super.registerExceptionGuard(guard);
    enqueuer.traceExceptionGuard(guard, getContext());
  }

  @Override
  public void registerMethodHandle(DexMethodHandle methodHandle, MethodHandleUse use) {
    super.registerMethodHandle(methodHandle, use);
    enqueuer.traceMethodHandle(methodHandle, use, getContext());
  }

  @Override
  public void registerCallSite(DexCallSite callSite) {
    super.registerCallSiteExceptBootstrapArgs(callSite);
    if (isInvokeDynamicOnRecord(callSite, appViewWithClassHierarchy, getContext())) {
      registerRecordCallSiteBootstrapArgs(callSite);
    } else {
      super.registerCallSiteBootstrapArgs(callSite, 0, callSite.bootstrapArgs.size());
    }
    enqueuer.traceCallSite(callSite, getContext());
  }

  @SuppressWarnings("HidingField")
  private void registerRecordCallSiteBootstrapArgs(DexCallSite callSite) {
    // The Instance Get method handle in invokeDynamicOnRecord are considered:
    // - a record use if not a constant value,
    // - unused if a constant value.
    registerCallSiteBootstrapArgs(callSite, 0, 2);
    for (int i = 2; i < callSite.getBootstrapArgs().size(); i++) {
      DexField field = callSite.getBootstrapArgs().get(i).asDexValueMethodHandle().value.asField();
      DexEncodedField encodedField =
          appViewWithClassHierarchy.appInfo().resolveField(field, getContext()).getResolvedField();
      // Member value propagation does not rewrite method handles, special handling for this
      // method handle access is done after the final tree shaking.
      if (!encodedField.getOptimizationInfo().isDead()) {
        registerInstanceFieldReadFromRecordMethodHandle(field);
      }
    }
  }
}
