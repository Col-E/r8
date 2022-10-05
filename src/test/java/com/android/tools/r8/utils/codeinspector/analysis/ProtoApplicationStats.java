// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector.analysis;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.FoundFieldSubject;
import com.android.tools.r8.utils.codeinspector.FoundMethodSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.google.common.collect.Sets;
import java.util.Set;
import java.util.function.Function;

public class ProtoApplicationStats {

  private static final String EXTENDABLE_MESSAGE_TYPE =
      "com.google.protobuf.GeneratedMessageLite$ExtendableMessage";
  private static final String EXTENSION_REGISTRY_LITE_TYPE =
      "com.google.protobuf.ExtensionRegistryLite";
  private static final String GENERATED_EXTENSION_TYPE =
      "com.google.protobuf.GeneratedMessageLite$GeneratedExtension";
  private static final String GENERATED_MESSAGE_LITE_TYPE =
      "com.google.protobuf.GeneratedMessageLite";
  private static final String GENERATED_MESSAGE_LITE_BUILDER_TYPE =
      GENERATED_MESSAGE_LITE_TYPE + "$Builder";
  private static final String GENERATED_MESSAGE_LITE_EXTENDABLE_BUILDER_TYPE =
      GENERATED_MESSAGE_LITE_TYPE + "$ExtendableBuilder";

  abstract static class Stats {

    static <T> String progress(T actual, T baseline, T original, Function<T, Set<?>> fn) {
      StringBuilder builder = new StringBuilder();
      if (original != null) {
        builder.append(fn.apply(original).size()).append(" -> ");
      }
      Set<?> actualSet = fn.apply(actual);
      builder.append(actualSet.size());
      if (baseline != null) {
        Set<?> baselineSet = fn.apply(baseline);
        builder
            .append(" (unfulfilled potential: ")
            .append(Sets.difference(actualSet, baselineSet).size())
            .append(", improvement over baseline: ")
            .append(Sets.difference(baselineSet, actualSet).size())
            .append(" )");
      }
      return builder.toString();
    }
  }

  class EnumStats extends Stats {

    final Set<DexType> enums = Sets.newIdentityHashSet();

    String getStats(EnumStats baseline, EnumStats original) {
      return StringUtils.lines(
          "Enum stats:", "  # enums: " + progress(this, baseline, original, x -> x.enums));
    }
  }

  class ProtoBuilderStats extends Stats {

    final Set<DexType> builders = Sets.newIdentityHashSet();

    String getStats(ProtoBuilderStats baseline, ProtoBuilderStats original) {
      return StringUtils.lines(
          "Proto builder stats:",
          "  # builders: " + progress(this, baseline, original, x -> x.builders));
    }
  }

  class ProtoMessageStats extends Stats {

    final boolean extendable;

    ProtoMessageStats(boolean extendable) {
      this.extendable = extendable;
    }

    final Set<DexType> messages = Sets.newIdentityHashSet();
    final Set<DexField> bitFields = Sets.newIdentityHashSet();
    final Set<DexField> nonBitFields = Sets.newIdentityHashSet();

    String getStats(ProtoMessageStats baseline, ProtoMessageStats original) {
      return StringUtils.lines(
          extendable ? "Extendable proto message stats" : "Proto message stats:",
          "  # messages: " + progress(this, baseline, original, x -> x.messages),
          "  # bit fields: " + progress(this, baseline, original, x -> x.bitFields),
          "  # non-bit fields: " + progress(this, baseline, original, x -> x.nonBitFields));
    }
  }

  public class GeneratedExtensionRegistryStats extends Stats {

    final Set<DexMethod> findLiteExtensionByNumberMethods = Sets.newIdentityHashSet();
    final Set<DexField> retainedExtensionFields = Sets.newIdentityHashSet();
    final Set<DexField> spuriouslyRetainedExtensionFields = Sets.newIdentityHashSet();

    public Set<DexField> getSpuriouslyRetainedExtensionFields() {
      return spuriouslyRetainedExtensionFields;
    }

    String getStats(
        GeneratedExtensionRegistryStats baseline, GeneratedExtensionRegistryStats original) {
      return StringUtils.lines(
          "Generated extension registry stats:",
          "  # findLiteExtensionByNumber() methods: "
              + progress(this, baseline, original, x -> x.findLiteExtensionByNumberMethods),
          "  # retained extensions: "
              + progress(this, baseline, original, x -> x.retainedExtensionFields),
          "  # spuriously retained extension fields: " + spuriouslyRetainedExtensionFields.size());
    }
  }

  private final DexItemFactory dexItemFactory;
  private final CodeInspector inspector;
  private final ProtoApplicationStats original;

  private final EnumStats enumStats = new EnumStats();
  private final ProtoMessageStats extendableProtoMessageStats = new ProtoMessageStats(true);
  private final GeneratedExtensionRegistryStats generatedExtensionRegistryStats =
      new GeneratedExtensionRegistryStats();
  private final ProtoBuilderStats protoBuilderStats = new ProtoBuilderStats();
  private final ProtoMessageStats protoMessageStats = new ProtoMessageStats(false);

