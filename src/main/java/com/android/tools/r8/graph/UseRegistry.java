// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import com.android.tools.r8.dex.code.CfOrDexInstanceFieldRead;
import com.android.tools.r8.dex.code.CfOrDexInstruction;
import com.android.tools.r8.dex.code.CfOrDexStaticFieldRead;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeInstructionMetadata;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.ListIterator;

public abstract class UseRegistry<T extends Definition> {

  protected final AppView<?> appView;
  private final T context;

  private TraversalContinuation<?, ?> continuation = TraversalContinuation.doContinue();

  public enum MethodHandleUse {
    ARGUMENT_TO_LAMBDA_METAFACTORY,
    NOT_ARGUMENT_TO_LAMBDA_METAFACTORY
  }

  public UseRegistry(AppView<?> appView, T context) {
    this.appView = appView;
    this.context = context;
  }

  public final void accept(ProgramMethod method) {
    method.registerCodeReferences(this);
  }

  public DexItemFactory dexItemFactory() {
    return appView.dexItemFactory();
  }

  public void doBreak() {
    assert continuation.shouldContinue();
    continuation = TraversalContinuation.doBreak();
  }

  public GraphLens getCodeLens() {
    assert context.isMethod();
    return getMethodContext().getDefinition().getCode().getCodeLens(appView);
  }

  public final T getContext() {
    return context;
  }

  public final DexClassAndMethod getMethodContext() {
    assert context.isMethod();
    return context.asMethod();
  }

  public TraversalContinuation<?, ?> getTraversalContinuation() {
    return continuation;
  }

  public void registerRecordFieldValues(DexField[] fields) {
    registerTypeReference(appView.dexItemFactory().objectArrayType);
  }

  public abstract void registerInitClass(DexType type);

  public abstract void registerInvokeVirtual(DexMethod method);

  public abstract void registerInvokeDirect(DexMethod method);

  public void registerInvokeSpecial(DexMethod method, boolean itf) {
    registerInvokeSpecial(method);
  }

  public void registerInvokeSpecial(DexMethod method) {
    DexClassAndMethod context = getMethodContext();
    InvokeType type = InvokeType.fromInvokeSpecial(method, context, appView, getCodeLens());
    if (type.isDirect()) {
      registerInvokeDirect(method);
    } else {
      assert type.isSuper();
      registerInvokeSuper(method);
    }
  }

  public abstract void registerInvokeStatic(DexMethod method);

  public abstract void registerInvokeInterface(DexMethod method);

  public abstract void registerInvokeSuper(DexMethod method);

  public abstract void registerInstanceFieldRead(DexField field);

  public void registerInstanceFieldReadWithMetadata(
      DexField field, BytecodeInstructionMetadata metadata) {
    registerInstanceFieldRead(field);
  }

  public void registerInstanceFieldReadInstruction(CfOrDexInstanceFieldRead instruction) {
    registerInstanceFieldRead(instruction.getField());
  }

  public void registerInstanceFieldReadFromMethodHandle(DexField field) {
    registerInstanceFieldRead(field);
  }

  public abstract void registerInstanceFieldWrite(DexField field);

  public void registerInstanceFieldWriteFromMethodHandle(DexField field) {
    registerInstanceFieldWrite(field);
  }

  public void registerInvokeStatic(DexMethod method, boolean itf) {
    registerInvokeStatic(method);
  }

  public void registerNewInstance(DexType type) {
    registerTypeReference(type);
  }

  public void registerNewUnboxedEnumInstance(DexType type) {
    registerTypeReference(type);
  }

  public abstract void registerStaticFieldRead(DexField field);

  public void registerStaticFieldReadWithMetadata(
      DexField field, BytecodeInstructionMetadata metadata) {
    registerStaticFieldRead(field);
  }

  public void registerStaticFieldReadInstruction(CfOrDexStaticFieldRead instruction) {
    registerStaticFieldRead(instruction.getField());
  }

  public void registerStaticFieldReadFromMethodHandle(DexField field) {
    registerStaticFieldRead(field);
  }

  public abstract void registerStaticFieldWrite(DexField field);

  public void registerStaticFieldWriteFromMethodHandle(DexField field) {
    registerStaticFieldWrite(field);
  }

  public abstract void registerTypeReference(DexType type);

