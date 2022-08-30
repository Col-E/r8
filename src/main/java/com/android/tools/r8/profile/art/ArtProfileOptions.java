// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import java.util.Collection;
import java.util.Collections;

public class ArtProfileOptions {

  private Collection<ArtProfileInput> inputs = Collections.emptyList();
  private boolean passthrough;

  public ArtProfileOptions() {}

  public Collection<ArtProfileInput> getArtProfileInputs() {
    return inputs;
  }

  public ArtProfileOptions setArtProfileInputs(Collection<ArtProfileInput> inputs) {
    this.inputs = inputs;
    return this;
  }

  public boolean isPassthrough() {
    return passthrough;
  }

  public ArtProfileOptions setPassthrough(boolean passthrough) {
    this.passthrough = passthrough;
    return this;
  }
}
