// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.SystemPropertyUtils.parseSystemPropertyOrDefault;

import com.android.tools.r8.utils.InternalOptions;
import java.util.Collection;
import java.util.Collections;

public class ArtProfileOptions {

  public static final String COMPLETENESS_PROPERTY_KEY =
      "com.android.tools.r8.artprofilerewritingcompletenesscheck";

  private Collection<ArtProfileForRewriting> artProfilesForRewriting = Collections.emptyList();
  private boolean enableCompletenessCheckForTesting =
      parseSystemPropertyOrDefault(COMPLETENESS_PROPERTY_KEY, false);

  private final InternalOptions options;

  public ArtProfileOptions(InternalOptions options) {
    this.options = options;
  }

  public Collection<ArtProfileForRewriting> getArtProfilesForRewriting() {
    return artProfilesForRewriting;
  }

  public boolean isCompletenessCheckForTestingEnabled() {
    return enableCompletenessCheckForTesting
        && !options.isDesugaredLibraryCompilation()
        && !options.getStartupOptions().isStartupCompletenessCheckForTestingEnabled()
        && !options.getStartupInstrumentationOptions().isStartupInstrumentationEnabled();
  }

  public boolean isIncludingApiReferenceStubs() {
    // We only include API reference stubs in the residual ART profiles for completeness testing.
    // This is because the API reference stubs should never be used at runtime except for
    // verification, meaning there should be no need to AOT them.
    return enableCompletenessCheckForTesting;
  }

  public boolean isIncludingBackportedClasses() {
    // Similar to isIncludingVarHandleClasses().
    return enableCompletenessCheckForTesting;
  }

  public boolean isIncludingConstantDynamicClass() {
    // Similar to isIncludingVarHandleClasses().
    return enableCompletenessCheckForTesting;
  }

  public boolean isIncludingDesugaredLibraryRetargeterForwardingMethodsUnconditionally() {
    // TODO(b/265729283): If we get as input the profile for the desugared library, maybe we can
    //  tell if the method targeted by the forwarding method is in the profile, e.g.:
    //  java.time.Instant java.util.DesugarDate.toInstant(java.util.Date).
    return enableCompletenessCheckForTesting;
  }

  public boolean isIncludingThrowingMethods() {
    // The throw methods we insert should generally be dead a runtime, so no need for them to be
    // optimized.
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
