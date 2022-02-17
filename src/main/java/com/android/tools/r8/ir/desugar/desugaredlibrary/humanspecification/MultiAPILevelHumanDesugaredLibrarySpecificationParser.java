// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.utils.Reporter;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import it.unimi.dsi.fastutil.ints.Int2ObjectArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

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

    Int2ObjectMap<HumanRewritingFlags> commonFlags = parseAllFlags(COMMON_FLAGS_KEY);
    Int2ObjectMap<HumanRewritingFlags> libraryFlags = parseAllFlags(LIBRARY_FLAGS_KEY);
    Int2ObjectMap<HumanRewritingFlags> programFlags = parseAllFlags(PROGRAM_FLAGS_KEY);

    return new MultiAPILevelHumanDesugaredLibrarySpecification(
        getOrigin(), topLevelFlags, commonFlags, libraryFlags, programFlags);
  }

  private Int2ObjectMap<HumanRewritingFlags> parseAllFlags(String flagKey) {
    JsonElement jsonFlags = required(getJsonConfig(), flagKey);
    Int2ObjectMap<HumanRewritingFlags> flags = new Int2ObjectArrayMap<>();
    for (JsonElement jsonFlagSet : jsonFlags.getAsJsonArray()) {
      JsonObject flag = jsonFlagSet.getAsJsonObject();
      int api_level_below_or_equal = required(flag, API_LEVEL_BELOW_OR_EQUAL_KEY).getAsInt();
      HumanRewritingFlags.Builder builder =
          flags.containsKey(api_level_below_or_equal)
              ? flags.get(api_level_below_or_equal).newBuilder(reporter(), getOrigin())
              : HumanRewritingFlags.builder(reporter(), getOrigin());
      parseFlags(flag, builder);
      flags.put(api_level_below_or_equal, builder.build());
    }
    return flags;
  }
}
