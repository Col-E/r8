// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile;

import com.android.tools.r8.profile.art.ArtProfileRule;
import com.android.tools.r8.profile.startup.profile.StartupProfileRule;

public interface AbstractProfileRule {

  @SuppressWarnings("unchecked")
  default ArtProfileRule asArtProfileRule() {
    return (ArtProfileRule) this;
  }

  @SuppressWarnings("unchecked")
  default StartupProfileRule asStartupProfileRule() {
    return (StartupProfileRule) this;
  }
}
