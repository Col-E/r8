// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import com.android.tools.r8.Keep;
import com.android.tools.r8.Resource;

/** Interface for providing a startup profile to the compiler. */
@Keep
public interface StartupProfileProvider extends Resource {

  /** Provides the startup profile by callbacks to the given {@param startupProfileBuilder}. */
  void getStartupProfile(StartupProfileBuilder startupProfileBuilder);
}
