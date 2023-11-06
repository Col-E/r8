// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic.apiconverter;

import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.synthetic.SyntheticCfCodeProvider;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

public class APIConversionCfCodeProvider extends SyntheticCfCodeProvider {

  private final DexMethod forwardMethod;
  private final boolean itfCall;
  private final DexMethod returnConversion;
  private final DexMethod[] parameterConversions;
  // By default, the method is forwarded to the receiver (unless static), if this is set, the
  // method is forwarded on this field on the receiver.
  private final int forwardCallOpcode;
  private final DexField forwardFieldOrNull;

  public APIConversionCfCodeProvider(
      AppView<?> appView,
      DexType holder,
      DexMethod forwardMethod,
      boolean itfCall,
      DexMethod returnConversion,
      DexMethod[] parameterConversions) {
    super(appView, holder);
    this.forwardMethod = forwardMethod;
    this.itfCall = itfCall;
    this.returnConversion = returnConversion;
    this.parameterConversions = parameterConversions;
    this.forwardCallOpcode = defaultForwardCallOpcode(itfCall);
    this.forwardFieldOrNull = null;
  }

  public APIConversionCfCodeProvider(
      AppView<?> appView,
      DexType holder,
      DexMethod forwardMethod,
      boolean itfCall,
      DexMethod returnConversion,
      DexMethod[] parameterConversions,
      int forwardCallOpcode) {
    super(appView, holder);
    this.forwardMethod = forwardMethod;
    this.itfCall = itfCall;
    this.returnConversion = returnConversion;
    this.parameterConversions = parameterConversions;
    this.forwardCallOpcode = forwardCallOpcode;
    this.forwardFieldOrNull = null;
  }

  public APIConversionCfCodeProvider(
      AppView<?> appView,
      DexType holder,
      DexMethod forwardMethod,
      boolean itfCall,
      DexMethod returnConversion,
      DexMethod[] parameterConversions,
      DexField forwardFieldOrNull) {
    super(appView, holder);
    this.forwardMethod = forwardMethod;
    this.itfCall = itfCall;
    this.returnConversion = returnConversion;
    this.parameterConversions = parameterConversions;
    this.forwardCallOpcode = defaultForwardCallOpcode(itfCall);
    this.forwardFieldOrNull = forwardFieldOrNull;
  }

  private int defaultForwardCallOpcode(boolean itfCall) {
    return itfCall ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
  }

  @Override
  public CfCode generateCfCode() {
    List<CfInstruction> instructions = new ArrayList<>();
    boolean isStatic = forwardCallOpcode == Opcodes.INVOKESTATIC;
    generatePushReceiver(instructions, isStatic);
    generateParameterConvertAndLoads(instructions, isStatic);
    generateForwardingCall(instructions);
    generateReturnConversion(instructions, isStatic);
    generateReturn(instructions);
    return standardCfCodeFromInstructions(instructions);
  }

  private void generateReturn(List<CfInstruction> instructions) {
    if (forwardMethod.getReturnType().isVoidType()) {
      instructions.add(new CfReturnVoid());
    } else {
      ValueType valueType = valueTypeFromForwardMethod(forwardMethod.getReturnType());
      instructions.add(new CfReturn(valueType));
    }
  }

  private void generateReturnConversion(List<CfInstruction> instructions, boolean isStatic) {
    if (returnConversion != null) {
      generateConversion(instructions, returnConversion, isStatic);
    }
  }

  private void generateConversion(
      List<CfInstruction> instructions, DexMethod conversion, boolean isStatic) {
    if (conversion.getArity() == 2) {
      // If there is a second parameter, D8/R8 passes the  receiver as the second parameter.
      if (isStatic) {
        throw new CompilationError("Unsupported conversion with two parameters on static method");
      }
      generatePushReceiver(instructions, isStatic);
    } else if (conversion.getArity() != 1) {
      throw new CompilationError(
          "Unsupported conversion with invalid number of parameters ("
              + conversion.getArity()
              + ")");
    }
    instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, conversion, false));
  }

  private void generateForwardingCall(List<CfInstruction> instructions) {
    instructions.add(new CfInvoke(forwardCallOpcode, forwardMethod, itfCall));
  }

  private void generateParameterConvertAndLoads(
      List<CfInstruction> instructions, boolean isStatic) {
    int localIndex = BooleanUtils.intValue(!isStatic);
    for (int i = 0; i < forwardMethod.getArity(); i++) {
      ValueType valueType = valueTypeFromForwardMethod(forwardMethod.getParameter(i));
      instructions.add(new CfLoad(valueType, localIndex));
      if (parameterConversions[i] != null) {
        generateConversion(instructions, parameterConversions[i], isStatic);
      }
      localIndex += valueType.isWide() ? 2 : 1;
    }
  }

  private void generatePushReceiver(List<CfInstruction> instructions, boolean isStatic) {
    if (!isStatic) {
      instructions.add(new CfLoad(ValueType.OBJECT, 0));
      if (forwardFieldOrNull != null) {
        instructions.add(new CfInstanceFieldRead(forwardFieldOrNull));
      }
    }
  }

  private ValueType valueTypeFromForwardMethod(DexType type) {
    // We note that the type may not be exact, we can have a vivified type instead of the expected
    // type, however, the valueType is correct (primitive types are never converted).
    return ValueType.fromDexType(type);
  }
}
