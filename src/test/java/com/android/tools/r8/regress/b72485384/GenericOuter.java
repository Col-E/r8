// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.regress.b72485384;

import java.util.function.Function;

public class GenericOuter<T> {

  public T aMethod(T one, T other) {
    return one;
  }

  public <V extends T> T outerGetter(GenericInner<V> anInner) {
    return anInner.get();
  }

  public <V extends T> GenericInner<V> makeInner(Function<T, V> transform, T value) {
    return new GenericInner<>(transform.apply(value));
  }

  public class GenericInner<S extends T> {

    private final S field;

    public GenericInner(S field) {
      this.field = field;
    }

    public S get() {
      return field;
    }

    public T innerGetter(GenericInner<S> anInner) {
      return anInner.get();
    }
  }
}
