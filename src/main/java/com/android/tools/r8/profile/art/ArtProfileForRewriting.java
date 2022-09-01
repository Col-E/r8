// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

/** Internal pair of an {@link ArtProfileProvider} and {@link ArtProfileConsumer}. */
public class ArtProfileForRewriting {

  private final ArtProfileProvider artProfileProvider;
  private final ArtProfileConsumer residualArtProfileConsumer;

  public ArtProfileForRewriting(
      ArtProfileProvider artProfileProvider, ArtProfileConsumer residualArtProfileConsumer) {
    this.artProfileProvider = artProfileProvider;
    this.residualArtProfileConsumer = residualArtProfileConsumer;
  }

  /** Specifies a provider that performs callbacks to a given {@link ArtProfileBuilder}. */
  public ArtProfileProvider getArtProfileProvider() {
    return artProfileProvider;
  }

  /**
   * Specifies a consumer that should receive the ART profile after it has been rewritten to match
   * the residual, optimized application.
   */
  public ArtProfileConsumer getResidualArtProfileConsumer() {
    return residualArtProfileConsumer;
  }
}
