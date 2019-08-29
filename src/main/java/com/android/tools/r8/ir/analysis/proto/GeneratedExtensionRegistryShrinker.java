// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import static com.google.common.base.Predicates.alwaysFalse;
import static com.google.common.base.Predicates.not;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DefaultUseRegistry;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.FieldAccessInfo;
import com.android.tools.r8.graph.FieldAccessInfoCollection;
import com.android.tools.r8.ir.conversion.CallSiteInformation;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.optimize.Outliner;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.FileUtils;
import com.google.common.base.Predicates;
import com.google.common.collect.Sets;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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

  private final AppView<AppInfoWithLiveness> appView;
  private final ProtoReferences references;

  private final Set<DexField> removedExtensionFields = Sets.newIdentityHashSet();

  GeneratedExtensionRegistryShrinker(
      AppView<AppInfoWithLiveness> appView, ProtoReferences references) {
    assert appView.options().enableGeneratedExtensionRegistryShrinking;
    this.appView = appView;
    this.references = references;
  }

  /**
   * Will be run after tree shaking. This populates the set {@link #removedExtensionFields}. This
   * set is used by the member value propagation, which rewrites all reads of these fields by
   * const-null.
   */
  public void run() {
    forEachDeadProtoExtensionField(removedExtensionFields::add);
  }

  public boolean wasRemoved(DexField field) {
    return removedExtensionFields.contains(field);
  }

  public void postOptimizeGeneratedExtensionRegistry(IRConverter converter) {
    forEachFindLiteExtensionByNumberMethod(
        method ->
            converter.processMethod(
                method,
                OptimizationFeedbackIgnore.getInstance(),
                alwaysFalse(),
                CallSiteInformation.empty(),
                Outliner::noProcessing));
  }

  private void forEachFindLiteExtensionByNumberMethod(Consumer<DexEncodedMethod> consumer) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (clazz.superType != references.extensionRegistryLiteType) {
        continue;
      }

      for (DexEncodedMethod method : clazz.methods()) {
        if (references.isFindLiteExtensionByNumberMethod(method.method)) {
          consumer.accept(method);
        }
      }
    }
  }

  public boolean isDeadProtoExtensionField(DexField field) {
    DexEncodedField encodedField = appView.appInfo().resolveField(field);
    if (encodedField != null) {
      return isDeadProtoExtensionField(
          encodedField, appView.appInfo().getFieldAccessInfoCollection());
    }
    return false;
  }

  public boolean isDeadProtoExtensionField(
      DexEncodedField encodedField, FieldAccessInfoCollection<?> fieldAccessInfoCollection) {
    DexField field = encodedField.field;
    if (field.type != references.generatedExtensionType) {
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
  public void logRemainingProtoExtensionFields() {
    Predicate<DexField> skip = getSkipPredicate(null);

    Set<DexField> remainingProtoExtensionFieldReads = Sets.newIdentityHashSet();
    forEachFindLiteExtensionByNumberMethod(
        method -> {
          Log.info(
              GeneratedExtensionRegistryShrinker.class,
              "Extracting remaining proto extension field reads from method `%s`",
              method.method.toSourceString());

          assert method.hasCode();
          method
              .getCode()
              .registerCodeReferences(
                  method,
                  new DefaultUseRegistry(appView.dexItemFactory()) {

                    @Override
                    public boolean registerStaticFieldRead(DexField field) {
                      if (!skip.test(field)) {
                        remainingProtoExtensionFieldReads.add(field);
                      }
                      return true;
                    }
                  });
        });

    Log.info(
        GeneratedExtensionRegistryShrinker.class,
        "Number of remaining proto extension fields: %s",
        remainingProtoExtensionFieldReads.size());

    FieldAccessInfoCollection<?> fieldAccessInfoCollection =
        appView.appInfo().getFieldAccessInfoCollection();
    for (DexField field : remainingProtoExtensionFieldReads) {
      StringBuilder message = new StringBuilder(field.toSourceString());
      FieldAccessInfo fieldAccessInfo = fieldAccessInfoCollection.get(field);
      fieldAccessInfo.forEachReadContext(
          readContext ->
              message
                  .append(System.lineSeparator())
                  .append("- ")
                  .append(readContext.toSourceString()));
      Log.info(GeneratedExtensionRegistryShrinker.class, message.toString());
    }
  }

  /**
   * Utility to disable logging for proto extensions fields that are expected to be present in the
   * output.
   *
   * <p>Each proto extension field that is expected to be present in the output can be added to the
   * given file. Then no logs will be emitted for that field.
   *
   * <p>Example: File expected-proto-extensions.txt with lines like this:
   *
   * <pre>
   *   foo.bar.SomeClass.someField
   *   foo.bar.SomeOtherClass.someOtherField
   * </pre>
   */
  private Predicate<DexField> getSkipPredicate(Path file) {
    if (file != null) {
      try {
        DexItemFactory dexItemFactory = appView.dexItemFactory();
        Set<DexField> skipFields =
            FileUtils.readAllLines(file).stream()
                .map(String::trim)
                .filter(not(String::isEmpty))
                .map(
                    x -> {
                      int separatorIndex = x.lastIndexOf(".");
                      return dexItemFactory.createField(
                          dexItemFactory.createType(
                              DescriptorUtils.javaTypeToDescriptor(x.substring(0, separatorIndex))),
                          references.generatedExtensionType,
                          dexItemFactory.createString(x.substring(separatorIndex + 1)));
                    })
                .collect(Collectors.toSet());
        return skipFields::contains;
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return Predicates.alwaysFalse();
  }
}
