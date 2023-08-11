// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.gson;

import com.android.tools.r8.DataResourceProvider;
import com.android.tools.r8.ProguardTestBuilder;
import com.android.tools.r8.R8FullTestBuilder;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestShrinkerBuilder;
import com.android.tools.r8.ToolHelper;
import com.android.tools.r8.shaking.FilteredClassPath;
import com.android.tools.r8.utils.ArchiveResourceProvider;
import java.nio.file.Path;

public class GsonTestBase extends TestBase {

  static class ArchiveResourceProviderWithoutResources extends ArchiveResourceProvider {
    public ArchiveResourceProviderWithoutResources(Path archive) {
      super(FilteredClassPath.unfiltered(archive), false);
    }

    @Override
    public DataResourceProvider getDataResourceProvider() {
      return null;
    }
  }

  static void addRuntimeLibrary(TestShrinkerBuilder builder, TestParameters parameters) {
    // Gson use java.lang.ReflectiveOperationException.
    builder.addDefaultRuntimeLibraryWithReflectiveOperationException(parameters);
  }

  static void addGsonLibraryAndKeepRules(R8FullTestBuilder builder) {
    builder
        .addProgramResourceProviders(new ArchiveResourceProviderWithoutResources(ToolHelper.GSON))
        .addKeepRuleFiles(ToolHelper.GSON_KEEP_RULES)
        .allowUnusedProguardConfigurationRules();
  }

  static void addGsonLibraryAndKeepRules(ProguardTestBuilder builder) {
    builder.addProgramFiles(ToolHelper.GSON).addKeepRuleFiles(ToolHelper.GSON_KEEP_RULES);
  }
}
