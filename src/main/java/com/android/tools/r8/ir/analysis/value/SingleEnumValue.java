// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithSubtyping;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;

public class SingleEnumValue extends SingleValue {

  private final DexField field;

  /** Intentionally package private, use {@link AbstractValueFactory} instead. */
  SingleEnumValue(DexField field) {
    this.field = field;
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
  public boolean equals(Object o) {
    return this == o;
  }

  @Override
  public int hashCode() {
    return System.identityHashCode(this);
  }

  @Override
  public Instruction createMaterializingInstruction(
      AppView<? extends AppInfoWithSubtyping> appView,
      IRCode code,
      TypeAndLocalInfoSupplier info) {
    throw new Unreachable("unless we store single enum as a method's returned value.");
  }
}
