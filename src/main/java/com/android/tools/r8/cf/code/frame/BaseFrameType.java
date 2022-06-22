// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code.frame;

import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;

public abstract class BaseFrameType implements FrameType {

  @Override
  public boolean isBoolean() {
    return false;
  }

  @Override
  public boolean isByte() {
    return false;
  }

  @Override
  public boolean isChar() {
    return false;
  }

  @Override
  public boolean isDouble() {
    return false;
  }

  @Override
  public boolean isDoubleLow() {
    return false;
  }

  @Override
  public boolean isDoubleHigh() {
    return false;
  }

  @Override
  public boolean isFloat() {
    return false;
  }

  @Override
  public boolean isInt() {
    return false;
  }

  @Override
  public boolean isLong() {
    return false;
  }

  @Override
  public boolean isLongLow() {
    return false;
  }

  @Override
  public boolean isLongHigh() {
    return false;
  }

  @Override
  public boolean isShort() {
    return false;
  }

  @Override
  public boolean isNullType() {
    return false;
  }

  @Override
  public NullFrameType asNullType() {
    return null;
  }

  @Override
  public boolean isObject() {
    return false;
  }

  @Override
  public DexType getObjectType(DexItemFactory dexItemFactory, DexType context) {
    assert false : "Unexpected use of getObjectType() for non-object FrameType";
    return null;
  }

  @Override
  public boolean isPrecise() {
    assert isOneWord() || isTwoWord();
    return false;
  }

  @Override
  public PreciseFrameType asPrecise() {
    assert isOneWord() || isTwoWord();
    return null;
  }

  @Override
  public boolean isPrimitive() {
    return false;
  }

  @Override
  public PrimitiveFrameType asPrimitive() {
    return null;
  }

  @Override
  public final boolean isSingle() {
    return !isWide();
  }

  @Override
  public SingleFrameType asSingle() {
    return null;
  }

  @Override
  public boolean isSinglePrimitive() {
    return false;
  }

  @Override
  public SinglePrimitiveFrameType asSinglePrimitive() {
    return null;
  }

  @Override
  public boolean isInitializedReferenceType() {
    return false;
  }

  @Override
  public InitializedReferenceFrameType asInitializedReferenceType() {
    return null;
  }

  @Override
  public boolean isInitializedNonNullReferenceType() {
    return false;
  }

  @Override
  public InitializedNonNullReferenceFrameType asInitializedNonNullReferenceType() {
    return null;
  }

  @Override
  public boolean isInitializedNonNullReferenceTypeWithoutInterfaces() {
    return false;
  }

  @Override
  public InitializedNonNullReferenceFrameTypeWithoutInterfaces
      asInitializedNonNullReferenceTypeWithoutInterfaces() {
    return null;
  }

  @Override
  public boolean isInitializedNonNullReferenceTypeWithInterfaces() {
    return false;
  }

  @Override
  public InitializedNonNullReferenceFrameTypeWithInterfaces
      asInitializedNonNullReferenceTypeWithInterfaces() {
    return null;
  }

  @Override
  public boolean isWide() {
    return false;
  }

  @Override
  public WideFrameType asWide() {
    return null;
  }

  @Override
  public boolean isWidePrimitive() {
    return false;
  }

  @Override
  public WidePrimitiveFrameType asWidePrimitive() {
    return null;
  }

  @Override
  public boolean isWidePrimitiveLow() {
    return false;
  }

  @Override
  public boolean isWidePrimitiveHigh() {
    return false;
  }

  @Override
  public int getWidth() {
    assert isSingle();
    return 1;
  }

  @Override
  public boolean isUninitializedNew() {
    return false;
  }

  @Override
  public UninitializedNew asUninitializedNew() {
    return null;
  }

  @Override
  public boolean isUninitialized() {
    return false;
  }

  @Override
  public UninitializedFrameType asUninitialized() {
    return null;
  }

  @Override
  public CfLabel getUninitializedLabel() {
    return null;
  }

  @Override
  public boolean isUninitializedThis() {
    return false;
  }

  @Override
  public UninitializedThis asUninitializedThis() {
    return null;
  }

  @Override
  public boolean isInitialized() {
    return false;
  }

  @Override
  public DexType getInitializedType(DexItemFactory dexItemFactory) {
    return null;
  }

  @Override
  public DexType getUninitializedNewType() {
    return null;
  }

  @Override
  public boolean isOneWord() {
    return false;
  }

  @Override
  public boolean isTwoWord() {
    return false;
  }

  BaseFrameType() {}

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();
}
