// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification;

import static com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibrarySpecificationParser.CONFIGURATION_FORMAT_VERSION_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.AMEND_LIBRARY_FIELD_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.AMEND_LIBRARY_METHOD_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.API_GENERIC_TYPES_CONVERSION_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.API_LEVEL_BELOW_OR_EQUAL_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.API_LEVEL_GREATER_OR_EQUAL_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.COMMON_FLAGS_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.COVARIANT_RETARGET_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.CUSTOM_CONVERSION_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.DONT_RETARGET_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.EMULATED_INTERFACE_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.EMULATED_VIRTUAL_RETARGET_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.EMULATED_VIRTUAL_RETARGET_THROUGH_EMULATED_INTERFACE_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.IDENTIFIER_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.LEGACY_BACKPORT_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.LIBRARY_FLAGS_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.MAINTAIN_TYPE_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.NON_EMULATED_VIRTUAL_RETARGET_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.PACKAGE_MAP_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.PROGRAM_FLAGS_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.REQUIRED_COMPILATION_API_LEVEL_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.REWRITE_DERIVED_TYPE_ONLY_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.REWRITE_TYPE_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.SHRINKER_CONFIG_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.STATIC_FIELD_RETARGET_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.STATIC_RETARGET_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.SUPPORT_ALL_CALLBACKS_FROM_LIBRARY_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.SYNTHESIZED_LIBRARY_CLASSES_PACKAGE_PREFIX_KEY;
import static com.android.tools.r8.ir.desugar.desugaredlibrary.machinespecification.MachineSpecificationJsonPool.WRAPPER_KEY;

