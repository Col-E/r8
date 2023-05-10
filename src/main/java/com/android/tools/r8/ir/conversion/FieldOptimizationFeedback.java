// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.value.AbstractValue;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public interface FieldOptimizationFeedback {

  void markFieldCannotBeKept(DexEncodedField field);

  void markFieldAsDead(DexEncodedField field);

  void markFieldAsPropagated(DexEncodedField field);

  default void markFieldHasDynamicType(DexClassAndField field, DynamicType dynamicType) {
    markFieldHasDynamicType(field.getDefinition(), dynamicType);
  }

  void markFieldHasDynamicType(DexEncodedField field, DynamicType dynamicType);

  void markFieldBitsRead(DexEncodedField field, int bitsRead);

  default void recordFieldHasAbstractValue(
      DexClassAndField field, AppView<AppInfoWithLiveness> appView, AbstractValue abstractValue) {
    recordFieldHasAbstractValue(field.getDefinition(), appView, abstractValue);
  }

  void recordFieldHasAbstractValue(
      DexEncodedField field, AppView<AppInfoWithLiveness> appView, AbstractValue abstractValue);
}
