// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.accessmodification;

import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.SystemPropertyUtils;

public class AccessModifierOptions {

  private boolean enableLegacyAccessModifier =
      SystemPropertyUtils.parseSystemPropertyOrDefault(
          "com.android.tools.r8.accessmodification.legacy", false);

  // TODO(b/131130038): Do not allow accessmodification when kept.
  private boolean forceModifyPackagePrivateAndProtectedMethods = true;

  private InternalOptions options;

  public AccessModifierOptions(InternalOptions options) {
    this.options = options;
  }

  public boolean canPollutePublicApi() {
    return isAccessModificationRulePresent() || options.isGeneratingDex();
  }

  public boolean isAccessModificationEnabled() {
    if (isAccessModificationRulePresent()) {
      return true;
    }
    if (isLegacyAccessModifierEnabled()) {
      return false;
    }
    // TODO(b/288062771): Enable access modification by default for L8.
    return options.synthesizedClassPrefix.isEmpty()
        && !options.forceProguardCompatibility
        && options.isOptimizing();
  }

  private boolean isAccessModificationRulePresent() {
    return options.hasProguardConfiguration()
        && options.getProguardConfiguration().isAccessModificationAllowed();
  }

  public boolean isForceModifyingPackagePrivateAndProtectedMethods() {
    return forceModifyPackagePrivateAndProtectedMethods;
  }

  public void setForceModifyPackagePrivateAndProtectedMethods(
      boolean forceModifyPackagePrivateAndProtectedMethods) {
    this.forceModifyPackagePrivateAndProtectedMethods =
        forceModifyPackagePrivateAndProtectedMethods;
  }

  public boolean isLegacyAccessModifierEnabled() {
    return enableLegacyAccessModifier;
  }
}
