// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.backports;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class ListMethods {

  public static <E> List<E> ofArray(E[] elements) {
    // TODO(140709356): The other overloads should call into this method to ensure consistent
    //  behavior, but we cannot link against List.of(E[]) because it's only available in Java 9.
    ArrayList<E> list = new ArrayList<>(elements.length);
    for (E element : elements) {
      list.add(Objects.requireNonNull(element));
    }
    return Collections.unmodifiableList(list);
  }
}
