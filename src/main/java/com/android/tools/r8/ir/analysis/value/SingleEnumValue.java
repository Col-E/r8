// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.EnumValueInfoMapCollection.EnumValueInfoMap;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class SingleEnumValue extends SingleFieldValue {

  /** Intentionally package private, use {@link AbstractValueFactory} instead. */
  SingleEnumValue(DexField field) {
    super(field);
  }

  @Override
  public boolean isSingleEnumValue() {
    return true;
  }

  @Override
  public SingleEnumValue asSingleEnumValue() {
    return this;
  }

  @Override
  public String toString() {
    return "SingleEnumValue(" + getField().toSourceString() + ")";
  }

  @Override
  public SingleValue rewrittenWithLens(AppView<AppInfoWithLiveness> appView, GraphLense lens) {
    DexField field = getField();
    EnumValueInfoMap unboxedEnumInfo = appView.unboxedEnums().getEnumValueInfoMap(field.type);
    if (unboxedEnumInfo != null) {
      // Return the ordinal of the unboxed enum.
      assert unboxedEnumInfo.hasEnumValueInfo(field);
      return appView
          .abstractValueFactory()
          .createSingleNumberValue(unboxedEnumInfo.getEnumValueInfo(field).convertToInt());
    }
    return appView.abstractValueFactory().createSingleEnumValue(lens.lookupField(field));
  }
}
