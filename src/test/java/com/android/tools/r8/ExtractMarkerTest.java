// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public class ExtractMarkerTest {

  @Test
  public void extractMarkerTest()
      throws CompilationFailedException, IOException, ExecutionException {
    String classFile = ToolHelper.EXAMPLES_BUILD_DIR + "classes/trivial/Trivial.class";
    D8.run(
        D8Command.builder()
            .addProgramFiles(Paths.get(classFile))
            .setProgramConsumer(
                new DexIndexedConsumer.ForwardingConsumer(null) {
                  @Override
                  public void accept(
                      int fileIndex,
                      byte[] data,
                      Set<String> descriptors,
                      DiagnosticsHandler handler) {
                    Marker marker;
                    try {
                      marker = ExtractMarker.extractMarkerFromDexProgramData(data);
                    } catch (Exception e) {
                      throw new RuntimeException(e);
                    }
                    assertEquals(Tool.D8, marker.getTool());
                    assertEquals(Version.LABEL, marker.getVersion());
                  }
                })
            .build());
  }
}
