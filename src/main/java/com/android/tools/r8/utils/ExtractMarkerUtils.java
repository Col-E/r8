// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.ExtractMarker;
import com.android.tools.r8.ExtractMarkerCommand;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.origin.Origin;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

// Internal tools for accessing marker info.
public class ExtractMarkerUtils {

  public static Collection<Marker> extractMarkersFromFile(Path file)
      throws CompilationFailedException {
    return extractMarkers(ExtractMarkerCommand.builder().addProgramFiles(file));
  }

  public static Collection<Marker> extractMarkerFromDexProgramData(byte[] data)
      throws CompilationFailedException {
    return extractMarkers(ExtractMarkerCommand.builder().addDexProgramData(data, Origin.unknown()));
  }

  public static Collection<Marker> extractMarkerFromClassProgramData(byte[] data)
      throws CompilationFailedException {
    return extractMarkers(
        ExtractMarkerCommand.builder().addClassProgramData(data, Origin.unknown()));
  }

  private static List<Marker> extractMarkers(ExtractMarkerCommand.Builder builder)
      throws CompilationFailedException {
    List<Marker> markers = new ArrayList<>();
    ExtractMarker.run(
        builder.setMarkerInfoConsumer(new MarkerInfoToInternalMarkerConsumer(markers)).build());
    return markers;
  }
}
