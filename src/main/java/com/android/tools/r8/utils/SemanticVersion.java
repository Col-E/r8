// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import java.util.Objects;

public class SemanticVersion {

  public static SemanticVersion parse(String version) {
    int majorEnd = version.indexOf('.');
    if (majorEnd <= 0) {
      throw new IllegalArgumentException("Invalid semantic version: " + version);
    }
    int minorEnd = version.indexOf('.', majorEnd + 1);
    if (minorEnd <= majorEnd) {
      throw new IllegalArgumentException("Invalid semantic version: " + version);
    }
    // No current support for extensions.
    int patchEnd = version.length();
    int major;
    int minor;
    int patch;
    try {
      major = Integer.parseInt(version.substring(0, majorEnd));
      minor = Integer.parseInt(version.substring(majorEnd + 1, minorEnd));
      patch = Integer.parseInt(version.substring(minorEnd + 1, patchEnd));
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid semantic version: " + version, e);
    }
    return new SemanticVersion(major, minor, patch);
  }

  private final int major;
  private final int minor;
  private final int patch;

  public SemanticVersion(int major, int minor, int patch) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
  }

  public int getMajor() {
    return major;
  }

  public int getMinor() {
    return minor;
  }

  public int getPatch() {
    return patch;
  }

  public boolean isNewerOrEqual(SemanticVersion other) {
    if (major != other.major) {
      return major > other.major;
    }
    if (minor != other.minor) {
      return minor > other.minor;
    }
    return patch >= other.patch;
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SemanticVersion)) {
      return false;
    }
    SemanticVersion other = (SemanticVersion) obj;
    return major == other.major && minor == other.minor && patch == other.patch;
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor, patch);
  }

  @Override
  public String toString() {
    return "" + major + "." + minor + "." + patch;
  }
}
