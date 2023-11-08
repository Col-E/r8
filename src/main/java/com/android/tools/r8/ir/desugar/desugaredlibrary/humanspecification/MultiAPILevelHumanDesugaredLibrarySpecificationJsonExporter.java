// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser.CONFIGURATION_FORMAT_VERSION_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.AMEND_LIBRARY_METHOD_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.API_GENERIC_TYPES_CONVERSION;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.API_LEVEL_BELOW_OR_EQUAL_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.API_LEVEL_GREATER_OR_EQUAL_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.BACKPORT_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.COMMON_FLAGS_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.CURRENT_HUMAN_CONFIGURATION_FORMAT_VERSION;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.CUSTOM_CONVERSION_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.DONT_RETARGET_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.DONT_REWRITE_PREFIX_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.EMULATED_METHODS_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.EMULATE_INTERFACE_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.IDENTIFIER_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.LIBRARY_FLAGS_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.MAINTAIN_PREFIX_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.NEVER_OUTLINE_API_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.PROGRAM_FLAGS_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.REQUIRED_COMPILATION_API_LEVEL_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.RETARGET_METHOD_EMULATED_DISPATCH_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.RETARGET_METHOD_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.RETARGET_STATIC_FIELD_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.REWRITE_DERIVED_PREFIX_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.REWRITE_PREFIX_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.REWRITTEN_TYPE_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.SHRINKER_CONFIG_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.SUPPORT_ALL_CALLBACKS_FROM_LIBRARY_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.SYNTHESIZED_LIBRARY_CLASSES_PACKAGE_PREFIX_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.WRAPPER_CONVERSION_EXCLUDING_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecificationParser.WRAPPER_CONVERSION_KEY;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.ApiLevelRange;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collections;
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
    toJson.put(CONFIGURATION_FORMAT_VERSION_KEY, CURRENT_HUMAN_CONFIGURATION_FORMAT_VERSION);
    toJson.put(
        REQUIRED_COMPILATION_API_LEVEL_KEY,
        humanSpec.getTopLevelFlags().getRequiredCompilationAPILevel().getLevel());
    toJson.put(
        SYNTHESIZED_LIBRARY_CLASSES_PACKAGE_PREFIX_KEY,
        humanSpec.getTopLevelFlags().getSynthesizedLibraryClassesPackagePrefix().replace('/', '.'));
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
      Map<ApiLevelRange, HumanRewritingFlags> rewritingFlagsMap) {
    ArrayList<Object> list = new ArrayList<>();
    ArrayList<ApiLevelRange> apis = new ArrayList<>(rewritingFlagsMap.keySet());
    apis.sort((x, y) -> -x.deterministicOrder(y));
    for (ApiLevelRange range : apis) {
      HumanRewritingFlags flags = rewritingFlagsMap.get(range);
      HashMap<String, Object> toJson = new LinkedHashMap<>();
      toJson.put(API_LEVEL_BELOW_OR_EQUAL_KEY, range.getApiLevelBelowOrEqualAsInt());
      if (range.hasApiLevelGreaterOrEqual()) {
        toJson.put(API_LEVEL_GREATER_OR_EQUAL_KEY, range.getApiLevelGreaterOrEqualAsInt());
      }
      if (!flags.getRewritePrefix().isEmpty()) {
        toJson.put(REWRITE_PREFIX_KEY, new TreeMap<>(flags.getRewritePrefix()));
      }
      if (!flags.getRewriteDerivedPrefix().isEmpty()) {
        TreeMap<String, Map<String, String>> rewriteDerivedPrefix = new TreeMap<>();
        flags
            .getRewriteDerivedPrefix()
            .forEach((k, v) -> rewriteDerivedPrefix.put(k, new TreeMap<>(v)));
        toJson.put(REWRITE_DERIVED_PREFIX_KEY, rewriteDerivedPrefix);
      }
      if (!flags.getDontRewritePrefix().isEmpty()) {
        toJson.put(DONT_REWRITE_PREFIX_KEY, stringSetToString(flags.getDontRewritePrefix()));
      }
      if (!flags.getMaintainPrefix().isEmpty()) {
        toJson.put(MAINTAIN_PREFIX_KEY, stringSetToString(flags.getMaintainPrefix()));
      }
      if (!flags.getEmulatedInterfaces().isEmpty()) {
        TreeMap<String, Map<String, Object>> emulatedInterfaces = new TreeMap<>();
        flags
            .getEmulatedInterfaces()
            .forEach(
                (itf, descriptor) -> {
                  TreeMap<String, Object> value = new TreeMap<>();
                  assert !descriptor.isLegacy();
                  emulatedInterfaces.put(toString(itf), value);
                  value.put(REWRITTEN_TYPE_KEY, toString(descriptor.getRewrittenType()));
                  value.put(EMULATED_METHODS_KEY, setToString(descriptor.getEmulatedMethods()));
                });
        toJson.put(EMULATE_INTERFACE_KEY, emulatedInterfaces);
      }
      if (!flags.getRetargetStaticField().isEmpty()) {
        toJson.put(RETARGET_STATIC_FIELD_KEY, mapToString(flags.getRetargetStaticField()));
      }
      if (!flags.getRetargetMethodToType().isEmpty()) {
        toJson.put(RETARGET_METHOD_KEY, mapToString(flags.getRetargetMethodToType()));
      }
      if (!flags.getRetargetMethodEmulatedDispatchToMethod().isEmpty()) {
        toJson.put(
            RETARGET_METHOD_KEY, mapToString(flags.getRetargetMethodEmulatedDispatchToMethod()));
      }
      if (!flags.getRetargetMethodEmulatedDispatchToType().isEmpty()) {
        toJson.put(
            RETARGET_METHOD_EMULATED_DISPATCH_KEY,
            mapToString(flags.getRetargetMethodEmulatedDispatchToType()));
      }
      if (!flags.getRetargetMethodEmulatedDispatchToMethod().isEmpty()) {
        toJson.put(
            RETARGET_METHOD_EMULATED_DISPATCH_KEY,
            mapToString(flags.getRetargetMethodEmulatedDispatchToMethod()));
      }
      if (!flags.getDontRetarget().isEmpty()) {
        toJson.put(DONT_RETARGET_KEY, setToString(flags.getDontRetarget()));
      }
      if (!flags.getLegacyBackport().isEmpty()) {
        toJson.put(BACKPORT_KEY, mapToString(flags.getLegacyBackport()));
      }
      if (!flags.getApiGenericConversion().isEmpty()) {
        toJson.put(API_GENERIC_TYPES_CONVERSION, mapArrayToString(flags.getApiGenericConversion()));
      }
      if (!flags.getWrapperConversions().isEmpty()) {
        registerWrapperConversions(toJson, flags.getWrapperConversions());
      }
      if (!flags.getCustomConversions().isEmpty()) {
        toJson.put(CUSTOM_CONVERSION_KEY, mapToString(flags.getCustomConversions()));
      }
      if (!flags.getNeverOutlineApi().isEmpty()) {
        toJson.put(NEVER_OUTLINE_API_KEY, setToString(flags.getNeverOutlineApi()));
      }
      if (!flags.getAmendLibraryMethod().isEmpty()) {
        toJson.put(AMEND_LIBRARY_METHOD_KEY, amendLibraryToString(flags.getAmendLibraryMethod()));
      }
      if (!flags.getAmendLibraryField().isEmpty()) {
        toJson.put(AMEND_LIBRARY_METHOD_KEY, amendLibraryToString(flags.getAmendLibraryField()));
      }
      list.add(toJson);
    }
    return list;
  }

  private void registerWrapperConversions(
      Map<String, Object> toJson, Map<DexType, Set<DexMethod>> wrapperConversions) {
    List<String> stringSet = new ArrayList<>();
    Map<String, List<String>> stringMap = new TreeMap<>();
    wrapperConversions.forEach(
        (k, v) -> {
          if (v.isEmpty()) {
            stringSet.add(toString(k));
          } else {
            stringMap.put(toString(k), setToString(v));
          }
        });
    if (!stringSet.isEmpty()) {
      toJson.put(WRAPPER_CONVERSION_KEY, stringSet);
    }
    if (!stringMap.isEmpty()) {
      toJson.put(WRAPPER_CONVERSION_EXCLUDING_KEY, stringMap);
    }
  }

  private List<String> amendLibraryToString(
      Map<? extends DexItem, ? extends AccessFlags<?>> amendLibraryMembers) {
    List<String> stringSet = new ArrayList<>();
    amendLibraryMembers.forEach(
        (member, flags) -> stringSet.add(flags.toString() + " " + toString(member)));
    return stringSet;
  }

  private List<String> stringSetToString(Set<String> set) {
    List<String> stringSet = new ArrayList<>(set);
    Collections.sort(stringSet);
    return stringSet;
  }

  private List<String> setToString(Set<? extends DexItem> set) {
    List<String> stringSet = new ArrayList<>();
    set.forEach(e -> stringSet.add(toString(e)));
    Collections.sort(stringSet);
    return stringSet;
  }

  private Map<String, String> mapToString(Map<? extends DexItem, ? extends DexItem> map) {
    Map<String, String> stringMap = new TreeMap<>();
    map.forEach((k, v) -> stringMap.put(toString(k), toString(v)));
    return stringMap;
  }

  private Map<String, Object[]> mapArrayToString(Map<? extends DexItem, DexMethod[]> map) {
    Map<String, Object[]> stringMap = new TreeMap<>();
    map.forEach((k, v) -> stringMap.put(toString(k), arrayToString(v)));
    return stringMap;
  }

  private Object[] arrayToString(DexMethod[] array) {
    List<Object> res = new ArrayList<>();
    int last = array.length - 1;
    if (array[last] != null) {
      res.add(-1);
      res.add(toString(array[last]));
    }
    for (int i = 0; i < array.length - 1; i++) {
      if (array[i] != null) {
        res.add(i);
        res.add(toString(array[i]));
      }
    }
    return res.toArray();
  }

  private String toString(DexItem o) {
    if (o instanceof DexType) {
      return o.toString();
    }
    if (o instanceof DexField) {
      DexField field = (DexField) o;
      return field.getType() + " " + field.getHolderType() + "#" + field.getName();
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
