// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase.KeepRuleConsumer;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApiLevel;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class LibraryDesugaringTestConfiguration {

  private final AndroidApiLevel minApiLevel;
  private final boolean withKeepRuleConsumer;
  private final KeepRuleConsumer keepRuleConsumer;
  private final List<StringResource> desugaredLibraryConfigurationResources;
  private final CompilationMode mode;
  private final boolean addRunClassPath;

  public static final LibraryDesugaringTestConfiguration DISABLED =
      new LibraryDesugaringTestConfiguration();

  private LibraryDesugaringTestConfiguration() {
    this.minApiLevel = null;
    this.keepRuleConsumer = null;
    this.withKeepRuleConsumer = false;
    this.desugaredLibraryConfigurationResources = null;
    this.mode = null;
    this.addRunClassPath = false;
  }

  private LibraryDesugaringTestConfiguration(
      AndroidApiLevel minApiLevel,
      boolean withKeepRuleConsumer,
      KeepRuleConsumer keepRuleConsumer,
      List<StringResource> desugaredLibraryConfigurationResources,
      CompilationMode mode,
      boolean addRunClassPath) {
    this.minApiLevel = minApiLevel;
    this.withKeepRuleConsumer = withKeepRuleConsumer;
    this.keepRuleConsumer = keepRuleConsumer;
    this.desugaredLibraryConfigurationResources = desugaredLibraryConfigurationResources;
    this.mode = mode;
    this.addRunClassPath = addRunClassPath;
  }

  public static class Builder {

    AndroidApiLevel minApiLevel;
    boolean withKeepRuleConsumer = false;
    KeepRuleConsumer keepRuleConsumer;
    private final List<StringResource> desugaredLibraryConfigurationResources = new ArrayList<>();
    private CompilationMode mode = CompilationMode.DEBUG;
    boolean addRunClassPath = true;

    private Builder() {}

    public Builder setMinApi(AndroidApiLevel minApiLevel) {
      this.minApiLevel = minApiLevel;
      return this;
    }

    public Builder withKeepRuleConsumer() {
      withKeepRuleConsumer = true;
      return this;
    }

    public Builder setKeepRuleConsumer(StringConsumer keepRuleConsumer) {
      withKeepRuleConsumer = false;
      if (keepRuleConsumer == null) {
        this.keepRuleConsumer = null;
      } else {
        assert keepRuleConsumer instanceof KeepRuleConsumer;
        this.keepRuleConsumer = (KeepRuleConsumer) keepRuleConsumer;
      }
      return this;
    }

    public Builder addDesugaredLibraryConfiguration(StringResource desugaredLibraryConfiguration) {
      desugaredLibraryConfigurationResources.add(desugaredLibraryConfiguration);
      return this;
    }

    public Builder setMode(CompilationMode mode) {
      this.mode = mode;
      return this;
    }

    public Builder dontAddRunClasspath() {
      addRunClassPath = false;
      return this;
    }

    public LibraryDesugaringTestConfiguration build() {
      if (desugaredLibraryConfigurationResources.isEmpty()) {
        desugaredLibraryConfigurationResources.add(
            StringResource.fromFile(ToolHelper.getDesugarLibJsonForTesting()));
      }
      if (withKeepRuleConsumer) {
        this.keepRuleConsumer = createKeepRuleConsumer(minApiLevel);
      }
      return new LibraryDesugaringTestConfiguration(
          minApiLevel,
          withKeepRuleConsumer,
          keepRuleConsumer,
          desugaredLibraryConfigurationResources,
          mode,
          addRunClassPath);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEnabled() {
    return this != DISABLED;
  }

  public boolean isAddRunClassPath() {
    return addRunClassPath;
  }

  public void configure(D8Command.Builder builder) {
    if (!isEnabled()) {
      return;
    }
    if (keepRuleConsumer != null) {
      builder.setDesugaredLibraryKeepRuleConsumer(keepRuleConsumer);
    }
    desugaredLibraryConfigurationResources.forEach(builder::addDesugaredLibraryConfiguration);
  }

  public void configure(R8Command.Builder builder) {
    if (!isEnabled()) {
      return;
    }
    if (keepRuleConsumer != null) {
      builder.setDesugaredLibraryKeepRuleConsumer(keepRuleConsumer);
    }
    desugaredLibraryConfigurationResources.forEach(builder::addDesugaredLibraryConfiguration);
  }

  public Path buildDesugaredLibrary(TestState state) {
    String generatedKeepRules = null;
    if (withKeepRuleConsumer) {
      if (keepRuleConsumer instanceof PresentKeepRuleConsumer) {
        generatedKeepRules = keepRuleConsumer.get();
        assertNotNull(generatedKeepRules);
      } else {
        assertThat(keepRuleConsumer, instanceOf(AbsentKeepRuleConsumer.class));
      }
    }
    String finalGeneratedKeepRules = generatedKeepRules;
    try {
      return L8TestBuilder.create(minApiLevel, state)
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
          .applyIf(
              mode == CompilationMode.RELEASE,
              builder -> {
                if (finalGeneratedKeepRules != null && !finalGeneratedKeepRules.trim().isEmpty()) {
                  builder.addGeneratedKeepRules(finalGeneratedKeepRules);
                }
              },
              L8TestBuilder::setDebug)
          .setDesugarJDKLibsConfiguration(ToolHelper.DESUGAR_LIB_CONVERSIONS)
          .compile()
          .writeToZip();
    } catch (CompilationFailedException | ExecutionException | IOException e) {
      throw new RuntimeException(e);
    }
  }

  public KeepRuleConsumer getKeepRuleConsumer() {
    return keepRuleConsumer;
  }

  public static KeepRuleConsumer createKeepRuleConsumer(TestParameters parameters) {
    return createKeepRuleConsumer(parameters.getApiLevel());
  }

  private static KeepRuleConsumer createKeepRuleConsumer(AndroidApiLevel apiLevel) {
    if (requiresAnyCoreLibDesugaring(apiLevel)) {
      return new PresentKeepRuleConsumer();
    }
    return new AbsentKeepRuleConsumer();
  }

  private static boolean requiresAnyCoreLibDesugaring(AndroidApiLevel apiLevel) {
    return apiLevel.isLessThan(AndroidApiLevel.O);
  }

  public static class PresentKeepRuleConsumer implements KeepRuleConsumer {

    StringBuilder stringBuilder = new StringBuilder();
    String result = null;

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      assert stringBuilder != null;
      assert result == null;
      stringBuilder.append(string);
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      assert stringBuilder != null;
      assert result == null;
      result = stringBuilder.toString();
      stringBuilder = null;
    }

    public String get() {
      // TODO(clement): remove that branch once StringConsumer has finished again.
      if (stringBuilder != null) {
        finished(null);
      }

      assert stringBuilder == null;
      assert result != null;
      return result;
    }
  }

  public static class AbsentKeepRuleConsumer implements KeepRuleConsumer {

    public String get() {
      return null;
    }

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      throw new Unreachable("No desugaring on high API levels");
    }

    @Override
    public void finished(DiagnosticsHandler handler) {
      throw new Unreachable("No desugaring on high API levels");
    }
  }
}
