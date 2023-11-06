// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.references.ClassReference;
import java.util.Collection;

@KeepForApi
public interface ClassConflictResolver {

  /**
   * Callback called in case the compiler is provided with duplicate class definitions.
   *
   * <p>The callback may be called multiple times for the same type with different origins, or it
   * may be called once with all origins. Assuming the client has provided unique origins for the
   * various inputs, the number of origins in any call will be at least two.
   *
   * <p>Note that all the duplicates are in the program's compilation unit. In other words, none of
   * them are classpath or library definitions.
   *
   * @param reference The type reference of the duplicated class.
   * @param origins The multiple origins of the class.
   * @param handler Diagnostics handler for reporting.
   * @return Returns the origin to use or null to fail compilation.
   */
  Origin resolveDuplicateClass(
      ClassReference reference, Collection<Origin> origins, DiagnosticsHandler handler);
}
