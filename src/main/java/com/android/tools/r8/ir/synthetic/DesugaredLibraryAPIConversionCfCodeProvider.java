// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfArithmeticBinop;
import com.android.tools.r8.cf.code.CfArithmeticBinop.Opcode;
import com.android.tools.r8.cf.code.CfArrayLength;
import com.android.tools.r8.cf.code.CfArrayLoad;
import com.android.tools.r8.cf.code.CfArrayStore;
import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.cf.code.CfGoto;
import com.android.tools.r8.cf.code.CfIf;
import com.android.tools.r8.cf.code.CfIfCmp;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstanceFieldWrite;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfNewArray;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStaticFieldRead;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryAPIConverter;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryClasspathWrapperSynthesizeEventConsumer;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.collections.ImmutableDeque;
import com.android.tools.r8.utils.collections.ImmutableInt2ReferenceSortedMap;
import java.util.ArrayList;
import java.util.Iterator;
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
      instructions.add(new CfInstanceFieldRead(wrapperField));
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
          appView.typeRewriter.hasRewrittenType(returnType, appView)
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
      instructions.add(new CfInstanceFieldRead(wrapperField));
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
      instructions.add(new CfInstanceFieldRead(reverseWrapperField));
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

  public static class EnumArrayConversionCfCodeProvider extends SyntheticCfCodeProvider {

    private final DexType enumType;
    private final DexType convertedType;

    public EnumArrayConversionCfCodeProvider(
        AppView<?> appView, DexType holder, DexType enumType, DexType convertedType) {
      super(appView, holder);
      this.enumType = enumType;
      this.convertedType = convertedType;
    }

    @Override
    public CfCode generateCfCode() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();
      DexType enumTypeArray = factory.createArrayType(1, enumType);
      DexType convertedTypeArray = factory.createArrayType(1, convertedType);

      // if (arg == null) { return null; }
      instructions.add(new CfLoad(ValueType.fromDexType(enumTypeArray), 0));
      instructions.add(new CfConstNull());
      CfLabel nonNull = new CfLabel();
      instructions.add(new CfIfCmp(If.Type.NE, ValueType.OBJECT, nonNull));
      instructions.add(new CfConstNull());
      instructions.add(new CfReturn(ValueType.fromDexType(convertedTypeArray)));
      instructions.add(nonNull);
      instructions.add(
          new CfFrame(
              ImmutableInt2ReferenceSortedMap.<FrameType>builder()
                  .put(0, FrameType.initialized(enumTypeArray))
                  .build(),
              ImmutableDeque.of()));

      ImmutableInt2ReferenceSortedMap<FrameType> locals =
          ImmutableInt2ReferenceSortedMap.<FrameType>builder()
              .put(0, FrameType.initialized(enumTypeArray))
              .put(1, FrameType.initialized(factory.intType))
              .put(2, FrameType.initialized(convertedTypeArray))
              .put(3, FrameType.initialized(factory.intType))
              .build();

      // int t1 = arg.length;
      instructions.add(new CfLoad(ValueType.fromDexType(enumTypeArray), 0));
      instructions.add(new CfArrayLength());
      instructions.add(new CfStore(ValueType.INT, 1));
      // ConvertedType[] t2 = new ConvertedType[t1];
      instructions.add(new CfLoad(ValueType.INT, 1));
      instructions.add(new CfNewArray(convertedTypeArray));
      instructions.add(new CfStore(ValueType.fromDexType(convertedTypeArray), 2));
      // int t3 = 0;
      instructions.add(new CfConstNumber(0, ValueType.INT));
      instructions.add(new CfStore(ValueType.INT, 3));
      // while (t3 < t1) {
      CfLabel returnLabel = new CfLabel();
      CfLabel loopLabel = new CfLabel();
      instructions.add(loopLabel);
      instructions.add(new CfFrame(locals, ImmutableDeque.of()));
      instructions.add(new CfLoad(ValueType.INT, 3));
      instructions.add(new CfLoad(ValueType.INT, 1));
      instructions.add(new CfIfCmp(If.Type.GE, ValueType.INT, returnLabel));
      // t2[t3] = convert(arg[t3]);
      instructions.add(new CfLoad(ValueType.fromDexType(convertedTypeArray), 2));
      instructions.add(new CfLoad(ValueType.INT, 3));
      instructions.add(new CfLoad(ValueType.fromDexType(enumTypeArray), 0));
      instructions.add(new CfLoad(ValueType.INT, 3));
      instructions.add(new CfArrayLoad(MemberType.OBJECT));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKESTATIC,
              factory.createMethod(
                  getHolder(),
                  factory.createProto(convertedType, enumType),
                  factory.convertMethodName),
              false));
      instructions.add(new CfArrayStore(MemberType.OBJECT));
      // t3 = t3 + 1; }
      instructions.add(new CfLoad(ValueType.INT, 3));
      instructions.add(new CfConstNumber(1, ValueType.INT));
      instructions.add(new CfArithmeticBinop(Opcode.Add, NumericType.INT));
      instructions.add(new CfStore(ValueType.INT, 3));
      instructions.add(new CfGoto(loopLabel));
      // return t2;
      instructions.add(returnLabel);
      instructions.add(new CfFrame(locals, ImmutableDeque.of()));
      instructions.add(new CfLoad(ValueType.fromDexType(convertedTypeArray), 2));
      instructions.add(new CfReturn(ValueType.fromDexType(convertedTypeArray)));
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class EnumConversionCfCodeProvider extends SyntheticCfCodeProvider {

    private final Iterable<DexEncodedField> enumFields;
    private final DexType enumType;
    private final DexType convertedType;

    public EnumConversionCfCodeProvider(
        AppView<?> appView,
        DexType holder,
        Iterable<DexEncodedField> enumFields,
        DexType enumType,
        DexType convertedType) {
      super(appView, holder);
      this.enumFields = enumFields;
      this.enumType = enumType;
      this.convertedType = convertedType;
    }

    @Override
    public CfCode generateCfCode() {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();

      ImmutableInt2ReferenceSortedMap<FrameType> locals =
          ImmutableInt2ReferenceSortedMap.<FrameType>builder()
              .put(0, FrameType.initialized(enumType))
              .build();

      // if (arg == null) { return null; }
      instructions.add(new CfLoad(ValueType.fromDexType(enumType), 0));
      instructions.add(new CfConstNull());
      CfLabel nonNull = new CfLabel();
      instructions.add(new CfIfCmp(If.Type.NE, ValueType.OBJECT, nonNull));
      instructions.add(new CfConstNull());
      instructions.add(new CfReturn(ValueType.fromDexType(convertedType)));
      instructions.add(nonNull);
      instructions.add(new CfFrame(locals, ImmutableDeque.of()));

      // if (arg == enumType.enumField1) { return convertedType.enumField1; }
      Iterator<DexEncodedField> iterator = enumFields.iterator();
      while (iterator.hasNext()) {
        DexEncodedField enumField = iterator.next();
        CfLabel notEqual = new CfLabel();
        if (iterator.hasNext()) {
          instructions.add(new CfLoad(ValueType.fromDexType(enumType), 0));
          instructions.add(
              new CfStaticFieldRead(factory.createField(enumType, enumType, enumField.getName())));
          instructions.add(new CfIfCmp(If.Type.NE, ValueType.OBJECT, notEqual));
        }
        instructions.add(
            new CfStaticFieldRead(
                factory.createField(convertedType, convertedType, enumField.getName())));
        instructions.add(new CfReturn(ValueType.fromDexType(convertedType)));
        if (iterator.hasNext()) {
          instructions.add(notEqual);
          instructions.add(new CfFrame(locals, ImmutableDeque.of()));
        }
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
      instructions.add(new CfInstanceFieldWrite(wrapperField));
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
