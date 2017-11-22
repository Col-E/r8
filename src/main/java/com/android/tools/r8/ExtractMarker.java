// Copyright (c) 2017, the Rex project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.ExecutionException;

public class ExtractMarker {

  public static Marker extractMarkerFromDexFile(Path file) throws IOException, ExecutionException {
    AndroidApp.Builder appBuilder = AndroidApp.builder();
    appBuilder.setVdexAllowed();
    appBuilder.addProgramFiles(FilteredClassPath.unfiltered(file));
    return extractMarker(appBuilder.build());
  }

  public static Marker extractMarkerFromDexProgramData(byte[] data)
      throws IOException, ExecutionException {
    AndroidApp app = AndroidApp.fromDexProgramData(data);
    return extractMarker(app);
  }

  private static Marker extractMarker(AndroidApp app) throws IOException, ExecutionException {
    InternalOptions options = new InternalOptions();
    options.skipReadingDexCode = true;
    options.minApiLevel = AndroidApiLevel.P.getLevel();
    DexApplication dexApp =
        new ApplicationReader(app, options, new Timing("ExtractMarker")).read();
    return dexApp.dexItemFactory.extractMarker();
  }

  public static void main(String[] args)
      throws IOException, CompilationException, ExecutionException {
    ExtractMarkerCommand.Builder builder = ExtractMarkerCommand.parse(args);
    ExtractMarkerCommand command = builder.build();
    if (command.isPrintHelp()) {
      System.out.println(ExtractMarkerCommand.USAGE_MESSAGE);
      return;
    }

    // Dex code is not needed for getting the marker. VDex files typically contains quickened byte
    // codes which cannot be read, and we want to get the marker from vdex files as well.
    int d8Count = 0;
    int r8Count = 0;
    int otherCount = 0;
    for (Path programFile : command.getProgramFiles()) {
      try {
        if (command.getVerbose()) {
          System.out.print(programFile);
          System.out.print(": ");
        }
        Marker marker = extractMarkerFromDexFile(programFile);
        if (marker == null) {
          System.out.println("D8/R8 marker not found.");
          otherCount++;
        } else {
          System.out.println(marker.toString());
          if (marker.isD8()) {
            d8Count++;
          } else {
            r8Count++;
          }
        }
      } catch (CompilationError e) {
        System.out.println(
            "Failed to read dex/vdex file `" + programFile +"`: '" + e.getMessage() + "'");
      }
    }
    if (command.getSummary()) {
      System.out.println("D8: " + d8Count);
      System.out.println("R8: " + r8Count);
      System.out.println("Other: " + otherCount);
      System.out.println("Total: " + (d8Count + r8Count + otherCount));
    }
  }
}
