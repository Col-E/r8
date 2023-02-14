// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.SystemPropertyUtils.parseSystemPropertyOrDefault;

import com.android.tools.r8.utils.InternalOptions;
import java.util.Collection;
import java.util.Collections;

public class ArtProfileOptions {

  private Collection<ArtProfileForRewriting> artProfilesForRewriting = Collections.emptyList();
  private boolean enableCompletenessCheckForTesting =
      parseSystemPropertyOrDefault(
          "com.android.tools.r8.artprofilerewritingcompletenesscheck", false);

  private final InternalOptions options;

  public ArtProfileOptions(InternalOptions options) {
    this.options = options;
  }

  public Collection<ArtProfileForRewriting> getArtProfilesForRewriting() {
    return artProfilesForRewriting;
  }

  public boolean isCompletenessCheckForTestingEnabled() {
    return enableCompletenessCheckForTesting && !options.isDesugaredLibraryCompilation();
  }

  public boolean isIncludingApiReferenceStubs() {
    // We only include API reference stubs in the residual ART profiles for completeness testing.
    // This is because the API reference stubs should never be used at runtime except for
    // verification, meaning there should be no need to AOT them.
    return enableCompletenessCheckForTesting;
  }

  public boolean isIncludingConstantDynamicClass() {
    // Similar to isIncludingVarHandleClasses().
    return enableCompletenessCheckForTesting;
  }

  public boolean isIncludingVarHandleClasses() {
    // We only include var handle classes in the residual ART profiles for completeness testing,
    // since the classes synthesized by var handle desugaring are fairly large and may not be that
    // important for runtime performance.
    return enableCompletenessCheckForTesting;
  }

  public ArtProfileOptions setArtProfilesForRewriting(Collection<ArtProfileForRewriting> inputs) {
    this.artProfilesForRewriting = inputs;
    return this;
  }

  public ArtProfileOptions setEnableCompletenessCheckForTesting(
      boolean enableCompletenessCheckForTesting) {
    this.enableCompletenessCheckForTesting = enableCompletenessCheckForTesting;
    return this;
  }
}
