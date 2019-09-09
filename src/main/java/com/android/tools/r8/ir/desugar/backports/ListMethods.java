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
import java.util.Objects;

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
    return Collections.unmodifiableList(
        Arrays.asList(
            Objects.requireNonNull(e0),
            Objects.requireNonNull(e1)));
  }

  public static <E> List<E> of(E e0, E e1, E e2) {
    return Collections.unmodifiableList(
        Arrays.asList(
            Objects.requireNonNull(e0),
            Objects.requireNonNull(e1),
            Objects.requireNonNull(e2)));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3) {
    return Collections.unmodifiableList(
        Arrays.asList(
            Objects.requireNonNull(e0),
            Objects.requireNonNull(e1),
            Objects.requireNonNull(e2),
            Objects.requireNonNull(e3)));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4) {
    return Collections.unmodifiableList(
        Arrays.asList(
            Objects.requireNonNull(e0),
            Objects.requireNonNull(e1),
            Objects.requireNonNull(e2),
            Objects.requireNonNull(e3),
            Objects.requireNonNull(e4)));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5) {
    return Collections.unmodifiableList(
        Arrays.asList(
            Objects.requireNonNull(e0),
            Objects.requireNonNull(e1),
            Objects.requireNonNull(e2),
            Objects.requireNonNull(e3),
            Objects.requireNonNull(e4),
            Objects.requireNonNull(e5)));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6) {
    return Collections.unmodifiableList(
        Arrays.asList(
            Objects.requireNonNull(e0),
            Objects.requireNonNull(e1),
            Objects.requireNonNull(e2),
            Objects.requireNonNull(e3),
            Objects.requireNonNull(e4),
            Objects.requireNonNull(e5),
            Objects.requireNonNull(e6)));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7) {
    return Collections.unmodifiableList(
        Arrays.asList(
            Objects.requireNonNull(e0),
            Objects.requireNonNull(e1),
            Objects.requireNonNull(e2),
            Objects.requireNonNull(e3),
            Objects.requireNonNull(e4),
            Objects.requireNonNull(e5),
            Objects.requireNonNull(e6),
            Objects.requireNonNull(e7)));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8) {
    return Collections.unmodifiableList(
        Arrays.asList(
            Objects.requireNonNull(e0),
            Objects.requireNonNull(e1),
            Objects.requireNonNull(e2),
            Objects.requireNonNull(e3),
            Objects.requireNonNull(e4),
            Objects.requireNonNull(e5),
            Objects.requireNonNull(e6),
            Objects.requireNonNull(e7),
            Objects.requireNonNull(e8)));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9) {
    return Collections.unmodifiableList(
        Arrays.asList(
            Objects.requireNonNull(e0),
            Objects.requireNonNull(e1),
            Objects.requireNonNull(e2),
            Objects.requireNonNull(e3),
            Objects.requireNonNull(e4),
            Objects.requireNonNull(e5),
            Objects.requireNonNull(e6),
            Objects.requireNonNull(e7),
            Objects.requireNonNull(e8),
            Objects.requireNonNull(e9)));
  }

  public static <E> List<E> of(E e0, E e1, E e2, E e3, E e4, E e5, E e6, E e7, E e8, E e9, E e10) {
    return Collections.unmodifiableList(
        Arrays.asList(
            Objects.requireNonNull(e0),
            Objects.requireNonNull(e1),
            Objects.requireNonNull(e2),
            Objects.requireNonNull(e3),
            Objects.requireNonNull(e4),
            Objects.requireNonNull(e5),
            Objects.requireNonNull(e6),
            Objects.requireNonNull(e7),
            Objects.requireNonNull(e8),
            Objects.requireNonNull(e9),
            Objects.requireNonNull(e10)));
  }
}
