// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import java.util.Objects;

public class SemanticVersion implements Comparable<SemanticVersion> {

  private static SemanticVersion MIN = SemanticVersion.create(0, 0, 0);
  private static SemanticVersion MAX =
      SemanticVersion.create(Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MAX_VALUE);

  public static SemanticVersion parse(String version) {
    int majorEnd = version.indexOf('.');
    if (majorEnd <= 0) {
      throw new IllegalArgumentException("Invalid semantic version: " + version);
    }
    int minorEnd = version.indexOf('.', majorEnd + 1);
    if (minorEnd <= majorEnd + 1) {
      throw new IllegalArgumentException("Invalid semantic version: " + version);
    }
    int patchEnd = version.indexOf('-', minorEnd + 1);
    int prereleaseEnd = -1;
    if (patchEnd == -1) {
      patchEnd = version.length();
    } else {
      if (patchEnd <= minorEnd + 1) {
        throw new IllegalArgumentException("Invalid semantic version: " + version);
      }
      prereleaseEnd = version.length();
    }
    int major;
    int minor;
    int patch;
    String prerelease;
    try {
      major = Integer.parseInt(version.substring(0, majorEnd));
      minor = Integer.parseInt(version.substring(majorEnd + 1, minorEnd));
      patch = Integer.parseInt(version.substring(minorEnd + 1, patchEnd));
      prerelease = prereleaseEnd < 0 ? null : version.substring(patchEnd + 1, prereleaseEnd);
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("Invalid semantic version: " + version, e);
    }
    return create(major, minor, patch, prerelease);
  }

  private final int major;
  private final int minor;
  private final int patch;
  private final String prerelease;

  private SemanticVersion(int major, int minor, int patch, String prerelease) {
    this.major = major;
    this.minor = minor;
    this.patch = patch;
    this.prerelease = prerelease;
  }

  public static SemanticVersion create(int major, int minor, int patch) {
    return create(major, minor, patch, null);
  }

  public static SemanticVersion create(int major, int minor, int patch, String prerelease) {
    return new SemanticVersion(major, minor, patch, prerelease);
  }

  public static SemanticVersion min() {
    return MIN;
  }

  public static SemanticVersion max() {
    return MAX;
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

  public boolean isNewer(SemanticVersion other) {
    if (major != other.major) {
      return major > other.major;
    }
    if (minor != other.minor) {
      return minor > other.minor;
    }
    return patch > other.patch;
  }

  public boolean isNewerOrEqual(SemanticVersion other) {
    return isNewer(other) || equals(other);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof SemanticVersion)) {
      return false;
    }
    SemanticVersion other = (SemanticVersion) obj;
    return major == other.major
        && minor == other.minor
        && patch == other.patch
        && Objects.equals(prerelease, other.prerelease);
  }

  @Override
  public int hashCode() {
    return Objects.hash(major, minor, patch, prerelease);
  }

  @Override
  public String toString() {
    return "" + major + "." + minor + "." + patch + (prerelease != null ? "-" + prerelease : "");
  }

  @Override
  public int compareTo(SemanticVersion other) {
    if (equals(other)) {
      return 0;
    }
    return isNewerOrEqual(other) ? -1 : 1;
  }
}
