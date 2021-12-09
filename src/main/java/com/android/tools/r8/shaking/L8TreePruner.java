// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.PrefixRewritingMapper;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// In L8, all classes not rewritten or emulated interfaces are pruned away.
// This avoids having non rewritten classes in the output when compiled with D8.
// This also avoids having non rewritten classes in the output.
public class L8TreePruner {

  private final InternalOptions options;
  private final Set<DexType> emulatedInterfaces = Sets.newIdentityHashSet();
  private final Set<DexType> backports = Sets.newIdentityHashSet();
  private final List<DexType> pruned = new ArrayList<>();

  public L8TreePruner(InternalOptions options) {
    this.options = options;
    backports.addAll(options.desugaredLibrarySpecification.getBackportCoreLibraryMember().keySet());
    emulatedInterfaces.addAll(
        options.desugaredLibrarySpecification.getEmulateLibraryInterface().keySet());
  }

  public DexApplication prune(DexApplication app, PrefixRewritingMapper rewritePrefix) {
    Map<DexType, DexProgramClass> typeMap = new IdentityHashMap<>();
    List<DexProgramClass> toKeep = new ArrayList<>();
    boolean pruneNestMember = false;
    for (DexProgramClass aClass : app.classes()) {
      typeMap.put(aClass.type, aClass);
      if (rewritePrefix.hasRewrittenType(aClass.type, null)
          || emulatedInterfaces.contains(aClass.type)) {
        toKeep.add(aClass);
      } else {
        pruneNestMember |= aClass.isInANest();
        pruned.add(aClass.type);
      }
    }
    if (pruneNestMember) {
      for (DexProgramClass keptClass : toKeep) {
        TreePruner.rewriteNestAttributes(keptClass, type -> !pruned.contains(type), typeMap::get);
      }
    }
    typeMap.clear();
    // TODO(b/134732760): Would be nice to add pruned type to the appView removedClasses instead
    // of just doing nothing with it.
    return app.builder().replaceProgramClasses(toKeep).build();
  }
}
