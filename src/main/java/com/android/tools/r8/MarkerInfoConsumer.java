// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;

/** Interface for consumers of the marker information. */
@KeepForApi
public interface MarkerInfoConsumer {

  /**
   * Callback that provides the marker information of a resource.
   *
   * <p>This callback is called exactly once for each resource in the {@link ExtractMarkerCommand},
   * also when no marker information is present in that resource.
   */
  void acceptMarkerInfo(MarkerInfoConsumerData data);

  /**
   * Callback to inform the extraction of marker information is complete.
   *
   * <p>After the callback is invoked no further calls to {@link
   * MarkerInfoConsumer#acceptMarkerInfo} will occur.
   */
  void finished();
}
