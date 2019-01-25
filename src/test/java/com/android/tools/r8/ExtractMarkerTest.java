// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import java.nio.file.Paths;
import java.util.Collection;
import java.util.Set;
import org.junit.Test;

public class ExtractMarkerTest {

  @Test
  public void extractMarkerTestDex() throws CompilationFailedException {
    String classFile = ToolHelper.EXAMPLES_BUILD_DIR + "classes/trivial/Trivial.class";
    boolean[] testExecuted = {false};
    D8.run(
        D8Command.builder()
            .addProgramFiles(Paths.get(classFile))
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
                    assertEquals(Tool.D8, marker.getTool());
                    assertEquals(Version.LABEL, marker.getVersion());
                    assertEquals(
                        CompilationMode.DEBUG.toString().toLowerCase(),
                        marker.getCompilationMode());
                    testExecuted[0] = true;
                  }
                })
            .build());
    assertTrue(testExecuted[0]);
  }

  @Test
  public void extractMarkerTestCf() throws CompilationFailedException {
    String classFile = ToolHelper.EXAMPLES_BUILD_DIR + "classes/trivial/Trivial.class";
    boolean[] testExecuted = {false};
    R8.run(
        R8Command.builder()
            .addProgramFiles(Paths.get(classFile))
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
                    assertEquals(Tool.R8, marker.getTool());
                    assertEquals(Version.LABEL, marker.getVersion());
                    assertEquals(
                        CompilationMode.DEBUG.toString().toLowerCase(),
                        marker.getCompilationMode());
                    testExecuted[0] = true;
                  }
                })
            .build());
    assertTrue(testExecuted[0]);
  }
}
