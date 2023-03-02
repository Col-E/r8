// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.UTF8TextInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;

public class ArtProfileProviderUtils {

  public static ArtProfileProvider createFromHumanReadableArtProfile(Path artProfile) {
    return new ArtProfileProvider() {
      @Override
      public void getArtProfile(ArtProfileBuilder profileBuilder) {
        try {
          profileBuilder.addHumanReadableArtProfile(
              new UTF8TextInputStream(artProfile), emptyConsumer());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public Origin getOrigin() {
        return new PathOrigin(artProfile);
      }
    };
  }
}