  public ProtoApplicationStats(DexItemFactory dexItemFactory, CodeInspector inspector) {
    this(dexItemFactory, inspector, null);
  }

  public ProtoApplicationStats(
      DexItemFactory dexItemFactory, CodeInspector inspector, ProtoApplicationStats original) {
    this.dexItemFactory = dexItemFactory;
    this.inspector = inspector;
    this.original = original;
    computeStats();
  }

  private void computeStats() {
    for (FoundClassSubject classSubject : inspector.allClasses()) {
      DexType originalType = classSubject.getOriginalDexType(dexItemFactory);
      if (classSubject.getDexProgramClass().isEnum()) {
        enumStats.enums.add(originalType);
      }

      ClassSubject superClassSubject = classSubject.getSuperClass();
      if (!superClassSubject.isPresent()) {
        continue;
      }

      ProtoMessageStats messageStats = null;
      switch (superClassSubject.getOriginalName()) {
        case GENERATED_MESSAGE_LITE_TYPE:
          messageStats = protoMessageStats;
          break;

        case EXTENDABLE_MESSAGE_TYPE:
          messageStats = extendableProtoMessageStats;
          break;

        case GENERATED_MESSAGE_LITE_BUILDER_TYPE:
        case GENERATED_MESSAGE_LITE_EXTENDABLE_BUILDER_TYPE:
          protoBuilderStats.builders.add(
              dexItemFactory.createType(classSubject.getOriginalDescriptor()));
          break;

        case EXTENSION_REGISTRY_LITE_TYPE:
          for (FoundMethodSubject methodSubject : classSubject.allMethods()) {
            String originalMethodName = methodSubject.getOriginalName(false);
            if (originalMethodName.startsWith("findLiteExtensionByNumber")) {
              generatedExtensionRegistryStats.findLiteExtensionByNumberMethods.add(
                  methodSubject.getOriginalDexMethod(dexItemFactory));

              for (InstructionSubject instruction :
                  methodSubject.instructions(InstructionSubject::isStaticGet)) {
                DexField field = instruction.getField();
                FoundClassSubject typeClassSubject =
                    inspector.clazz(field.type.toSourceString()).asFoundClassSubject();
                if (!typeClassSubject.getOriginalName().equals(GENERATED_EXTENSION_TYPE)) {
                  continue;
                }
                FoundClassSubject extensionClassSubject =
                    inspector.clazz(field.holder.toSourceString()).asFoundClassSubject();
                FoundFieldSubject extensionFieldSubject =
                    extensionClassSubject
                        .uniqueFieldWithFinalName(field.name.toSourceString())
                        .asFoundFieldSubject();
                generatedExtensionRegistryStats.retainedExtensionFields.add(
                    extensionFieldSubject.getOriginalDexField(dexItemFactory));
              }
            }
          }
          break;
      }

      if (messageStats != null) {
        messageStats.messages.add(originalType);
        for (FoundFieldSubject fieldSubject : classSubject.allInstanceFields()) {
          String originalFieldName = fieldSubject.getOriginalName(false);
          if (originalFieldName.startsWith("bitField")) {
            messageStats.bitFields.add(fieldSubject.getOriginalDexField(dexItemFactory));
          } else {
            messageStats.nonBitFields.add(fieldSubject.getOriginalDexField(dexItemFactory));
          }
        }
      }
    }

    if (original != null) {
      for (DexField extensionField :
          original.generatedExtensionRegistryStats.retainedExtensionFields) {
        if (generatedExtensionRegistryStats.retainedExtensionFields.contains(extensionField)) {
          continue;
        }
        ClassSubject classSubject = inspector.clazz(extensionField.holder.toSourceString());
        if (!classSubject.isPresent()) {
          continue;
        }
        FieldSubject fieldSubject =
            classSubject.uniqueFieldWithOriginalName(extensionField.name.toSourceString());
        if (fieldSubject.isPresent()) {
          generatedExtensionRegistryStats.spuriouslyRetainedExtensionFields.add(extensionField);
        }
      }
    }
  }

  public GeneratedExtensionRegistryStats getGeneratedExtensionRegistryStats() {
    return generatedExtensionRegistryStats;
  }

  public String getStats() {
    return StringUtils.lines(
        enumStats.getStats(null, original.enumStats),
        protoMessageStats.getStats(null, original.protoMessageStats),
        extendableProtoMessageStats.getStats(null, original.extendableProtoMessageStats),
        protoBuilderStats.getStats(null, original.protoBuilderStats),
        generatedExtensionRegistryStats.getStats(null, original.generatedExtensionRegistryStats));
  }

  public String getStats(ProtoApplicationStats baseline) {
    return StringUtils.lines(
        enumStats.getStats(baseline.enumStats, original.enumStats),
        protoMessageStats.getStats(baseline.protoMessageStats, original.protoMessageStats),
        extendableProtoMessageStats.getStats(
            baseline.extendableProtoMessageStats, original.extendableProtoMessageStats),
        protoBuilderStats.getStats(baseline.protoBuilderStats, original.protoBuilderStats),
        generatedExtensionRegistryStats.getStats(
            baseline.generatedExtensionRegistryStats, original.generatedExtensionRegistryStats));
  }
}
