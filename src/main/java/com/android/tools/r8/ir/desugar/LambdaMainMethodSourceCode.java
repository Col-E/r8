// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfCheckCast;
import com.android.tools.r8.cf.code.CfInstanceFieldRead;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfNumberConversion;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfReturnVoid;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.cf.code.CfTryCatch;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.desugar.LambdaClass.InvalidLambdaImplTarget;
import com.android.tools.r8.ir.desugar.LambdaClass.NoAccessorMethodTarget;
import com.android.tools.r8.ir.desugar.lambda.LambdaInstructionDesugaring.DesugarInvoke;
import com.android.tools.r8.utils.IntBox;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableList.Builder;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.objectweb.asm.Opcodes;

// Source code representing synthesized lambda main method
final class LambdaMainMethodSourceCode {

  private static boolean checkSignatures(
      DexType[] captures,
      DexType[] enforcedParams,
      DexType enforcedReturnType,
      List<DexType> implReceiverAndArgs,
      DexType implReturnType,
      DexItemFactory factory) {
    List<DexType> capturesAndParams = new ArrayList<>();
    capturesAndParams.addAll(Lists.newArrayList(captures));
    capturesAndParams.addAll(Lists.newArrayList(enforcedParams));

    int size = capturesAndParams.size();
    if (size != implReceiverAndArgs.size()) {
      assert false;
    }

    for (int i = 0; i < size; i++) {
      if (!isSameOrAdaptableTo(capturesAndParams.get(i), implReceiverAndArgs.get(i), factory)) {
        assert false;
      }
    }

    if (!enforcedReturnType.isVoidType()
        && !isSameOrAdaptableTo(implReturnType, enforcedReturnType, factory)) {
      assert false;
    }
    return true;
  }

