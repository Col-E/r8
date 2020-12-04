// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValues;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValues.EnumStaticFieldValues;
import com.android.tools.r8.ir.analysis.value.ObjectState;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Proto enum switch maps can be removed even if the static enum fields are pinned. */
public class ProtoEnumSwitchMapRemover {

  private final ProtoReferences references;

  private final Map<DexType, EnumStaticFieldValues> staticFieldValuesMap =
      new ConcurrentHashMap<>();

  public ProtoEnumSwitchMapRemover(ProtoReferences references) {
    this.references = references;
  }

  public void recordStaticValues(DexProgramClass clazz, StaticFieldValues staticFieldValues) {
    if (staticFieldValues == null || !staticFieldValues.isEnumStaticFieldValues()) {
      return;
    }
    assert clazz.isEnum();
    EnumStaticFieldValues enumStaticFieldValues = staticFieldValues.asEnumStaticFieldValues();
    if (isProtoEnum(clazz)) {
      staticFieldValuesMap.put(clazz.type, enumStaticFieldValues);
    }
  }

  private boolean isProtoEnum(DexProgramClass clazz) {
    assert clazz.isEnum();
    if (clazz.type == references.methodToInvokeType) {
      return true;
    }
    return clazz.getInterfaces().contains(references.enumLiteMapType);
  }

  public SingleNumberValue getOrdinal(
      DexProgramClass enumClass, DexEncodedField enumInstanceField, DexEncodedField ordinalField) {
    if (enumClass == null || !isProtoEnum(enumClass)) {
      return null;
    }
    EnumStaticFieldValues enumStaticFieldValues = staticFieldValuesMap.get(enumClass.type);
    if (enumStaticFieldValues == null) {
      if (enumClass.type == references.methodToInvokeType) {
        throw new CompilationError("Proto optimizations: missing information for MethodToInvoke.");
      }
      return null;
    }
    ObjectState state =
        enumStaticFieldValues.getObjectStateForPossiblyPinnedField(enumInstanceField.field);
    if (state == null) {
      return null;
    }
    return state.getAbstractFieldValue(ordinalField).asSingleNumberValue();
  }
}
