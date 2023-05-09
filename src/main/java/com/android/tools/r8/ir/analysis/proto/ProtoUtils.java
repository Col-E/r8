// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;

public class ProtoUtils {

  public static final int IS_PROTO_2_MASK = 0x1;

  public static Value getInfoValueFromMessageInfoConstructionInvoke(
      InvokeMethod invoke, ProtoReferences references) {
    assert references.isMessageInfoConstruction(invoke);
    // First check if there is a call to the static method newMessageInfo(...).
    if (invoke.getInvokedMethod().match(references.newMessageInfoMethod)) {
      return invoke.getOperand(1);
    }
    // Otherwise, the static method has been inlined. Check if there is a call to
    // RawMessageInfo.<init>(...).
    assert invoke.isInvokeDirect();
    if (invoke.getInvokedMethod() == references.rawMessageInfoConstructor) {
      return invoke.getOperand(2);
    }
    // Otherwise, RawMessageInfo.<init>(...) has been inlined, and we should find a call to
    // Object.<init>(). In this case, we should find an instance field assignment to
    // `RawMessageInfo.info`.
    assert invoke.getInvokedMethod() == references.dexItemFactory().objectMembers.constructor;
    // Find the value being assigned to the `info` field.
    for (InstancePut instancePut :
        invoke.getFirstArgument().<InstancePut>uniqueUsers(Instruction::isInstancePut)) {
      if (instancePut.getField() == references.rawMessageInfoInfoField) {
        return instancePut.value();
      }
    }
    throw new Unreachable();
  }

  static Value getObjectsValueFromMessageInfoConstructionInvoke(
      InvokeMethod invoke, ProtoReferences references) {
    assert references.isMessageInfoConstruction(invoke);
    if (invoke.getInvokedMethod().match(references.newMessageInfoMethod)) {
      return invoke.getOperand(2);
    }
    assert invoke.isInvokeDirect();
    if (invoke.getInvokedMethod() == references.rawMessageInfoConstructor) {
      return invoke.getOperand(3);
    }
    assert invoke.getInvokedMethod() == references.dexItemFactory().objectMembers.constructor;
    // Find the value being assigned to the `info` field.
    for (InstancePut instancePut :
        invoke.getFirstArgument().<InstancePut>uniqueUsers(Instruction::isInstancePut)) {
      if (instancePut.getField() == references.rawMessageInfoObjectsField) {
        return instancePut.value();
      }
    }
    throw new Unreachable();
  }

  static void setObjectsValueForMessageInfoConstructionInvoke(
      InvokeMethod invoke, Value newObjectsValue, ProtoReferences references) {
    assert references.isMessageInfoConstruction(invoke);
    if (invoke.getInvokedMethod().match(references.newMessageInfoMethod)) {
      invoke.replaceValue(2, newObjectsValue);
      return;
    }
    assert invoke.isInvokeDirect();
    if (invoke.getInvokedMethod() == references.rawMessageInfoConstructor) {
      invoke.replaceValue(3, newObjectsValue);
      return;
    }
    assert invoke.getInvokedMethod() == references.dexItemFactory().objectMembers.constructor;
    // Find the value being assigned to the `info` field.
    for (InstancePut instancePut :
        invoke.getFirstArgument().<InstancePut>uniqueUsers(Instruction::isInstancePut)) {
      if (instancePut.getField() == references.rawMessageInfoObjectsField) {
        instancePut.setValue(newObjectsValue);
        return;
      }
    }
    throw new Unreachable();
  }

  public static boolean isProto2(int flags) {
    return (flags & IS_PROTO_2_MASK) != 0;
  }
}
