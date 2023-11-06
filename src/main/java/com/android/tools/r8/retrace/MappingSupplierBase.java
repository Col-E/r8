// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Finishable;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;

@KeepForApi
public interface MappingSupplierBase<T extends MappingSupplierBase<T>> extends Finishable {

  /***
   * Register an allowed mapping lookup to allow for prefetching of resources.
   *
   * @param classReference The minified class reference allowed to be lookup up.
   */
  T registerClassUse(DiagnosticsHandler diagnosticsHandler, ClassReference classReference);

  /***
   * Register an allowed mapping lookup to allow for prefetching of resources.
   *
   * @param methodReference The minified method reference allowed to be lookup up.
   */
  T registerMethodUse(DiagnosticsHandler diagnosticsHandler, MethodReference methodReference);

  /***
   * Register an allowed mapping lookup to allow for prefetching of resources.
   *
   * @param fieldReference The minified field reference allowed to be lookup up.
   */
  T registerFieldUse(DiagnosticsHandler diagnosticsHandler, FieldReference fieldReference);
}
