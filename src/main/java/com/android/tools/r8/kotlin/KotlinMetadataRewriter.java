// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinClassMetadataReader.toKotlinClassMetadata;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getInvalidKotlinInfo;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getNoKotlinInfo;
import static com.android.tools.r8.kotlin.KotlinMetadataWriter.kotlinMetadataToString;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.graph.DexValue.DexValueInt;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.utils.BooleanBox;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.ThreadUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

public class KotlinMetadataRewriter {

  // Due to a bug with nested classes and the lookup of RequirementVersion, we bump all metadata
  // versions to 1.4 if compiled with kotlin 1.3 (1.1.16). For more information, see b/161885097.
  private static final int[] METADATA_VERSION_1_4 = new int[] {1, 4, 0};

  private static final class WriteMetadataFieldInfo {
    final boolean writeKind;
    final boolean writeMetadataVersion;
    final boolean writeByteCodeVersion;
    final boolean writeData1;
    final boolean writeData2;
    final boolean writeExtraString;
    final boolean writePackageName;
    final boolean writeExtraInt;

    private WriteMetadataFieldInfo(
        boolean writeKind,
        boolean writeMetadataVersion,
        boolean writeByteCodeVersion,
        boolean writeData1,
        boolean writeData2,
        boolean writeExtraString,
        boolean writePackageName,
        boolean writeExtraInt) {
      this.writeKind = writeKind;
      this.writeMetadataVersion = writeMetadataVersion;
      this.writeByteCodeVersion = writeByteCodeVersion;
      this.writeData1 = writeData1;
      this.writeData2 = writeData2;
      this.writeExtraString = writeExtraString;
      this.writePackageName = writePackageName;
      this.writeExtraInt = writeExtraInt;
    }

    private static WriteMetadataFieldInfo rewriteAll() {
      return new WriteMetadataFieldInfo(true, true, true, true, true, true, true, true);
    }
  }

  private final AppView<?> appView;
  private final DexItemFactory factory;
  private final Kotlin kotlin;

  public KotlinMetadataRewriter(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.kotlin = factory.kotlin;
  }

  @SuppressWarnings("ReferenceEquality")
  private static boolean isNotKotlinMetadata(DexAnnotation annotation, DexType kotlinMetadataType) {
    return annotation.annotation.type != kotlinMetadataType;
  }

