// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser.CONFIGURATION_FORMAT_VERSION_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser.isHumanSpecification;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.TopLevelFlagsBuilder;
import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanRewritingFlags.HumanEmulatedInterfaceDescriptor;
import com.android.tools.r8.ir.desugar.desugaredlibrary.memberparser.HumanFieldParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.memberparser.HumanMethodParser;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;

public class HumanDesugaredLibrarySpecificationParser {

  public static final int CURRENT_HUMAN_CONFIGURATION_FORMAT_VERSION = 101;

  static final String IDENTIFIER_KEY = "identifier";
  static final String REQUIRED_COMPILATION_API_LEVEL_KEY = "required_compilation_api_level";
  static final String SYNTHESIZED_LIBRARY_CLASSES_PACKAGE_PREFIX_KEY =
      "synthesized_library_classes_package_prefix";

  static final String COMMON_FLAGS_KEY = "common_flags";
  static final String LIBRARY_FLAGS_KEY = "library_flags";
  static final String PROGRAM_FLAGS_KEY = "program_flags";

  static final String API_LEVEL_BELOW_OR_EQUAL_KEY = "api_level_below_or_equal";
  static final String API_LEVEL_GREATER_OR_EQUAL_KEY = "api_level_greater_or_equal";
  static final String API_GENERIC_TYPES_CONVERSION = "api_generic_types_conversion";
  static final String REWRITTEN_TYPE_KEY = "rewrittenType";
  static final String EMULATED_METHODS_KEY = "emulatedMethods";
  static final String WRAPPER_CONVERSION_KEY = "wrapper_conversion";
  static final String WRAPPER_CONVERSION_EXCLUDING_KEY = "wrapper_conversion_excluding";
  static final String CUSTOM_CONVERSION_KEY = "custom_conversion";
  static final String REWRITE_PREFIX_KEY = "rewrite_prefix";
  static final String DONT_REWRITE_PREFIX_KEY = "dont_rewrite_prefix";
  static final String MAINTAIN_PREFIX_KEY = "maintain_prefix";
  static final String RETARGET_STATIC_FIELD_KEY = "retarget_static_field";
  static final String NEVER_OUTLINE_API_KEY = "never_outline_api";
  static final String COVARIANT_RETARGET_METHOD_KEY = "covariant_retarget_method";
  static final String RETARGET_METHOD_KEY = "retarget_method";
  static final String RETARGET_METHOD_EMULATED_DISPATCH_KEY =
      "retarget_method_with_emulated_dispatch";
  static final String REWRITE_DERIVED_PREFIX_KEY = "rewrite_derived_prefix";
  static final String EMULATE_INTERFACE_KEY = "emulate_interface";
  static final String DONT_RETARGET_KEY = "dont_retarget";
  static final String BACKPORT_KEY = "backport";
  static final String AMEND_LIBRARY_METHOD_KEY = "amend_library_method";
  static final String AMEND_LIBRARY_FIELD_KEY = "amend_library_field";
  static final String SHRINKER_CONFIG_KEY = "shrinker_config";
  static final String SUPPORT_ALL_CALLBACKS_FROM_LIBRARY_KEY = "support_all_callbacks_from_library";

