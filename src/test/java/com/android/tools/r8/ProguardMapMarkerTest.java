// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.naming.ProguardMapSupplier;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.VersionProperties;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class ProguardMapMarkerTest {
  private static final int EXPECTED_NUMBER_OF_KEYS_DEX = 5;
  private static final int EXPECTED_NUMBER_OF_KEYS_CF = 4;
  private static final String CLASS_FILE =
      ToolHelper.EXAMPLES_BUILD_DIR + "classes/trivial/Trivial.class";

  @Test
  public void proguardMapMarkerTest24() throws CompilationFailedException {
    proguardMapMarkerTestDex(AndroidApiLevel.N);
  }

  @Test
  public void proguardMapMarkerTest26() throws CompilationFailedException {
    proguardMapMarkerTestDex(AndroidApiLevel.O);
  }

  private static class BuildIds {
    String fromProgram = null;
    String fromMap = null;
  }

  private void proguardMapMarkerTestDex(AndroidApiLevel minApiLevel)
      throws CompilationFailedException {
    BuildIds buildIds = new BuildIds();
    R8.run(
        R8Command.builder()
            .addProgramFiles(Paths.get(CLASS_FILE))
            .setDisableTreeShaking(true)
            .setProgramConsumer(
                new DexIndexedConsumer.ForwardingConsumer(null) {
                  @Override
                  public void accept(
                      int fileIndex,
                      ByteDataView data,
                      Set<String> descriptors,
                      DiagnosticsHandler handler) {
                    Marker marker;
                    try {
                      Collection<Marker> markers =
                          ExtractMarker.extractMarkerFromDexProgramData(data.copyByteData());
                      assertEquals(1, markers.size());
                      marker = markers.iterator().next();
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                    buildIds.fromProgram = marker.getBuildId();
                  }
                })
            .addLibraryFiles(ToolHelper.getAndroidJar(minApiLevel))
            .setMinApiLevel(minApiLevel.getLevel())
            .setProguardMapConsumer(
                (proguardMap, handler) -> {
                  buildIds.fromMap =
                      verifyMarkersGetBuildId(
                          proguardMap, minApiLevel.getLevel(), EXPECTED_NUMBER_OF_KEYS_DEX);
                })
            .build());
    verifyBuildIds(buildIds);
  }

  private void verifyBuildIds(BuildIds buildIds) {
    assertTrue(buildIds.fromProgram != null && buildIds.fromProgram.length() > 0);
    assertTrue(buildIds.fromMap != null && buildIds.fromMap.length() > 0);
    assertEquals(buildIds.fromMap, buildIds.fromProgram);
  }

  @Test
  public void proguardMapMarkerTestCf() throws CompilationFailedException {
    BuildIds buildIds = new BuildIds();
    R8.run(
        R8Command.builder()
            .addProgramFiles(Paths.get(CLASS_FILE))
            .setDisableTreeShaking(true)
            .setProgramConsumer(
                new ClassFileConsumer.ForwardingConsumer(null) {
                  @Override
                  public void accept(
                      ByteDataView data, String descriptor, DiagnosticsHandler handler) {
                    Marker marker;
                    try {
                      Collection<Marker> markers =
                          ExtractMarker.extractMarkerFromClassProgramData(data.copyByteData());
                      assertEquals(1, markers.size());
                      marker = markers.iterator().next();
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                    buildIds.fromProgram = marker.getBuildId();
                  }
                })
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setProguardMapConsumer(
                (proguardMap, handler) -> {
                  buildIds.fromMap =
                      verifyMarkersGetBuildId(proguardMap, null, EXPECTED_NUMBER_OF_KEYS_CF);
                })
            .build());
    verifyBuildIds(buildIds);
  }

  private static String verifyMarkersGetBuildId(
      String proguardMap, Integer minApiLevel, int expectedNumberOfKeys) {
    String[] lines = proguardMap.split("\n");
    Set<String> keysFound = new HashSet<>();
    String buildId = null;
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
        assertNotNull(minApiLevel);
        assertEquals(minApiLevel.intValue(), Integer.parseInt(value));
      } else if (key.equals(ProguardMapSupplier.MARKER_KEY_COMPILER_HASH)) {
        assertEquals(VersionProperties.INSTANCE.getSha(), value);
      } else if (key.equals(ProguardMapSupplier.MARKER_KEY_BUILD_ID)) {
        assertNull(buildId);
        buildId = value;
      } else {
        continue;
      }
      assertFalse(keysFound.contains(key));
      keysFound.add(key);
    }
    assertEquals(expectedNumberOfKeys, keysFound.size());
    return buildId;
  }
}