  public void runForR8(ExecutorService executorService) throws ExecutionException {
    GraphLens graphLens = appView.graphLens();
    GraphLens kotlinMetadataLens = appView.getKotlinMetadataLens();
    DexType rewrittenMetadataType =
        graphLens.lookupClassType(factory.kotlinMetadataType, kotlinMetadataLens);
    // The Kotlin metadata may be present in the input but pruned away in the final tree shaking.
    DexClass kotlinMetadata =
        appView.appInfo().definitionForWithoutExistenceAssert(rewrittenMetadataType);
    WriteMetadataFieldInfo writeMetadataFieldInfo =
        kotlinMetadata == null
            ? WriteMetadataFieldInfo.rewriteAll()
            : new WriteMetadataFieldInfo(
                kotlinMetadataFieldExists(kotlinMetadata, appView, kotlin.metadata.kind),
                kotlinMetadataFieldExists(kotlinMetadata, appView, kotlin.metadata.metadataVersion),
                kotlinMetadataFieldExists(kotlinMetadata, appView, kotlin.metadata.bytecodeVersion),
                kotlinMetadataFieldExists(kotlinMetadata, appView, kotlin.metadata.data1),
                kotlinMetadataFieldExists(kotlinMetadata, appView, kotlin.metadata.data2),
                kotlinMetadataFieldExists(kotlinMetadata, appView, kotlin.metadata.extraString),
                kotlinMetadataFieldExists(kotlinMetadata, appView, kotlin.metadata.packageName),
                kotlinMetadataFieldExists(kotlinMetadata, appView, kotlin.metadata.extraInt));
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          KotlinClassLevelInfo kotlinInfo = clazz.getKotlinInfo();
          if (kotlinInfo == getInvalidKotlinInfo()) {
            // Maintain invalid kotlin info for classes.
            return;
          }
          DexAnnotation oldMeta = clazz.annotations().getFirstMatching(rewrittenMetadataType);
          // TODO(b/181103083): Consider removing if rewrittenMetadataType
          //  != factory.kotlinMetadataType
          if (oldMeta == null
              || kotlinInfo == getNoKotlinInfo()
              || (appView.appInfo().hasLiveness()
                  && !appView.withLiveness().appInfo().isPinned(clazz))) {
            // Remove @Metadata in DexAnnotation when there is no kotlin info and the type is not
            // missing.
            if (oldMeta != null) {
              clazz.setAnnotations(
                  clazz
                      .annotations()
                      .keepIf(anno -> isNotKotlinMetadata(anno, rewrittenMetadataType)));
            }
            return;
          }
          writeKotlinInfoToAnnotation(clazz, kotlinInfo, oldMeta, writeMetadataFieldInfo);
        },
        executorService);
    appView.setKotlinMetadataLens(appView.graphLens());
  }

  public void runForD8(ExecutorService executorService) throws ExecutionException {
    if (appView.getNamingLens().isIdentityLens()) {
      return;
    }
    final WriteMetadataFieldInfo writeMetadataFieldInfo = WriteMetadataFieldInfo.rewriteAll();
    BooleanBox reportedUnknownMetadataVersion = new BooleanBox();
    ThreadUtils.processItems(
        appView.appInfo().classes(),
        clazz -> {
          DexAnnotation metadata = clazz.annotations().getFirstMatching(factory.kotlinMetadataType);
          if (metadata == null) {
            return;
          }
          KotlinClassLevelInfo kotlinInfo =
              KotlinClassMetadataReader.getKotlinInfoFromAnnotation(
                  appView,
                  clazz,
                  metadata,
                  ConsumerUtils.emptyConsumer(),
                  reportedUnknownMetadataVersion::getAndSet);
          if (kotlinInfo == getNoKotlinInfo()) {
            return;
          }
          writeKotlinInfoToAnnotation(clazz, kotlinInfo, metadata, writeMetadataFieldInfo);
        },
        executorService);
  }

  @SuppressWarnings("ReferenceEquality")
  private void writeKotlinInfoToAnnotation(
      DexClass clazz,
      KotlinClassLevelInfo kotlinInfo,
      DexAnnotation oldMeta,
      WriteMetadataFieldInfo writeMetadataFieldInfo) {
    try {
      Pair<kotlin.Metadata, Boolean> kotlinMetadata = kotlinInfo.rewrite(clazz, appView);
      // TODO(b/185756596): Remove when special handling is no longer needed.
      if (!kotlinMetadata.getSecond() && appView.options().testing.keepMetadataInR8IfNotRewritten) {
        // No rewrite occurred and the data is the same as before.
        assert appView.checkForTesting(
            () ->
                verifyRewrittenMetadataIsEquivalent(
                    clazz.annotations().getFirstMatching(factory.kotlinMetadataType),
                    createKotlinMetadataAnnotation(
                        kotlinMetadata.getFirst(),
                        kotlinInfo.getPackageName(),
                        getMaxVersion(METADATA_VERSION_1_4, kotlinInfo.getMetadataVersion()),
                        writeMetadataFieldInfo)));
        return;
      }
      DexAnnotation newMeta =
          createKotlinMetadataAnnotation(
              kotlinMetadata.getFirst(),
              kotlinInfo.getPackageName(),
              getMaxVersion(METADATA_VERSION_1_4, kotlinInfo.getMetadataVersion()),
              writeMetadataFieldInfo);
      clazz.setAnnotations(clazz.annotations().rewrite(anno -> anno == oldMeta ? newMeta : anno));
    } catch (Throwable t) {
      assert appView.checkForTesting(
          () -> {
            throw appView
                .options()
                .reporter
                .fatalError(KotlinMetadataDiagnostic.unexpectedErrorWhenRewriting(clazz.type, t));
          });
      appView
          .options()
          .reporter
          .warning(KotlinMetadataDiagnostic.unexpectedErrorWhenRewriting(clazz.type, t));
    }
  }

  private boolean verifyRewrittenMetadataIsEquivalent(
      DexAnnotation original, DexAnnotation rewritten) {
    try {
      String originalMetadata =
          kotlinMetadataToString("", toKotlinClassMetadata(kotlin, original.annotation));
      String rewrittenMetadata =
          kotlinMetadataToString("", toKotlinClassMetadata(kotlin, rewritten.annotation));
      assert originalMetadata.equals(rewrittenMetadata) : "The metadata should be equivalent";
    } catch (KotlinMetadataException ignored) {

    }
    return true;
  }

  @SuppressWarnings("ReferenceEquality")
  private boolean kotlinMetadataFieldExists(
      DexClass kotlinMetadata, AppView<?> appView, DexString fieldName) {
    if (!appView.appInfo().hasLiveness()) {
      return true;
    }
    if (kotlinMetadata == null || kotlinMetadata.isNotProgramClass()) {
      return true;
    }
    return kotlinMetadata
        .methods(method -> method.getReference().name == fieldName)
        .iterator()
        .hasNext();
  }

  private DexAnnotation createKotlinMetadataAnnotation(
      kotlin.Metadata metadata,
      String packageName,
      int[] metadataVersion,
      WriteMetadataFieldInfo writeMetadataFieldInfo) {
    List<DexAnnotationElement> elements = new ArrayList<>();
    if (writeMetadataFieldInfo.writeMetadataVersion) {
      elements.add(
          new DexAnnotationElement(
              kotlin.metadata.metadataVersion, createIntArray(metadataVersion)));
    }
    if (writeMetadataFieldInfo.writeKind) {
      elements.add(
          new DexAnnotationElement(kotlin.metadata.kind, DexValueInt.create(metadata.k())));
    }
    if (writeMetadataFieldInfo.writeData1) {
      elements.add(
          new DexAnnotationElement(kotlin.metadata.data1, createStringArray(metadata.d1())));
    }
    if (writeMetadataFieldInfo.writeData2) {
      elements.add(
          new DexAnnotationElement(kotlin.metadata.data2, createStringArray(metadata.d2())));
    }
    if (writeMetadataFieldInfo.writePackageName && packageName != null && !packageName.isEmpty()) {
      elements.add(
          new DexAnnotationElement(
              kotlin.metadata.packageName, new DexValueString(factory.createString(packageName))));
    }
    if (writeMetadataFieldInfo.writeExtraString && !metadata.xs().isEmpty()) {
      elements.add(
          new DexAnnotationElement(
              kotlin.metadata.extraString,
              new DexValueString(factory.createString(metadata.xs()))));
    }
    if (writeMetadataFieldInfo.writeExtraInt && metadata.xi() != 0) {
      elements.add(
          new DexAnnotationElement(kotlin.metadata.extraInt, DexValueInt.create(metadata.xi())));
    }
    DexEncodedAnnotation encodedAnnotation =
        new DexEncodedAnnotation(
            factory.kotlinMetadataType, elements.toArray(DexAnnotationElement.EMPTY_ARRAY));
    return new DexAnnotation(DexAnnotation.VISIBILITY_RUNTIME, encodedAnnotation);
  }

  private DexValueArray createIntArray(int[] data) {
    DexValue[] values = new DexValue[data.length];
    for (int i = 0; i < data.length; i++) {
      values[i] = DexValueInt.create(data[i]);
    }
    return new DexValueArray(values);
  }

  private DexValueArray createStringArray(String[] data) {
    DexValue[] values = new DexValue[data.length];
    for (int i = 0; i < data.length; i++) {
      values[i] = new DexValueString(factory.createString(data[i]));
    }
    return new DexValueArray(values);
  }

  // We are not sure that the format is <Major>-<Minor>-<Patch>, the format can be: <Major>-<Minor>.
  private int[] getMaxVersion(int[] one, int[] other) {
    assert one.length == 2 || one.length == 3;
    assert other.length == 2 || other.length == 3;
    if (one[0] != other[0]) {
      return one[0] > other[0] ? one : other;
    }
    if (one[1] != other[1]) {
      return one[1] > other[1] ? one : other;
    }
    int patchOne = one.length >= 3 ? one[2] : 0;
    int patchOther = other.length >= 3 ? other[2] : 0;
    if (patchOne != patchOther) {
      return patchOne > patchOther ? one : other;
    }
    // They are equal up to patch, just return one.
    return one;
  }
}
