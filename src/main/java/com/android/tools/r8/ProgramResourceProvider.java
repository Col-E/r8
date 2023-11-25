// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import java.io.IOException;
import java.util.Collection;

/** Program resource provider. */
@KeepForApi
public interface ProgramResourceProvider {

  Collection<ProgramResource> getProgramResources() throws ResourceException;

  default DataResourceProvider getDataResourceProvider() {
    return null;
  }

  /**
   * Callback signifying that a given compilation unit is done using the resource provider.
   *
   * <p>This can be used to clean-up resources once it is guaranteed that the compiler will no
   * longer request them. If a client shares a resource provider among multiple compilation units
   * then the provider should be sure to either retain the resources or support reloading them on
   * demand.
   *
   * <p>Providers should make sure finished can be safely called multiple times.
   */
  @SuppressWarnings("RedundantThrows")
  default void finished(DiagnosticsHandler handler) throws IOException {
    // Do nothing by default.
  }
}
