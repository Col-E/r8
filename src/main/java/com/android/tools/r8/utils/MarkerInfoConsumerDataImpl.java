// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.MarkerInfo;
import com.android.tools.r8.MarkerInfoConsumerData;
import com.android.tools.r8.origin.Origin;
import java.util.Collection;
import java.util.List;

public class MarkerInfoConsumerDataImpl implements MarkerInfoConsumerData {

  private final Origin origin;
  private final List<MarkerInfo> markerInfos;

  public MarkerInfoConsumerDataImpl(Origin origin, List<MarkerInfo> markerInfos) {
    this.origin = origin;
    this.markerInfos = markerInfos;
  }

  @Override
  public Origin getInputOrigin() {
    return origin;
  }

  @Override
  public boolean hasMarkers() {
    return !markerInfos.isEmpty();
  }

  @Override
  public Collection<MarkerInfo> getMarkers() {
    return markerInfos;
  }
}
