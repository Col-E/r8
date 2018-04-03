// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.cf.code;

import com.android.tools.r8.cf.CfPrinter;
import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueDouble;
import com.android.tools.r8.graph.DexValue.DexValueFloat;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.DexValue.DexValueLong;
import com.android.tools.r8.graph.DexValue.DexValueMethodHandle;
import com.android.tools.r8.graph.DexValue.DexValueMethodType;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.graph.UseRegistry;
import com.android.tools.r8.naming.NamingLens;
import java.util.List;
import org.objectweb.asm.Handle;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Type;

public class CfInvokeDynamic extends CfInstruction {

  private final DexCallSite callSite;

  public CfInvokeDynamic(DexCallSite callSite) {
    this.callSite = callSite;
  }

  @Override
  public void write(MethodVisitor visitor, NamingLens lens) {
    DexMethodHandle bootstrapMethod = callSite.bootstrapMethod;
    List<DexValue> bootstrapArgs = callSite.bootstrapArgs;
    Object[] bsmArgs = new Object[bootstrapArgs.size()];
    for (int i = 0; i < bootstrapArgs.size(); i++) {
      bsmArgs[i] = decodeBootstrapArgument(bootstrapArgs.get(i), lens);
    }
    Handle bsmHandle = bootstrapMethod.toAsmHandle(lens);
    DexString methodName;
    if (lens.isIdentityLens()) {
      methodName = callSite.methodName;
    } else if (!callSite.interfaceMethods.isEmpty()) {
      methodName = lens.lookupName(callSite.interfaceMethods.get(0));
    } else {
      throw new Unimplemented("Minification of non-lambda InvokeDynamic not supported");
    }
    visitor.visitInvokeDynamicInsn(
        methodName.toString(), callSite.methodProto.toDescriptorString(lens), bsmHandle, bsmArgs);
  }

  private Object decodeBootstrapArgument(DexValue dexValue, NamingLens lens) {
    if (dexValue instanceof DexValueInt) {
      return ((DexValueInt) dexValue).getValue();
    } else if (dexValue instanceof DexValueLong) {
      return ((DexValueLong) dexValue).getValue();
    } else if (dexValue instanceof DexValueFloat) {
      return ((DexValueFloat) dexValue).getValue();
    } else if (dexValue instanceof DexValueDouble) {
      return ((DexValueDouble) dexValue).getValue();
    } else if (dexValue instanceof DexValueString) {
      return ((DexValueString) dexValue).getValue();
    } else if (dexValue instanceof DexValueType) {
      return Type.getType(lens.lookupDescriptor(((DexValueType) dexValue).value).toString());
    } else if (dexValue instanceof DexValueMethodType) {
      return Type.getMethodType(((DexValueMethodType) dexValue).value.toDescriptorString(lens));
    } else if (dexValue instanceof DexValueMethodHandle) {
      return ((DexValueMethodHandle) dexValue).value.toAsmHandle(lens);
    } else {
      throw new Unreachable(
          "Unsupported bootstrap argument of type " + dexValue.getClass().getSimpleName());
    }
  }

  @Override
  public void print(CfPrinter printer) {
    printer.print(this);
  }

  public DexCallSite getCallSite() {
    return callSite;
  }

  @Override
  public void registerUse(UseRegistry registry, DexType clazz) {
    registry.registerCallSite(callSite);
  }
}
