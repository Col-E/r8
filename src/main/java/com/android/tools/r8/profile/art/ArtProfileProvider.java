// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.Keep;
import com.android.tools.r8.Resource;

/** API for providing an ART profile to the compiler. */
@Keep
public interface ArtProfileProvider extends Resource {

  void getArtProfile(ArtProfileBuilder profileBuilder);
}
