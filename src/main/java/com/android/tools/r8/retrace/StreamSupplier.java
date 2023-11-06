// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/**
 * Supplier of input lines to be retraced.
 *
 * @param <E> the type of the {@link Throwable}
 */
@FunctionalInterface
@KeepForApi
public interface StreamSupplier<E extends Throwable> {

  String getNext() throws E;
}