  private static DexType getBoxedForPrimitiveType(DexType primitive, DexItemFactory factory) {
    switch (primitive.descriptor.content[0]) {
      case 'Z':  // byte
      case 'B':  // byte
      case 'S':  // short
      case 'C':  // char
      case 'I':  // int
      case 'J':  // long
      case 'F':  // float
      case 'D':  // double
        return factory.getBoxedForPrimitiveType(primitive);
      default:
        throw new Unreachable("Invalid primitive type descriptor: " + primitive);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  // Checks if the types are the same OR type `a` is adaptable to type `b`.
  private static boolean isSameOrAdaptableTo(DexType a, DexType b, DexItemFactory factory) {
    if (a == b) {
      return true;
    }

    if (a.isArrayType()) {
      // Arrays are only adaptable to java.lang.Object or other arrays, note that we
      // don't check element type inheritance in the second case since we assume the
      // input code is verifiable.
      return b == factory.objectType || b.isArrayType();
    }

    if (b.isArrayType()) {
      // If A is typed object it can be convertible to an array type.
      return a == factory.objectType;
    }

    if (a.isPrimitiveType()) {
      if (b.isPrimitiveType()) {
        return isSameOrAdaptableTo(a.descriptor.content[0], b.descriptor.content[0]);
      }

      // `a` is primitive and `b` is a supertype of the boxed type `a`.
      DexType boxedPrimitiveType = getBoxedForPrimitiveType(a, factory);
      if (b == boxedPrimitiveType ||
          b == factory.objectType ||
          b == factory.serializableType ||
          b == factory.comparableType) {
        return true;
      }
      return boxedPrimitiveType != factory.boxedCharType
          && boxedPrimitiveType != factory.boxedBooleanType
          && b.descriptor == factory.boxedNumberDescriptor;
    }

    if (b.isPrimitiveType()) {
      if (a == factory.objectType) {
        // `a` is java.lang.Object in which case we assume it represented by
        // proper boxed type.
        return true;
      }
      // `a` is a boxed type for `a*` which can be
      // widened to primitive type `b`.
      DexType unboxedA = factory.getPrimitiveFromBoxed(a);
      return unboxedA != null &&
          isSameOrAdaptableTo(unboxedA.descriptor.content[0], b.descriptor.content[0]);
    }

    // Otherwise `a` should be a reference type derived from `b`.
    // NOTE: we don't check `b` for being actually a supertype, since we
    // might not have full classpath with inheritance information to do that.
    // We assume this requirement stands and will be caught by cast
    // instruction anyways in most cases.
    return a.isClassType() && b.isClassType();
  }

  // For two primitive types `a` is adjustable to `b` iff `a` is the same as `b`
  // or can be converted to `b` via a primitive widening conversion.
  private static boolean isSameOrAdaptableTo(byte from, byte to) {
    if (from == to) {
      return true;
    }
    switch (from) {
      case 'B':  // byte
        return to == 'S' || to == 'I' || to == 'J' || to == 'F' || to == 'D';
      case 'S':  // short
      case 'C':  // char
        return to == 'I' || to == 'J' || to == 'F' || to == 'D';
      case 'I':  // int
        return to == 'J' || to == 'F' || to == 'D';
      case 'J':  // long
        return to == 'F' || to == 'D';
      case 'F':  // float
        return to == 'D';
      case 'Z':  // boolean
      case 'D':  // double
        return false;
      default:
        throw new Unreachable("Invalid primitive type descriptor: " + from);
    }
  }

  @SuppressWarnings({"BadImport", "ReferenceEquality"})
  public static CfCode build(
      LambdaClass lambda, DexMethod mainMethod, DesugarInvoke desugarInvoke) {
    DexItemFactory factory = lambda.appView.dexItemFactory();
    LambdaClass.Target target = lambda.target;
    if (target instanceof InvalidLambdaImplTarget) {
      InvalidLambdaImplTarget invalidTarget = (InvalidLambdaImplTarget) target;
      DexType exceptionType = invalidTarget.exceptionType;
      return buildThrowingCode(mainMethod, exceptionType, factory);
    }

    DexMethod methodToCall = target.callTarget;
    DexType[] capturedTypes = lambda.descriptor.captures.values;
    DexType[] erasedParams = lambda.descriptor.erasedProto.parameters.values;
    DexType erasedReturnType = lambda.descriptor.erasedProto.returnType;
    DexType[] enforcedParams = lambda.descriptor.enforcedProto.parameters.values;
    DexType enforcedReturnType = lambda.descriptor.enforcedProto.returnType;

    // Only constructor call should use direct invoke type since super
    // and private methods require accessor methods.
    boolean constructorTarget = methodToCall.name == factory.constructorMethodName;
    assert !constructorTarget || target.invokeType == InvokeType.DIRECT;

    boolean targetWithReceiver =
        target.invokeType == InvokeType.VIRTUAL
            || target.invokeType == InvokeType.INTERFACE
            || (target.invokeType == InvokeType.DIRECT && !constructorTarget);
    List<DexType> implReceiverAndArgs = new ArrayList<>();
    if (targetWithReceiver) {
      implReceiverAndArgs.add(methodToCall.holder);
    }
    implReceiverAndArgs.addAll(Lists.newArrayList(methodToCall.proto.parameters.values));
    DexType implReturnType = methodToCall.proto.returnType;

    assert target.invokeType == InvokeType.STATIC
        || target.invokeType == InvokeType.VIRTUAL
        || target.invokeType == InvokeType.DIRECT
        || target.invokeType == InvokeType.INTERFACE;
    assert checkSignatures(
        capturedTypes,
        enforcedParams,
        enforcedReturnType,
        implReceiverAndArgs,
        constructorTarget ? target.callTarget.holder : implReturnType,
        factory);

    int maxStack = 0;
    Builder<CfInstruction> instructions = ImmutableList.builder();

    // If the target is a constructor, we need to create the instance first.
    // This instance will be the first argument to the call and the dup will be on stack at return.
    if (constructorTarget) {
      instructions.add(new CfNew(methodToCall.holder));
      instructions.add(CfStackInstruction.DUP);
      maxStack += 2;
    }

    // Load captures if needed.
    int capturedValues = capturedTypes.length;
    for (int i = 0; i < capturedValues; i++) {
      DexField field = lambda.getCaptureField(i);
      ValueType valueType = ValueType.fromDexType(field.type);
      instructions.add(CfLoad.ALOAD_0);
      instructions.add(new CfInstanceFieldRead(field));
      maxStack += valueType.requiredRegisters();
    }

    // Prepare arguments.
    int maxLocals = 1; // Local 0 is the lambda/receiver.
    for (int i = 0; i < erasedParams.length; i++) {
      ValueType valueType = ValueType.fromDexType(mainMethod.getParameters().values[i]);
      instructions.add(CfLoad.load(valueType, maxLocals));
      maxLocals += valueType.requiredRegisters();
      DexType expectedParamType = implReceiverAndArgs.get(i + capturedValues);
      maxStack +=
          prepareParameterValue(
              erasedParams[i], enforcedParams[i], expectedParamType, instructions, factory);
    }

    CfInvoke invoke =
        new CfInvoke(target.invokeType.getCfOpcode(), methodToCall, target.isInterface());
    if (target instanceof NoAccessorMethodTarget) {
      IntBox locals = new IntBox();
      IntBox stack = new IntBox();
      Collection<CfInstruction> is =
          desugarInvoke.desugarInvoke(invoke, locals::getAndIncrement, stack::getAndIncrement);
      if (is != null) {
        instructions.addAll(is);
        maxLocals += locals.get();
        maxStack += stack.get();
      } else {
        instructions.add(invoke);
      }
    } else {
      instructions.add(invoke);
    }

    DexType methodToCallReturnType = methodToCall.getReturnType();
    if (!methodToCallReturnType.isVoidType()) {
      maxStack =
          Math.max(maxStack, ValueType.fromDexType(methodToCallReturnType).requiredRegisters());
    }

    if (enforcedReturnType.isVoidType()) {
      if (!methodToCallReturnType.isVoidType()) {
        instructions.add(CfStackInstruction.popType(methodToCallReturnType));
      }
      instructions.add(CfReturnVoid.INSTANCE);
    } else {
      // Either the new instance or the called-method result is on top of stack.
      assert constructorTarget || !methodToCallReturnType.isVoidType();
      maxStack =
          Math.max(
              maxStack,
              prepareReturnValue(
                  erasedReturnType,
                  enforcedReturnType,
                  constructorTarget ? methodToCall.holder : methodToCallReturnType,
                  instructions,
                  factory));
      instructions.add(CfReturn.forType(ValueType.fromDexType(enforcedReturnType)));
    }

    ImmutableList<CfTryCatch> tryCatchRanges = ImmutableList.of();
    ImmutableList<CfCode.LocalVariableInfo> localVariables = ImmutableList.of();
    CfCode code =
        new CfCode(
            mainMethod.holder,
            maxStack,
            maxLocals,
            instructions.build(),
            tryCatchRanges,
            localVariables);
    return code;
  }

  private static CfCode buildThrowingCode(
      DexMethod method, DexType exceptionType, DexItemFactory factory) {
    DexProto initProto = factory.createProto(factory.voidType);
    DexMethod initMethod =
        factory.createMethod(exceptionType, initProto, factory.constructorMethodName);
    int maxStack = 2;
    int maxLocals = 1;
    for (DexType param : method.proto.parameters.values) {
      maxLocals += ValueType.fromDexType(param).requiredRegisters();
    }
    ImmutableList<CfTryCatch> tryCatchRanges = ImmutableList.of();
    ImmutableList<CfCode.LocalVariableInfo> localVariables = ImmutableList.of();
    return new CfCode(
        method.holder,
        maxStack,
        maxLocals,
        ImmutableList.of(
            new CfNew(exceptionType),
            CfStackInstruction.DUP,
            new CfInvoke(Opcodes.INVOKESPECIAL, initMethod, false),
            CfThrow.INSTANCE),
        tryCatchRanges,
        localVariables);
  }

  // Adds necessary casts and transformations to adjust the value
  // returned by impl-method to expected return type of the method.
  @SuppressWarnings("BadImport")
  private static int prepareReturnValue(
      DexType erasedType,
      DexType enforcedType,
      DexType actualType,
      Builder<CfInstruction> instructions,
      DexItemFactory factory) {
    // `erasedType` and `enforcedType` may only differ when they both
    // are class types and `erasedType` is a base type of `enforcedType`,
    // so no transformation is actually needed.
    assert LambdaDescriptor.isSameOrDerived(factory, enforcedType, erasedType);
    return adjustType(actualType, enforcedType, true, instructions, factory);
  }

  // Adds necessary casts and transformations to adjust parameter
  // value to the expected type of method-impl argument.
  //
  // Note that the original parameter type (`erasedType`) may need to
  // be converted to enforced parameter type (`enforcedType`), which,
  // in its turn, may need to be adjusted to the parameter type of
  // the impl-method (`expectedType`).
  @SuppressWarnings("BadImport")
  private static int prepareParameterValue(
      DexType erasedType,
      DexType enforcedType,
      DexType expectedType,
      Builder<CfInstruction> instructions,
      DexItemFactory factory) {
    enforceParameterType(erasedType, enforcedType, instructions, factory);
    return adjustType(enforcedType, expectedType, false, instructions, factory);
  }

  @SuppressWarnings({"BadImport", "ReferenceEquality"})
  private static void enforceParameterType(
      DexType paramType,
      DexType enforcedType,
      Builder<CfInstruction> instructions,
      DexItemFactory factory) {
    // `paramType` must be either same as `enforcedType` or both must be class
    // types and `enforcedType` must be a subclass of `paramType` in which case
    // a cast need to be inserted.
    if (paramType != enforcedType) {
      assert LambdaDescriptor.isSameOrDerived(factory, enforcedType, paramType);
      instructions.add(new CfCheckCast(enforcedType));
    }
  }

  @SuppressWarnings({"BadImport", "ReferenceEquality"})
  private static int adjustType(
      DexType fromType,
      DexType toType,
      boolean returnType,
      Builder<CfInstruction> instructions,
      DexItemFactory factory) {
    internalAdjustType(fromType, toType, returnType, instructions, factory);
    if (fromType == toType) {
      return ValueType.fromDexType(fromType).requiredRegisters();
    }
    // Account for the potential unboxing of a wide type.
    DexType fromTypeAsPrimitive = factory.getPrimitiveFromBoxed(fromType);
    return Math.max(
        ValueType.fromDexType(fromType).requiredRegisters(),
        Math.max(
            fromTypeAsPrimitive == null
                ? 0
                : ValueType.fromDexType(fromTypeAsPrimitive).requiredRegisters(),
            ValueType.fromDexType(toType).requiredRegisters()));
  }

  @SuppressWarnings({"BadImport", "ReferenceEquality"})
  private static void internalAdjustType(
      DexType fromType,
      DexType toType,
      boolean returnType,
      Builder<CfInstruction> instructions,
      DexItemFactory factory) {
    if (fromType == toType) {
      return;
    }

    boolean fromTypePrimitive = fromType.isPrimitiveType();
    boolean toTypePrimitive = toType.isPrimitiveType();

    // If both are primitive they must be convertible via primitive widening conversion.
    if (fromTypePrimitive && toTypePrimitive) {
      addPrimitiveWideningConversion(fromType, toType, instructions);
      return;
    }

    // If the first one is a boxed primitive type and the second one is a primitive
    // type, the value must be unboxed and converted to the resulting type via primitive
    // widening conversion.
    if (toTypePrimitive) {
      DexType boxedType = fromType;
      if (boxedType == factory.objectType) {
        // We are in situation when from(=java.lang.Object) is being adjusted to a
        // primitive type, in which case we assume it is of proper box type.
        boxedType = getBoxedForPrimitiveType(toType, factory);
        instructions.add(new CfCheckCast(boxedType));
      }
      DexType fromTypeAsPrimitive = factory.getPrimitiveFromBoxed(boxedType);
      if (fromTypeAsPrimitive != null) {
        addPrimitiveUnboxing(boxedType, instructions, factory);
        addPrimitiveWideningConversion(fromTypeAsPrimitive, toType, instructions);
        return;
      }
    }

    // If the first one is a primitive type and the second one is a boxed
    // type for this primitive type, just box the value.
    if (fromTypePrimitive) {
      DexType boxedFromType = getBoxedForPrimitiveType(fromType, factory);
      if (toType == boxedFromType
          || toType == factory.objectType
          || toType == factory.serializableType
          || toType == factory.comparableType
          || (boxedFromType != factory.booleanType
              && boxedFromType != factory.charType
              && toType == factory.boxedNumberType)) {
        addPrimitiveBoxing(boxedFromType, instructions, factory);
        return;
      }
    }

    if (fromType.isArrayType() && (toType == factory.objectType || toType.isArrayType())) {
      // If `fromType` is an array and `toType` is java.lang.Object, no cast is needed.
      // If both `fromType` and `toType` are arrays, no cast is needed since we assume
      // the input code is verifiable.
      return;
    }

    if ((fromType.isClassType() && toType.isClassType())
        || (fromType == factory.objectType && toType.isArrayType())) {
      if (returnType && toType != factory.objectType) {
        // For return type adjustment in case `fromType` and `toType` are both reference types,
        // `fromType` does NOT have to be deriving from `toType` and we need to add a cast.
        // NOTE: we don't check `toType` for being actually a supertype, since we
        // might not have full classpath with inheritance information to do that.
        instructions.add(new CfCheckCast(toType));
      }
      return;
    }

    throw new Unreachable("Unexpected type adjustment from "
        + fromType.toSourceString() + " to " + toType);
  }

  @SuppressWarnings({"BadImport", "ReferenceEquality"})
  private static void addPrimitiveWideningConversion(
      DexType fromType, DexType toType, Builder<CfInstruction> instructions) {
    assert fromType.isPrimitiveType() && toType.isPrimitiveType();
    if (fromType == toType) {
      return;
    }

    NumericType from = NumericType.fromDexType(fromType);
    NumericType to = NumericType.fromDexType(toType);

    if (from != null && to != null) {
      assert from != to;

      switch (to) {
        case SHORT: {
          if (from != NumericType.BYTE) {
            break; // Only BYTE can be converted to SHORT via widening conversion.
          }
            instructions.add(CfNumberConversion.convert(NumericType.INT, to));
            return;
        }

        case INT:
          if (from == NumericType.BYTE || from == NumericType.CHAR || from == NumericType.SHORT) {
            return;
          }
          break;

        case LONG: {
          if (from == NumericType.FLOAT || from == NumericType.DOUBLE) {
            break; // Not a widening conversion.
          }
            instructions.add(CfNumberConversion.convert(NumericType.INT, to));
            return;
        }

        case FLOAT: {
          if (from == NumericType.DOUBLE) {
            break; // Not a widening conversion.
          }
          NumericType type = (from == NumericType.LONG) ? NumericType.LONG : NumericType.INT;
            instructions.add(CfNumberConversion.convert(type, to));
            return;
        }

        case DOUBLE: {
          NumericType type = (from == NumericType.FLOAT || from == NumericType.LONG)
              ? from : NumericType.INT;
            instructions.add(CfNumberConversion.convert(type, to));
            return;
        }
        default:
          // exception is thrown below
          break;
      }
    }

    throw new Unreachable("Type " + fromType.toSourceString() + " cannot be " +
        "converted to " + toType.toSourceString() + " via primitive widening conversion.");
  }

  @SuppressWarnings("BadImport")
  private static void addPrimitiveUnboxing(
      DexType boxType, Builder<CfInstruction> instructions, DexItemFactory factory) {
    DexMethod method = factory.getUnboxPrimitiveMethod(boxType);
    instructions.add(new CfInvoke(Opcodes.INVOKEVIRTUAL, method, false));
  }

  @SuppressWarnings("BadImport")
  private static void addPrimitiveBoxing(
      DexType boxType, Builder<CfInstruction> instructions, DexItemFactory factory) {
    DexMethod method = factory.getBoxPrimitiveMethod(boxType);
    instructions.add(new CfInvoke(Opcodes.INVOKESTATIC, method, false));
  }
}
