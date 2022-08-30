// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

/**
 * Top-level API for supplying an ART profile to the compiler and retrieving the ART profile after
 * the profile has been rewritten to match the residual, optimized application.
 */
// TODO(b/237043695): @Keep this when adding a public API for passing ART profiles to the compiler.
public interface ArtProfileInput {

  /**
   * Specifies a consumer that should receive the ART profile after it has been rewritten to match
   * the residual, optimized application.
   */
  // TODO(b/237043695): If this ends up in the public API, maybe rename this method to
  //  getResidualArtProfileConsumer() and ResidualArtProfileConsumer to ArtProfileConsumer.
  ResidualArtProfileConsumer getArtProfileConsumer();

  /** Provides the ART profile by performing callbacks to the given {@param profileBuilder}. */
  void getArtProfile(ArtProfileBuilder profileBuilder);
}
