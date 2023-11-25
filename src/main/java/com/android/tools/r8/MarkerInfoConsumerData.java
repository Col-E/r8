// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import java.util.Collection;

/** Information about which markers are present in a given input. */
@KeepForApi
public interface MarkerInfoConsumerData {

  Origin getInputOrigin();

  boolean hasMarkers();

  Collection<MarkerInfo> getMarkers();
}
