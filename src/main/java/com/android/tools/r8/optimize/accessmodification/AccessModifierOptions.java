// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.accessmodification;

import com.android.tools.r8.utils.InternalOptions;

public class AccessModifierOptions {

  private InternalOptions options;

  public AccessModifierOptions(InternalOptions options) {
    this.options = options;
  }

  public boolean isAccessModificationEnabled() {
    return options.hasProguardConfiguration()
        && options.getProguardConfiguration().isAccessModificationAllowed();
  }
}
