// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.utils.BooleanUtils;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

@RunWith(Parameterized.class)
public class ExtractMarkerTest extends TestBase {
  private static final String CLASS_FILE =
      ToolHelper.EXAMPLES_BUILD_DIR + "classes/trivial/Trivial.class";

  private final TestParameters parameters;
  private boolean includeClassesChecksum;

  @Parameterized.Parameters(name = "{0}, includeClassesChecksum: {1}")
  public static Collection<Object[]> data() {
    return buildParameters(
        getTestParameters().withAllRuntimes().withAllApiLevels().build(), BooleanUtils.values());
  }

  public ExtractMarkerTest(TestParameters parameters, boolean includeClassesChecksum) {
    this.parameters = parameters;
    this.includeClassesChecksum = includeClassesChecksum;
  }

  private void verifyMarkerDex(Marker marker, Tool tool) {
    assertEquals(tool, marker.getTool());
    assertEquals(Version.LABEL, marker.getVersion());
    assertEquals(CompilationMode.DEBUG.toString().toLowerCase(), marker.getCompilationMode());
    assertEquals(parameters.getApiLevel().getLevel(), marker.getMinApi().intValue());
    assertEquals(includeClassesChecksum, marker.getHasChecksums());
  }

  @Test
  public void extractMarkerTestDex() throws CompilationFailedException {
    Assume.assumeTrue(parameters.getRuntime().isDex());

    boolean[] testExecuted = {false};
    D8.run(
        D8Command.builder()
            .addProgramFiles(Paths.get(CLASS_FILE))
            .setMinApiLevel(parameters.getApiLevel().getLevel())
            .setIncludeClassesChecksum(includeClassesChecksum)
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
                    verifyMarkerDex(marker, Tool.D8);
                    testExecuted[0] = true;
                  }
                })
            .build());
    assertTrue(testExecuted[0]);
  }

  private static void verifyMarkerCf(Marker marker, Tool tool) {
    assertEquals(tool, marker.getTool());
    assertEquals(Version.LABEL, marker.getVersion());
    assertEquals(CompilationMode.DEBUG.toString().toLowerCase(), marker.getCompilationMode());
    assertFalse(marker.getHasChecksums());
  }

  @Test
  public void extractMarkerTestCf() throws CompilationFailedException {
    Assume.assumeTrue(parameters.getRuntime().isCf());
    Assume.assumeFalse(includeClassesChecksum);

    boolean[] testExecuted = {false};
    R8.run(
        R8Command.builder()
            .addProgramFiles(Paths.get(CLASS_FILE))
            .addLibraryFiles(ToolHelper.getJava8RuntimeJar())
            .setMode(CompilationMode.DEBUG)
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
                    verifyMarkerCf(marker, Tool.R8);
                    testExecuted[0] = true;
                  }
                })
            .build());
    assertTrue(testExecuted[0]);
  }
}
