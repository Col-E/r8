// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDebugEvent;
import com.android.tools.r8.graph.DexDebugEvent.SetFile;
import com.android.tools.r8.graph.DexDebugInfo;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.shaking.ProguardConfiguration;
import java.util.Arrays;

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
      clazz.forEachMethod(encodedMethod -> {
        // Abstract methods do not have code_item.
        if (encodedMethod.shouldNotHaveCode()) {
          return;
        }
        Code code = encodedMethod.getCode();
        if (code == null) {
          return;
        }
        if (code.isDexCode()) {
          DexDebugInfo dexDebugInfo = code.asDexCode().getDebugInfo();
          if (dexDebugInfo == null) {
            return;
          }
          // Thanks to a single global source file, we can safely remove DBG_SET_FILE entirely.
          dexDebugInfo.events =
              Arrays.stream(dexDebugInfo.events)
                  .filter(dexDebugEvent -> !(dexDebugEvent instanceof SetFile))
                  .toArray(DexDebugEvent[]::new);
        } else {
          assert code.isCfCode();
          // CF has nothing equivalent to SetFile, so there is nothing to remove.
        }
      });
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