  private final DexItemFactory dexItemFactory;
  private final HumanMethodParser methodParser;
  private final HumanFieldParser fieldParser;
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
    this.methodParser = new HumanMethodParser(dexItemFactory);
    this.fieldParser = new HumanFieldParser(dexItemFactory);
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
    String jsonConfigString = parseJson(stringResource);
    return parse(origin, jsonConfigString, jsonConfig, ignored -> {});
  }

  public HumanDesugaredLibrarySpecification parse(
      Origin origin, String jsonConfigString, JsonObject jsonConfig) {
    return parse(origin, jsonConfigString, jsonConfig, ignored -> {});
  }

  public HumanDesugaredLibrarySpecification parse(
      Origin origin,
      String jsonConfigString,
      JsonObject jsonConfig,
      Consumer<TopLevelFlagsBuilder<?>> topLevelFlagAmender) {
    if (!isHumanSpecification(jsonConfig, reporter, origin)) {
      reporter.error(
          "Attempt to parse a non desugared library human specification as a human specification.");
    }
    this.origin = origin;
    this.jsonConfig = jsonConfig;
    HumanTopLevelFlags topLevelFlags = parseTopLevelFlags(jsonConfigString, topLevelFlagAmender);

    HumanRewritingFlags legacyRewritingFlags = parseRewritingFlags();

    HumanDesugaredLibrarySpecification config =
        new HumanDesugaredLibrarySpecification(
            topLevelFlags, legacyRewritingFlags, libraryCompilation);
    this.origin = null;
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
    HumanRewritingFlags.Builder builder = HumanRewritingFlags.builder(reporter, origin);
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
      String jsonConfigString, Consumer<TopLevelFlagsBuilder<?>> topLevelFlagAmender) {
    HumanTopLevelFlags.Builder builder = HumanTopLevelFlags.builder();

    builder.setJsonSource(jsonConfigString);

    JsonElement formatVersionElement = required(jsonConfig, CONFIGURATION_FORMAT_VERSION_KEY);
    int formatVersion = formatVersionElement.getAsInt();
    if (formatVersion != CURRENT_HUMAN_CONFIGURATION_FORMAT_VERSION) {
      reporter.warning(
          new StringDiagnostic(
              "Human desugared library specification format version "
                  + formatVersion
                  + " mismatches the parser expected version ("
                  + CURRENT_HUMAN_CONFIGURATION_FORMAT_VERSION
                  + "). This is allowed and should happen only while extending the specifications.",
              origin));
    }

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
        if (flag.has(API_LEVEL_GREATER_OR_EQUAL_KEY)) {
          if (minAPILevel >= flag.get(API_LEVEL_GREATER_OR_EQUAL_KEY).getAsInt()) {
            parseFlags(flag, builder);
          }
        } else {
          parseFlags(flag, builder);
        }
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
    if (jsonFlagSet.has(MAINTAIN_PREFIX_KEY)) {
      for (JsonElement maintainPrefix : jsonFlagSet.get(MAINTAIN_PREFIX_KEY).getAsJsonArray()) {
        builder.putMaintainPrefix(maintainPrefix.getAsString());
      }
    }
    if (jsonFlagSet.has(DONT_REWRITE_PREFIX_KEY)) {
      for (JsonElement dontRewritePrefix :
          jsonFlagSet.get(DONT_REWRITE_PREFIX_KEY).getAsJsonArray()) {
        builder.putDontRewritePrefix(dontRewritePrefix.getAsString());
      }
    }
    if (jsonFlagSet.has(NEVER_OUTLINE_API_KEY)) {
      for (JsonElement neverOutlineApi : jsonFlagSet.get(NEVER_OUTLINE_API_KEY).getAsJsonArray()) {
        builder.neverOutlineApi(parseMethod(neverOutlineApi.getAsString()));
      }
    }
    if (jsonFlagSet.has(API_GENERIC_TYPES_CONVERSION)) {
      for (Map.Entry<String, JsonElement> methodAndDescription :
          jsonFlagSet.get(API_GENERIC_TYPES_CONVERSION).getAsJsonObject().entrySet()) {
        JsonArray array = methodAndDescription.getValue().getAsJsonArray();
        for (int i = 0; i < array.size(); i += 2) {
          builder.addApiGenericTypesConversion(
              parseMethod(methodAndDescription.getKey()),
              array.get(i).getAsInt(),
              parseMethod(array.get(i + 1).getAsString()));
        }
      }
    }
    if (jsonFlagSet.has(REWRITE_DERIVED_PREFIX_KEY)) {
      for (Map.Entry<String, JsonElement> prefixToMatch :
          jsonFlagSet.get(REWRITE_DERIVED_PREFIX_KEY).getAsJsonObject().entrySet()) {
        for (Entry<String, JsonElement> rewriteRule :
            prefixToMatch.getValue().getAsJsonObject().entrySet()) {
          builder.putRewriteDerivedPrefix(
              prefixToMatch.getKey(), rewriteRule.getKey(), rewriteRule.getValue().getAsString());
        }
      }
    }
    if (jsonFlagSet.has(RETARGET_STATIC_FIELD_KEY)) {
      for (Map.Entry<String, JsonElement> retarget :
          jsonFlagSet.get(RETARGET_STATIC_FIELD_KEY).getAsJsonObject().entrySet()) {
        builder.retargetStaticField(
            parseField(retarget.getKey()), parseField(retarget.getValue().getAsString()));
      }
    }
    if (jsonFlagSet.has(RETARGET_METHOD_KEY)) {
      for (Map.Entry<String, JsonElement> retarget :
          jsonFlagSet.get(RETARGET_METHOD_KEY).getAsJsonObject().entrySet()) {
        String key = retarget.getKey();
        String value = retarget.getValue().getAsString();
        if (value.contains("#")) {
          builder.retargetMethodToMethod(parseMethod(key), parseMethod(value));
        } else {
          builder.retargetMethodToType(parseMethod(key), stringDescriptorToDexType(value));
        }
      }
    }
    if (jsonFlagSet.has(RETARGET_METHOD_EMULATED_DISPATCH_KEY)) {
      for (Map.Entry<String, JsonElement> retarget :
          jsonFlagSet.get(RETARGET_METHOD_EMULATED_DISPATCH_KEY).getAsJsonObject().entrySet()) {
        String key = retarget.getKey();
        String value = retarget.getValue().getAsString();
        if (value.contains("#")) {
          builder.retargetMethodEmulatedDispatchToMethod(parseMethod(key), parseMethod(value));
        } else {
          builder.retargetMethodEmulatedDispatchToType(
              parseMethod(key), stringDescriptorToDexType(value));
        }
      }
    }
    if (jsonFlagSet.has(COVARIANT_RETARGET_METHOD_KEY)) {
      for (Map.Entry<String, JsonElement> retarget :
          jsonFlagSet.get(COVARIANT_RETARGET_METHOD_KEY).getAsJsonObject().entrySet()) {
        builder.covariantRetargetMethod(
            parseMethod(retarget.getKey()),
            stringDescriptorToDexType(retarget.getValue().getAsString()));
      }
    }
    if (jsonFlagSet.has(BACKPORT_KEY)) {
      for (Map.Entry<String, JsonElement> backport :
          jsonFlagSet.get(BACKPORT_KEY).getAsJsonObject().entrySet()) {
        builder.putLegacyBackport(
            stringDescriptorToDexType(backport.getKey()),
            stringDescriptorToDexType(backport.getValue().getAsString()));
      }
    }
    if (jsonFlagSet.has(EMULATE_INTERFACE_KEY)) {
      for (Map.Entry<String, JsonElement> itf :
          jsonFlagSet.get(EMULATE_INTERFACE_KEY).getAsJsonObject().entrySet()) {
        builder.putEmulatedInterface(
            stringDescriptorToDexType(itf.getKey()), parseEmulatedInterface(itf.getValue()));
      }
    }
    if (jsonFlagSet.has(CUSTOM_CONVERSION_KEY)) {
      for (Map.Entry<String, JsonElement> conversion :
          jsonFlagSet.get(CUSTOM_CONVERSION_KEY).getAsJsonObject().entrySet()) {
        builder.putCustomConversion(
            stringDescriptorToDexType(conversion.getKey()),
            stringDescriptorToDexType(conversion.getValue().getAsString()));
      }
    }
    if (jsonFlagSet.has(WRAPPER_CONVERSION_KEY)) {
      for (JsonElement wrapper : jsonFlagSet.get(WRAPPER_CONVERSION_KEY).getAsJsonArray()) {
        builder.addWrapperConversion(stringDescriptorToDexType(wrapper.getAsString()));
      }
    }
    if (jsonFlagSet.has(WRAPPER_CONVERSION_EXCLUDING_KEY)) {
      for (Map.Entry<String, JsonElement> wrapper :
          jsonFlagSet.get(WRAPPER_CONVERSION_EXCLUDING_KEY).getAsJsonObject().entrySet()) {
        builder.addWrapperConversion(
            stringDescriptorToDexType(wrapper.getKey()),
            parseMethods(wrapper.getValue().getAsJsonArray()));
      }
    }
    if (jsonFlagSet.has(DONT_RETARGET_KEY)) {
      JsonArray dontRetarget = jsonFlagSet.get(DONT_RETARGET_KEY).getAsJsonArray();
      for (JsonElement rewrite : dontRetarget) {
        builder.addDontRetargetLibMember(stringDescriptorToDexType(rewrite.getAsString()));
      }
    }
    if (jsonFlagSet.has(AMEND_LIBRARY_METHOD_KEY)) {
      JsonArray amendLibraryMember = jsonFlagSet.get(AMEND_LIBRARY_METHOD_KEY).getAsJsonArray();
      for (JsonElement amend : amendLibraryMember) {
        methodParser.parseMethod(amend.getAsString());
        builder.amendLibraryMethod(methodParser.getMethod(), methodParser.getFlags());
      }
    }
    if (jsonFlagSet.has(AMEND_LIBRARY_FIELD_KEY)) {
      JsonArray amendLibraryMember = jsonFlagSet.get(AMEND_LIBRARY_FIELD_KEY).getAsJsonArray();
      for (JsonElement amend : amendLibraryMember) {
        fieldParser.parseField(amend.getAsString());
        builder.amendLibraryField(fieldParser.getField(), fieldParser.getFlags());
      }
    }
  }

  private HumanEmulatedInterfaceDescriptor parseEmulatedInterface(JsonElement value) {
    JsonObject jsonObject = value.getAsJsonObject();
    DexType rewrittenType =
        stringDescriptorToDexType(required(jsonObject, REWRITTEN_TYPE_KEY).getAsString());
    Set<DexMethod> emulatedMethods = Sets.newIdentityHashSet();
    if (jsonObject.has(EMULATED_METHODS_KEY)) {
      JsonArray methods = jsonObject.get(EMULATED_METHODS_KEY).getAsJsonArray();
      for (JsonElement method : methods) {
        methodParser.parseMethod(method.getAsString());
        emulatedMethods.add(methodParser.getMethod());
      }
    }
    return new HumanEmulatedInterfaceDescriptor(rewrittenType, emulatedMethods);
  }

  private Set<DexMethod> parseMethods(JsonArray array) {
    Set<DexMethod> methods = Sets.newIdentityHashSet();
    for (JsonElement method : array) {
      methods.add(parseMethod(method.getAsString()));
    }
    return methods;
  }

  private DexMethod parseMethod(String signature) {
    methodParser.parseMethod(signature);
    return methodParser.getMethod();
  }

  private DexField parseField(String signature) {
    fieldParser.parseField(signature);
    return fieldParser.getField();
  }

  private DexType stringDescriptorToDexType(String stringClass) {
    return dexItemFactory.createType(DescriptorUtils.javaTypeToDescriptor(stringClass));
  }
}
