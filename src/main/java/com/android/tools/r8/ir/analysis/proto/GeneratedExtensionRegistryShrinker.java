// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.graph.FieldAccessInfoImpl;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import java.util.function.Consumer;

/**
 * This optimization is responsible for pruning dead proto extensions.
 *
 * <p>When using proto lite, a registry for all proto extensions is created. The generated extension
 * registry roughly looks as follows:
 *
 * <pre>
 *   class GeneratedExtensionRegistry {
 *     public static GeneratedMessageLite$GeneratedExtension findLiteExtensionByNumber(
 *         MessageLite message, int number) {
 *       ...
 *       switch (...) {
 *         case ...:
 *           return SomeExtension.extensionField;
 *         case ...:
 *           return SomeOtherExtension.extensionField;
 *         ... // Many other cases.
 *         default:
 *           return null;
 *       }
 *     }
 *   }
 * </pre>
 *
 * <p>We consider an extension to be dead if it is only accessed via a static-get instruction inside
 * the GeneratedExtensionRegistry. For such dead extensions, we simply rewrite the static-get
 * instructions inside the GeneratedExtensionRegistry to null. This ensures that the extensions will
 * be removed as a result of tree shaking.
 */
public class GeneratedExtensionRegistryShrinker {

  static class ProtoReferences {

    public final DexType generatedExtensionType;
    public final DexType generatedMessageLiteType;
    public final DexType messageLiteType;

    public final DexString findLiteExtensionByNumberName;
    public final DexString findLiteExtensionByNumber1Name;
    public final DexString findLiteExtensionByNumber2Name;
    public final DexProto findLiteExtensionByNumberProto;

    private ProtoReferences(DexItemFactory factory) {
      generatedExtensionType =
          factory.createType(
              factory.createString(
                  "Lcom/google/protobuf/GeneratedMessageLite$GeneratedExtension;"));
      generatedMessageLiteType =
          factory.createType(factory.createString("Lcom/google/protobuf/GeneratedMessageLite;"));
      messageLiteType =
          factory.createType(factory.createString("Lcom/google/protobuf/MessageLite;"));
      findLiteExtensionByNumberName = factory.createString("findLiteExtensionByNumber");
      findLiteExtensionByNumber1Name = factory.createString("findLiteExtensionByNumber1");
      findLiteExtensionByNumber2Name = factory.createString("findLiteExtensionByNumber2");
      findLiteExtensionByNumberProto =
          factory.createProto(generatedExtensionType, messageLiteType, factory.intType);
    }

    public boolean isFindLiteExtensionByNumberMethod(DexMethod method) {
      if (method.proto == findLiteExtensionByNumberProto) {
        assert method.name != findLiteExtensionByNumber2Name;
        return method.name == findLiteExtensionByNumberName
            || method.name == findLiteExtensionByNumber1Name;
      }
      return false;
    }
  }

  private final AppView<AppInfoWithLiveness> appView;
  private final ProtoReferences references;

  public GeneratedExtensionRegistryShrinker(AppView<AppInfoWithLiveness> appView) {
    assert appView.options().enableGeneratedExtensionRegistryShrinking;
    this.appView = appView;
    this.references = new ProtoReferences(appView.dexItemFactory());
  }

  /**
   * Will be run after the initial round of tree shaking. This clears the reads and writes to fields
   * that store dead proto extensions. As a result of this, the member value propagation will
   * automatically rewrite the reads of this field by null.
   */
  public void run() {
    FieldAccessInfoCollection<?> fieldAccessInfoCollection =
        appView.appInfo().getFieldAccessInfoCollection();
    forEachDeadProtoExtensionField(
        field -> {
          FieldAccessInfoImpl fieldAccessInfo = fieldAccessInfoCollection.get(field).asMutable();
          fieldAccessInfo.clearReads();
          fieldAccessInfo.clearWrites();
        });
  }

  public boolean isDeadProtoExtensionField(DexField field) {
    return isDeadProtoExtensionField(field, appView.appInfo().getFieldAccessInfoCollection());
  }

  public boolean isDeadProtoExtensionField(
      DexField field, FieldAccessInfoCollection<?> fieldAccessInfoCollection) {
    if (field.type != references.generatedExtensionType) {
      return false;
    }

    DexEncodedField encodedField = appView.appInfo().resolveField(field);
    if (encodedField == null) {
      return false;
    }

    DexClass clazz = appView.definitionFor(encodedField.field.holder);
    if (clazz == null || !clazz.isProgramClass()) {
      return false;
    }

    if (!appView.isSubtype(clazz.type, references.generatedMessageLiteType).isTrue()) {
      return false;
    }

    FieldAccessInfo fieldAccessInfo = fieldAccessInfoCollection.get(encodedField.field);
    if (fieldAccessInfo == null) {
      return false;
    }

    DexEncodedMethod uniqueReadContext = fieldAccessInfo.getUniqueReadContext();
    return uniqueReadContext != null
        && references.isFindLiteExtensionByNumberMethod(uniqueReadContext.method);
  }

  private void forEachDeadProtoExtensionField(Consumer<DexField> consumer) {
    FieldAccessInfoCollection<?> fieldAccessInfoCollection =
        appView.appInfo().getFieldAccessInfoCollection();
    fieldAccessInfoCollection.forEach(
        info -> {
          DexField field = info.getField();
          if (isDeadProtoExtensionField(field)) {
            consumer.accept(field);
          }
        });
  }

  /** For debugging. */
  public void logDeadProtoExtensionFields() {
    if (Log.isLoggingEnabledFor(GeneratedExtensionRegistryShrinker.class)) {
      forEachDeadProtoExtensionField(
          field ->
              System.out.println("Dead proto extension field: `" + field.toSourceString() + "`"));
    }
  }
}
