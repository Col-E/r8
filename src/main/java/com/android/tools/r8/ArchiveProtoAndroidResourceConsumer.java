// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.utils.ArchiveBuilder;
import com.android.tools.r8.utils.OutputBuilder;
import java.nio.file.Path;

public class ArchiveProtoAndroidResourceConsumer implements AndroidResourceConsumer {
  private final OutputBuilder outputBuilder;

  public ArchiveProtoAndroidResourceConsumer(Path outputPath) {
    this.outputBuilder = new ArchiveBuilder(outputPath);
    this.outputBuilder.open();
  }

  @Override
  public void accept(AndroidResourceOutput androidResource, DiagnosticsHandler diagnosticsHandler) {
    outputBuilder.addFile(
        androidResource.getPath().location(),
        androidResource.getByteDataView(),
        diagnosticsHandler);
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    outputBuilder.close(handler);
  }
}
