// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinition;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringDiagnostic;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class DiscardedChecker {

  private final Set<DexDefinition> checkDiscarded;
  private final List<DexProgramClass> classes;
  private boolean fail = false;
  private final InternalOptions options;

  public DiscardedChecker(RootSet rootSet, DexApplication application, InternalOptions options) {
    this.checkDiscarded = rootSet.checkDiscarded;
    this.classes = application.classes();
    this.options = options;
  }

  public DiscardedChecker(
      RootSet rootSet, Set<DexType> types, AppInfo appInfo, InternalOptions options) {
    this.checkDiscarded = rootSet.checkDiscarded;
    this.classes = new ArrayList<>();
    types.forEach(
        type -> {
          DexClass clazz = appInfo.definitionFor(type);
          assert clazz.isProgramClass();
          this.classes.add(clazz.asProgramClass());
        });
    this.options = options;
  }

  public void run() {
    for (DexProgramClass clazz : classes) {
      checkItem(clazz);
      clazz.forEachMethod(this::checkItem);
      clazz.forEachField(this::checkItem);
    }
    if (fail) {
      throw new CompilationError("Discard checks failed.");
    }
  }

  private void checkItem(DexDefinition item) {
    if (checkDiscarded.contains(item)) {
      options.reporter.info(
          new StringDiagnostic("Item " + item.toSourceString() + " was not discarded."));
      fail = true;
    }
  }
}
