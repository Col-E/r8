// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.dex.Marker.Tool;
import com.android.tools.r8.utils.CompilationFailedException;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.concurrent.ExecutionException;
import org.junit.Test;

public class ExtractMarkerTest {

  @Test
  public void extractMarkerTest()
      throws CompilationFailedException, IOException, ExecutionException {
    String classFile = ToolHelper.EXAMPLES_BUILD_DIR + "classes/trivial/Trivial.class";
    D8Command command = D8Command.builder()
            .addProgramFiles(Paths.get(classFile))
            .build();
    D8Output output = D8.run(command);
    byte[] data = ByteStreams.toByteArray(output.getDexResources().get(0).getStream());
    Marker marker = ExtractMarker.extractMarkerFromDexProgramData(data);
    assert marker != null;
    assert marker.getTool() == Tool.D8;
    assert marker.getVersion().equals(Version.LABEL);
  }
}
