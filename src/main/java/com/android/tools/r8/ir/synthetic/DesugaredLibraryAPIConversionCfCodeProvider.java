// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFieldInstruction;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryWrapperSynthesizer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryClasspathWrapperSynthesizeEventConsumer;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.collections.ImmutableDeque;
import com.android.tools.r8.utils.collections.ImmutableInt2ReferenceSortedMap;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

public abstract class DesugaredLibraryAPIConversionCfCodeProvider extends SyntheticCfCodeProvider {

  DesugaredLibraryAPIConversionCfCodeProvider(AppView<?> appView, DexType holder) {
    super(appView, holder);
  }

  DexType vivifiedTypeFor(DexType type) {
    return DesugaredLibraryAPIConverter.vivifiedTypeFor(type, appView);
  }

  public static class APIConverterVivifiedWrapperCfCodeProvider
      extends DesugaredLibraryAPIConversionCfCodeProvider {

    private final DexField wrapperField;
    private final DexMethod forwardMethod;
    private final DesugaredLibraryWrapperSynthesizer wrapperSynthesizer;
    private final boolean itfCall;

    public APIConverterVivifiedWrapperCfCodeProvider(
        AppView<?> appView,
        DexMethod forwardMethod,
        DexField wrapperField,
        DesugaredLibraryWrapperSynthesizer wrapperSynthesizer,
        boolean itfCall) {
      super(appView, wrapperField.holder);
      this.forwardMethod = forwardMethod;
      this.wrapperField = wrapperField;
      this.wrapperSynthesizer = wrapperSynthesizer;
      this.itfCall = itfCall;
    }

    @Override
    public CfCode generateCfCode() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();
      // Wrapped value is a vivified type. Method uses type as external. Forward method should
      // use vivifiedTypes.

      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
      instructions.add(new CfFieldInstruction(Opcodes.GETFIELD, wrapperField, wrapperField));
      int index = 1;
      int stackIndex = 1;
      DexType[] newParameters = forwardMethod.proto.parameters.values.clone();
      for (DexType param : forwardMethod.proto.parameters.values) {
        instructions.add(new CfLoad(ValueType.fromDexType(param), stackIndex));
        if (wrapperSynthesizer.shouldConvert(param, forwardMethod)) {
          instructions.add(
              new CfInvoke(
                  Opcodes.INVOKESTATIC,
                  wrapperSynthesizer.getExistingProgramConversionMethod(
                      param, param, vivifiedTypeFor(param)),
                  false));
          newParameters[index - 1] = vivifiedTypeFor(param);
        }
        if (param == factory.longType || param == factory.doubleType) {
          stackIndex++;
        }
        stackIndex++;
        index++;
      }

      DexType returnType = forwardMethod.proto.returnType;
      DexType forwardMethodReturnType =
          appView.rewritePrefix.hasRewrittenType(returnType, appView)
              ? vivifiedTypeFor(returnType)
              : returnType;

      DexProto newProto = factory.createProto(forwardMethodReturnType, newParameters);
      DexMethod newForwardMethod =
          factory.createMethod(wrapperField.type, newProto, forwardMethod.name);

      if (itfCall) {
        instructions.add(new CfInvoke(Opcodes.INVOKEINTERFACE, newForwardMethod, true));
      } else {
        instructions.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, newForwardMethod, false));
      }

      if (wrapperSynthesizer.shouldConvert(returnType, forwardMethod)) {
        instructions.add(
            new CfInvoke(
                Opcodes.INVOKESTATIC,
                wrapperSynthesizer.getExistingProgramConversionMethod(
                    returnType, vivifiedTypeFor(returnType), returnType),
                false));
      }
      if (returnType == factory.voidType) {
        instructions.add(new CfReturnVoid());
      } else {
        instructions.add(new CfReturn(ValueType.fromDexType(returnType)));
      }
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public abstract static class AbstractAPIConverterWrapperCfCodeProvider
      extends DesugaredLibraryAPIConversionCfCodeProvider {

    DexMethod forwardMethod;
    DesugaredLibraryWrapperSynthesizer wrapperSynthesizor;
    boolean itfCall;

    public AbstractAPIConverterWrapperCfCodeProvider(
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

    abstract void generatePushReceiver(List<CfInstruction> instructions);

    abstract DexMethod ensureConversionMethod(DexType type, DexType srcType, DexType destType);

    @Override
    public CfCode generateCfCode() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();
      // Wrapped value is a type. Method uses vivifiedTypes as external. Forward method should
      // use types.

      generatePushReceiver(instructions);
      int stackIndex = 1;
      for (DexType param : forwardMethod.proto.parameters.values) {
        instructions.add(new CfLoad(ValueType.fromDexType(param), stackIndex));
        if (wrapperSynthesizor.shouldConvert(param, forwardMethod)) {
          instructions.add(
              new CfInvoke(
                  Opcodes.INVOKESTATIC,
                  ensureConversionMethod(param, vivifiedTypeFor(param), param),
                  false));
        }
        if (param == factory.longType || param == factory.doubleType) {
          stackIndex++;
        }
        stackIndex++;
      }

      if (itfCall) {
        instructions.add(new CfInvoke(Opcodes.INVOKEINTERFACE, forwardMethod, true));
      } else {
        instructions.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, forwardMethod, false));
      }

      DexType returnType = forwardMethod.proto.returnType;
      if (wrapperSynthesizor.shouldConvert(returnType, forwardMethod)) {
        instructions.add(
            new CfInvoke(
                Opcodes.INVOKESTATIC,
                ensureConversionMethod(returnType, returnType, vivifiedTypeFor(returnType)),
                false));
        returnType = vivifiedTypeFor(returnType);
      }
      if (returnType == factory.voidType) {
        instructions.add(new CfReturnVoid());
      } else {
        instructions.add(new CfReturn(ValueType.fromDexType(returnType)));
      }
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class APICallbackWrapperCfCodeProvider
      extends AbstractAPIConverterWrapperCfCodeProvider {

    private final DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer;

    public APICallbackWrapperCfCodeProvider(
        AppView<?> appView,
        DexMethod forwardMethod,
        DesugaredLibraryWrapperSynthesizer wrapperSynthesizor,
        boolean itfCall,
        DesugaredLibraryClasspathWrapperSynthesizeEventConsumer eventConsumer) {
      super(appView, forwardMethod.holder, forwardMethod, wrapperSynthesizor, itfCall);
      this.eventConsumer = eventConsumer;
    }

    @Override
    void generatePushReceiver(List<CfInstruction> instructions) {
      instructions.add(new CfLoad(ValueType.fromDexType(forwardMethod.holder), 0));
    }

    @Override
    DexMethod ensureConversionMethod(DexType type, DexType srcType, DexType destType) {
      return wrapperSynthesizor.ensureConversionMethod(type, srcType, destType, eventConsumer);
    }
  }

  public static class APIConverterWrapperCfCodeProvider
      extends AbstractAPIConverterWrapperCfCodeProvider {

    private final DexField wrapperField;

    public APIConverterWrapperCfCodeProvider(
        AppView<?> appView,
        DexMethod forwardMethod,
        DexField wrapperField,
        DesugaredLibraryWrapperSynthesizer wrapperSynthesizor,
        boolean itfCall) {
      super(appView, wrapperField.holder, forwardMethod, wrapperSynthesizor, itfCall);
      this.wrapperField = wrapperField;
    }

    @Override
    void generatePushReceiver(List<CfInstruction> instructions) {
      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
      instructions.add(new CfFieldInstruction(Opcodes.GETFIELD, wrapperField, wrapperField));
    }

    @Override
    DexMethod ensureConversionMethod(DexType type, DexType srcType, DexType destType) {
      return wrapperSynthesizor.getExistingProgramConversionMethod(type, srcType, destType);
    }
  }

  public static class APIConverterWrapperConversionCfCodeProvider extends SyntheticCfCodeProvider {

    DexField reverseWrapperField;
    DexField wrapperField;

    public APIConverterWrapperConversionCfCodeProvider(
        AppView<?> appView, DexField reverseWrapperField, DexField wrapperField) {
      super(appView, wrapperField.holder);
      this.reverseWrapperField = reverseWrapperField;
      this.wrapperField = wrapperField;
    }

    @Override
    public CfCode generateCfCode() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();

      DexType argType = wrapperField.type;
      ImmutableInt2ReferenceSortedMap<FrameType> locals =
          ImmutableInt2ReferenceSortedMap.<FrameType>builder()
              .put(0, FrameType.initialized(argType))
              .build();

      // if (arg == null) { return null };
      CfLabel nullDest = new CfLabel();
      instructions.add(new CfLoad(ValueType.fromDexType(argType), 0));
      instructions.add(new CfIf(If.Type.NE, ValueType.OBJECT, nullDest));
      instructions.add(new CfConstNull());
      instructions.add(new CfReturn(ValueType.OBJECT));
      instructions.add(nullDest);
      instructions.add(new CfFrame(locals, ImmutableDeque.of()));

      // if (arg instanceOf ReverseWrapper) { return ((ReverseWrapper) arg).wrapperField};
      assert reverseWrapperField != null;
      CfLabel unwrapDest = new CfLabel();
      instructions.add(new CfLoad(ValueType.fromDexType(argType), 0));
      instructions.add(new CfInstanceOf(reverseWrapperField.holder));
      instructions.add(new CfIf(If.Type.EQ, ValueType.INT, unwrapDest));
      instructions.add(new CfLoad(ValueType.fromDexType(argType), 0));
      instructions.add(new CfCheckCast(reverseWrapperField.holder));
      instructions.add(
          new CfFieldInstruction(Opcodes.GETFIELD, reverseWrapperField, reverseWrapperField));
      instructions.add(new CfReturn(ValueType.fromDexType(reverseWrapperField.type)));
      instructions.add(unwrapDest);
      instructions.add(new CfFrame(locals, ImmutableDeque.of()));

      // return new Wrapper(wrappedValue);
      instructions.add(new CfNew(wrapperField.holder));
      instructions.add(CfStackInstruction.fromAsm(Opcodes.DUP));
      instructions.add(new CfLoad(ValueType.fromDexType(argType), 0));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKESPECIAL,
              factory.createMethod(
                  wrapperField.holder,
                  factory.createProto(factory.voidType, argType),
                  factory.constructorMethodName),
              false));
      instructions.add(new CfReturn(ValueType.fromDexType(wrapperField.holder)));
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class APIConversionCfCodeProvider extends SyntheticCfCodeProvider {

    private final CfInvoke initialInvoke;
    private final DexMethod returnConversion;
    private final DexMethod[] parameterConversions;

    public APIConversionCfCodeProvider(
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

  public static class APIConverterConstructorCfCodeProvider extends SyntheticCfCodeProvider {

    DexField wrapperField;

    public APIConverterConstructorCfCodeProvider(AppView<?> appView, DexField wrapperField) {
      super(appView, wrapperField.holder);
      this.wrapperField = wrapperField;
    }

    @Override
    public CfCode generateCfCode() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();
      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKESPECIAL,
              factory.createMethod(
                  factory.objectType,
                  factory.createProto(factory.voidType),
                  factory.constructorMethodName),
              false));
      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.type), 1));
      instructions.add(new CfFieldInstruction(Opcodes.PUTFIELD, wrapperField, wrapperField));
      instructions.add(new CfReturnVoid());
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class APIConverterThrowRuntimeExceptionCfCodeProvider
      extends SyntheticCfCodeProvider {

    DexString message;

    public APIConverterThrowRuntimeExceptionCfCodeProvider(
        AppView<?> appView, DexString message, DexType holder) {
      super(appView, holder);
      this.message = message;
    }

    @Override
    public CfCode generateCfCode() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();
      instructions.add(new CfNew(factory.runtimeExceptionType));
      instructions.add(CfStackInstruction.fromAsm(Opcodes.DUP));
      instructions.add(new CfConstString(message));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKESPECIAL,
              factory.createMethod(
                  factory.runtimeExceptionType,
                  factory.createProto(factory.voidType, factory.stringType),
                  factory.constructorMethodName),
              false));
      instructions.add(new CfThrow());
      return standardCfCodeFromInstructions(instructions);
    }
  }
}
