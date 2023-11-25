// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.ArchiveBuilder;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.ZipUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;

@KeepForApi
public class ArchiveProtoAndroidResourceConsumer implements AndroidResourceConsumer {
  private final ArchiveBuilder archiveBuilder;
  private final Path inputPath;
  private Map<String, Boolean> compressionMap;

  public ArchiveProtoAndroidResourceConsumer(Path outputPath) {
    this(outputPath, null);
  }

  public ArchiveProtoAndroidResourceConsumer(Path outputPath, Path inputPath) {
    this.archiveBuilder = new ArchiveBuilder(outputPath);
    this.archiveBuilder.open();
    this.inputPath = inputPath;
  }

  private synchronized Map<String, Boolean> getCompressionMap(
      DiagnosticsHandler diagnosticsHandler) {
    if (compressionMap != null) {
      return compressionMap;
    }
    if (inputPath != null) {
      compressionMap = new HashMap<>();
      try {
        ZipUtils.iter(
            inputPath,
            entry -> {
              compressionMap.put(entry.getName(), entry.getMethod() != ZipEntry.STORED);
            });
      } catch (IOException e) {
        diagnosticsHandler.error(new ExceptionDiagnostic(e, new PathOrigin(inputPath)));
      }
    } else {
      compressionMap = Collections.emptyMap();
    }
    return compressionMap;
  }

  @Override
  public void accept(AndroidResourceOutput androidResource, DiagnosticsHandler diagnosticsHandler) {
    archiveBuilder.addFile(
        androidResource.getPath().location(),
        androidResource.getByteDataView(),
        diagnosticsHandler,
        getCompressionMap(diagnosticsHandler)
            .getOrDefault(androidResource.getPath().location(), true));
  }

  @Override
  public void finished(DiagnosticsHandler handler) {
    archiveBuilder.close(handler);
  }
}
