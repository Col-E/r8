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
import com.android.tools.r8.contexts.CompilationContext.UniqueContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryClasspathWrapperSynthesizeEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer;
import com.android.tools.r8.ir.synthetic.SyntheticCfCodeProvider;
import com.android.tools.r8.utils.BooleanUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.objectweb.asm.Opcodes;

public abstract class APIConversionCfCodeProvider extends SyntheticCfCodeProvider {

  DexMethod forwardMethod;
  DesugaredLibraryWrapperSynthesizer wrapperSynthesizor;
  boolean itfCall;

  public APIConversionCfCodeProvider(
      AppView<?> appView,
      DexType holder,
      DexMethod forwardMethod,
      DesugaredLibraryWrapperSynthesizer wrapperSynthesizor,
      boolean itfCall) {
    super(appView, holder);
    this.forwardMethod = forwardMethod;
    this.wrapperSynthesizor = wrapperSynthesizor;
    this.itfCall = itfCall;
  }

  DexType vivifiedTypeFor(DexType type) {
    return DesugaredLibraryAPIConverter.vivifiedTypeFor(type, appView);
  }

  abstract void generatePushReceiver(List<CfInstruction> instructions);

  abstract DexMethod ensureConversionMethod(DexType type, boolean destIsVivified);

  abstract DexMethod parameterConversion(DexType param);

  abstract DexMethod returnConversion(DexType param);

  abstract DexMethod getMethodToForwardTo();

  @Override
  public CfCode generateCfCode() {
    List<CfInstruction> instructions = new ArrayList<>();
    generatePushReceiver(instructions);
    generateParameterConvertAndLoad(instructions);
    generateForwardCall(instructions);
    generateConvertAndReturn(instructions);
    return standardCfCodeFromInstructions(instructions);
  }

