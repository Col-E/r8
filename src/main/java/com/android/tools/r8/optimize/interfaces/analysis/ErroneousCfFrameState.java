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
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.ReferenceTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.utils.Pair;
import java.util.Iterator;
import java.util.function.BiFunction;

/** An analysis state representing that the code does not type check. */
public class ErroneousCfFrameState extends CfFrameState {

  private enum FormatKind {
    ACTUAL,
    EXPECTED
  }

  private final String message;

  ErroneousCfFrameState(String message) {
    assert message != null;
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
      if (frameType.isInitializedReferenceType()) {
        if (frameType.isNullType()) {
          return "null";
        } else if (frameType.isInitializedNonNullReferenceTypeWithInterfaces()) {
          ReferenceTypeElement initializedType =
              frameType
                  .asInitializedNonNullReferenceTypeWithInterfaces()
                  .getInitializedTypeWithInterfaces();
          if (initializedType.isArrayType()) {
            return format(initializedType);
          } else {
            assert initializedType.isClassType();
            return "initialized " + format(initializedType);
          }
        } else {
          assert frameType.isInitializedNonNullReferenceTypeWithoutInterfaces();
          DexType initializedType =
              frameType.asInitializedNonNullReferenceTypeWithoutInterfaces().getInitializedType();
          if (initializedType.isArrayType()) {
            return initializedType.getTypeName();
          } else {
            assert initializedType.isClassType();
            return "initialized " + initializedType.getTypeName();
          }
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

  private static String format(TypeElement type) {
    if (type.isArrayType()) {
      ArrayTypeElement arrayType = type.asArrayType();
      TypeElement baseType = arrayType.getBaseType();
      assert baseType.isClassType() || baseType.isPrimitiveType();
      boolean parenthesize =
          baseType.isClassType() && !baseType.asClassType().getInterfaces().isEmpty();
      StringBuilder result = new StringBuilder();
      if (parenthesize) {
        result.append("(");
      }
      result.append(format(baseType));
      if (parenthesize) {
        result.append(")");
      }
      for (int i = 0; i < arrayType.getNesting(); i++) {
        result.append("[]");
      }
      return result.toString();
    } else if (type.isClassType()) {
      ClassTypeElement classType = type.asClassType();
      StringBuilder result = new StringBuilder(classType.getClassType().getTypeName());
      if (!classType.getInterfaces().isEmpty()) {
        Iterator<Pair<DexType, Boolean>> iterator =
            classType.getInterfaces().getInterfaceList().iterator();
        result.append(" implements ").append(iterator.next().getFirst().getTypeName());
        while (iterator.hasNext()) {
          result.append(", ").append(iterator.next().getFirst().getTypeName());
        }
      }
      return result.toString();
    } else if (type.isNullType()) {
      return "null";
    } else {
      assert type.isPrimitiveType();
      return type.asPrimitiveType().getTypeName();
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
  public CfFrameState check(CfAnalysisConfig config, CfFrame frame) {
    return this;
  }

  @Override
  public CfFrameState checkLocals(CfAnalysisConfig config, CfFrame frame) {
    return this;
  }

  @Override
  public CfFrameState checkStack(CfAnalysisConfig config, CfFrame frame) {
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
      CfAnalysisConfig config,
      DexType expectedType,
      BiFunction<CfFrameState, PreciseFrameType, CfFrameState> fn) {
    return this;
  }

  @Override
  public CfFrameState popInitialized(
      AppView<?> appView, CfAnalysisConfig config, DexType... expectedTypes) {
    return this;
  }

  @Override
  public CfFrameState push(CfAnalysisConfig config, DexType type) {
    return this;
  }

  @Override
  public CfFrameState push(CfAnalysisConfig config, TypeElement type) {
    return this;
  }

  @Override
  public CfFrameState push(CfAnalysisConfig config, PreciseFrameType frameType) {
    return this;
  }

  @Override
  public CfFrameState pushException(CfAnalysisConfig config, DexType guard) {
    return this;
  }

  @Override
  public CfFrameState readLocal(
      AppView<?> appView,
      CfAnalysisConfig config,
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
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (other == null || getClass() != other.getClass()) {
      return false;
    }
    ErroneousCfFrameState error = (ErroneousCfFrameState) other;
    return message.equals(error.message);
  }

  @Override
  public int hashCode() {
    return message.hashCode();
  }
}
