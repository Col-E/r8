// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.SourceFileEnvironment;
import com.android.tools.r8.SourceFileProvider;
import com.android.tools.r8.graph.AppView;

/** Computes the source file provider based on the proguard configuration if none is set. */
public class SourceFileRewriter {

  private final AppView<?> appView;

  public SourceFileRewriter(AppView<?> appView) {
    this.appView = appView;
  }

  public void run() {
    if (appView.options().sourceFileProvider != null) {
      return;
    }
    appView.options().sourceFileProvider = computeSourceFileProvider();
  }

  public SourceFileProvider computeSourceFileProvider() {
    if (!appView.options().getProguardConfiguration().getKeepAttributes().sourceFile) {
      return rewriteToDefaultSourceFile();
    }
    if (appView.options().forceProguardCompatibility) {
      return computeCompatProvider();
    }
    return computeNonCompatProvider();
  }

  private SourceFileProvider computeCompatProvider() {
    // Compatibility mode will only apply -renamesourcefileattribute when minifying names.
    if (appView.options().isMinifying()) {
      String renaming = getRenameSourceFileAttribute();
      if (renaming != null) {
        return rewriteTo(renaming);
      }
    }
    return null;
  }

  private SourceFileProvider computeNonCompatProvider() {
    String renaming = getRenameSourceFileAttribute();
    if (renaming != null) {
      return rewriteTo(renaming);
    }
    if (appView.options().isMinifying() || appView.options().isOptimizing()) {
      return rewriteToDefaultSourceFile();
    }
    return null;
  }

  private String getRenameSourceFileAttribute() {
    return appView.options().getProguardConfiguration().getRenameSourceFileAttribute();
  }

  private SourceFileProvider rewriteToDefaultSourceFile() {
    return rewriteTo(appView.dexItemFactory().defaultSourceFileAttributeString);
  }

  private SourceFileProvider rewriteTo(String renaming) {
    return new SourceFileProvider() {
      @Override
      public String get(SourceFileEnvironment environment) {
        return renaming;
      }
    };
  }
}
