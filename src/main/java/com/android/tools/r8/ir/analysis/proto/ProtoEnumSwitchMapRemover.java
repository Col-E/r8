// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValues;
import com.android.tools.r8.ir.analysis.fieldvalueanalysis.StaticFieldValues.EnumStaticFieldValues;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.analysis.value.objectstate.ObjectState;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Proto enum switch maps can be removed even if the static enum fields are pinned. */
public class ProtoEnumSwitchMapRemover {

  private final ProtoReferences references;

  private final Map<DexType, EnumStaticFieldValues> staticFieldValuesMap = new IdentityHashMap<>();
  private final Map<DexType, EnumStaticFieldValues> staticFieldValuesMapDelayed =
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
      staticFieldValuesMapDelayed.put(clazz.type, enumStaticFieldValues);
    }
  }

  public void updateVisibleStaticFieldValues() {
    staticFieldValuesMap.putAll(staticFieldValuesMapDelayed);
    staticFieldValuesMapDelayed.clear();
  }

  @SuppressWarnings("ReferenceEquality")
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
      // If the switch map is found in a wave previous to the wave containing the enum clinit,
      // then bail out. This can happen but is extremely uncommon.
      return null;
    }
    ObjectState state =
        enumStaticFieldValues.getObjectStateForPossiblyPinnedField(
            enumInstanceField.getReference());
    if (state == null) {
      return null;
    }
    return state.getAbstractFieldValue(ordinalField).asSingleNumberValue();
  }
}
