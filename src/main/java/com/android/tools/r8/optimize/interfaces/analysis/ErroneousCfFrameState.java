// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.interfaces.analysis;

import com.android.tools.r8.cf.code.CfFrame;
import com.android.tools.r8.cf.code.frame.FrameType;
import com.android.tools.r8.cf.code.frame.PreciseFrameType;
import com.android.tools.r8.cf.code.frame.UninitializedFrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.ValueType;
import java.util.function.BiFunction;

/** An analysis state representing that the code does not type check. */
public class ErroneousCfFrameState extends CfFrameState {

  private enum FormatKind {
    ACTUAL,
    EXPECTED
  }

  private final String message;

  ErroneousCfFrameState(String message) {
    this.message = message;
  }

  public static String formatExpected(DexType type) {
    return format(type);
  }

  private static String format(DexType type) {
    if (type.isArrayType() || type.isClassType()) {
      return type.getTypeName();
    } else if (type.isNullValueType()) {
      return "null";
    } else {
      assert type.isPrimitiveType();
      return "primitive " + type.getTypeName();
    }
  }

  public static String formatActual(FrameType frameType) {
    return format(frameType, FormatKind.ACTUAL);
  }

  public static String formatExpected(FrameType frameType) {
    return format(frameType, FormatKind.EXPECTED);
  }

  private static String format(FrameType frameType, FormatKind formatKind) {
    if (frameType.isInitialized()) {
      if (frameType.isObject()) {
        DexType initializedType = frameType.asInitializedReferenceType().getInitializedType();
        if (initializedType.isArrayType()) {
          return initializedType.getTypeName();
        } else if (initializedType.isClassType()) {
          return "initialized " + initializedType.getTypeName();
        } else {
          assert initializedType.isNullValueType();
          return "null";
        }
      } else {
        assert frameType.isPrimitive();
        return "primitive " + frameType.asPrimitive().getTypeName();
      }
    } else if (frameType.isUninitialized()) {
      if (frameType.isUninitializedNew()) {
        DexType uninitializedNewType = frameType.getUninitializedNewType();
        if (uninitializedNewType != null) {
          return "uninitialized " + uninitializedNewType.getTypeName();
        }
        return "uninitialized-new";
      } else {
        return "uninitialized-this";
      }
    } else {
      assert frameType.isOneWord() || frameType.isTwoWord();
      if (formatKind == FormatKind.ACTUAL) {
        return "top";
      } else {
        return frameType.isOneWord() ? "a single width value" : "a double width value";
      }
    }
  }

  public static String formatExpected(ValueType valueType) {
    return format(valueType);
  }

  private static String format(ValueType valueType) {
    if (valueType.isObject()) {
      return "object";
    } else {
      return "primitive " + valueType.toPrimitiveType().getTypeName();
    }
  }

  public String getMessage() {
    return message;
  }

  @Override
  public boolean isError() {
    return true;
  }

  @Override
  public ErroneousCfFrameState asError() {
    return this;
  }

  @Override
  public CfFrameState check(AppView<?> appView, CfFrame frame) {
    return this;
  }

  @Override
  public CfFrameState checkLocals(AppView<?> appView, CfFrame frame) {
    return this;
  }

  @Override
  public CfFrameState checkStack(AppView<?> appView, CfFrame frame) {
    return this;
  }

  @Override
  public CfFrameState clear() {
    return this;
  }

  @Override
  public CfFrameState markInitialized(
      UninitializedFrameType uninitializedType, DexType initializedType) {
    return this;
  }

  @Override
  public CfFrameState pop() {
    return this;
  }

  @Override
  public CfFrameState pop(BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn) {
    return this;
  }

  @Override
  public CfFrameState popAndInitialize(
      AppView<?> appView, DexMethod constructor, CfAnalysisConfig config) {
    return this;
  }

  @Override
  public CfFrameState popArray(AppView<?> appView) {
    return this;
  }

  @Override
  public CfFrameState popInitialized(
      AppView<?> appView,
      DexType expectedType,
      BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn) {
    return this;
  }

  @Override
  public CfFrameState popInitialized(AppView<?> appView, DexType... expectedTypes) {
    return this;
  }

  @Override
  public CfFrameState push(CfAnalysisConfig config, DexType type) {
    return this;
  }

  @Override
  public CfFrameState push(CfAnalysisConfig config, PreciseFrameType frameType) {
    return this;
  }

  @Override
  public CfFrameState readLocal(
      AppView<?> appView,
      int localIndex,
      ValueType expectedType,
      BiFunction<CfFrameState, FrameType, CfFrameState> fn) {
    return this;
  }

  @Override
  public CfFrameState storeLocal(int localIndex, FrameType frameType, CfAnalysisConfig config) {
    return this;
  }

  @Override
  public boolean equals(Object other) {
    return this == other;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }
}
