// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.experimental.startup;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.startup.StartupProfileBuilder;
import com.android.tools.r8.startup.StartupProfileProvider;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.UTF8TextInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public class StartupProfileProviderUtils {

  public static StartupProfileProvider createFromHumanReadableARTProfile(Path path) {
    return new StartupProfileProvider() {

      @Override
      public void getStartupProfile(StartupProfileBuilder startupProfileBuilder) {
        try {
          startupProfileBuilder.addHumanReadableARTProfile(
              new UTF8TextInputStream(path), ConsumerUtils.emptyConsumer());
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
