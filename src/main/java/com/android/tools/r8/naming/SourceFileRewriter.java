// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.shaking.ProguardConfiguration;

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
    boolean isMinifying = appView.options().isMinifying();
    ProguardConfiguration proguardConfiguration = appView.options().getProguardConfiguration();
    boolean hasKeptNonRenamedSourceFile =
        proguardConfiguration.getRenameSourceFileAttribute() == null
            && proguardConfiguration.getKeepAttributes().sourceFile;
    // If source file is kept without a rewrite, it is only modified it in a minifing full-mode.
    if (hasKeptNonRenamedSourceFile
        && (!isMinifying || appView.options().forceProguardCompatibility)) {
      return;
    }
    assert !isMinifying || appView.appInfo().hasLiveness();
    DexString defaultRenaming = getSourceFileRenaming(proguardConfiguration);
    for (DexClass clazz : application.classes()) {
      if (hasKeptNonRenamedSourceFile
          && !appView.withLiveness().appInfo().isMinificationAllowed(clazz.type)) {
        continue;
      }
      clazz.sourceFile = defaultRenaming;
    }
  }

  private DexString getSourceFileRenaming(ProguardConfiguration proguardConfiguration) {
    // If we should not be keeping the source file, null it out.
    if (!proguardConfiguration.getKeepAttributes().sourceFile) {
      // For class files, we always remove the attribute
      if (appView.options().isGeneratingClassFiles()) {
        return null;
      }
      assert appView.options().isGeneratingDex();
      // When generating DEX we only remove the attribute for full-mode to ensure that we get
      // line-numbers printed in stack traces.
      if (!appView.options().forceProguardCompatibility) {
        return null;
      }
    }

    String renamedSourceFileAttribute = proguardConfiguration.getRenameSourceFileAttribute();
    if (renamedSourceFileAttribute != null) {
      return appView.dexItemFactory().createString(renamedSourceFileAttribute);
    }

    // Otherwise, take the smallest size depending on platform. We cannot use NULL since the jvm
    // and art will write at foo.bar.baz(Unknown Source) without a line-number. Newer version of ART
    // will report the DEX PC.
    return appView
        .dexItemFactory()
        .createString(appView.options().isGeneratingClassFiles() ? "SourceFile" : "");
  }
}
