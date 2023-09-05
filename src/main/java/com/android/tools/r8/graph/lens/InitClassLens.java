// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.lens;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.Timing;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public abstract class InitClassLens {

  public static Builder builder() {
    return new Builder();
  }

  public static ThrowingInitClassLens getThrowingInstance() {
    return ThrowingInitClassLens.getInstance();
  }

  public abstract DexField getInitClassField(DexType clazz);

  public boolean isFinal() {
    return false;
  }

  public abstract InitClassLens rewrittenWithLens(GraphLens lens, Timing timing);

  public static class Builder {

    private final Map<DexType, DexField> mapping = new ConcurrentHashMap<>();

    @SuppressWarnings("ReferenceEquality")
    public void map(DexType type, DexField field) {
      assert field.holder == type;
      mapping.put(type, field);
    }

    public FinalInitClassLens build() {
      return new FinalInitClassLens(mapping);
    }
  }
}
