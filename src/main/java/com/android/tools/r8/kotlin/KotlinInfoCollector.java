// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class KotlinInfoCollector {
  public static void computeKotlinInfoForProgramClasses(
      DexApplication application, AppView<?> appView, ExecutorService executorService)
      throws ExecutionException {
    if (appView.options().kotlinOptimizationOptions().disableKotlinSpecificOptimizations) {
      return;
    }
    Kotlin kotlin = appView.dexItemFactory().kotlin;
    Reporter reporter = appView.options().reporter;
    Map<DexProgramClass, DexProgramClass> companionToHostMap = new ConcurrentHashMap<>();
    ThreadUtils.processItems(
        application.classes(),
        programClass -> {
          KotlinInfo kotlinInfo = kotlin.getKotlinInfo(programClass, reporter);
          programClass.setKotlinInfo(kotlinInfo);
          KotlinMemberInfo.markKotlinMemberInfo(programClass, kotlinInfo, reporter);
          // Store a companion type to revisit.
          if (kotlinInfo != null
              && kotlinInfo.isClass()
              && kotlinInfo.asClass().hasCompanionObject()) {
            DexType companionType = kotlinInfo.asClass().getCompanionObjectType();
            DexProgramClass companionClass = appView.definitionForProgramType(companionType);
            if (companionClass != null) {
              companionToHostMap.put(companionClass, programClass);
            }
          }
        },
        executorService);
    // TODO(b/151194869): if we can guarantee that Companion classes are visited ahead and their
    //  KotlinInfo is created before processing host classes, below could be hoisted to 1st pass.
    //  Maybe name-based filtering? E.g., classes whose name ends with "$Companion" v.s. not?
    ThreadUtils.processItems(
        companionToHostMap.keySet(),
        companionClass -> {
          KotlinInfo kotlinInfo = companionClass.getKotlinInfo();
          if (kotlinInfo != null && kotlinInfo.isClass()) {
            DexProgramClass hostClass = companionToHostMap.get(companionClass);
            assert hostClass != null;
            kotlinInfo.asClass().linkHostClass(hostClass);
            // Revisit host class's members with declarations in the companion object.
            KotlinMemberInfo.markKotlinMemberInfo(hostClass, kotlinInfo, reporter);
          }
        },
        executorService);
  }
}