  public void registerInstanceOf(DexType type) {
    registerTypeReference(type);
  }

  public void registerConstClass(
      DexType type,
      ListIterator<? extends CfOrDexInstruction> iterator,
      boolean ignoreCompatRules) {
    registerTypeReference(type);
  }

  public void registerCheckCast(DexType type, boolean ignoreCompatRules) {
    registerTypeReference(type);
  }

  public void registerSafeCheckCast(DexType type) {
    registerCheckCast(type, true);
  }

  public void registerExceptionGuard(DexType guard) {
    registerTypeReference(guard);
  }

  public void registerMethodHandle(DexMethodHandle methodHandle, MethodHandleUse use) {
    switch (methodHandle.type) {
      case INSTANCE_GET:
        registerInstanceFieldReadFromMethodHandle(methodHandle.asField());
        break;
      case INSTANCE_PUT:
        registerInstanceFieldWriteFromMethodHandle(methodHandle.asField());
        break;
      case STATIC_GET:
        registerStaticFieldReadFromMethodHandle(methodHandle.asField());
        break;
      case STATIC_PUT:
        registerStaticFieldWriteFromMethodHandle(methodHandle.asField());
        break;
      case INVOKE_INSTANCE:
        registerInvokeVirtual(methodHandle.asMethod());
        break;
      case INVOKE_STATIC:
        registerInvokeStatic(methodHandle.asMethod());
        break;
      case INVOKE_CONSTRUCTOR:
        DexMethod method = methodHandle.asMethod();
        registerNewInstance(method.holder);
        registerInvokeDirect(method);
        break;
      case INVOKE_INTERFACE:
        registerInvokeInterface(methodHandle.asMethod());
        break;
      case INVOKE_SUPER:
        registerInvokeSuper(methodHandle.asMethod());
        break;
      case INVOKE_DIRECT:
        registerInvokeDirect(methodHandle.asMethod());
        break;
      default:
        throw new AssertionError();
    }
  }

  protected void registerCallSiteExceptBootstrapArgs(DexCallSite callSite) {
    boolean isLambdaMetaFactory =
        dexItemFactory().isLambdaMetafactoryMethod(callSite.bootstrapMethod.asMethod());

    if (!isLambdaMetaFactory) {
      registerMethodHandle(
          callSite.bootstrapMethod, MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
    }

    // Lambda metafactory will use this type as the main SAM
    // interface for the dynamically created lambda class.
    registerTypeReference(callSite.methodProto.returnType);
  }

  protected void registerCallSiteBootstrapArgs(DexCallSite callSite, int start, int end) {
    boolean isLambdaMetaFactory =
        appView.dexItemFactory().isLambdaMetafactoryMethod(callSite.bootstrapMethod.asMethod());
    // Register bootstrap method arguments.
    // Only Type, MethodHandle, and MethodType need to be registered.
    assert start >= 0;
    assert end <= callSite.bootstrapArgs.size();
    for (int i = start; i < end; i++) {
      DexValue arg = callSite.bootstrapArgs.get(i);
      switch (arg.getValueKind()) {
        case METHOD_HANDLE:
          DexMethodHandle handle = arg.asDexValueMethodHandle().value;
          MethodHandleUse use =
              isLambdaMetaFactory
                  ? MethodHandleUse.ARGUMENT_TO_LAMBDA_METAFACTORY
                  : MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;
          registerMethodHandle(handle, use);
          break;
        case METHOD_TYPE:
          registerProto(arg.asDexValueMethodType().value);
          break;
        case TYPE:
          registerTypeReference(arg.asDexValueType().value);
          break;
        default:
          assert arg.isDexValueInt()
              || arg.isDexValueLong()
              || arg.isDexValueFloat()
              || arg.isDexValueDouble()
              || arg.isDexValueString();
      }
      if (continuation.shouldBreak()) {
        break;
      }
    }
  }

  public void registerCallSite(DexCallSite callSite) {
    registerCallSiteExceptBootstrapArgs(callSite);
    registerCallSiteBootstrapArgs(callSite, 0, callSite.bootstrapArgs.size());
  }

  public void registerProto(DexProto proto) {
    registerTypeReference(proto.returnType);
    for (DexType type : proto.parameters.values) {
      registerTypeReference(type);
    }
  }
}
