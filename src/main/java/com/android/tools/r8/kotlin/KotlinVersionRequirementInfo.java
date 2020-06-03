// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.google.common.collect.ImmutableList;
import java.util.List;
import kotlinx.metadata.KmVersion;
import kotlinx.metadata.KmVersionRequirement;
import kotlinx.metadata.KmVersionRequirementLevel;
import kotlinx.metadata.KmVersionRequirementVersionKind;
import kotlinx.metadata.KmVersionRequirementVisitor;

class KotlinVersionRequirementInfo {

  private static final KotlinVersionRequirementInfo NO_VERSION_REQUIREMENTS =
      new KotlinVersionRequirementInfo(ImmutableList.of());

  private final List<KotlinVersionRequirementPoint> versionRequirements;

  private KotlinVersionRequirementInfo(List<KotlinVersionRequirementPoint> versionRequirements) {
    this.versionRequirements = versionRequirements;
  }

  static KotlinVersionRequirementInfo create(List<KmVersionRequirement> kmVersionRequirements) {
    if (kmVersionRequirements.isEmpty()) {
      return NO_VERSION_REQUIREMENTS;
    }
    ImmutableList.Builder<KotlinVersionRequirementPoint> builder = ImmutableList.builder();
    for (KmVersionRequirement kmVersionRequirement : kmVersionRequirements) {
      builder.add(KotlinVersionRequirementPoint.create(kmVersionRequirement));
    }
    return new KotlinVersionRequirementInfo(builder.build());
  }

  public void rewrite(KmVisitorProviders.KmVersionRequirementVisitorProvider visitorProvider) {
    if (this == NO_VERSION_REQUIREMENTS) {
      return;
    }
    for (KotlinVersionRequirementPoint versionRequirement : versionRequirements) {
      versionRequirement.rewrite(visitorProvider.get());
    }
  }

  private static class KotlinVersionRequirementPoint {

    private final Integer errorCode;
    private final KmVersionRequirementVersionKind kind;
    private final KmVersionRequirementLevel level;
    private final String message;
    private final KmVersion version;

    private KotlinVersionRequirementPoint(
        KmVersionRequirementVersionKind kind,
        KmVersionRequirementLevel level,
        Integer errorCode,
        String message,
        KmVersion version) {
      this.errorCode = errorCode;
      this.kind = kind;
      this.level = level;
      this.message = message;
      this.version = version;
    }

    private static KotlinVersionRequirementPoint create(KmVersionRequirement kmVersionRequirement) {
      return new KotlinVersionRequirementPoint(
          kmVersionRequirement.kind,
          kmVersionRequirement.level,
          kmVersionRequirement.getErrorCode(),
          kmVersionRequirement.getMessage(),
          kmVersionRequirement.version);
    }

    private void rewrite(KmVersionRequirementVisitor visitor) {
      visitor.visit(kind, level, errorCode, message);
      visitor.visitVersion(version.getMajor(), version.getMinor(), version.getPatch());
      visitor.visitEnd();
    }
  }
}
