// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.Keep;
import com.android.tools.r8.naming.mappinginformation.MapVersionMappingInformation;
import com.android.tools.r8.references.ClassReference;
import java.util.Set;

@Keep
public abstract class MappingSupplier<T extends MappingSupplier<T>> {

  /***
   * Register an allowed mapping lookup to allow for prefetching of resources.
   *
   * @param classReference The minified class reference allowed to be lookup up.
   */
  public abstract T registerClassUse(
      DiagnosticsHandler diagnosticsHandler, ClassReference classReference);

  public abstract void verifyMappingFileHash(DiagnosticsHandler diagnosticsHandler);

  public abstract Set<MapVersionMappingInformation> getMapVersions(
      DiagnosticsHandler diagnosticsHandler);

  public abstract Retracer createRetracer(DiagnosticsHandler diagnosticsHandler);
}
