// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.naming.ProguardMapSupplier;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ExtractMarkerUtils;
import com.android.tools.r8.utils.VersionProperties;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProguardMapMarkerTest extends TestBase {
  private static final int EXPECTED_NUMBER_OF_KEYS_DEX = 6;
  private static final int EXPECTED_NUMBER_OF_KEYS_CF = 5;
  private static final String CLASS_FILE =
      ToolHelper.EXAMPLES_BUILD_DIR + "classes/trivial/Trivial.class";

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  public ProguardMapMarkerTest(TestParameters parameters) {
    parameters.assertNoneRuntime();
  }

  @Test
  public void proguardMapMarkerTest24() throws CompilationFailedException {
    proguardMapMarkerTestDex(AndroidApiLevel.N);
  }

  @Test
  public void proguardMapMarkerTest26() throws CompilationFailedException {
    proguardMapMarkerTestDex(AndroidApiLevel.O);
  }

  private static class ProguardMapIds {
    String fromProgram = null;
    String fromMap = null;
  }

  private void proguardMapMarkerTestDex(AndroidApiLevel minApiLevel)
      throws CompilationFailedException {
    ProguardMapIds proguardMapIds = new ProguardMapIds();
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
                          ExtractMarkerUtils.extractMarkerFromDexProgramData(data.copyByteData());
                      assertEquals(1, markers.size());
                      marker = markers.iterator().next();
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                    proguardMapIds.fromProgram = marker.getPgMapId();
                  }
                })
            .addLibraryFiles(ToolHelper.getAndroidJar(minApiLevel))
            .setMinApiLevel(minApiLevel.getLevel())
            .setProguardMapConsumer(
                ToolHelper.consumeString(
                    proguardMap -> {
                      proguardMapIds.fromMap =
                          verifyMarkersGetPgMapId(
                              proguardMap, minApiLevel.getLevel(), EXPECTED_NUMBER_OF_KEYS_DEX);
                    }))
            .build());
    verifyProguardMapIds(proguardMapIds);
  }

  private void verifyProguardMapIds(ProguardMapIds proguardMapIds) {
    assertTrue(
        proguardMapIds.fromProgram != null
            && proguardMapIds.fromProgram.length() == ProguardMapSupplier.PG_MAP_ID_LENGTH);
    assertTrue(proguardMapIds.fromMap != null);
    assertEquals(proguardMapIds.fromMap, proguardMapIds.fromProgram);
  }

  @Test
  public void proguardMapMarkerTestCf() throws CompilationFailedException {
    ProguardMapIds buildIds = new ProguardMapIds();
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
                          ExtractMarkerUtils.extractMarkerFromClassProgramData(data.copyByteData());
                      assertEquals(1, markers.size());
                      marker = markers.iterator().next();
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                    buildIds.fromProgram = marker.getPgMapId();
                  }
                })
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setProguardMapConsumer(
                ToolHelper.consumeString(
                    proguardMap -> {
                      buildIds.fromMap =
                          verifyMarkersGetPgMapId(proguardMap, null, EXPECTED_NUMBER_OF_KEYS_CF);
                    }))
            .build());
    verifyProguardMapIds(buildIds);
  }

  private static String verifyMarkersGetPgMapId(
      String proguardMap, Integer minApiLevel, int expectedNumberOfKeys) {
    String[] lines = proguardMap.split("\n");
    Set<String> keysFound = new HashSet<>();
    String proguardMapId = null;
    String proguardMapHash = null;
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
        assertEquals("R8", value);
      } else if (key.equals(ProguardMapSupplier.MARKER_KEY_COMPILER_VERSION)) {
        assertEquals(Version.LABEL, value);
      } else if (key.equals(ProguardMapSupplier.MARKER_KEY_MIN_API)) {
        assertNotNull(minApiLevel);
        assertEquals(minApiLevel.intValue(), Integer.parseInt(value));
      } else if (key.equals(ProguardMapSupplier.MARKER_KEY_COMPILER_HASH)) {
        assertEquals(VersionProperties.INSTANCE.getSha(), value);
      } else if (key.equals(ProguardMapSupplier.MARKER_KEY_PG_MAP_ID)) {
        proguardMapId = value;
      } else if (key.equals(ProguardMapSupplier.MARKER_KEY_PG_MAP_HASH)) {
        proguardMapHash = value;
      } else {
        continue;
      }
      assertFalse(keysFound.contains(key));
      keysFound.add(key);
    }
    assertEquals(expectedNumberOfKeys, keysFound.size());
    return proguardMapId;
  }
}
