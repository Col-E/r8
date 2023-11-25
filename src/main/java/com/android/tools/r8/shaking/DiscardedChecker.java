// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramDefinition;
import com.android.tools.r8.shaking.KeepInfo.Joiner;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class DiscardedChecker {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final InternalOptions options;

  private final List<ProgramDefinition> failed = new ArrayList<>();

  private DiscardedChecker(AppView<? extends AppInfoWithClassHierarchy> appView) {
    this.appView = appView;
    this.options = appView.options();
  }

  public static DiscardedChecker create(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return new DiscardedChecker(appView);
  }

  public static DiscardedChecker createForMainDex(
      AppView<? extends AppInfoWithClassHierarchy> appView) {
    MinimumKeepInfoCollection unconditionalKeepInfo =
        appView
            .getMainDexRootSet()
            .getDependentMinimumKeepInfo()
            .getOrCreateUnconditionalMinimumKeepInfo();
    return new DiscardedChecker(appView) {

      @Override
      boolean isCheckDiscardedEnabled(ProgramDefinition definition) {
        return unconditionalKeepInfo.hasMinimumKeepInfoThatMatches(
            definition.getReference(), Joiner::isCheckDiscardedEnabled);
      }
    };
  }

  public List<ProgramDefinition> run(
      Collection<DexProgramClass> classes, ExecutorService executorService)
      throws ExecutionException {
    assert failed.isEmpty();

    // TODO(b/131668850): Consider only iterating the items matched by a -checkdiscard rule.
    ThreadUtils.processItems(
        classes,
        this::checkClassAndMembers,
        appView.options().getThreadingModule(),
        executorService);

    // Sort the failures for determinism.
    failed.sort((item, other) -> item.getReference().compareTo(other.getReference()));

    return failed;
  }

  boolean isCheckDiscardedEnabled(ProgramDefinition definition) {
    return appView.getKeepInfo().getInfo(definition).isCheckDiscardedEnabled(options);
  }

  private void checkClassAndMembers(DexProgramClass clazz) {
    // Only look for -checkdiscard failures for members if the class itself did not fail a
    // -checkdiscard check
    if (check(clazz)) {
      clazz.forEachProgramMember(this::check);
    }
  }

  /** Returns true if the check succeeded (i.e., no -checkdiscard failure was found). */
  private boolean check(ProgramDefinition item) {
    if (isCheckDiscardedEnabled(item)) {
      // We expect few check discarded failures thus locking here should be OK.
      synchronized (failed) {
        failed.add(item);
      }
      return false;
    }
    return true;
  }
}
