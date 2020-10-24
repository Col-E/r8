// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.SemanticVersion;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class DesugaredLibraryConfigurationParser {

  public static final int MAX_SUPPORTED_VERSION = 4;
  public static final SemanticVersion MIN_SUPPORTED_VERSION = new SemanticVersion(1, 0, 9);

  static final String CONFIGURATION_FORMAT_VERSION_KEY = "configuration_format_version";
  static final String VERSION_KEY = "version";
  static final String GROUP_ID_KEY = "group_id";
  static final String ARTIFACT_ID_KEY = "artifact_id";
  static final String REQUIRED_COMPILATION_API_LEVEL_KEY = "required_compilation_api_level";
  static final String SYNTHESIZED_LIBRARY_CLASSES_PACKAGE_PREFIX_KEY =
      "synthesized_library_classes_package_prefix";

  static final String COMMON_FLAGS_KEY = "common_flags";
  static final String LIBRARY_FLAGS_KEY = "library_flags";
  static final String PROGRAM_FLAGS_KEY = "program_flags";

  static final String API_LEVEL_BELOW_OR_EQUAL_KEY = "api_level_below_or_equal";
  static final String WRAPPER_CONVERSION_KEY = "wrapper_conversion";
  static final String CUSTOM_CONVERSION_KEY = "custom_conversion";
  static final String REWRITE_PREFIX_KEY = "rewrite_prefix";
  static final String RETARGET_LIB_MEMBER_KEY = "retarget_lib_member";
  static final String EMULATE_INTERFACE_KEY = "emulate_interface";
  static final String DONT_REWRITE_KEY = "dont_rewrite";
  static final String BACKPORT_KEY = "backport";
  static final String SHRINKER_CONFIG_KEY = "shrinker_config";
  static final String SUPPORT_ALL_CALLBACKS_FROM_LIBRARY_KEY = "support_all_callbacks_from_library";

  private final DexItemFactory dexItemFactory;
  private final Reporter reporter;
  private final boolean libraryCompilation;
  private final int minAPILevel;

  private DesugaredLibraryConfiguration.Builder configurationBuilder = null;
  private Origin origin;

  public DesugaredLibraryConfigurationParser(
      DexItemFactory dexItemFactory,
      Reporter reporter,
      boolean libraryCompilation,
      int minAPILevel) {
    this.dexItemFactory = dexItemFactory;
    this.reporter = reporter;
    this.minAPILevel = minAPILevel;
    this.libraryCompilation = libraryCompilation;
  }

  private JsonElement required(JsonObject json, String key) {
    if (!json.has(key)) {
      throw reporter.fatalError(
          new StringDiagnostic(
              "Invalid desugared library configuration. Expected required key '" + key + "'",
              origin));
    }
    return json.get(key);
  }

  public DesugaredLibraryConfiguration parse(StringResource stringResource) {
    return parse(stringResource, builder -> {});
  }

  public DesugaredLibraryConfiguration parse(
      StringResource stringResource,
      Consumer<DesugaredLibraryConfiguration.Builder> configurationAmender) {
    origin = stringResource.getOrigin();
    assert origin != null;
    configurationBuilder = DesugaredLibraryConfiguration.builder(dexItemFactory, reporter, origin);
    if (libraryCompilation) {
      configurationBuilder.setLibraryCompilation();
    } else {
      configurationBuilder.setProgramCompilation();
    }
    JsonObject jsonConfig;
    try {
      String jsonConfigString = stringResource.getString();
      JsonParser parser = new JsonParser();
      jsonConfig = parser.parse(jsonConfigString).getAsJsonObject();
    } catch (Exception e) {
      throw reporter.fatalError(new ExceptionDiagnostic(e, origin));
    }

    JsonElement formatVersionElement = required(jsonConfig, CONFIGURATION_FORMAT_VERSION_KEY);
    int formatVersion = formatVersionElement.getAsInt();
    if (formatVersion > MAX_SUPPORTED_VERSION) {
      throw reporter.fatalError(
          new StringDiagnostic(
              "Unsupported desugared library configuration version, please upgrade the D8/R8"
                  + " compiler.",
              origin));
    }

    String version = required(jsonConfig, VERSION_KEY).getAsString();
    SemanticVersion semanticVersion = SemanticVersion.parse(version);
    if (!semanticVersion.isNewerOrEqual(MIN_SUPPORTED_VERSION)) {
      throw reporter.fatalError(
          new StringDiagnostic(
              "Unsupported desugared library version: "
                  + version
                  + ", please upgrade the desugared library to at least version "
                  + MIN_SUPPORTED_VERSION
                  + ".",
              origin));
    }

    String groupID = required(jsonConfig, GROUP_ID_KEY).getAsString();
    String artifactID = required(jsonConfig, ARTIFACT_ID_KEY).getAsString();
    String identifier = String.join(":", groupID, artifactID, version);
    configurationBuilder.setDesugaredLibraryIdentifier(identifier);
    configurationBuilder.setSynthesizedLibraryClassesPackagePrefix(
        required(jsonConfig, SYNTHESIZED_LIBRARY_CLASSES_PACKAGE_PREFIX_KEY).getAsString());

    int required_compilation_api_level =
        required(jsonConfig, REQUIRED_COMPILATION_API_LEVEL_KEY).getAsInt();
    configurationBuilder.setRequiredCompilationAPILevel(
        AndroidApiLevel.getAndroidApiLevel(required_compilation_api_level));
    JsonElement commonFlags = required(jsonConfig, COMMON_FLAGS_KEY);
    JsonElement libraryFlags = required(jsonConfig, LIBRARY_FLAGS_KEY);
    JsonElement programFlags = required(jsonConfig, PROGRAM_FLAGS_KEY);
    parseFlagsList(commonFlags.getAsJsonArray());
    parseFlagsList(
        libraryCompilation ? libraryFlags.getAsJsonArray() : programFlags.getAsJsonArray());
    if (jsonConfig.has(SHRINKER_CONFIG_KEY)) {
      JsonArray jsonKeepRules = jsonConfig.get(SHRINKER_CONFIG_KEY).getAsJsonArray();
      List<String> extraKeepRules = new ArrayList<>(jsonKeepRules.size());
      for (JsonElement keepRule : jsonKeepRules) {
        extraKeepRules.add(keepRule.getAsString());
      }
      configurationBuilder.setExtraKeepRules(extraKeepRules);
    }

    if (jsonConfig.has(SUPPORT_ALL_CALLBACKS_FROM_LIBRARY_KEY)) {
      boolean supportAllCallbacksFromLibrary =
          jsonConfig.get(SUPPORT_ALL_CALLBACKS_FROM_LIBRARY_KEY).getAsBoolean();
      configurationBuilder.setSupportAllCallbacksFromLibrary(supportAllCallbacksFromLibrary);
    }
    configurationAmender.accept(configurationBuilder);
    DesugaredLibraryConfiguration config = configurationBuilder.build();
    configurationBuilder = null;
    origin = null;
    return config;
  }

  private void parseFlagsList(JsonArray jsonFlags) {
    for (JsonElement jsonFlagSet : jsonFlags) {
      JsonObject flag = jsonFlagSet.getAsJsonObject();
      int api_level_below_or_equal = required(flag, API_LEVEL_BELOW_OR_EQUAL_KEY).getAsInt();
      if (minAPILevel <= api_level_below_or_equal) {
        parseFlags(flag);
      }
    }
  }

  private void parseFlags(JsonObject jsonFlagSet) {
    if (jsonFlagSet.has(REWRITE_PREFIX_KEY)) {
      for (Map.Entry<String, JsonElement> rewritePrefix :
          jsonFlagSet.get(REWRITE_PREFIX_KEY).getAsJsonObject().entrySet()) {
        configurationBuilder.putRewritePrefix(
            rewritePrefix.getKey(), rewritePrefix.getValue().getAsString());
      }
    }
    if (jsonFlagSet.has(RETARGET_LIB_MEMBER_KEY)) {
      for (Map.Entry<String, JsonElement> retarget :
          jsonFlagSet.get(RETARGET_LIB_MEMBER_KEY).getAsJsonObject().entrySet()) {
        configurationBuilder.putRetargetCoreLibMember(
            retarget.getKey(), retarget.getValue().getAsString());
      }
    }
    if (jsonFlagSet.has(BACKPORT_KEY)) {
      for (Map.Entry<String, JsonElement> backport :
          jsonFlagSet.get(BACKPORT_KEY).getAsJsonObject().entrySet()) {
        configurationBuilder.putBackportCoreLibraryMember(
            backport.getKey(), backport.getValue().getAsString());
      }
    }
    if (jsonFlagSet.has(EMULATE_INTERFACE_KEY)) {
      for (Map.Entry<String, JsonElement> itf :
          jsonFlagSet.get(EMULATE_INTERFACE_KEY).getAsJsonObject().entrySet()) {
        configurationBuilder.putEmulateLibraryInterface(itf.getKey(), itf.getValue().getAsString());
      }
    }
    if (jsonFlagSet.has(CUSTOM_CONVERSION_KEY)) {
      for (Map.Entry<String, JsonElement> conversion :
          jsonFlagSet.get(CUSTOM_CONVERSION_KEY).getAsJsonObject().entrySet()) {
        configurationBuilder.putCustomConversion(
            conversion.getKey(), conversion.getValue().getAsString());
      }
    }
    if (jsonFlagSet.has(WRAPPER_CONVERSION_KEY)) {
      for (JsonElement wrapper : jsonFlagSet.get(WRAPPER_CONVERSION_KEY).getAsJsonArray()) {
        configurationBuilder.addWrapperConversion(wrapper.getAsString());
      }
    }
    if (jsonFlagSet.has(DONT_REWRITE_KEY)) {
      JsonArray dontRewrite = jsonFlagSet.get(DONT_REWRITE_KEY).getAsJsonArray();
      for (JsonElement rewrite : dontRewrite) {
        configurationBuilder.addDontRewriteInvocation(rewrite.getAsString());
      }
    }
  }
}
