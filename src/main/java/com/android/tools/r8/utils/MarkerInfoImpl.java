// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.MarkerInfo;
import com.android.tools.r8.dex.Marker;

public class MarkerInfoImpl implements MarkerInfo {

  private final Marker marker;

  public MarkerInfoImpl(Marker marker) {
    this.marker = marker;
  }

  Marker getInternalMarker() {
    return marker;
  }

  @Override
  public String getTool() {
    return marker.getTool().toString();
  }

  @Override
  public boolean isR8() {
    return marker.isR8();
  }

  @Override
  public boolean isD8() {
    return marker.isD8();
  }

  @Override
  public boolean isL8() {
    return marker.isL8();
  }

  @Override
  public int getMinApi() {
    return marker.hasMinApi() ? marker.getMinApi().intValue() : -1;
  }

  @Override
  public String getRawEncoding() {
    return marker.toString();
  }
}
