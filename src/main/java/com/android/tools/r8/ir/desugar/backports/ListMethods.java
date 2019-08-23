// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.utils.InternalOptions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("unchecked")
public class ListMethods extends TemplateMethodCode {

  public ListMethods(InternalOptions options, DexMethod method, String methodName) {
    super(options, method, methodName, method.proto.toDescriptorString());
  }

  public static <E> List<E> of() {
    return Collections.emptyList();
  }

  public static <E> List<E> of(E e0) {
    return Collections.singletonList(Objects.requireNonNull(e0));
  }

  public static <E> List<E> of(E e0, E e1) {
    E[] elements = (E[]) new Object[] { e0, e1 };
    return List.of(elements);
  }

  public static <E> List<E> of(E e0, E e1, E e2) {
    E[] elements = (E[]) new Object[] { e0, e1, e2 };
    return List.of(elements);
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3 };
    return List.of(elements);
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3, e4 };
    return List.of(elements);
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3, e4, e5 };
    return List.of(elements);
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3, e4, e5, e6 };
    return List.of(elements);
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3, e4, e5, e6, e7 };
    return List.of(elements);
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3, e4, e5, e6, e7, e8 };
    return List.of(elements);
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3, e4, e5, e6, e7, e8, e9 };
    return List.of(elements);
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10) {
    E[] elements = (E[]) new Object[] { e0, e1, e2, e3, e4, e5, e6, e7, e8, e9, e10 };
    return List.of(elements);
  }

  public static <E> List<E> ofVarargs(E[] elements) {
    ArrayList<E> list = new ArrayList<>(elements.length);
    for (E element : elements) {
      list.add(Objects.requireNonNull(element));
    }
    return Collections.unmodifiableList(list);
  }
}
