// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.ir.desugar.desugaredlibrary.ApiLevelRange;
import com.android.tools.r8.utils.Reporter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;

public class MultiAPILevelHumanDesugaredLibrarySpecificationParser
    extends HumanDesugaredLibrarySpecificationParser {

  public MultiAPILevelHumanDesugaredLibrarySpecificationParser(
      DexItemFactory dexItemFactory, Reporter reporter) {
    super(dexItemFactory, reporter, false, 1);
  }

  public MultiAPILevelHumanDesugaredLibrarySpecification parseMultiLevelConfiguration(
      StringResource stringResource) {

    String jsonConfigString = parseJson(stringResource);

    HumanTopLevelFlags topLevelFlags = parseTopLevelFlags(jsonConfigString, builder -> {});

    Map<ApiLevelRange, HumanRewritingFlags> commonFlags = parseAllFlags(COMMON_FLAGS_KEY);
    Map<ApiLevelRange, HumanRewritingFlags> libraryFlags = parseAllFlags(LIBRARY_FLAGS_KEY);
    Map<ApiLevelRange, HumanRewritingFlags> programFlags = parseAllFlags(PROGRAM_FLAGS_KEY);

    return new MultiAPILevelHumanDesugaredLibrarySpecification(
        getOrigin(), topLevelFlags, commonFlags, libraryFlags, programFlags);
  }

  private Map<ApiLevelRange, HumanRewritingFlags> parseAllFlags(String flagKey) {
    JsonElement jsonFlags = required(getJsonConfig(), flagKey);
    Map<ApiLevelRange, HumanRewritingFlags> flags = new HashMap<>();
    for (JsonElement jsonFlagSet : jsonFlags.getAsJsonArray()) {
      JsonObject flag = jsonFlagSet.getAsJsonObject();
      int apiLevelBelowOrEqual = required(flag, API_LEVEL_BELOW_OR_EQUAL_KEY).getAsInt();
      ApiLevelRange range =
          flag.has(API_LEVEL_GREATER_OR_EQUAL_KEY)
              ? new ApiLevelRange(
                  apiLevelBelowOrEqual, flag.get(API_LEVEL_GREATER_OR_EQUAL_KEY).getAsInt())
              : new ApiLevelRange(apiLevelBelowOrEqual);
      HumanRewritingFlags.Builder builder =
          flags.containsKey(range)
              ? flags.get(range).newBuilder(reporter(), getOrigin())
              : HumanRewritingFlags.builder(reporter(), getOrigin());
      parseFlags(flag, builder);
      flags.put(range, builder.build());
    }
    return flags;
  }
}