import com.android.tools.r8.StringResource;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.memberparser.MachineFieldParser;
import com.android.tools.r8.ir.desugar.desugaredlibrary.memberparser.MachineMethodParser;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import com.google.common.collect.ImmutableBiMap;
import com.google.common.collect.ImmutableList;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class MachineDesugaredLibrarySpecificationParser {

  private static final int MIN_SUPPORTED_VERSION = 200;
  private static final int MAX_SUPPORTED_VERSION = 200;

  private static final String ERROR_MESSAGE_PREFIX = "Invalid desugared library specification: ";

  private final DexItemFactory dexItemFactory;
  private final MachineMethodParser methodParser;
  private final MachineFieldParser fieldParser;
  private final Reporter reporter;
  private final boolean libraryCompilation;
  private final int minAPILevel;
  private final SyntheticNaming syntheticNaming;

  private Origin origin;
  private JsonObject jsonConfig;
  private Map<String, String> packageMap;

  public MachineDesugaredLibrarySpecificationParser(
      DexItemFactory dexItemFactory,
      Reporter reporter,
      boolean libraryCompilation,
      int minAPILevel,
      SyntheticNaming syntheticNaming) {
    this.dexItemFactory = dexItemFactory;
    this.methodParser = new MachineMethodParser(dexItemFactory, this::stringDescriptorToDexType);
    this.fieldParser = new MachineFieldParser(dexItemFactory, this::stringDescriptorToDexType);
    this.reporter = reporter;
    this.minAPILevel = minAPILevel;
    this.syntheticNaming = syntheticNaming;
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

  public MachineDesugaredLibrarySpecification parse(StringResource stringResource) {
    String jsonConfigString = parseJson(stringResource);
    return parse(origin, jsonConfigString, jsonConfig);
  }

  public MachineDesugaredLibrarySpecification parse(
      Origin origin, String jsonConfigString, JsonObject jsonConfig) {
    this.origin = origin;
    this.jsonConfig = jsonConfig;
    int machineVersion = required(jsonConfig, CONFIGURATION_FORMAT_VERSION_KEY).getAsInt();
    if (machineVersion < MIN_SUPPORTED_VERSION || machineVersion > MAX_SUPPORTED_VERSION) {
      throw reporter.fatalError(
          new StringDiagnostic(
              "Unsupported machine version number "
                  + machineVersion
                  + " not in ["
                  + MIN_SUPPORTED_VERSION
                  + ","
                  + MAX_SUPPORTED_VERSION
                  + "]",
              origin));
    }
    MachineTopLevelFlags topLevelFlags = parseTopLevelFlags(jsonConfigString);
    parsePackageMap();
    MachineRewritingFlags rewritingFlags = parseRewritingFlags();
    MachineDesugaredLibrarySpecification config =
        new MachineDesugaredLibrarySpecification(libraryCompilation, topLevelFlags, rewritingFlags);
    this.origin = null;
    return config;
  }

  private void parsePackageMap() {
    JsonObject packageMapJson = required(jsonConfig, PACKAGE_MAP_KEY).getAsJsonObject();
    ImmutableBiMap.Builder<String, String> builder = ImmutableBiMap.builder();
    packageMapJson.entrySet().forEach((e) -> builder.put(e.getValue().getAsString(), e.getKey()));
    packageMap = builder.build();
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

  private MachineRewritingFlags parseRewritingFlags() {
    MachineRewritingFlags.Builder builder = MachineRewritingFlags.builder();
    JsonElement commonFlags = required(jsonConfig, COMMON_FLAGS_KEY);
    JsonElement libraryFlags = required(jsonConfig, LIBRARY_FLAGS_KEY);
    JsonElement programFlags = required(jsonConfig, PROGRAM_FLAGS_KEY);
    parseFlagsList(commonFlags.getAsJsonArray(), builder);
    parseFlagsList(
        libraryCompilation ? libraryFlags.getAsJsonArray() : programFlags.getAsJsonArray(),
        builder);
    return builder.build();
  }

  MachineTopLevelFlags parseTopLevelFlags(String jsonConfigString) {
    String identifier = required(jsonConfig, IDENTIFIER_KEY).getAsString();
    String prefix =
        required(jsonConfig, SYNTHESIZED_LIBRARY_CLASSES_PACKAGE_PREFIX_KEY).getAsString();
    int required_compilation_api_level =
        required(jsonConfig, REQUIRED_COMPILATION_API_LEVEL_KEY).getAsInt();
    String keepRules = required(jsonConfig, SHRINKER_CONFIG_KEY).getAsString();
    boolean supportAllCallbacksFromLibrary =
        jsonConfig.get(SUPPORT_ALL_CALLBACKS_FROM_LIBRARY_KEY).getAsBoolean();

    return new MachineTopLevelFlags(
        AndroidApiLevel.getAndroidApiLevel(required_compilation_api_level),
        prefix,
        identifier,
        jsonConfigString,
        supportAllCallbacksFromLibrary,
        ImmutableList.of(keepRules));
  }

  private void parseFlagsList(JsonArray jsonFlags, MachineRewritingFlags.Builder builder) {
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

  void parseFlags(JsonObject jsonFlagSet, MachineRewritingFlags.Builder builder) {
    if (jsonFlagSet.has(REWRITE_TYPE_KEY)) {
      for (Map.Entry<String, JsonElement> rewritePrefix :
          jsonFlagSet.get(REWRITE_TYPE_KEY).getAsJsonObject().entrySet()) {
        builder.rewriteType(
            stringDescriptorToDexType(rewritePrefix.getKey()),
            stringDescriptorToDexType(rewritePrefix.getValue().getAsString()));
      }
    }
    if (jsonFlagSet.has(MAINTAIN_TYPE_KEY)) {
      for (JsonElement maintainPrefix : jsonFlagSet.get(MAINTAIN_TYPE_KEY).getAsJsonArray()) {
        builder.maintainType(stringDescriptorToDexType(maintainPrefix.getAsString()));
      }
    }
    if (jsonFlagSet.has(REWRITE_DERIVED_TYPE_ONLY_KEY)) {
      for (Map.Entry<String, JsonElement> rewriteDerivedTypeOnly :
          jsonFlagSet.get(REWRITE_DERIVED_TYPE_ONLY_KEY).getAsJsonObject().entrySet()) {
        builder.rewriteDerivedTypeOnly(
            stringDescriptorToDexType(rewriteDerivedTypeOnly.getKey()),
            stringDescriptorToDexType(rewriteDerivedTypeOnly.getValue().getAsString()));
      }
    }
    if (jsonFlagSet.has(STATIC_FIELD_RETARGET_KEY)) {
      for (Map.Entry<String, JsonElement> staticFieldRetarget :
          jsonFlagSet.get(STATIC_FIELD_RETARGET_KEY).getAsJsonObject().entrySet()) {
        builder.putStaticFieldRetarget(
            parseField(staticFieldRetarget.getKey()),
            parseField(staticFieldRetarget.getValue().getAsString()));
      }
    }
    if (jsonFlagSet.has(COVARIANT_RETARGET_KEY)) {
      for (Map.Entry<String, JsonElement> covariantRetarget :
          jsonFlagSet.get(COVARIANT_RETARGET_KEY).getAsJsonObject().entrySet()) {
        builder.putCovariantRetarget(
            parseMethod(covariantRetarget.getKey()),
            parseMethod(covariantRetarget.getValue().getAsString()));
      }
    }
    if (jsonFlagSet.has(STATIC_RETARGET_KEY)) {
      for (Map.Entry<String, JsonElement> staticRetarget :
          jsonFlagSet.get(STATIC_RETARGET_KEY).getAsJsonObject().entrySet()) {
        builder.putStaticRetarget(
            parseMethod(staticRetarget.getKey()),
            parseMethod(staticRetarget.getValue().getAsString()));
      }
    }
    if (jsonFlagSet.has(NON_EMULATED_VIRTUAL_RETARGET_KEY)) {
      for (Map.Entry<String, JsonElement> virtualRetarget :
          jsonFlagSet.get(NON_EMULATED_VIRTUAL_RETARGET_KEY).getAsJsonObject().entrySet()) {
        builder.putNonEmulatedVirtualRetarget(
            parseMethod(virtualRetarget.getKey()),
            parseMethod(virtualRetarget.getValue().getAsString()));
      }
    }
    if (jsonFlagSet.has(EMULATED_VIRTUAL_RETARGET_KEY)) {
      for (Map.Entry<String, JsonElement> virtualRetarget :
          jsonFlagSet.get(EMULATED_VIRTUAL_RETARGET_KEY).getAsJsonObject().entrySet()) {
        builder.putEmulatedVirtualRetarget(
            parseMethod(virtualRetarget.getKey()),
            parseEmulatedDispatchDescriptor(virtualRetarget.getValue().getAsJsonArray()));
      }
    }
    if (jsonFlagSet.has(EMULATED_VIRTUAL_RETARGET_THROUGH_EMULATED_INTERFACE_KEY)) {
      for (Map.Entry<String, JsonElement> virtualRetarget :
          jsonFlagSet
              .get(EMULATED_VIRTUAL_RETARGET_THROUGH_EMULATED_INTERFACE_KEY)
              .getAsJsonObject()
              .entrySet()) {
        builder.putEmulatedVirtualRetargetThroughEmulatedInterface(
            parseMethod(virtualRetarget.getKey()),
            parseMethod(virtualRetarget.getValue().getAsString()));
      }
    }
    if (jsonFlagSet.has(API_GENERIC_TYPES_CONVERSION_KEY)) {
      for (Map.Entry<String, JsonElement> apiGenericType :
          jsonFlagSet.get(API_GENERIC_TYPES_CONVERSION_KEY).getAsJsonObject().entrySet()) {
        builder.addApiGenericTypesConversion(
            parseMethod(apiGenericType.getKey()),
            parseMethodArray(apiGenericType.getValue().getAsJsonArray()));
      }
    }
    if (jsonFlagSet.has(EMULATED_INTERFACE_KEY)) {
      for (Map.Entry<String, JsonElement> emulatedInterface :
          jsonFlagSet.get(EMULATED_INTERFACE_KEY).getAsJsonObject().entrySet()) {
        builder.putEmulatedInterface(
            stringDescriptorToDexType(emulatedInterface.getKey()),
            parseEmulatedInterfaceDescriptor(emulatedInterface.getValue().getAsJsonArray()));
      }
    }
    if (jsonFlagSet.has(WRAPPER_KEY)) {
      for (Map.Entry<String, JsonElement> wrapper :
          jsonFlagSet.get(WRAPPER_KEY).getAsJsonObject().entrySet()) {
        builder.addWrapper(
            stringDescriptorToDexType(wrapper.getKey()),
            parseWrapperDescriptor(wrapper.getValue().getAsJsonArray()));
      }
    }
    if (jsonFlagSet.has(LEGACY_BACKPORT_KEY)) {
      for (Map.Entry<String, JsonElement> legacyBackport :
          jsonFlagSet.get(LEGACY_BACKPORT_KEY).getAsJsonObject().entrySet()) {
        builder.putLegacyBackport(
            stringDescriptorToDexType(legacyBackport.getKey()),
            stringDescriptorToDexType(legacyBackport.getValue().getAsString()));
      }
    }
    if (jsonFlagSet.has(DONT_RETARGET_KEY)) {
      for (JsonElement dontRetarget : jsonFlagSet.get(DONT_RETARGET_KEY).getAsJsonArray()) {
        builder.addDontRetarget(stringDescriptorToDexType(dontRetarget.getAsString()));
      }
    }
    if (jsonFlagSet.has(CUSTOM_CONVERSION_KEY)) {
      for (Map.Entry<String, JsonElement> customConversion :
          jsonFlagSet.get(CUSTOM_CONVERSION_KEY).getAsJsonObject().entrySet()) {
        builder.putCustomConversion(
            stringDescriptorToDexType(customConversion.getKey()),
            parseCustomConversionDescriptor(customConversion.getValue().getAsJsonArray()));
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

  private CustomConversionDescriptor parseCustomConversionDescriptor(JsonArray jsonArray) {
    return new CustomConversionDescriptor(
        parseMethod(jsonArray.get(0).getAsString()), parseMethod(jsonArray.get(1).getAsString()));
  }

  private WrapperDescriptor parseWrapperDescriptor(JsonArray jsonArray) {
    List<DexMethod> methods = parseMethodList(jsonArray.get(0).getAsJsonArray());
    boolean nonPublicAccess = jsonArray.get(1).getAsBoolean();
    List<DexType> subwrappers = parseTypeList(jsonArray.get(2).getAsJsonArray());
    return new WrapperDescriptor(methods, subwrappers, nonPublicAccess);
  }

  private void require(JsonArray jsonArray, int size, String elementString) {
    if (jsonArray.size() != size) {
      throw reporter.fatalError(
          ERROR_MESSAGE_PREFIX + elementString + "(Json array of size " + jsonArray.size() + ")");
    }
  }

  private EmulatedInterfaceDescriptor parseEmulatedInterfaceDescriptor(JsonArray jsonArray) {
    require(jsonArray, 2, "emulated interface descriptor");
    DexType rewrittenType = stringDescriptorToDexType(jsonArray.get(0).getAsString());
    Map<DexMethod, EmulatedDispatchMethodDescriptor> methods =
        parseEmulatedInterfaceMap(jsonArray.get(1).getAsJsonObject());
    return new EmulatedInterfaceDescriptor(rewrittenType, methods);
  }

  private Map<DexMethod, EmulatedDispatchMethodDescriptor> parseEmulatedInterfaceMap(
      JsonObject jsonObject) {
    Map<DexMethod, EmulatedDispatchMethodDescriptor> map = new IdentityHashMap<>();
    for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
      map.put(
          parseMethod(entry.getKey()),
          parseEmulatedDispatchDescriptor(entry.getValue().getAsJsonArray()));
    }
    return map;
  }

  private LinkedHashMap<DexType, DerivedMethod> parseEmulatedDispatchMap(JsonObject jsonObject) {
    LinkedHashMap<DexType, DerivedMethod> map = new LinkedHashMap<>();
    for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
      map.put(
          stringDescriptorToDexType(entry.getKey()),
          parseDerivedMethod(entry.getValue().getAsJsonArray()));
    }
    return map;
  }

  private EmulatedDispatchMethodDescriptor parseEmulatedDispatchDescriptor(JsonArray jsonArray) {
    require(jsonArray, 4, "emulated dispatch descriptor");
    DerivedMethod interfaceMethod = parseDerivedMethod(jsonArray.get(0).getAsJsonArray());
    DerivedMethod emulatedDispatchMethod = parseDerivedMethod(jsonArray.get(1).getAsJsonArray());
    DerivedMethod forwardingMethod = parseDerivedMethod(jsonArray.get(2).getAsJsonArray());
    LinkedHashMap<DexType, DerivedMethod> dispatchCases =
        parseEmulatedDispatchMap(jsonArray.get(3).getAsJsonObject());
    return new EmulatedDispatchMethodDescriptor(
        interfaceMethod, emulatedDispatchMethod, forwardingMethod, dispatchCases);
  }

  private DerivedMethod parseDerivedMethod(JsonArray jsonArray) {
    require(jsonArray, 2, "derived method");
    DexMethod dexMethod = parseMethod(jsonArray.get(0).getAsString());
    int kind = jsonArray.get(1).getAsInt();
    if (kind == -1) {
      return new DerivedMethod(dexMethod);
    }
    SyntheticKind syntheticKind = syntheticNaming.fromId(kind);
    return new DerivedMethod(dexMethod, syntheticKind);
  }

  private List<DexMethod> parseMethodList(JsonArray array) {
    List<DexMethod> methods = new ArrayList<>();
    for (JsonElement method : array) {
      methods.add(parseMethod(method.getAsString()));
    }
    return methods;
  }

  private List<DexType> parseTypeList(JsonArray array) {
    List<DexType> types = new ArrayList<>();
    for (JsonElement typeString : array) {
      types.add(stringDescriptorToDexType(typeString.getAsString()));
    }
    return types;
  }

  private DexMethod[] parseMethodArray(JsonArray array) {
    DexMethod[] dexMethods = new DexMethod[array.size()];
    for (int i = 0; i < array.size(); i++) {
      String str = array.get(i).getAsString();
      dexMethods[i] = str.isEmpty() ? null : parseMethod(str);
    }
    return dexMethods;
  }

  private DexMethod parseMethod(String signature) {
    methodParser.parseMethod(signature);
    return methodParser.getMethod();
  }

  private DexField parseField(String signature) {
    fieldParser.parseField(signature);
    return fieldParser.getField();
  }

  public DexType stringDescriptorToDexType(String stringClass) {
    if (stringClass.charAt(1) != '$') {
      return dexItemFactory.createType(DescriptorUtils.javaTypeToDescriptor(stringClass));
    }
    String type = stringClass.substring(2);
    String prefix = packageMap.get(stringClass.substring(0, 2));
    if (prefix == null) {
      throw reporter.fatalError(
          ERROR_MESSAGE_PREFIX + "Missing package mapping for " + stringClass.substring(0, 2));
    }
    return dexItemFactory.createType(DescriptorUtils.javaTypeToDescriptor(prefix + "." + type));
  }
}
