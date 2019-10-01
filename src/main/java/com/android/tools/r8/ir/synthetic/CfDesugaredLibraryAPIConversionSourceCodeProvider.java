// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.synthetic;

import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfFieldInstruction;
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
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.DesugaredLibraryAPIConverter;
import com.android.tools.r8.utils.StringDiagnostic;
import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.Opcodes;

public class CfDesugaredLibraryAPIConversionSourceCodeProvider {

  static boolean shouldConvert(
      DexType type,
      DesugaredLibraryAPIConverter converter,
      AppView<?> appView,
      DexString methodName) {
    if (appView.rewritePrefix.hasRewrittenType(type) && converter.canConvert(type)) {
      return true;
    }
    appView
        .options()
        .reporter
        .warning(
            new StringDiagnostic(
                "Desugared library API conversion failed for "
                    + type
                    + ", unexpected behavior for method "
                    + methodName
                    + "."));
    return false;
  }

  public static class CfAPIConverterVivifiedWrapperCodeProvider
      extends CfSyntheticSourceCodeProvider {

    DexField wrapperField;
    DexMethod forwardMethod;
    DesugaredLibraryAPIConverter converter;

    public CfAPIConverterVivifiedWrapperCodeProvider(
        DexEncodedMethod method,
        DexMethod originalMethod,
        AppView<?> appView,
        DexMethod forwardMethod,
        DexField wrappedValue,
        DesugaredLibraryAPIConverter converter) {
      super(method, originalMethod, appView);
      this.forwardMethod = forwardMethod;
      this.wrapperField = wrappedValue;
      this.converter = converter;
    }

    @Override
    protected CfCode generateCfCode(Position callerPosition) {
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
        if (shouldConvert(param, converter, appView, forwardMethod.name)) {
          instructions.add(
              new CfInvoke(
                  Opcodes.INVOKESTATIC,
                  converter.createConversionMethod(param, param, converter.vivifiedTypeFor(param)),
                  false));
          newParameters[index - 1] = converter.vivifiedTypeFor(param);
        }
        if (param == factory.longType || param == factory.doubleType) {
          stackIndex++;
        }
        stackIndex++;
        index++;
      }

      DexType returnType = forwardMethod.proto.returnType;
      DexType forwardMethodReturnType =
          appView.rewritePrefix.hasRewrittenType(returnType)
              ? converter.vivifiedTypeFor(returnType)
              : returnType;

      DexProto newProto = factory.createProto(forwardMethodReturnType, newParameters);
      DexMethod newForwardMethod =
          factory.createMethod(wrapperField.type, newProto, forwardMethod.name);
      // TODO(b/134732760): Deal with abstract class instead of interfaces.
      instructions.add(new CfInvoke(Opcodes.INVOKEINTERFACE, newForwardMethod, true));

      if (shouldConvert(returnType, converter, appView, forwardMethod.name)) {
        instructions.add(
            new CfInvoke(
                Opcodes.INVOKESTATIC,
                converter.createConversionMethod(
                    returnType, converter.vivifiedTypeFor(returnType), returnType),
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

  public static class CfAPIConverterWrapperCodeProvider extends CfSyntheticSourceCodeProvider {

    DexField wrapperField;
    DexMethod forwardMethod;
    DesugaredLibraryAPIConverter converter;

    public CfAPIConverterWrapperCodeProvider(
        DexEncodedMethod method,
        DexMethod originalMethod,
        AppView<?> appView,
        DexMethod forwardMethod,
        DexField wrappedValue,
        DesugaredLibraryAPIConverter converter) {
      super(method, originalMethod, appView);
      this.forwardMethod = forwardMethod;
      this.wrapperField = wrappedValue;
      this.converter = converter;
    }

    @Override
    protected CfCode generateCfCode(Position callerPosition) {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();
      // Wrapped value is a type. Method uses vivifiedTypes as external. Forward method should
      // use types.

      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
      instructions.add(new CfFieldInstruction(Opcodes.GETFIELD, wrapperField, wrapperField));
      int stackIndex = 1;
      for (DexType param : forwardMethod.proto.parameters.values) {
        instructions.add(new CfLoad(ValueType.fromDexType(param), stackIndex));
        if (shouldConvert(param, converter, appView, forwardMethod.name)) {
          instructions.add(
              new CfInvoke(
                  Opcodes.INVOKESTATIC,
                  converter.createConversionMethod(param, converter.vivifiedTypeFor(param), param),
                  false));
        }
        if (param == factory.longType || param == factory.doubleType) {
          stackIndex++;
        }
        stackIndex++;
      }

      // TODO(b/134732760): Deal with abstract class instead of interfaces.
      instructions.add(new CfInvoke(Opcodes.INVOKEINTERFACE, forwardMethod, true));

      DexType returnType = forwardMethod.proto.returnType;
      if (shouldConvert(returnType, converter, appView, forwardMethod.name)) {
        instructions.add(
            new CfInvoke(
                Opcodes.INVOKESTATIC,
                converter.createConversionMethod(
                    returnType, returnType, converter.vivifiedTypeFor(returnType)),
                false));
        returnType = converter.vivifiedTypeFor(returnType);
      }
      if (returnType == factory.voidType) {
        instructions.add(new CfReturnVoid());
      } else {
        instructions.add(new CfReturn(ValueType.fromDexType(returnType)));
      }
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class CfAPIConverterWrapperConversionCodeProvider
      extends CfSyntheticSourceCodeProvider {

    DexType argType;
    DexType reverseWrapperType;
    DexField wrapperField;

    public CfAPIConverterWrapperConversionCodeProvider(
        DexEncodedMethod method,
        DexMethod originalMethod,
        AppView<?> appView,
        DexType argType,
        DexType reverseWrapperType,
        DexField wrapperField) {
      super(method, originalMethod, appView);
      this.argType = argType;
      this.reverseWrapperType = reverseWrapperType;
      this.wrapperField = wrapperField;
    }

    @Override
    protected CfCode generateCfCode(Position callerPosition) {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();

      // if (arg == null) { return null };
      CfLabel nullDest = new CfLabel();
      instructions.add(new CfLoad(ValueType.fromDexType(argType), 0));
      instructions.add(new CfIf(If.Type.NE, ValueType.OBJECT, nullDest));
      instructions.add(new CfConstNull());
      instructions.add(new CfReturn(ValueType.OBJECT));
      instructions.add(nullDest);

      // if (arg instanceOf ReverseWrapper) { return ((ReverseWrapper) arg).wrapperField};
      if (reverseWrapperType != null) {
        CfLabel unwrapDest = new CfLabel();
        instructions.add(new CfLoad(ValueType.fromDexType(argType), 0));
        instructions.add(new CfInstanceOf(reverseWrapperType));
        instructions.add(new CfIf(If.Type.EQ, ValueType.INT, unwrapDest));
        instructions.add(new CfLoad(ValueType.fromDexType(argType), 0));
        instructions.add(new CfCheckCast(reverseWrapperType));
        instructions.add(new CfFieldInstruction(Opcodes.GETFIELD, wrapperField, wrapperField));
        instructions.add(new CfReturn(ValueType.fromDexType(wrapperField.type)));
        instructions.add(unwrapDest);
      }

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
                  factory.initMethodName),
              false));
      instructions.add(new CfReturn(ValueType.fromDexType(wrapperField.holder)));
      return standardCfCodeFromInstructions(instructions);
    }
  }

  public static class CfAPIConverterConstructorCodeProvider extends CfSyntheticSourceCodeProvider {

    DexField wrapperField;

    public CfAPIConverterConstructorCodeProvider(
        DexEncodedMethod method, DexMethod originalMethod, AppView<?> appView, DexField field) {
      super(method, originalMethod, appView);
      this.wrapperField = field;
    }

    @Override
    protected CfCode generateCfCode(Position callerPosition) {
      DexItemFactory factory = appView.dexItemFactory();
      List<CfInstruction> instructions = new ArrayList<>();
      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
      instructions.add(
          new CfInvoke(
              Opcodes.INVOKESPECIAL,
              factory.createMethod(
                  factory.objectType,
                  factory.createProto(factory.voidType),
                  factory.initMethodName),
              false));
      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.holder), 0));
      instructions.add(new CfLoad(ValueType.fromDexType(wrapperField.type), 1));
      instructions.add(new CfFieldInstruction(Opcodes.PUTFIELD, wrapperField, wrapperField));
      instructions.add(new CfReturnVoid());
      return standardCfCodeFromInstructions(instructions);
    }
  }
}
