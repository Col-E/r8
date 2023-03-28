// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.analysis.type.PrimitiveTypeElement;
import com.android.tools.r8.ir.analysis.type.ReferenceTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.naming.NamingLens;
import java.util.function.Function;

public interface FrameType {

  static BooleanFrameType booleanType() {
    return BooleanFrameType.SINGLETON;
  }

  static ByteFrameType byteType() {
    return ByteFrameType.SINGLETON;
  }

  static CharFrameType charType() {
    return CharFrameType.SINGLETON;
  }

  static DoubleFrameType doubleType() {
    return DoubleFrameType.SINGLETON;
  }

  static DoubleHighFrameType doubleHighType() {
    return DoubleHighFrameType.SINGLETON;
  }

  static FloatFrameType floatType() {
    return FloatFrameType.SINGLETON;
  }

  static IntFrameType intType() {
    return IntFrameType.SINGLETON;
  }

  static LongFrameType longType() {
    return LongFrameType.SINGLETON;
  }

  static LongHighFrameType longHighType() {
    return LongHighFrameType.SINGLETON;
  }

  static ShortFrameType shortType() {
    return ShortFrameType.SINGLETON;
  }

  static InitializedFrameType initialized(DexType type) {
    if (type.isPrimitiveType()) {
      return primitive(type);
    }
    return initializedReference(type);
  }

  static InitializedFrameType initialized(TypeElement type) {
    if (type.isPrimitiveType()) {
      return primitive(type.asPrimitiveType());
    }
    return initializedReference(type.asReferenceType());
  }

  static InitializedReferenceFrameType initializedReference(DexType type) {
    assert type.isReferenceType();
    return type.isNullValueType() ? nullType() : initializedNonNullReference(type);
  }

  static InitializedReferenceFrameType initializedReference(ReferenceTypeElement type) {
    return type.isNullType() ? nullType() : initializedNonNullReference(type);
  }

  static InitializedNonNullReferenceFrameTypeWithoutInterfaces initializedNonNullReference(
      DexType type) {
    assert type.isReferenceType();
    assert !type.isNullValueType();
    return new InitializedNonNullReferenceFrameTypeWithoutInterfaces(type);
  }

  static InitializedNonNullReferenceFrameTypeWithInterfaces initializedNonNullReference(
      ReferenceTypeElement type) {
    assert !type.isNullType();
    return new InitializedNonNullReferenceFrameTypeWithInterfaces(type);
  }

  static NullFrameType nullType() {
    return NullFrameType.SINGLETON;
  }

  static PrimitiveFrameType primitive(DexType type) {
    assert type.isPrimitiveType();
    return internalPrimitive(type.getDescriptor().getFirstByteAsChar());
  }

  static PrimitiveFrameType primitive(PrimitiveTypeElement type) {
    return internalPrimitive(type.getDescriptor().charAt(0));
  }

  static PrimitiveFrameType internalPrimitive(char descriptor) {
    switch (descriptor) {
      case 'Z':
        return booleanType();
      case 'B':
        return byteType();
      case 'C':
        return charType();
      case 'D':
        return doubleType();
      case 'F':
        return floatType();
      case 'I':
        return intType();
      case 'J':
        return longType();
      case 'S':
        return shortType();
      default:
        throw new Unreachable("Unexpected primitive type: " + descriptor);
    }
  }

  static UninitializedNew uninitializedNew(CfLabel label, DexType typeToInitialize) {
    return new UninitializedNew(label, typeToInitialize);
  }

  static UninitializedThis uninitializedThis() {
    return UninitializedThis.SINGLETON;
  }

  static OneWord oneWord() {
    return OneWord.SINGLETON;
  }

  static TwoWord twoWord() {
    return TwoWord.SINGLETON;
  }

  static PrimitiveFrameType fromNumericType(NumericType numericType, DexItemFactory factory) {
    return FrameType.primitive(numericType.toDexType(factory));
  }

  static InitializedFrameType fromPreciseMemberType(MemberType memberType, DexItemFactory factory) {
    assert memberType.isPrecise();
    switch (memberType) {
      case OBJECT:
        return FrameType.initializedNonNullReference(factory.objectType);
      case BOOLEAN_OR_BYTE:
      case CHAR:
      case SHORT:
      case INT:
        return intType();
      case FLOAT:
        return floatType();
      case LONG:
        return longType();
      case DOUBLE:
        return doubleType();
      default:
        throw new Unreachable("Unexpected MemberType: " + memberType);
    }
  }

  DexType getInitializedType(DexItemFactory dexItemFactory);

  DexType getObjectType(DexItemFactory dexItemFactory, DexType context);

  Object getTypeOpcode(GraphLens graphLens, NamingLens namingLens);

  CfLabel getUninitializedLabel();

  DexType getUninitializedNewType();

  int getWidth();

  boolean isBoolean();

  boolean isByte();

  boolean isChar();

  boolean isDouble();

  boolean isDoubleLow();

  boolean isDoubleHigh();

  boolean isFloat();

  boolean isInitialized();

  boolean isInitializedReferenceType();

  InitializedReferenceFrameType asInitializedReferenceType();

  boolean isInitializedNonNullReferenceType();

  InitializedNonNullReferenceFrameType asInitializedNonNullReferenceType();

  boolean isInitializedNonNullReferenceTypeWithoutInterfaces();

  InitializedNonNullReferenceFrameTypeWithoutInterfaces
      asInitializedNonNullReferenceTypeWithoutInterfaces();

  boolean isInitializedNonNullReferenceTypeWithInterfaces();

  InitializedNonNullReferenceFrameTypeWithInterfaces
      asInitializedNonNullReferenceTypeWithInterfaces();

  boolean isInt();

  boolean isLong();

  boolean isLongLow();

  boolean isLongHigh();

  boolean isNullType();

  NullFrameType asNullType();

  boolean isObject();

  boolean isOneWord();

  boolean isPrecise();

  PreciseFrameType asPrecise();

  boolean isPrimitive();

  PrimitiveFrameType asPrimitive();

  boolean isShort();

  boolean isSingle();

  SingleFrameType asSingle();

  boolean isSinglePrimitive();

  SinglePrimitiveFrameType asSinglePrimitive();

  boolean isTwoWord();

  boolean isUninitialized();

  UninitializedFrameType asUninitialized();

  boolean isUninitializedNew();

  UninitializedNew asUninitializedNew();

  boolean isUninitializedThis();

  UninitializedThis asUninitializedThis();

  boolean isWide();

  WideFrameType asWide();

  boolean isWidePrimitive();

  WidePrimitiveFrameType asWidePrimitive();

  boolean isWidePrimitiveLow();

  boolean isWidePrimitiveHigh();

  default FrameType map(Function<DexType, DexType> fn) {
    assert !isPrecise();
    return this;
  }
}
