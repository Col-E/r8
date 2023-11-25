// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/**
 * Interface for providing a custom map-id to the compiler.
 *
 * <p>The map-id is inserted in the mapping file output and included in the compiler marker
 * information. The map-id can be used to provide an key/identifier to automate the lookup of
 * mapping file information for builds. For example, by including it in the source-file part of
 * program stacktraces. See {@code SourceFileProvider}.
 */
@KeepForApi
@FunctionalInterface
public interface MapIdProvider {

  /**
   * Return the map-id content.
   *
   * @param environment An environment of values available for use when defining the map id.
   * @return A non-null string that will be used as the map id.
   */
  String get(MapIdEnvironment environment);
}
