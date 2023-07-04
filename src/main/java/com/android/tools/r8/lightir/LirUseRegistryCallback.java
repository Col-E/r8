// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.lightir;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.graph.UseRegistry.MethodHandleUse;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeInstructionMetadata;
import com.android.tools.r8.ir.code.IfType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.naming.dexitembasedstring.NameComputationInfo;
import java.util.List;

public class LirUseRegistryCallback<EV> extends LirParsedInstructionCallback<EV> {

  private final UseRegistry registry;
  private BytecodeInstructionMetadata currentMetadata;

  public LirUseRegistryCallback(LirCode<EV> code, UseRegistry registry) {
    super(code);
    this.registry = registry;
  }

  public void setCurrentMetadata(BytecodeInstructionMetadata metadata) {
    currentMetadata = metadata;
  }

  @Override
  public int getCurrentValueIndex() {
    // The registry of instructions does not require knowledge of value indexes.
    return 0;
  }

  @Override
  public void onInstruction() {
    // TODO(b/225838009): Consider defining an abstract base class for the parsed exception so that
    //  missing a callback is a compile-time error.
    boolean debug = false;
    if (debug) {
      throw new Unimplemented();
    }
  }

  @Override
  public void onFallthrough() {
    // Nothing to register.
  }

  @Override
  public void onConstString(DexString string) {
    // Nothing to register.
  }

  @Override
  public void onThrow(EV exception) {
    // Nothing to register.
  }

  @Override
  public void onMoveException(DexType exceptionType) {
    // Nothing to register.
  }

  @Override
  public void onReturnVoid() {
    // Nothing to register.
  }

  @Override
  public void onReturn(EV value) {
    // Nothing to register.
  }

  @Override
  public void onGoto(int blockIndex) {
    // Nothing to register.
  }

  @Override
  public void onPhi(List<EV> operands) {
    // Nothing to register.
  }

  @Override
  public void onConstNumber(NumericType type, long value) {
    // Nothing to register.
  }

  @Override
  public void onCmpInstruction(int opcode, EV leftValue, EV rightValue) {
    // Nothing to register.
  }

  @Override
  public void onIf(IfType ifKind, int blockIndex, EV valueIndex) {
    // Nothing to register.
  }

  @Override
  public void onCheckCast(DexType type, EV value, boolean ignoreCompatRules) {
    registry.registerCheckCast(type, ignoreCompatRules);
  }

  @Override
  public void onSafeCheckCast(DexType type, EV value) {
    registry.registerSafeCheckCast(type);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void onConstClass(DexType type, boolean ignoreCompatRules) {
    registry.registerConstClass(type, null, ignoreCompatRules);
  }

  @Override
  public void onConstMethodHandle(DexMethodHandle methodHandle) {
    registry.registerMethodHandle(methodHandle, MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
  }

  @Override
  public void onConstMethodType(DexProto methodType) {
    registry.registerProto(methodType);
  }

  @Override
  public void onDexItemBasedConstString(
      DexReference item, NameComputationInfo<?> nameComputationInfo) {
    if (nameComputationInfo.needsToRegisterReference()) {
      assert item.isDexType();
      registry.registerTypeReference(item.asDexType());
    }
  }

  @Override
  public void onInitClass(DexType clazz) {
    registry.registerInitClass(clazz);
  }

  @Override
  public void onInstanceGet(DexField field, EV object) {
    registry.registerInstanceFieldReadWithMetadata(field, currentMetadata);
  }

  @Override
  public void onInstancePut(DexField field, EV object, EV value) {
    registry.registerInstanceFieldWrite(field);
  }

  @Override
  public void onStaticGet(DexField field) {
    registry.registerStaticFieldReadWithMetadata(field, currentMetadata);
  }

  @Override
  public void onStaticPut(DexField field, EV value) {
    registry.registerStaticFieldWrite(field);
  }

  @Override
  public void onInstanceOf(DexType type, EV value) {
    registry.registerInstanceOf(type);
  }

  @Override
  public void onInvokeCustom(DexCallSite callSite, List<EV> arguments) {
    registry.registerCallSite(callSite);
  }

  @Override
  public void onInvokeDirect(DexMethod method, List<EV> arguments, boolean isInterface) {
    registry.registerInvokeDirect(method);
  }

  @Override
  public void onInvokeInterface(DexMethod method, List<EV> arguments) {
    registry.registerInvokeInterface(method);
  }

  @Override
  public void onInvokeStatic(DexMethod method, List<EV> arguments, boolean isInterface) {
    registry.registerInvokeStatic(method);
  }

  @Override
  public void onInvokeSuper(DexMethod method, List<EV> arguments, boolean isInterface) {
    registry.registerInvokeSuper(method);
  }

  @Override
  public void onInvokeVirtual(DexMethod method, List<EV> arguments) {
    registry.registerInvokeVirtual(method);
  }

  @Override
  public void onInvokeMultiNewArray(DexType type, List<EV> arguments) {
    registry.registerTypeReference(type);
  }

  @Override
  public void onInvokeNewArray(DexType type, List<EV> arguments) {
    registry.registerTypeReference(type);
  }

  @Override
  public void onNewArrayEmpty(DexType type, EV size) {
    registry.registerTypeReference(type);
  }

  @Override
  public void onNewInstance(DexType clazz) {
    registry.registerNewInstance(clazz);
  }

  @Override
  public void onNewUnboxedEnumInstance(DexType type, int ordinal) {
    registry.registerNewUnboxedEnumInstance(type);
  }

  @Override
  public void onRecordFieldValues(DexField[] fields, List<EV> values) {
    registry.registerRecordFieldValues(fields);
  }
}
