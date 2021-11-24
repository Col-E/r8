// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.SourceFileEnvironment;
import com.android.tools.r8.SourceFileProvider;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.shaking.ProguardConfiguration;
import com.android.tools.r8.utils.InternalOptions;

/** Computes the source file provider based on the proguard configuration if none is set. */
public class SourceFileRewriter {

  public static SourceFileProvider computeSourceFileProvider(
      SourceFileProvider provider, ProguardConfiguration configuration, InternalOptions options) {
    if (provider != null) {
      return provider;
    }
    if (!configuration.getKeepAttributes().sourceFile) {
      return rewriteToDefaultSourceFile(options.dexItemFactory());
    }
    if (options.forceProguardCompatibility) {
      return computeCompatProvider(options);
    }
    return computeNonCompatProvider(options);
  }

  private static SourceFileProvider computeCompatProvider(InternalOptions options) {
    // Compatibility mode will only apply -renamesourcefileattribute when minifying names.
    if (options.isMinifying()) {
      String renaming = getRenameSourceFileAttribute(options);
      if (renaming != null) {
        return rewriteTo(renaming, isDefaultOrEmpty(renaming, options));
      }
    }
    return null;
  }

  private static SourceFileProvider computeNonCompatProvider(InternalOptions options) {
    String renaming = getRenameSourceFileAttribute(options);
    if (renaming != null) {
      return rewriteTo(renaming, isDefaultOrEmpty(renaming, options));
    }
    if (options.isMinifying() || options.isOptimizing()) {
      return rewriteToDefaultSourceFile(options.dexItemFactory());
    }
    return null;
  }

  private static String getRenameSourceFileAttribute(InternalOptions options) {
    return options.getProguardConfiguration().getRenameSourceFileAttribute();
  }

  public static boolean isDefaultOrEmpty(String sourceFile, InternalOptions options) {
    return sourceFile.isEmpty()
        || options.dexItemFactory().defaultSourceFileAttributeString.equals(sourceFile);
  }

  private static SourceFileProvider rewriteToDefaultSourceFile(DexItemFactory factory) {
    return rewriteTo(factory.defaultSourceFileAttributeString, true);
  }

  private static SourceFileProvider rewriteTo(String renaming, boolean allowDiscard) {
    return new SourceFileProvider() {
      @Override
      public String get(SourceFileEnvironment environment) {
        return renaming;
      }

      @Override
      public boolean allowDiscardingSourceFile() {
        return allowDiscard;
      }
    };
  }
}
