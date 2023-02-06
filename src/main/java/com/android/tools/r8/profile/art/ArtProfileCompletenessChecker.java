// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.List;

public class ArtProfileCompletenessChecker {

  public static boolean verify(AppView<?> appView) {
    if (appView.options().getArtProfileOptions().isCompletenessCheckForTestingEnabled()) {
      ArtProfile completeArtProfile = appView.getArtProfileCollection().asNonEmpty().getLast();
      assert verifyProfileIsComplete(appView, completeArtProfile);
    }
    return true;
  }

  private static boolean verifyProfileIsComplete(AppView<?> appView, ArtProfile artProfile) {
    assert !appView.getSyntheticItems().hasPendingSyntheticClasses();
    List<DexReference> missing = new ArrayList<>();
    for (DexProgramClass clazz : appView.appInfo().classesWithDeterministicOrder()) {
      if (appView.horizontallyMergedClasses().hasBeenMergedIntoDifferentType(clazz.getType())
          || (appView.hasVerticallyMergedClasses()
              && appView.verticallyMergedClasses().hasBeenMergedIntoSubtype(clazz.getType()))) {
        continue;
      }
      if (!artProfile.containsClassRule(clazz.getType())) {
        missing.add(clazz.getType());
      }
      for (DexEncodedMethod method : clazz.methods()) {
        if (!artProfile.containsMethodRule(method.getReference())) {
          missing.add(method.getReference());
        }
      }
    }
    if (!missing.isEmpty()) {
      String message =
          StringUtils.join(System.lineSeparator(), missing, DexReference::toSmaliString);
      assert false : message;
    }
    return true;
  }
}
