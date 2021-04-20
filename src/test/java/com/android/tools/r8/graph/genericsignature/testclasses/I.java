// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph.genericsignature.testclasses;

import com.android.tools.r8.NeverInline;

public interface I<T extends Comparable<T>, R extends Foo<T>> extends L<R> {
  @NeverInline
  T method(T t);
}
