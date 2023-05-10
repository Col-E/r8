// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;

public interface KnownFieldSet {

  boolean contains(DexEncodedField field);

  boolean contains(DexClassAndField field);

  default boolean isConcreteFieldSet() {
    return false;
  }

  default ConcreteMutableFieldSet asConcreteFieldSet() {
    return null;
  }

  int size();
}
