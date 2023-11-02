// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize;

import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedMember;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.threading.ThreadingModule;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.IterableUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * This pass:
 *
 * <ul>
 *   <li>cleans the nests: it removes missing nest host/members from the input,
 *   <li>clears nests which do not use nest based access control to allow other optimizations such
 *       as class merging to perform better.
 * </ul>
 */
public class NestReducer {

  private AppView<AppInfoWithLiveness> appView;

  public NestReducer(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
  }

  public void run(ExecutorService executorService, Timing timing) throws ExecutionException {
    timing.begin("NestReduction");
    if (appView.options().shouldDesugarNests()) {
      removeNests();
    } else {
      reduceNests(executorService);
    }
    appView.notifyOptimizationFinishedForTesting();
    timing.end();
  }

  private void removeNests() {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.isInANest()) {
        if (clazz.isNestHost()) {
          clazz.clearNestMembers();
        } else {
          clazz.clearNestHost();
        }
      }
    }
  }

  private void reduceNests(ExecutorService executorService) throws ExecutionException {
    Set<DexProgramClass> nestHosts = Sets.newIdentityHashSet();
    Set<DexProgramClass> nestMembers = Sets.newIdentityHashSet();
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.isInANest()) {
        if (clazz.isNestHost()) {
          nestHosts.add(clazz);
        } else {
          nestMembers.add(clazz);
        }
      }
    }
    ThreadingModule threadingModule = appView.options().getThreadingModule();
    ThreadUtils.processItems(nestHosts, this::processNestHost, threadingModule, executorService);
    ThreadUtils.processItems(
        nestMembers, this::processNestMember, threadingModule, executorService);
  }

  private void processNestHost(DexProgramClass clazz) {
    BooleanBox nestHasPrivateMembers =
        new BooleanBox(IterableUtils.hasNext(clazz.members(DexEncodedMember::isPrivate)));
    clazz
        .getNestMembersClassAttributes()
        .removeIf(
            attribute -> {
              DexProgramClass member =
                  asProgramClassOrNull(appView.definitionFor(attribute.getNestMember(), clazz));
              if (member == null) {
                return true;
              }
              nestHasPrivateMembers.computeIfNotSet(
                  () -> IterableUtils.hasNext(member.members(DexEncodedMember::isPrivate)));
              return false;
            });
    if (nestHasPrivateMembers.isFalse() && appView.options().enableNestReduction) {
      clazz.getNestMembersClassAttributes().clear();
    }
  }

  private void processNestMember(DexProgramClass clazz) {
    DexProgramClass hostClass =
        asProgramClassOrNull(appView.definitionFor(clazz.getNestHost(), clazz));
    if (hostClass == null || !hostClass.isNestHost()) {
      clazz.clearNestHost();
    }
  }
}
