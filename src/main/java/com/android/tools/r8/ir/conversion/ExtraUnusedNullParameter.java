// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ExtraUnusedNullParameter extends ExtraParameter {

  private final DexType type;

  public ExtraUnusedNullParameter(DexType type) {
    this.type = type;
  }

  @SuppressWarnings("MixedMutabilityReturnType")
  public static List<ExtraUnusedNullParameter> computeExtraUnusedNullParameters(
      DexMethod from, DexMethod to) {
    int numberOfExtraNullParameters = to.getArity() - from.getArity();
    if (numberOfExtraNullParameters == 0) {
      return Collections.emptyList();
    }
    List<ExtraUnusedNullParameter> extraUnusedNullParameters =
        new ArrayList<>(numberOfExtraNullParameters);
    for (int extraUnusedNullParameterIndex = from.getArity();
        extraUnusedNullParameterIndex < to.getParameters().size();
        extraUnusedNullParameterIndex++) {
      DexType extraUnusedNullParameterType = to.getParameter(extraUnusedNullParameterIndex);
      extraUnusedNullParameters.add(new ExtraUnusedNullParameter(extraUnusedNullParameterType));
    }
    return extraUnusedNullParameters;
  }

  @Override
  public DexType getType(DexItemFactory dexItemFactory) {
    assert type != null;
    return type;
  }

  @Override
  public TypeElement getTypeElement(AppView<?> appView, DexType argType) {
    return TypeElement.fromDexType(argType, Nullability.maybeNull(), appView);
  }

  @Override
  public SingleNumberValue getValue(AppView<?> appView) {
    return appView.abstractValueFactory().createNullValue();
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object obj) {
    return obj != null && getClass() == obj.getClass();
  }

  @Override
  public int hashCode() {
    return 0;
  }
}
