// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import static com.android.tools.r8.LibraryDesugaringTestConfiguration.Configuration.DEFAULT;
import static junit.framework.TestCase.assertNotNull;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.TestBase.Backend;
import com.android.tools.r8.desugar.desugaredlibrary.DesugaredLibraryTestBase.KeepRuleConsumer;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class LibraryDesugaringTestConfiguration {

  private static final String RELEASES_DIR = "third_party/openjdk/desugar_jdk_libs_releases/";

  public enum Configuration {
    DEFAULT(
        ToolHelper.getDesugarJDKLibs(),
        ToolHelper.DESUGAR_LIB_CONVERSIONS,
        ToolHelper.getDesugarLibJsonForTesting()),
    DEFAULT_JDK11(
        Paths.get("third_party/openjdk/desugar_jdk_libs_11/desugar_jdk_libs.jar"),
        ToolHelper.DESUGAR_LIB_CONVERSIONS,
        Paths.get("src/library_desugar/jdk11/desugar_jdk_libs.json")),
    RELEASED_1_0_9("1.0.9"),
    RELEASED_1_0_10("1.0.10"),
    RELEASED_1_1_0("1.1.0"),
    RELEASED_1_1_1("1.1.1"),
    RELEASED_1_1_5("1.1.5");

    private final Path desugarJdkLibs;
    private final Path customConversions;
    private final Path configuration;

    Configuration(Path desugarJdkLibs, Path customConversions, Path configuration) {
      this.desugarJdkLibs = desugarJdkLibs;
      this.customConversions = customConversions;
      this.configuration = configuration;
    }

    Configuration(String version) {
      this(
          Paths.get(RELEASES_DIR, version, "desugar_jdk_libs.jar"),
          Paths.get(RELEASES_DIR, version, "desugar_jdk_libs_configuration.jar"),
          Paths.get(RELEASES_DIR, version, "desugar.json"));
    }

    public static List<Configuration> getReleased() {
      return ImmutableList.of(
          RELEASED_1_0_9, RELEASED_1_0_10, RELEASED_1_1_0, RELEASED_1_1_1, RELEASED_1_1_5);
    }
  }

  private final AndroidApiLevel minApiLevel;
  private final Path desugarJdkLibs;
  private final Path customConversions;
  private final List<StringResource> desugaredLibraryConfigurationResources;
  private final boolean withKeepRuleConsumer;
  private final KeepRuleConsumer keepRuleConsumer;
  private final CompilationMode mode;
  private final boolean addRunClassPath;

  public static final LibraryDesugaringTestConfiguration DISABLED =
      new LibraryDesugaringTestConfiguration();

  private LibraryDesugaringTestConfiguration() {
    this.minApiLevel = null;
    this.desugarJdkLibs = null;
    this.customConversions = null;
    this.keepRuleConsumer = null;
    this.withKeepRuleConsumer = false;
    this.desugaredLibraryConfigurationResources = null;
    this.mode = null;
    this.addRunClassPath = false;
  }

  private LibraryDesugaringTestConfiguration(
      AndroidApiLevel minApiLevel,
      Path desugarJdkLibs,
      Path customConversions,
      List<StringResource> desugaredLibraryConfigurationResources,
      boolean withKeepRuleConsumer,
      KeepRuleConsumer keepRuleConsumer,
      CompilationMode mode,
      boolean addRunClassPath) {
    this.minApiLevel = minApiLevel;
    this.desugarJdkLibs = desugarJdkLibs;
    this.customConversions = customConversions;
    this.desugaredLibraryConfigurationResources = desugaredLibraryConfigurationResources;
    this.withKeepRuleConsumer = withKeepRuleConsumer;
    this.keepRuleConsumer = keepRuleConsumer;
    this.mode = mode;
    this.addRunClassPath = addRunClassPath;
  }

  public static class Builder {

    AndroidApiLevel minApiLevel;
    private Path desugarJdkLibs;
    private Path customConversions;
    private final List<StringResource> desugaredLibraryConfigurationResources = new ArrayList<>();
    boolean withKeepRuleConsumer = false;
    KeepRuleConsumer keepRuleConsumer;
    private CompilationMode mode = CompilationMode.DEBUG;
    boolean addRunClassPath = true;

    private Builder() {}

    public Builder setMinApi(AndroidApiLevel minApiLevel) {
      this.minApiLevel = minApiLevel;
      return this;
    }

    public Builder setConfiguration(Configuration configuration) {
      desugarJdkLibs = configuration.desugarJdkLibs;
      customConversions = configuration.customConversions;
      desugaredLibraryConfigurationResources.clear();
      desugaredLibraryConfigurationResources.add(
          StringResource.fromFile(configuration.configuration));
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
          desugarJdkLibs != null ? desugarJdkLibs : DEFAULT.desugarJdkLibs,
          customConversions != null ? customConversions : DEFAULT.customConversions,
          desugaredLibraryConfigurationResources,
          withKeepRuleConsumer,
          keepRuleConsumer,
          mode,
          addRunClassPath);
    }
  }

  public static Builder builder() {
    return new Builder();
  }

  public static LibraryDesugaringTestConfiguration forApiLevel(AndroidApiLevel apiLevel) {
    return LibraryDesugaringTestConfiguration.builder().setMinApi(apiLevel).build();
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
      assert desugaredLibraryConfigurationResources.size() == 1 : "There can be only one";
      return L8TestBuilder.create(minApiLevel, Backend.DEX, state)
          .addLibraryFiles(ToolHelper.getAndroidJar(AndroidApiLevel.P))
          .setDesugarJDKLibs(desugarJdkLibs)
          .setDesugarJDKLibsConfiguration(customConversions)
          .setDesugaredLibraryConfiguration(desugaredLibraryConfigurationResources.get(0))
          .applyIf(
              mode == CompilationMode.RELEASE,
              builder -> {
                if (finalGeneratedKeepRules != null && !finalGeneratedKeepRules.trim().isEmpty()) {
                  builder.addGeneratedKeepRules(finalGeneratedKeepRules);
                }
              },
              L8TestBuilder::setDebug)
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
