// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import java.util.Set;

public interface LiveSubTypeInfo {

  LiveSubTypeResult getLiveSubTypes(DexType type);

  LiveSubTypeResult getLiveImmediateSubtypes(DexType type);

  class LiveSubTypeResult {

    private final Set<DexProgramClass> programClasses;
    private final Set<DexCallSite> callSites;

    public LiveSubTypeResult(Set<DexProgramClass> programClasses, Set<DexCallSite> callSites) {
      // TODO(b/149006127): Enable when we no longer rely on callSites == null.
      this.programClasses = programClasses;
      this.callSites = callSites;
    }

    public Set<DexProgramClass> getProgramClasses() {
      return programClasses;
    }

    public Set<DexCallSite> getCallSites() {
      return callSites;
    }
  }
}