  private void generateConvertAndReturn(List<CfInstruction> instructions) {
    DexType returnType = forwardMethod.proto.returnType;
    if (wrapperSynthesizor.shouldConvert(returnType, forwardMethod)) {
      instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, returnConversion(returnType), false));
      returnType = vivifiedTypeFor(returnType);
    }
    if (returnType.isVoidType()) {
      instructions.add(new CfReturnVoid());
    } else {
      instructions.add(new CfReturn(ValueType.fromDexType(returnType)));
    }
  }

  private void generateForwardCall(List<CfInstruction> instructions) {
    int opcode = itfCall ? Opcodes.INVOKEINTERFACE : Opcodes.INVOKEVIRTUAL;
    instructions.add(new CfInvoke(opcode, getMethodToForwardTo(), itfCall));
  }

  private void generateParameterConvertAndLoad(List<CfInstruction> instructions) {
    int stackIndex = 1;
    for (DexType param : forwardMethod.proto.parameters.values) {
      instructions.add(new CfLoad(ValueType.fromDexType(param), stackIndex));
      if (wrapperSynthesizor.shouldConvert(param, forwardMethod)) {
        instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, parameterConversion(param), false));
      }
      if (param.isWideType()) {
        stackIndex++;
      }
      stackIndex++;
    }
  }

  public static class VivifiedWrapperConversionCfCodeProvider extends APIConversionCfCodeProvider {

    private final DexField wrapperField;
    private final DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer eventConsumer;
    private final Supplier<UniqueContext> contextSupplier;

    public VivifiedWrapperConversionCfCodeProvider(
        AppView<?> appView,
        DexMethod forwardMethod,
        DexField wrapperField,
        DesugaredLibraryWrapperSynthesizer wrapperSynthesizer,
        boolean itfCall,
        DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer eventConsumer,
        Supplier<UniqueContext> contextSupplier) {
      super(appView, wrapperField.holder, forwardMethod, wrapperSynthesizer, itfCall);
      this.wrapperField = wrapperField;
      this.eventConsumer = eventConsumer;
      this.contextSupplier = contextSupplier;
    }

    @Override
    void generatePushReceiver(List<CfInstruction> instructions) {
      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
      instructions.add(new CfInstanceFieldRead(wrapperField));
    }

    @Override
    DexMethod ensureConversionMethod(DexType type, boolean destIsVivified) {
      return wrapperSynthesizor.getExistingProgramConversionMethod(
          type, destIsVivified, eventConsumer, contextSupplier);
    }

    @Override
    DexMethod getMethodToForwardTo() {
      DexType[] newParameters = forwardMethod.proto.parameters.values.clone();
      for (int i = 0; i < forwardMethod.proto.parameters.values.length; i++) {
        DexType param = forwardMethod.proto.parameters.values[i];
        if (wrapperSynthesizor.shouldConvert(param, forwardMethod)) {
          newParameters[i] = vivifiedTypeFor(param);
        }
      }

      DexType returnType = forwardMethod.proto.returnType;
      DexType forwardMethodReturnType =
          wrapperSynthesizor.shouldConvert(returnType, forwardMethod)
              ? vivifiedTypeFor(returnType)
              : returnType;

      DexProto newProto =
          appView.dexItemFactory().createProto(forwardMethodReturnType, newParameters);
      return appView.dexItemFactory().createMethod(wrapperField.type, newProto, forwardMethod.name);
    }

    @Override
    DexMethod parameterConversion(DexType param) {
      return ensureConversionMethod(param, true);
    }

    @Override
    DexMethod returnConversion(DexType param) {
      return ensureConversionMethod(param, false);
    }
  }

  public static class CallbackConversionCfCodeProvider extends APIConversionCfCodeProvider {

    private final DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer;
    private final Supplier<UniqueContext> contextSupplier;

    public CallbackConversionCfCodeProvider(
        AppView<?> appView,
        DexMethod forwardMethod,
        DesugaredLibraryWrapperSynthesizer wrapperSynthesizor,
        boolean itfCall,
        DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer,
        Supplier<UniqueContext> contextSupplier) {
      super(appView, forwardMethod.holder, forwardMethod, wrapperSynthesizor, itfCall);
      this.eventConsumer = eventConsumer;
      this.contextSupplier = contextSupplier;
    }

    @Override
    void generatePushReceiver(List<CfInstruction> instructions) {
      instructions.add(new CfLoad(ValueType.fromDexType(forwardMethod.holder), 0));
    }

    @Override
    DexMethod ensureConversionMethod(DexType type, boolean destIsVivified) {
      return wrapperSynthesizor.ensureConversionMethod(
          type, destIsVivified, eventConsumer, contextSupplier);
    }

    @Override
    DexMethod parameterConversion(DexType param) {
      return ensureConversionMethod(param, false);
    }

    @Override
    DexMethod returnConversion(DexType param) {
      return ensureConversionMethod(param, true);
    }

    @Override
    DexMethod getMethodToForwardTo() {
      return forwardMethod;
    }
  }

  public static class WrapperConversionCfCodeProvider extends APIConversionCfCodeProvider {

    private final DexField wrapperField;
    private final DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer eventConsumer;
    private final Supplier<UniqueContext> contextSupplier;

    public WrapperConversionCfCodeProvider(
        AppView<?> appView,
        DexMethod forwardMethod,
        DexField wrapperField,
        DesugaredLibraryWrapperSynthesizer wrapperSynthesizor,
        boolean itfCall,
        DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer eventConsumer,
        Supplier<UniqueContext> contextSupplier) {
      super(appView, wrapperField.holder, forwardMethod, wrapperSynthesizor, itfCall);
      this.wrapperField = wrapperField;
      this.eventConsumer = eventConsumer;
      this.contextSupplier = contextSupplier;
    }

    @Override
    void generatePushReceiver(List<CfInstruction> instructions) {
      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
      instructions.add(new CfInstanceFieldRead(wrapperField));
    }

    @Override
    DexMethod ensureConversionMethod(DexType type, boolean destIsVivified) {
      return wrapperSynthesizor.getExistingProgramConversionMethod(
          type, destIsVivified, eventConsumer, contextSupplier);
    }

    @Override
    DexMethod parameterConversion(DexType param) {
      return ensureConversionMethod(param, false);
    }

    @Override
    DexMethod returnConversion(DexType param) {
      return ensureConversionMethod(param, true);
    }

    @Override
    DexMethod getMethodToForwardTo() {
      return forwardMethod;
    }
  }

  public static class OutlinedAPIConversionCfCodeProvider extends SyntheticCfCodeProvider {

    private final CfInvoke initialInvoke;
    private final DexMethod returnConversion;
    private final DexMethod[] parameterConversions;

    public OutlinedAPIConversionCfCodeProvider(
        AppView<?> appView,
        DexType holder,
        CfInvoke initialInvoke,
        DexMethod returnConversion,
        DexMethod[] parameterConversions) {
      super(appView, holder);
      this.initialInvoke = initialInvoke;
      this.returnConversion = returnConversion;
      this.parameterConversions = parameterConversions;
    }

    @Override
    public CfCode generateCfCode() {
      DexMethod invokedMethod = initialInvoke.getMethod();
      DexMethod convertedMethod =
          DesugaredLibraryAPIConverter.getConvertedAPI(
              invokedMethod, returnConversion, parameterConversions, appView);

      List<CfInstruction> instructions = new ArrayList<>();

      boolean isStatic = initialInvoke.getOpcode() == Opcodes.INVOKESTATIC;
      if (!isStatic) {
        instructions.add(new CfLoad(ValueType.fromDexType(invokedMethod.holder), 0));
      }
      int receiverShift = BooleanUtils.intValue(!isStatic);
      int stackIndex = 0;
      for (int i = 0; i < invokedMethod.getArity(); i++) {
        DexType param = invokedMethod.getParameter(i);
        instructions.add(new CfLoad(ValueType.fromDexType(param), stackIndex + receiverShift));
        if (parameterConversions[i] != null) {
          instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, parameterConversions[i], false));
        }
        if (param == appView.dexItemFactory().longType
            || param == appView.dexItemFactory().doubleType) {
          stackIndex++;
        }
        stackIndex++;
      }

      // Actual call to converted value.
      instructions.add(
          new CfInvoke(initialInvoke.getOpcode(), convertedMethod, initialInvoke.isInterface()));

      // Return conversion.
      if (returnConversion != null) {
        instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, returnConversion, false));
      }

      if (invokedMethod.getReturnType().isVoidType()) {
        instructions.add(new CfReturnVoid());
      } else {
        instructions.add(new CfReturn(ValueType.fromDexType(invokedMethod.getReturnType())));
      }
      return standardCfCodeFromInstructions(instructions);
    }
  }
}
