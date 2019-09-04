// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.Map;

public class DesugaredLibraryConfigurationParser {

  private static final int MAX_SUPPORTED_VERSION = 1;

  private final DesugaredLibraryConfiguration.Builder configurationBuilder;
  private final Reporter reporter;
  private final boolean libraryCompilation;
  private final int minAPILevel;

  public DesugaredLibraryConfigurationParser(
      DexItemFactory dexItemFactory,
      Reporter reporter,
      boolean libraryCompilation,
      int minAPILevel) {
    this.reporter = reporter;
    this.configurationBuilder = DesugaredLibraryConfiguration.builder(dexItemFactory, reporter);
    this.minAPILevel = minAPILevel;
    this.libraryCompilation = libraryCompilation;
    if (libraryCompilation) {
      configurationBuilder.setLibraryCompilation();
    } else {
      configurationBuilder.setProgramCompilation();
    }
  }

  public DesugaredLibraryConfiguration parse(StringResource stringResource) {
    String jsonConfigString;
    try {
      jsonConfigString = stringResource.getString();
    } catch (Exception e) {
      throw reporter.fatalError(
          new StringDiagnostic("Cannot access desugared library configuration."));
    }
    JsonParser parser = new JsonParser();
    JsonObject jsonConfig = parser.parse(jsonConfigString).getAsJsonObject();

    int version = jsonConfig.get("configuration_format_version").getAsInt();
    if (version > MAX_SUPPORTED_VERSION) {
      throw reporter.fatalError(
          new StringDiagnostic(
              "Unsupported desugared library configuration version, please upgrade the D8/R8"
                  + " compiler."));
    }
    JsonArray jsonFlags =
        libraryCompilation
            ? jsonConfig.getAsJsonArray("library_flags")
            : jsonConfig.getAsJsonArray("program_flags");
    for (JsonElement jsonFlagSet : jsonFlags) {
      int api_level_below_or_equal =
          jsonFlagSet.getAsJsonObject().get("api_level_below_or_equal").getAsInt();
      if (minAPILevel > api_level_below_or_equal) {
        continue;
      }
      parseFlags(jsonFlagSet.getAsJsonObject());
    }
    return configurationBuilder.build();
  }

  private void parseFlags(JsonObject jsonFlagSet) {
    if (jsonFlagSet.has("rewrite_prefix")) {
      for (Map.Entry<String, JsonElement> rewritePrefix :
          jsonFlagSet.get("rewrite_prefix").getAsJsonObject().entrySet()) {
        configurationBuilder.putRewritePrefix(
            rewritePrefix.getKey(), rewritePrefix.getValue().getAsString());
      }
    }
    if (jsonFlagSet.has("retarget_lib_member")) {
      for (Map.Entry<String, JsonElement> retarget :
          jsonFlagSet.get("retarget_lib_member").getAsJsonObject().entrySet()) {
        configurationBuilder.putRetargetCoreLibMember(
            retarget.getKey(), retarget.getValue().getAsString());
      }
    }
    if (jsonFlagSet.has("backport")) {
      for (Map.Entry<String, JsonElement> backport :
          jsonFlagSet.get("backport").getAsJsonObject().entrySet()) {
        configurationBuilder.putBackportCoreLibraryMember(
            backport.getKey(), backport.getValue().getAsString());
      }
    }
    if (jsonFlagSet.has("emulate_interface")) {
      for (Map.Entry<String, JsonElement> itf :
          jsonFlagSet.get("emulate_interface").getAsJsonObject().entrySet()) {
        configurationBuilder.putEmulateLibraryInterface(itf.getKey(), itf.getValue().getAsString());
      }
    }
    if (jsonFlagSet.has("dont_rewrite")) {
      JsonArray dontRewrite = jsonFlagSet.get("dont_rewrite").getAsJsonArray();
      for (JsonElement rewrite : dontRewrite) {
        configurationBuilder.addDontRewriteInvocation(rewrite.getAsString());
      }
    }
  }
}
