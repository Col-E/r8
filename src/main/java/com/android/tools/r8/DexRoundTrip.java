// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.AndroidAppConsumers;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.io.ByteStreams;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DexRoundTrip {

  public static AndroidApp process(Collection<ProgramResource> dexResources)
      throws CompilationFailedException {
    InternalOptions options = new InternalOptions();
    options.useTreeShaking = false;
    options.skipMinification = true;
    options.ignoreMissingClasses = true;
    options.enableDesugaring = false;
    AndroidAppConsumers consumer = new AndroidAppConsumers(options);
    AndroidApp.Builder builder = AndroidApp.builder();
    ExceptionUtils.withR8CompilationHandler(
        options.reporter,
        () -> {
          for (ProgramResource resource : dexResources) {
            try (InputStream stream = resource.getByteStream()) {
              builder.addDexProgramData(ByteStreams.toByteArray(stream), resource.getOrigin());
            }
          }
          R8.runForTesting(builder.build(), options);
        });
    return consumer.build();
  }

  public static void main(String[] args) throws CompilationFailedException, IOException {
    List<ProgramResource> resources = new ArrayList<>(args.length);
    for (String arg : args) {
      Path file = Paths.get(arg);
      if (!FileUtils.isDexFile(file)) {
        throw new IllegalArgumentException(
            "Only DEX files are supported as inputs. Invalid file: " + file);
      }
      resources.add(ProgramResource.fromFile(Kind.DEX, file));
    }
    AndroidApp result = process(resources);
    process(result.getDexProgramResources());
  }
}
