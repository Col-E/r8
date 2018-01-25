// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.android.tools.r8.naming.ProguardMapSupplier;
import com.android.tools.r8.utils.VersionProperties;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class ProguardMapMarkerTest {
  @Test
  public void proguardMapMarkerTest24() throws CompilationFailedException {
    proguardMapMarkerTest(24);
  }

  @Test
  public void proguardMapMarkerTest26() throws CompilationFailedException {
    proguardMapMarkerTest(26);
  }

  private void proguardMapMarkerTest(int minApiLevel) throws CompilationFailedException {
    String classFile = ToolHelper.EXAMPLES_BUILD_DIR + "classes/trivial/Trivial.class";
    R8.run(
        R8Command.builder()
            .addProgramFiles(Paths.get(classFile))
            .setProgramConsumer(
                new DexIndexedConsumer() {
                  @Override
                  public void accept(
                      int fileIndex,
                      byte[] data,
                      Set<String> descriptors,
                      DiagnosticsHandler handler) {}

                  @Override
                  public void finished(DiagnosticsHandler handler) {}
                })
            .addLibraryFiles(ToolHelper.getAndroidJar(minApiLevel))
            .setMinApiLevel(minApiLevel)
            .setProguardMapConsumer(
                (proguardMap, handler) -> {
                  verifyMarkers(proguardMap, minApiLevel);
                })
            .build());
  }

  private static void verifyMarkers(String proguardMap, int minApiLevel) {
    String[] lines = proguardMap.split("\n");
    Set<String> keysFound = new HashSet<>();
    for (String line : lines) {
      if (!line.startsWith("#")) {
        continue;
      }
      String comment = line.substring(1).trim();
      int colonIndex = comment.indexOf(":");
      if (colonIndex < 0) {
        continue;
      }
      String key = comment.substring(0, colonIndex).trim();
      String value = comment.substring(colonIndex + 1).trim();
      if (key.equals(ProguardMapSupplier.MARKER_KEY_COMPILER)) {
        assertEquals(ProguardMapSupplier.MARKER_VALUE_COMPILER, value);
      } else if (key.equals(ProguardMapSupplier.MARKER_KEY_COMPILER_VERSION)) {
        assertEquals(Version.LABEL, value);
      } else if (key.equals(ProguardMapSupplier.MARKER_KEY_MIN_API)) {
        assertEquals(minApiLevel, Integer.parseInt(value));
      } else if (key.equals(ProguardMapSupplier.MARKER_KEY_COMPILER_HASH)) {
        assertEquals(VersionProperties.INSTANCE.getSha(), value);
      } else {
        continue;
      }
      assertFalse(keysFound.contains(key));
      keysFound.add(key);
    }
  }
}
