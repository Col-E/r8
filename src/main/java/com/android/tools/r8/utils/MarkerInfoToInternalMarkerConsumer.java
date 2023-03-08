// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.MarkerInfo;
import com.android.tools.r8.MarkerInfoConsumer;
import com.android.tools.r8.MarkerInfoConsumerData;
import com.android.tools.r8.dex.Marker;
import java.util.List;

/** Consumer to convert the marker interface to the internal marker class. */
public class MarkerInfoToInternalMarkerConsumer implements MarkerInfoConsumer {

  private final List<Marker> markers;

  public MarkerInfoToInternalMarkerConsumer(List<Marker> markers) {
    this.markers = markers;
  }

  @Override
  public void acceptMarkerInfo(MarkerInfoConsumerData data) {
    if (data.hasMarkers()) {
      for (MarkerInfo marker : data.getMarkers()) {
        MarkerInfoImpl impl = (MarkerInfoImpl) marker;
        markers.add(impl.getInternalMarker());
      }
    }
  }

  @Override
  public void finished() {}
}
