// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecificationParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineDesugaredLibrarySpecificationParser;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.function.Consumer;

public class DesugaredLibrarySpecificationParser {

  public static final String CONFIGURATION_FORMAT_VERSION_KEY = "configuration_format_version";
  private static final int MIN_HUMAN_CONFIGURATION_FORMAT_VERSION = 100;
  private static final int MIN_MACHINE_CONFIGURATION_FORMAT_VERSION = 200;

  public static DesugaredLibrarySpecification parseDesugaredLibrarySpecification(
      StringResource stringResource,
      DexItemFactory dexItemFactory,
      Reporter reporter,
      boolean libraryCompilation,
      int minAPILevel) {
    return parseDesugaredLibrarySpecificationforTesting(
        stringResource, dexItemFactory, reporter, libraryCompilation, minAPILevel, flags -> {});
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
    // Machine Specification is the shippable format released in Maven. D8/R8 has to be *very*
    // backward compatible to any machine specification, and raise proper error messages for
    // compatibility issues. The format is also exhaustive (Very limited pattern matching, if any).
    // It can hardly be written by hand and is always generated.
    if (isMachineSpecification(jsonConfig, reporter, origin)) {
      return new MachineDesugaredLibrarySpecificationParser(
              dexItemFactory, reporter, libraryCompilation, minAPILevel, new SyntheticNaming())
          .parse(origin, jsonConfigString, jsonConfig);
    }
    // Human Specification is the easy to write format for developers and allows one to widely use
    // pattern matching. This format is mainly used for development and to generate the machine
    // specification. D8/R8 is *not* backward compatible with any previous version of human
    // specification, which is therefore not suited to be shipped for external users. It can be
    // shipped to internal users where we can easily update the D8/R8 compiler and the
    // desugared library specification at the same time.
    if (isHumanSpecification(jsonConfig, reporter, origin)) {
      return new HumanDesugaredLibrarySpecificationParser(
              dexItemFactory, reporter, libraryCompilation, minAPILevel)
          .parse(origin, jsonConfigString, jsonConfig, topLevelFlagsAmender);
    }
    // Legacy specification is the legacy format, as was shipped desugared library JDK8.
    // Hopefully the day will come where this format is no longer supported, and the other formats
    // shall always be preferred+.
    return new LegacyDesugaredLibrarySpecificationParser(
            dexItemFactory, reporter, libraryCompilation, minAPILevel)
        .parse(origin, jsonConfigString, jsonConfig, topLevelFlagsAmender);
  }

  public static boolean isMachineSpecification(
      JsonObject jsonConfig, Reporter reporter, Origin origin) {
    ensureConfigurationFormatVersion(jsonConfig, reporter, origin);

    int formatVersion = jsonConfig.get(CONFIGURATION_FORMAT_VERSION_KEY).getAsInt();
    return formatVersion >= MIN_MACHINE_CONFIGURATION_FORMAT_VERSION;
  }

  public static boolean isHumanSpecification(
      JsonObject jsonConfig, Reporter reporter, Origin origin) {
    ensureConfigurationFormatVersion(jsonConfig, reporter, origin);

    int formatVersion = jsonConfig.get(CONFIGURATION_FORMAT_VERSION_KEY).getAsInt();
    return formatVersion >= MIN_HUMAN_CONFIGURATION_FORMAT_VERSION
        && formatVersion < MIN_MACHINE_CONFIGURATION_FORMAT_VERSION;
  }

  private static void ensureConfigurationFormatVersion(
      JsonObject jsonConfig, Reporter reporter, Origin origin) {
    if (!jsonConfig.has(CONFIGURATION_FORMAT_VERSION_KEY)) {
      throw reporter.fatalError(
          new StringDiagnostic(
              "Invalid desugared library configuration. Expected required key '"
                  + CONFIGURATION_FORMAT_VERSION_KEY
                  + "'",
              origin));
    }
  }
}
