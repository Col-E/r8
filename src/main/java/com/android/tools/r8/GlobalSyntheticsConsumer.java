// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.ClassReference;

/**
 * Consumer receiving the data representing global synthetics for the program.
 *
 * <p>Global synthetic information is only produced as part of D8 intermediate builds (e.g., for
 * incremental compilation.) The global synthetic information represents desugaring content that may
 * be duplicated among many intermediate-mode builds and will need to be merged to ensure a valid
 * final program (i.e., a program that does not contain any duplicate definitions).
 *
 * <p>The data obtained for global synthetics must be passed to the subsequent compilation unit that
 * builds a non-intermediate output. That compilation output can then be packaged as a final
 * application. It is valid to merge just the globals in such a final step. See {@code
 * GlobalSyntheticsResourceProvider}.
 */
@KeepForApi
public interface GlobalSyntheticsConsumer {

  /**
   * Callback to receive the data representing the global synthetics for the program.
   *
   * <p>The encoding of the global synthetics is compiler internal and may vary between compiler
   * versions. The data received here is thus only valid as inputs to the same compiler version.
   *
   * <p>The context class is the class for which the global synthetic data is needed. If compiling
   * in DexIndexed mode, the context class will be null.
   *
   * <p>The accept method will be called at most once for a given context class (any only once at
   * all for a DexIndexed mode compilation). The global data for that class may be the same as for
   * other context classes, but it will be provided for each context.
   *
   * @param data Opaque encoding of the global synthetics for the program.
   * @param context The class giving rise to the global synthetics. Null in DexIndexed mode.
   * @param handler Diagnostics handler for reporting.
   */
  void accept(ByteDataView data, ClassReference context, DiagnosticsHandler handler);

  /**
   * Callback indicating that no more global synthetics will be reported for the compilation unit.
   *
   * @param handler Diagnostics handler for reporting.
   */
  default void finished(DiagnosticsHandler handler) {}
}
