// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph.invokesuper;

/**
 * Copy of {@ref java.util.function.Consumer} to allow tests to run on early versions of art.
 */
public interface Consumer<T> {

  void accept(T item);
}
