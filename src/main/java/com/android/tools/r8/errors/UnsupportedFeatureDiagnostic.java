// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.errors;

import com.android.tools.r8.Diagnostic;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.position.Position;
import com.android.tools.r8.utils.AndroidApiLevel;

@KeepForApi
public abstract class UnsupportedFeatureDiagnostic implements Diagnostic {

  public static String makeMessage(
      AndroidApiLevel minApiLevel, String unsupportedFeatures, String sourceString) {
    String message =
        minApiLevel == null
            ? (unsupportedFeatures + " are not supported at any API level known by the compiler")
            : (unsupportedFeatures
                + " are only supported starting with "
                + minApiLevel.getName()
                + " (--min-api "
                + minApiLevel.getLevel()
                + ")");
    message = (sourceString != null) ? message + ": " + sourceString : message;
    return message;
  }

  private final String descriptor;
  private final AndroidApiLevel supportedApiLevel;
  private final Origin origin;
  private final Position position;

  // Package-private constructor.
  UnsupportedFeatureDiagnostic(
      String descriptor, AndroidApiLevel supportedApiLevel, Origin origin, Position position) {
    this.descriptor = descriptor;
    this.supportedApiLevel = supportedApiLevel;
    this.origin = origin;
    this.position = position;
  }

  @Override
  public Origin getOrigin() {
    return origin;
  }

  @Override
  public Position getPosition() {
    return position;
  }

  /**
   * Get a descriptor for the unsupported feature.
   *
   * <p>This descriptor is guaranteed by the compiler to be unique for possible unsupported features
   * and will remain unchanged in future versions of the compiler.
   */
  public String getFeatureDescriptor() {
    return descriptor;
  }

  /**
   * Get the API level at which this feature is supported.
   *
   * @return Supported level or -1 if unsupported at all API levels known to the compiler.
   */
  public int getSupportedApiLevel() {
    return supportedApiLevel == null ? -1 : supportedApiLevel.getLevel();
  }
}
