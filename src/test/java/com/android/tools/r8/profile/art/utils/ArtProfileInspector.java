// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.android.tools.r8.profile.art.model.ExternalArtProfile;

public class ArtProfileInspector {

  private final ExternalArtProfile artProfile;

  public ArtProfileInspector(ExternalArtProfile artProfile) {
    this.artProfile = artProfile;
  }

  public ArtProfileInspector assertEmpty() {
    assertEquals(0, artProfile.size());
    return this;
  }

  public ArtProfileInspector assertEqualTo(ExternalArtProfile otherArtProfile) {
    assertEquals(otherArtProfile, artProfile);
    return this;
  }

  public ArtProfileInspector assertNotEmpty() {
    assertNotEquals(0, artProfile.size());
    return this;
  }
}
