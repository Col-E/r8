// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Collections;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@SuppressWarnings("unchecked")
public class SetMethods extends TemplateMethodCode {

  public SetMethods(InternalOptions options, DexMethod method, String methodName) {
    super(options, method, methodName, method.proto.toDescriptorString());
  }

  public static <E> Set<E> of() {
    return Collections.emptySet();
  }

  public static <E> Set<E> of(E e0) {
    return Collections.singleton(Objects.requireNonNull(e0));
  }

  public static <E> Set<E> of(E e0, E e1) {
    E[] elements = (E[]) new Object[] { e0, e1 };
    return Set.of(elements);
  }

  public static <E> Set<E> of(E e0, E e1, E e2) {
    E[] elements = (E[]) new Object[] { e0, e1, e2 };
    return Set.of(elements);
  }

  public static <E> Set<E> of(E e0, E e1, E e2, E e3) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3 };
    return Set.of(elements);
  }

  public static <E> Set<E> of(E e0, E e1, E e2, E e3, E e4) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3, e4 };
    return Set.of(elements);
  }

  public static <E> Set<E> of(E e0, E e1, E e2, E e3, E e4, E e5) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3, e4, e5 };
    return Set.of(elements);
  }

  public static <E> Set<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3, e4, e5, e6 };
    return Set.of(elements);
  }

  public static <E> Set<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3, e4, e5, e6, e7 };
    return Set.of(elements);
  }

  public static <E> Set<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3, e4, e5, e6, e7, e8 };
    return Set.of(elements);
  }

  public static <E> Set<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3, e4, e5, e6, e7, e8, e9 };
    return Set.of(elements);
  }

  public static <E> Set<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3, e4, e5, e6, e7, e8, e9, e10 };
    return Set.of(elements);
  }

  public static <E> Set<E> ofVarargs(E[] elements) {
    HashSet<E> set = new HashSet<>(elements.length);
    for (E element : elements) {
      if (!set.add(Objects.requireNonNull(element))) {
        throw new IllegalArgumentException("duplicate element: " + element);
      }
    }
    return Collections.unmodifiableSet(set);
  }
}
