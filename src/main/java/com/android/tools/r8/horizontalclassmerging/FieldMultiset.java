// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging;

import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.HashMultiset;
import com.google.common.collect.Multiset;

public class FieldMultiset {

  private final Multiset<DexType> fields = HashMultiset.create();

  public FieldMultiset(DexProgramClass clazz) {
    for (DexEncodedField field : clazz.instanceFields()) {
      fields.add(field.type());
    }
  }

  @Override
  public int hashCode() {
    return fields.hashCode();
  }

  @Override
  public boolean equals(Object object) {
    if (object instanceof FieldMultiset) {
      FieldMultiset other = (FieldMultiset) object;
      return fields.equals(other.fields);
    } else {
      return false;
    }
  }
}
