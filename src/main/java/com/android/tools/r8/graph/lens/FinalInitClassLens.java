// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.lens;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.Timing;
import java.util.Map;

public class FinalInitClassLens extends InitClassLens {

  private final Map<DexType, DexField> mapping;

  FinalInitClassLens(Map<DexType, DexField> mapping) {
    this.mapping = mapping;
  }

  @Override
  public DexField getInitClassField(DexType type) {
    DexField field = mapping.get(type);
    if (field != null) {
      return field;
    }
    throw new Unreachable("Unexpected InitClass instruction for `" + type.toSourceString() + "`");
  }

  @Override
  public boolean isFinal() {
    return true;
  }

  @Override
  public InitClassLens rewrittenWithLens(GraphLens lens, Timing timing) {
    return timing.time("Rewrite FinalInitClassLens", () -> rewrittenWithLens(lens));
  }

  private InitClassLens rewrittenWithLens(GraphLens lens) {
    InitClassLens.Builder builder = InitClassLens.builder();
    mapping.forEach(
        (type, field) -> {
          DexType rewrittenType = lens.lookupType(type);
          DexField rewrittenField = lens.lookupField(field);
          builder.map(rewrittenType, rewrittenField);
        });
    return builder.build();
  }
}
