// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexString;

/**
 * Visit program {@link DexClass}es and replace their sourceFile with the given string.
 *
 * If -keepattribute SourceFile is not set, we rather remove that attribute.
 */
public class SourceFileRewriter {

  private final AppView<?> appView;
  private final DexApplication application;

  public SourceFileRewriter(AppView<?> appView, DexApplication application) {
    this.appView = appView;
    this.application = application;
  }

  public void run() {
    if (!appView.options().getProguardConfiguration().getKeepAttributes().sourceFile) {
      doRenaming(getDefaultSourceFileAttribute());
    } else if (appView.options().forceProguardCompatibility) {
      runCompat();
    } else {
      runNonCompat();
    }
  }

  private void runCompat() {
    // Compatibility mode will only apply -renamesourcefileattribute when minifying names.
    if (appView.options().isMinifying()) {
      String renaming = getRenameSourceFileAttribute();
      if (renaming != null) {
        doRenaming(renaming);
      }
    }
  }

  private void runNonCompat() {
    String renaming = getRenameSourceFileAttribute();
    if (renaming != null) {
      doRenaming(renaming);
    } else if (appView.options().isMinifying()) {
      // TODO(b/202367773): This should also apply if optimizing.
      doRenaming(getDefaultSourceFileAttribute());
    }
  }

  private String getRenameSourceFileAttribute() {
    return appView.options().getProguardConfiguration().getRenameSourceFileAttribute();
  }

  private DexString getDefaultSourceFileAttribute() {
    return appView.dexItemFactory().defaultSourceFileAttribute;
  }

  private void doRenaming(String renaming) {
    doRenaming(appView.dexItemFactory().createString(renaming));
  }

  private void doRenaming(DexString renaming) {
    for (DexClass clazz : application.classes()) {
      clazz.sourceFile = renaming;
    }
  }
}
