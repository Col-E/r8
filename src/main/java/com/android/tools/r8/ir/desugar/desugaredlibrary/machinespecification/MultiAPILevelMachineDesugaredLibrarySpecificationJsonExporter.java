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

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.StringConsumer;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AccessFlags;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.desugar.desugaredlibrary.ApiLevelRange;
import com.google.gson.Gson;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class MultiAPILevelMachineDesugaredLibrarySpecificationJsonExporter {

  private static final int MACHINE_VERSION_NUMBER = 200;

  private final DexItemFactory factory;
  private final Map<String, String> packageMap = new TreeMap<>();
  private static final String chars =
      "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789æÆøØ";
  private int next = 0;

  public MultiAPILevelMachineDesugaredLibrarySpecificationJsonExporter(DexItemFactory factory) {
    this.factory = factory;
  }

  public static void export(
      MultiAPILevelMachineDesugaredLibrarySpecification specification,
      StringConsumer output,
      DexItemFactory factory) {
    new MultiAPILevelMachineDesugaredLibrarySpecificationJsonExporter(factory)
        .internalExport(specification, output);
  }

  private void internalExport(
      MultiAPILevelMachineDesugaredLibrarySpecification machineSpec, StringConsumer output) {
    HashMap<String, Object> toJson = new LinkedHashMap<>();

    exportTopLevelFlags(machineSpec.getTopLevelFlags(), toJson);
    toJson.put(CONFIGURATION_FORMAT_VERSION_KEY, MACHINE_VERSION_NUMBER);

    toJson.put(COMMON_FLAGS_KEY, rewritingFlagsToString(machineSpec.getCommonFlags()));
    toJson.put(PROGRAM_FLAGS_KEY, rewritingFlagsToString(machineSpec.getProgramFlags()));
    toJson.put(LIBRARY_FLAGS_KEY, rewritingFlagsToString(machineSpec.getLibraryFlags()));

    toJson.put(PACKAGE_MAP_KEY, packageMap);

    Gson gson = new Gson();
    String export = gson.toJson(toJson);
    output.accept(export, new DiagnosticsHandler() {});
  }

  private void exportTopLevelFlags(MachineTopLevelFlags topLevelFlags, Map<String, Object> toJson) {
    toJson.put(IDENTIFIER_KEY, topLevelFlags.getIdentifier());
    toJson.put(
        REQUIRED_COMPILATION_API_LEVEL_KEY,
        topLevelFlags.getRequiredCompilationApiLevel().getLevel());
    toJson.put(
        SYNTHESIZED_LIBRARY_CLASSES_PACKAGE_PREFIX_KEY,
        topLevelFlags.getSynthesizedLibraryClassesPackagePrefix());
    toJson.put(
        SUPPORT_ALL_CALLBACKS_FROM_LIBRARY_KEY, topLevelFlags.supportAllCallbacksFromLibrary());
    toJson.put(SHRINKER_CONFIG_KEY, topLevelFlags.getExtraKeepRulesConcatenated());
  }

  private List<Object> rewritingFlagsToString(
      Map<ApiLevelRange, MachineRewritingFlags> rewritingFlagsMap) {
    ArrayList<Object> list = new ArrayList<>();
    ArrayList<ApiLevelRange> apis = new ArrayList<>(rewritingFlagsMap.keySet());
    apis.sort((x, y) -> -x.deterministicOrder(y));
    for (ApiLevelRange range : apis) {
      MachineRewritingFlags flags = rewritingFlagsMap.get(range);
      HashMap<String, Object> toJson = new LinkedHashMap<>();
      toJson.put(API_LEVEL_BELOW_OR_EQUAL_KEY, range.getApiLevelBelowOrEqualAsInt());
      if (range.hasApiLevelGreaterOrEqual()) {
        toJson.put(API_LEVEL_GREATER_OR_EQUAL_KEY, range.getApiLevelGreaterOrEqualAsInt());
      }
      writeFlags(flags, toJson);
      list.add(toJson);
    }
    return list;
  }

  private void writeFlagCollection(
      String key, Collection<? extends DexItem> collection, Map<String, Object> toJson) {
    if (!collection.isEmpty()) {
      toJson.put(key, collectionToJsonStruct(collection));
    }
  }

  private void writeFlagMap(
      String key, Map<? extends DexItem, ? extends DexItem> map, Map<String, Object> toJson) {
    if (!map.isEmpty()) {
      toJson.put(key, mapToJsonStruct(map));
    }
  }

  private void writeFlagMapToSpecificationDescriptor(
      String key,
      Map<? extends DexItem, ? extends SpecificationDescriptor> map,
      Map<String, Object> toJson) {
    if (!map.isEmpty()) {
      toJson.put(key, specificationDescriptorMapToJsonStruct(map));
    }
  }

  private void writeFlagMapToMethodArray(
      String key, Map<? extends DexItem, DexMethod[]> map, Map<String, Object> toJson) {
    if (!map.isEmpty()) {
      TreeMap<String, Object> stringMap = new TreeMap<>();
      map.forEach((k, v) -> stringMap.put(toString(k), methodArrayToJsonStruct(v)));
      toJson.put(key, stringMap);
    }
  }

  private void writeFlagLinkedHashMapToSpecificationDescriptor(
      String key,
      LinkedHashMap<? extends DexItem, ? extends SpecificationDescriptor> map,
      Map<String, Object> toJson) {
    if (!map.isEmpty()) {
      toJson.put(key, specificationDescriptorLinkedHashMapToJsonStruct(map));
    }
  }

  private void writeMembersWithFlags(
      String key,
      Map<? extends DexItem, ? extends AccessFlags<?>> membersWithFlags,
      Map<String, Object> toJson) {
    if (!membersWithFlags.isEmpty()) {
      List<String> stringSet = new ArrayList<>();
      membersWithFlags.forEach(
          (member, flags) -> stringSet.add(flags.toString() + " " + toString(member)));
      toJson.put(key, stringSet);
    }
  }

  private void writeFlags(MachineRewritingFlags flags, Map<String, Object> toJson) {
    writeFlagMap(REWRITE_TYPE_KEY, flags.getRewriteType(), toJson);
    writeFlagCollection(MAINTAIN_TYPE_KEY, flags.getMaintainType(), toJson);
    writeFlagMap(REWRITE_DERIVED_TYPE_ONLY_KEY, flags.getRewriteDerivedTypeOnly(), toJson);
    writeFlagMap(STATIC_FIELD_RETARGET_KEY, flags.getStaticFieldRetarget(), toJson);
    writeFlagMap(COVARIANT_RETARGET_KEY, flags.getCovariantRetarget(), toJson);
    writeFlagMap(STATIC_RETARGET_KEY, flags.getStaticRetarget(), toJson);
    writeFlagMap(NON_EMULATED_VIRTUAL_RETARGET_KEY, flags.getNonEmulatedVirtualRetarget(), toJson);
    writeFlagMapToSpecificationDescriptor(
        EMULATED_VIRTUAL_RETARGET_KEY, flags.getEmulatedVirtualRetarget(), toJson);
    writeFlagMap(
        EMULATED_VIRTUAL_RETARGET_THROUGH_EMULATED_INTERFACE_KEY,
        flags.getEmulatedVirtualRetargetThroughEmulatedInterface(),
        toJson);
    writeFlagMapToMethodArray(
        API_GENERIC_TYPES_CONVERSION_KEY, flags.getApiGenericConversion(), toJson);
    writeFlagMapToSpecificationDescriptor(
        EMULATED_INTERFACE_KEY, flags.getEmulatedInterfaces(), toJson);
    writeFlagLinkedHashMapToSpecificationDescriptor(WRAPPER_KEY, flags.getWrappers(), toJson);
    writeFlagMap(LEGACY_BACKPORT_KEY, flags.getLegacyBackport(), toJson);
    writeFlagCollection(DONT_RETARGET_KEY, flags.getDontRetarget(), toJson);
    writeFlagMapToSpecificationDescriptor(
        CUSTOM_CONVERSION_KEY, flags.getCustomConversions(), toJson);
    writeMembersWithFlags(AMEND_LIBRARY_METHOD_KEY, flags.getAmendLibraryMethod(), toJson);
    writeMembersWithFlags(AMEND_LIBRARY_FIELD_KEY, flags.getAmendLibraryField(), toJson);
  }

  private LinkedHashMap<String, ?> specificationDescriptorLinkedHashMapToJsonStruct(
      LinkedHashMap<? extends DexItem, ? extends SpecificationDescriptor> map) {
    // Already sorted with custom advanced deterministic sort, maintain the order.
    LinkedHashMap<String, Object> stringMap = new LinkedHashMap<>();
    map.forEach((k, v) -> stringMap.put(toString(k), v.toJsonStruct(this)));
    return stringMap;
  }

  private TreeMap<String, String> mapToJsonStruct(Map<? extends DexItem, ? extends DexItem> map) {
    TreeMap<String, String> stringMap = new TreeMap<>();
    map.forEach((k, v) -> stringMap.put(toString(k), toString(v)));
    return stringMap;
  }

  private TreeMap<String, ?> specificationDescriptorMapToJsonStruct(
      Map<? extends DexItem, ? extends SpecificationDescriptor> map) {
    TreeMap<String, Object> stringMap = new TreeMap<>();
    map.forEach((k, v) -> stringMap.put(toString(k), v.toJsonStruct(this)));
    return stringMap;
  }

  private List<String> collectionToJsonStruct(Collection<? extends DexItem> col) {
    List<String> stringCol = new ArrayList<>();
    col.forEach(e -> stringCol.add(toString(e)));
    stringCol.sort(Comparator.naturalOrder());
    return stringCol;
  }

  private String[] methodArrayToJsonStruct(DexMethod[] methodArray) {
    String[] strings = new String[methodArray.length];
    for (int i = 0; i < methodArray.length; i++) {
      strings[i] = methodArray[i] == null ? "" : toString(methodArray[i]);
    }
    return strings;
  }

  private String toString(DexItem o) {
    if (o instanceof DexType) {
      return typeToString((DexType) o);
    }
    if (o instanceof DexField) {
      DexField field = (DexField) o;
      return typeToString(field.getType())
          + " "
          + typeToString(field.getHolderType())
          + "#"
          + field.getName();
    }
    if (o instanceof DexMethod) {
      DexMethod method = (DexMethod) o;
      StringBuilder sb =
          new StringBuilder()
              .append(typeToString(method.getReturnType()))
              .append(" ")
              .append(typeToString(method.getHolderType()))
              .append("#")
              .append(method.getName())
              .append("(");
      for (int i = 0; i < method.getParameters().size(); i++) {
        sb.append(typeToString(method.getParameter(i)));
        if (i != method.getParameters().size() - 1) {
          sb.append(", ");
        }
      }
      sb.append(")");
      return sb.toString();
    }
    throw new Unreachable();
  }

  private String typeToString(DexType type) {
    if (type.isPrimitiveType() || type.isPrimitiveArrayType() || type.isVoidType()) {
      return type.toString();
    }
    if (type.isArrayType()) {
      StringBuilder sb = new StringBuilder();
      sb.append(typeToString(type.toBaseType(factory)));
      for (int i = 0; i < type.getNumberOfLeadingSquareBrackets(); i++) {
        sb.append("[]");
      }
      return sb.toString();
    }
    String pack =
        packageMap.computeIfAbsent(type.getPackageName(), k -> nextMinifiedPackagePrefix());
    return pack + type.getSimpleName();
  }

  private String nextMinifiedPackagePrefix() {
    if (next >= chars.length()) {
      // This should happen only when the R8 team release machine specifications (not in user
      // compilations).
      throw new RuntimeException(
          "MultiAPILevelMachineDesugaredLibrarySpecificationJsonExporter "
              + "cannot encode the next package because the encoding ran out of characters."
              + " Extend the chars sequence or improve the encoding to fix this.");
    }
    return chars.charAt(next++) + "$";
  }

  public Object[] exportCustomConversionDescriptor(
      CustomConversionDescriptor customConversionDescriptor) {
    String toString = toString(customConversionDescriptor.getTo());
    String fromString = toString(customConversionDescriptor.getFrom());
    return new Object[] {toString, fromString};
  }

  public Object[] exportDerivedMethod(DerivedMethod derivedMethod) {
    String methodString = toString(derivedMethod.getMethod());
    String holderKindString =
        Integer.toString(
            derivedMethod.getHolderKind() == null ? -1 : derivedMethod.getHolderKind().getId());
    return new Object[] {methodString, holderKindString};
  }

  public Object[] exportEmulatedDispatchMethodDescriptor(
      EmulatedDispatchMethodDescriptor descriptor) {
    Object interfaceMethodJsonStruct = exportDerivedMethod(descriptor.getInterfaceMethod());
    Object emulatedDispatchMethodJsonStruct =
        exportDerivedMethod(descriptor.getEmulatedDispatchMethod());
    Object forwardingMethodJsonStruct = exportDerivedMethod(descriptor.getForwardingMethod());
    Object dispatchCasesJsonStruct =
        specificationDescriptorLinkedHashMapToJsonStruct(descriptor.getDispatchCases());
    return new Object[] {
      interfaceMethodJsonStruct,
      emulatedDispatchMethodJsonStruct,
      forwardingMethodJsonStruct,
      dispatchCasesJsonStruct
    };
  }

  public Object[] exportEmulatedInterfaceDescriptor(EmulatedInterfaceDescriptor descriptor) {
    Object rewrittenTypeString = toString(descriptor.getRewrittenType());
    Object emulatedMethodsJsonStruct =
        specificationDescriptorMapToJsonStruct(descriptor.getEmulatedMethods());
    return new Object[] {rewrittenTypeString, emulatedMethodsJsonStruct};
  }

  public Object[] exportWrapperDescriptor(WrapperDescriptor descriptor) {
    Object methodStruct = collectionToJsonStruct(descriptor.getMethods());
    Object subwrappersStruct = collectionToJsonStruct(descriptor.getSubwrappers());
    return new Object[] {methodStruct, descriptor.hasNonPublicAccess(), subwrappersStruct};
  }
}
