// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.ir.synthetic.TemplateMethodCode;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ListMethods extends TemplateMethodCode {

  public ListMethods(InternalOptions options, DexMethod method, String methodName) {
    super(options, method, methodName, method.proto.toDescriptorString());
  }

  public static <E> List<E> of() {
    return Collections.emptyList();
  }

  public static <E> List<E> of(E e0) {
    return Collections.singletonList(e0);
  }

  public static <E> List<E> of(E e0, E e1) {
    return Collections.unmodifiableList(Arrays.asList(e0, e1));
  }

  public static <E> List<E> of(E e0, E e1, E e2) {
    return Collections.unmodifiableList(Arrays.asList(e0, e1, e2));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3) {
    return Collections.unmodifiableList(Arrays.asList(e0, e1, e2, e3));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4) {
    return Collections.unmodifiableList(Arrays.asList(e0, e1, e2, e3, e4));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5) {
    return Collections.unmodifiableList(Arrays.asList(e0, e1, e2, e3, e4, e5));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6) {
    return Collections.unmodifiableList(Arrays.asList(e0, e1, e2, e3, e4, e5, e6));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
    return Collections.unmodifiableList(Arrays.asList(e0, e1, e2, e3, e4, e5, e6, e7));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
    return Collections.unmodifiableList(Arrays.asList(e0, e1, e2, e3, e4, e5, e6, e7, e8));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) {
    return Collections.unmodifiableList(Arrays.asList(e0, e1, e2, e3, e4, e5, e6, e7, e8, e9));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10) {
    return Collections.unmodifiableList(Arrays.asList(e0, e1, e2, e3, e4, e5, e6, e7, e8, e9, e10));
  }
}
