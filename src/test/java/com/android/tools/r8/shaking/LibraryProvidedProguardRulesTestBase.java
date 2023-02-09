// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.TestBase;

public class LibraryProvidedProguardRulesTestBase extends TestBase {

  enum LibraryType {
    JAR_WITH_RULES,
    AAR_WITH_RULES,
    AAR_WITH_RULES_ONLY_IN_JAR,
    AAR_WITH_RULES_BOTH_IN_JAR_AND_IN_AAR;

    boolean isAar() {
      return this != JAR_WITH_RULES;
    }

    boolean hasRulesInJar() {
      return this != AAR_WITH_RULES;
    }

    boolean hasRulesInAar() {
      return this == AAR_WITH_RULES || this == AAR_WITH_RULES_BOTH_IN_JAR_AND_IN_AAR;
    }
  }

  enum ProviderType {
    API,
    INJARS
  }
}
