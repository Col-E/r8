// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.value;

import com.android.tools.r8.graph.DexField;
import java.util.concurrent.ConcurrentHashMap;

public class AbstractValueFactory {

  private ConcurrentHashMap<DexField, SingleEnumValue> singleEnumValues = new ConcurrentHashMap<>();

  public SingleEnumValue createSingleEnumValue(DexField field) {
    return singleEnumValues.computeIfAbsent(field, SingleEnumValue::new);
  }
}
