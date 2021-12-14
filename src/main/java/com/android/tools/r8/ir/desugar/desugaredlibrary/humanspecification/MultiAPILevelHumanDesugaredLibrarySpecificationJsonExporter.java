// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.*;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.IDENTIFIER_KEY;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MultiAPILevelHumanDesugaredLibrarySpecificationJsonExporter {

  public static void export(
      MultiAPILevelHumanDesugaredLibrarySpecification specification, StringConsumer output) {
    new MultiAPILevelHumanDesugaredLibrarySpecificationJsonExporter()
        .internalExport(specification, output);
  }

  private void internalExport(
      MultiAPILevelHumanDesugaredLibrarySpecification humanSpec, StringConsumer output) {
    HashMap<String, Object> toJson = new LinkedHashMap<>();
    toJson.put(IDENTIFIER_KEY, humanSpec.getTopLevelFlags().getIdentifier());
    toJson.put(
        REQUIRED_COMPILATION_API_LEVEL_KEY,
        humanSpec.getTopLevelFlags().getRequiredCompilationAPILevel().getLevel());
    toJson.put(
        SYNTHESIZED_LIBRARY_CLASSES_PACKAGE_PREFIX_KEY,
        humanSpec.getTopLevelFlags().getSynthesizedLibraryClassesPackagePrefix());
    toJson.put(
        SUPPORT_ALL_CALLBACKS_FROM_LIBRARY_KEY,
        humanSpec.getTopLevelFlags().supportAllCallbacksFromLibrary());

    toJson.put(COMMON_FLAGS_KEY, rewritingFlagsToString(humanSpec.getCommonFlags()));
    toJson.put(PROGRAM_FLAGS_KEY, rewritingFlagsToString(humanSpec.getProgramFlags()));
    toJson.put(LIBRARY_FLAGS_KEY, rewritingFlagsToString(humanSpec.getLibraryFlags()));

    toJson.put(SHRINKER_CONFIG_KEY, humanSpec.getTopLevelFlags().getExtraKeepRules());

    Gson gson = new Gson();
    String export = gson.toJson(toJson);
    output.accept(export, new DiagnosticsHandler() {});
  }

  private List<Object> rewritingFlagsToString(
      Int2ObjectMap<HumanRewritingFlags> rewritingFlagsMap) {
    ArrayList<Object> list = new ArrayList<>();
    rewritingFlagsMap.forEach(
        (apiBelowOrEqual, flags) -> {
          HashMap<String, Object> toJson = new LinkedHashMap<>();
          toJson.put(API_LEVEL_BELOW_OR_EQUAL_KEY, apiBelowOrEqual);
          if (!flags.getRewritePrefix().isEmpty()) {
            toJson.put(REWRITE_PREFIX_KEY, new TreeMap<>(flags.getRewritePrefix()));
          }
          if (!flags.getEmulateLibraryInterface().isEmpty()) {
            toJson.put(EMULATE_INTERFACE_KEY, mapToString(flags.getEmulateLibraryInterface()));
          }
          if (!flags.getDontRewriteInvocation().isEmpty()) {
            toJson.put(DONT_REWRITE_KEY, setToString(flags.getDontRewriteInvocation()));
          }
          if (!flags.getRetargetCoreLibMember().isEmpty()) {
            toJson.put(RETARGET_LIB_MEMBER_KEY, mapToString(flags.getRetargetCoreLibMember()));
          }
          if (!flags.getDontRetargetLibMember().isEmpty()) {
            toJson.put(DONT_RETARGET_LIB_MEMBER_KEY, setToString(flags.getDontRetargetLibMember()));
          }
          if (!flags.getBackportCoreLibraryMember().isEmpty()) {
            toJson.put(BACKPORT_KEY, mapToString(flags.getBackportCoreLibraryMember()));
          }
          if (!flags.getWrapperConversions().isEmpty()) {
            toJson.put(WRAPPER_CONVERSION_KEY, setToString(flags.getWrapperConversions()));
          }
          if (!flags.getCustomConversions().isEmpty()) {
            toJson.put(CUSTOM_CONVERSION_KEY, mapToString(flags.getCustomConversions()));
          }
          list.add(toJson);
        });
    return list;
  }

  private Set<String> setToString(Set<? extends DexItem> set) {
    Set<String> stringSet = Sets.newHashSet();
    set.forEach(e -> stringSet.add(toString(e)));
    return stringSet;
  }

  private Map<String, String> mapToString(Map<? extends DexItem, ? extends DexItem> map) {
    Map<String, String> stringMap = new TreeMap<>();
    map.forEach((k, v) -> stringMap.put(toString(k), toString(v)));
    return stringMap;
  }

  private String toString(DexItem o) {
    if (o instanceof DexType) {
      return o.toString();
    }
    if (o instanceof DexMethod) {
      DexMethod method = (DexMethod) o;
      StringBuilder sb =
          new StringBuilder()
              .append(method.getReturnType())
              .append(" ")
              .append(method.getHolderType())
              .append("#")
              .append(method.getName())
              .append("(");
      for (int i = 0; i < method.getParameters().size(); i++) {
        sb.append(method.getParameter(i));
        if (i != method.getParameters().size() - 1) {
          sb.append(", ");
        }
      }
      sb.append(")");
      return sb.toString();
    }
    throw new Unreachable();
  }
}
