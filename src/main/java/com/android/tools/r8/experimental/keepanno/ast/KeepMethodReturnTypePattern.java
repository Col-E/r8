// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

import java.util.function.Function;
import java.util.function.Supplier;

public abstract class KeepMethodReturnTypePattern {

  private static SomeType ANY_TYPE_INSTANCE = null;

  public static KeepMethodReturnTypePattern any() {
    if (ANY_TYPE_INSTANCE == null) {
      ANY_TYPE_INSTANCE = new SomeType(KeepTypePattern.any());
    }
    return ANY_TYPE_INSTANCE;
  }

  public static KeepMethodReturnTypePattern voidType() {
    return VoidType.getInstance();
  }

  public abstract <T> T match(Supplier<T> onVoid, Function<KeepTypePattern, T> onType);

  private static class VoidType extends KeepMethodReturnTypePattern {
    private static VoidType INSTANCE = null;

    public static VoidType getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new VoidType();
      }
      return INSTANCE;
    }

    @Override
    public <T> T match(Supplier<T> onVoid, Function<KeepTypePattern, T> onType) {
      return onVoid.get();
    }
  }

  private static class SomeType extends KeepMethodReturnTypePattern {

    private final KeepTypePattern typePattern;

    private SomeType(KeepTypePattern typePattern) {
      this.typePattern = typePattern;
    }

    @Override
    public <T> T match(Supplier<T> onVoid, Function<KeepTypePattern, T> onType) {
      return onType.apply(typePattern);
    }
  }
}
