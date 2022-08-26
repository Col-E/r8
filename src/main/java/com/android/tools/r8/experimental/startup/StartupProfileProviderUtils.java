// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.experimental.startup.profile.art.HumanReadableARTProfileParser;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.FileUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public class StartupProfileProviderUtils {

  public static StartupProfileProvider createFromFile(Path path, Reporter reporter) {
    return new StartupProfileProvider() {

      @Override
      public void getStartupProfile(StartupProfileBuilder startupProfileBuilder) {
        try {
          HumanReadableARTProfileParser.create()
              .parseLines(
                  FileUtils.readAllLines(path).stream(),
                  startupProfileBuilder,
                  error ->
                      reporter.warning(
                          new StringDiagnostic(
                              "Invalid descriptor for startup class or method: " + error)));
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public Origin getOrigin() {
        return new PathOrigin(path);
      }
    };
  }
}
