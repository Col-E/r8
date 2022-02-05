// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class HumanDesugaredLibrarySpecificationParser {

  static final String IDENTIFIER_KEY = "identifier";
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
  static final String DONT_RETARGET_LIB_MEMBER_KEY = "dont_retarget_lib_member";
  static final String BACKPORT_KEY = "backport";
  static final String SHRINKER_CONFIG_KEY = "shrinker_config";
  static final String SUPPORT_ALL_CALLBACKS_FROM_LIBRARY_KEY = "support_all_callbacks_from_library";

  private final DexItemFactory dexItemFactory;
  private final Reporter reporter;
  private final boolean libraryCompilation;
  private final int minAPILevel;

  private Origin origin;
  private JsonObject jsonConfig;

  public HumanDesugaredLibrarySpecificationParser(
      DexItemFactory dexItemFactory,
      Reporter reporter,
      boolean libraryCompilation,
      int minAPILevel) {
    this.dexItemFactory = dexItemFactory;
    this.reporter = reporter;
    this.minAPILevel = minAPILevel;
    this.libraryCompilation = libraryCompilation;
  }

  public DexItemFactory dexItemFactory() {
    return dexItemFactory;
  }

  public Reporter reporter() {
    return reporter;
  }

  public JsonObject getJsonConfig() {
    return jsonConfig;
  }

  public Origin getOrigin() {
    assert origin != null;
    return origin;
  }

  JsonElement required(JsonObject json, String key) {
    if (!json.has(key)) {
      throw reporter.fatalError(
          new StringDiagnostic(
              "Invalid desugared library configuration. Expected required key '" + key + "'",
              origin));
    }
    return json.get(key);
  }

  public HumanDesugaredLibrarySpecification parse(StringResource stringResource) {
    return parse(stringResource, builder -> {});
  }

  public HumanDesugaredLibrarySpecification parse(
      StringResource stringResource, Consumer<HumanTopLevelFlags.Builder> topLevelFlagAmender) {
    String jsonConfigString = parseJson(stringResource);

    HumanTopLevelFlags topLevelFlags = parseTopLevelFlags(jsonConfigString, topLevelFlagAmender);

    HumanRewritingFlags legacyRewritingFlags = parseRewritingFlags();

    HumanDesugaredLibrarySpecification config =
        new HumanDesugaredLibrarySpecification(
            topLevelFlags, legacyRewritingFlags, libraryCompilation, dexItemFactory);
    origin = null;
    return config;
  }

  String parseJson(StringResource stringResource) {
    setOrigin(stringResource);
    String jsonConfigString;
    try {
      jsonConfigString = stringResource.getString();
      JsonParser parser = new JsonParser();
      jsonConfig = parser.parse(jsonConfigString).getAsJsonObject();
    } catch (Exception e) {
      throw reporter.fatalError(new ExceptionDiagnostic(e, origin));
    }
    return jsonConfigString;
  }

  void setOrigin(StringResource stringResource) {
    origin = stringResource.getOrigin();
    assert origin != null;
  }

  private HumanRewritingFlags parseRewritingFlags() {
    HumanRewritingFlags.Builder builder =
        HumanRewritingFlags.builder(dexItemFactory, reporter, origin);
    JsonElement commonFlags = required(jsonConfig, COMMON_FLAGS_KEY);
    JsonElement libraryFlags = required(jsonConfig, LIBRARY_FLAGS_KEY);
    JsonElement programFlags = required(jsonConfig, PROGRAM_FLAGS_KEY);
    parseFlagsList(commonFlags.getAsJsonArray(), builder);
    parseFlagsList(
        libraryCompilation ? libraryFlags.getAsJsonArray() : programFlags.getAsJsonArray(),
        builder);
    return builder.build();
  }

  HumanTopLevelFlags parseTopLevelFlags(
      String jsonConfigString, Consumer<HumanTopLevelFlags.Builder> topLevelFlagAmender) {
    HumanTopLevelFlags.Builder builder = HumanTopLevelFlags.builder();

    builder.setJsonSource(jsonConfigString);

    String identifier = required(jsonConfig, IDENTIFIER_KEY).getAsString();
    builder.setDesugaredLibraryIdentifier(identifier);
    builder.setSynthesizedLibraryClassesPackagePrefix(
        required(jsonConfig, SYNTHESIZED_LIBRARY_CLASSES_PACKAGE_PREFIX_KEY).getAsString());

    int required_compilation_api_level =
        required(jsonConfig, REQUIRED_COMPILATION_API_LEVEL_KEY).getAsInt();
    builder.setRequiredCompilationAPILevel(
        AndroidApiLevel.getAndroidApiLevel(required_compilation_api_level));
    if (jsonConfig.has(SHRINKER_CONFIG_KEY)) {
      JsonArray jsonKeepRules = jsonConfig.get(SHRINKER_CONFIG_KEY).getAsJsonArray();
      List<String> extraKeepRules = new ArrayList<>(jsonKeepRules.size());
      for (JsonElement keepRule : jsonKeepRules) {
        extraKeepRules.add(keepRule.getAsString());
      }
      builder.setExtraKeepRules(extraKeepRules);
    }

    if (jsonConfig.has(SUPPORT_ALL_CALLBACKS_FROM_LIBRARY_KEY)) {
      boolean supportAllCallbacksFromLibrary =
          jsonConfig.get(SUPPORT_ALL_CALLBACKS_FROM_LIBRARY_KEY).getAsBoolean();
      builder.setSupportAllCallbacksFromLibrary(supportAllCallbacksFromLibrary);
    }

    topLevelFlagAmender.accept(builder);

    return builder.build();
  }

  private void parseFlagsList(JsonArray jsonFlags, HumanRewritingFlags.Builder builder) {
    for (JsonElement jsonFlagSet : jsonFlags) {
      JsonObject flag = jsonFlagSet.getAsJsonObject();
      int api_level_below_or_equal = required(flag, API_LEVEL_BELOW_OR_EQUAL_KEY).getAsInt();
      if (minAPILevel <= api_level_below_or_equal) {
        parseFlags(flag, builder);
      }
    }
  }

  void parseFlags(JsonObject jsonFlagSet, HumanRewritingFlags.Builder builder) {
    if (jsonFlagSet.has(REWRITE_PREFIX_KEY)) {
      for (Map.Entry<String, JsonElement> rewritePrefix :
          jsonFlagSet.get(REWRITE_PREFIX_KEY).getAsJsonObject().entrySet()) {
        builder.putRewritePrefix(rewritePrefix.getKey(), rewritePrefix.getValue().getAsString());
      }
    }
    if (jsonFlagSet.has(RETARGET_LIB_MEMBER_KEY)) {
      for (Map.Entry<String, JsonElement> retarget :
          jsonFlagSet.get(RETARGET_LIB_MEMBER_KEY).getAsJsonObject().entrySet()) {
        builder.putRetargetCoreLibMember(retarget.getKey(), retarget.getValue().getAsString());
      }
    }
    if (jsonFlagSet.has(BACKPORT_KEY)) {
      for (Map.Entry<String, JsonElement> backport :
          jsonFlagSet.get(BACKPORT_KEY).getAsJsonObject().entrySet()) {
        builder.putBackportCoreLibraryMember(backport.getKey(), backport.getValue().getAsString());
      }
    }
    if (jsonFlagSet.has(EMULATE_INTERFACE_KEY)) {
      for (Map.Entry<String, JsonElement> itf :
          jsonFlagSet.get(EMULATE_INTERFACE_KEY).getAsJsonObject().entrySet()) {
        builder.putEmulateLibraryInterface(itf.getKey(), itf.getValue().getAsString());
      }
    }
    if (jsonFlagSet.has(CUSTOM_CONVERSION_KEY)) {
      for (Map.Entry<String, JsonElement> conversion :
          jsonFlagSet.get(CUSTOM_CONVERSION_KEY).getAsJsonObject().entrySet()) {
        builder.putCustomConversion(conversion.getKey(), conversion.getValue().getAsString());
      }
    }
    if (jsonFlagSet.has(WRAPPER_CONVERSION_KEY)) {
      for (JsonElement wrapper : jsonFlagSet.get(WRAPPER_CONVERSION_KEY).getAsJsonArray()) {
        builder.addWrapperConversion(wrapper.getAsString());
      }
    }
    if (jsonFlagSet.has(DONT_REWRITE_KEY)) {
      JsonArray dontRewrite = jsonFlagSet.get(DONT_REWRITE_KEY).getAsJsonArray();
      for (JsonElement rewrite : dontRewrite) {
        builder.addDontRewriteInvocation(rewrite.getAsString());
      }
    }
    if (jsonFlagSet.has(DONT_RETARGET_LIB_MEMBER_KEY)) {
      JsonArray dontRetarget = jsonFlagSet.get(DONT_RETARGET_LIB_MEMBER_KEY).getAsJsonArray();
      for (JsonElement rewrite : dontRetarget) {
        builder.addDontRetargetLibMember(rewrite.getAsString());
      }
    }
  }
}
