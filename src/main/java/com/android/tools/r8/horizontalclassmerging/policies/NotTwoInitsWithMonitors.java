// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.horizontalclassmerging.policies;

import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;

public class NotTwoInitsWithMonitors extends AtMostOneClassThatMatchesPolicy {

  @Override
  public boolean atMostOneOf(DexProgramClass clazz) {
    for (ProgramMethod initializer : clazz.programInstanceInitializers()) {
      DexEncodedMethod definition = initializer.getDefinition();
      if (definition.isSynchronized() || definition.getCode().hasMonitorInstructions()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public String getName() {
    return "NotTwoInitsWithMonitors";
  }
}
