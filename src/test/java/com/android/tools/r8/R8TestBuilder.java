// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.R8Command.Builder;
import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.StringUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class R8TestBuilder
    extends TestCompilerBuilder<R8Command, Builder, R8TestCompileResult, R8TestBuilder> {

  private R8TestBuilder(TestState state, Builder builder, Backend backend) {
    super(state, builder, backend);
  }

  public static R8TestBuilder create(TestState state, Backend backend) {
    return new R8TestBuilder(state, R8Command.builder(), backend);
  }

  private boolean enableInliningAnnotations = false;

  @Override
  R8TestBuilder self() {
    return this;
  }

  @Override
  R8TestCompileResult internalCompile(
      Builder builder, Consumer<InternalOptions> optionsConsumer, Supplier<AndroidApp> app)
      throws CompilationFailedException {
    if (enableInliningAnnotations) {
      ToolHelper.allowTestProguardOptions(builder);
    }
    StringBuilder proguardMapBuilder = new StringBuilder();
    builder.setProguardMapConsumer((string, ignore) -> proguardMapBuilder.append(string));
    ToolHelper.runR8WithoutResult(builder.build(), optionsConsumer);
    return new R8TestCompileResult(getState(), backend, app.get(), proguardMapBuilder.toString());
  }

  public R8TestBuilder addDataResources(List<DataEntryResource> resources) {
    resources.forEach(builder.getAppBuilder()::addDataResource);
    return self();
  }

  public R8TestBuilder addKeepRules(String... rules) {
    return addKeepRules(Arrays.asList(rules));
  }

  public R8TestBuilder addKeepRules(Collection<String> rules) {
    builder.addProguardConfiguration(new ArrayList<>(rules), Origin.unknown());
    return self();
  }

  public R8TestBuilder addKeepAllClassesRule() {
    addKeepRules("-keep class ** { *; }");
    return self();
  }

  public R8TestBuilder addKeepClassRules(Class<?>... classes) {
    for (Class<?> clazz : classes) {
      addKeepRules("-keep class " + clazz.getTypeName());
    }
    return self();
  }

  public R8TestBuilder addKeepPackageRules(Package pkg) {
    return addKeepRules("-keep class " + pkg.getName() + ".*");
  }

  public R8TestBuilder addKeepMainRule(Class<?> mainClass) {
    return addKeepRules(
        StringUtils.joinLines(
            "-keep class " + mainClass.getTypeName() + " {",
            "  public static void main(java.lang.String[]);",
            "}"));
  }

  public R8TestBuilder enableInliningAnnotations() {
    if (!enableInliningAnnotations) {
      enableInliningAnnotations = true;
      addKeepRules(
          "-forceinline class * { @com.android.tools.r8.ForceInline *; }",
          "-neverinline class * { @com.android.tools.r8.NeverInline *; }");
    }
    return self();
  }

  public R8TestBuilder enableProguardTestOptions() {
    builder.allowTestProguardOptions();
    return self();
  }
}
