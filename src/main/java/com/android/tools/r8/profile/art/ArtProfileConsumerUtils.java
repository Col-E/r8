// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TextOutputStream;
import com.android.tools.r8.utils.UTF8TextOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public class ArtProfileConsumerUtils {

  public static ArtProfileConsumer create(Path rewrittenArtProfile) {
    return new ArtProfileConsumer() {

      @Override
      public TextOutputStream getHumanReadableArtProfileConsumer() {
        try {
          return new UTF8TextOutputStream(rewrittenArtProfile);
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public void finished(DiagnosticsHandler handler) {
        // Intentionally empty.
      }
    };
  }
}
