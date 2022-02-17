// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecificationParser;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.function.Consumer;

public class DesugaredLibrarySpecificationParser {

  public static final String CONFIGURATION_FORMAT_VERSION_KEY = "configuration_format_version";
  private static final int MIN_HUMAN_CONFIGURATION_FORMAT_VERSION = 100;

  public static DesugaredLibrarySpecification parseDesugaredLibrarySpecification(
      StringResource stringResource,
      DexItemFactory dexItemFactory,
      Reporter reporter,
      boolean libraryCompilation,
      int minAPILevel) {
    Origin origin = stringResource.getOrigin();
    assert origin != null;
    String jsonConfigString;
    JsonObject jsonConfig;
    try {
      jsonConfigString = stringResource.getString();
      JsonParser parser = new JsonParser();
      jsonConfig = parser.parse(jsonConfigString).getAsJsonObject();
    } catch (Exception e) {
      throw reporter.fatalError(new ExceptionDiagnostic(e, origin));
    }

    if (isHumanSpecification(jsonConfig, reporter, origin)) {
      return new HumanDesugaredLibrarySpecificationParser(
              dexItemFactory, reporter, libraryCompilation, minAPILevel)
          .parse(origin, jsonConfigString, jsonConfig);
    }
    return new LegacyDesugaredLibrarySpecificationParser(
            dexItemFactory, reporter, libraryCompilation, minAPILevel)
        .parse(origin, jsonConfigString, jsonConfig);
  }

  public static DesugaredLibrarySpecification parseDesugaredLibrarySpecificationforTesting(
      StringResource stringResource,
      DexItemFactory dexItemFactory,
      Reporter reporter,
      boolean libraryCompilation,
      int minAPILevel,
      Consumer<TopLevelFlagsBuilder<?>> topLevelFlagsAmender) {
    Origin origin = stringResource.getOrigin();
    assert origin != null;
    String jsonConfigString;
    JsonObject jsonConfig;
    try {
      jsonConfigString = stringResource.getString();
      JsonParser parser = new JsonParser();
      jsonConfig = parser.parse(jsonConfigString).getAsJsonObject();
    } catch (Exception e) {
      throw reporter.fatalError(new ExceptionDiagnostic(e, origin));
    }
    if (isHumanSpecification(jsonConfig, reporter, origin)) {
      return new HumanDesugaredLibrarySpecificationParser(
              dexItemFactory, reporter, libraryCompilation, minAPILevel)
          .parse(origin, jsonConfigString, jsonConfig, topLevelFlagsAmender);
    }
    return new LegacyDesugaredLibrarySpecificationParser(
            dexItemFactory, reporter, libraryCompilation, minAPILevel)
        .parse(origin, jsonConfigString, jsonConfig, topLevelFlagsAmender);
  }

  public static boolean isHumanSpecification(
      JsonObject jsonConfig, Reporter reporter, Origin origin) {
    if (!jsonConfig.has(CONFIGURATION_FORMAT_VERSION_KEY)) {
      throw reporter.fatalError(
          new StringDiagnostic(
              "Invalid desugared library configuration. Expected required key '"
                  + CONFIGURATION_FORMAT_VERSION_KEY
                  + "'",
              origin));
    }

    return jsonConfig.get(CONFIGURATION_FORMAT_VERSION_KEY).getAsInt()
        >= MIN_HUMAN_CONFIGURATION_FORMAT_VERSION;
  }
}
