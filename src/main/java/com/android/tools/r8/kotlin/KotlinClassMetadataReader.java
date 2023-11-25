// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getInvalidKotlinInfo;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getNoKotlinInfo;
import static com.android.tools.r8.kotlin.KotlinSyntheticClassInfo.getFlavour;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.kotlin.KotlinSyntheticClassInfo.Flavour;
import com.android.tools.r8.utils.StringDiagnostic;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import kotlin.Metadata;
import kotlinx.metadata.InconsistentKotlinMetadataException;
import kotlinx.metadata.jvm.KotlinClassMetadata;
import kotlinx.metadata.jvm.KotlinClassMetadata.FileFacade;
import kotlinx.metadata.jvm.KotlinClassMetadata.MultiFileClassFacade;
import kotlinx.metadata.jvm.KotlinClassMetadata.MultiFileClassPart;
import kotlinx.metadata.jvm.KotlinClassMetadata.SyntheticClass;

public final class KotlinClassMetadataReader {

  private static final int SYNTHETIC_CLASS_KIND = 3;

  public static KotlinClassLevelInfo getKotlinInfo(
      AppView<?> appView,
      DexClass clazz,
      Consumer<DexEncodedMethod> keepByteCode,
      Supplier<Boolean> reportUnknownMetadata) {
    DexAnnotation meta =
        clazz.annotations().getFirstMatching(appView.dexItemFactory().kotlinMetadataType);
    if (meta == null) {
      return getNoKotlinInfo();
    }
    return getKotlinInfoFromAnnotation(appView, clazz, meta, keepByteCode, reportUnknownMetadata);
  }

  public static KotlinClassLevelInfo getKotlinInfoFromAnnotation(
      AppView<?> appView,
      DexClass clazz,
      DexAnnotation meta,
      Consumer<DexEncodedMethod> keepByteCode,
      Supplier<Boolean> reportUnknownMetadata) {
    try {
      return getKotlinInfo(appView, clazz, keepByteCode, meta);
    } catch (KotlinMetadataException e) {
      if (reportUnknownMetadata.get()) {
        appView.reporter().warning(KotlinMetadataDiagnostic.unknownMetadataVersion());
      }
      appView
          .reporter()
          .info(
              new StringDiagnostic(
                  "Class "
                      + clazz.type.toSourceString()
                      + " has malformed kotlin.Metadata: "
                      + e.getMessage()));
      return getInvalidKotlinInfo();
    } catch (Throwable e) {
      if (reportUnknownMetadata.get()) {
        appView.reporter().warning(KotlinMetadataDiagnostic.unknownMetadataVersion());
      }
      appView
          .reporter()
          .info(
              new StringDiagnostic(
                  "Unexpected error while reading "
                      + clazz.type.toSourceString()
                      + "'s kotlin.Metadata: "
                      + e.getMessage()));
      return getNoKotlinInfo();
    }
  }

  private static KotlinClassLevelInfo getKotlinInfo(
      AppView<?> appView,
      DexClass clazz,
      Consumer<DexEncodedMethod> keepByteCode,
      DexAnnotation annotation)
      throws KotlinMetadataException {
    Kotlin kotlin = appView.dexItemFactory().kotlin;
    KotlinClassMetadata kMetadata = toKotlinClassMetadata(kotlin, annotation.annotation);
    if (kMetadata == null) {
      throw new KotlinMetadataException(
          new Exception("Could not parse metadata for " + clazz.toSourceString()));
    }
    return createKotlinInfo(kotlin, clazz, kMetadata, appView, keepByteCode);
  }

  public static boolean isLambda(
      AppView<?> appView, DexClass clazz, Supplier<Boolean> reportUnknownMetadata) {
    DexItemFactory dexItemFactory = appView.dexItemFactory();
    Kotlin kotlin = dexItemFactory.kotlin;
    Flavour flavour = getFlavour(clazz, kotlin);
    if (flavour == Flavour.Unclassified) {
      return false;
    }
    DexAnnotation metadataAnnotation =
        clazz.annotations().getFirstMatching(dexItemFactory.kotlinMetadataType);
    if (metadataAnnotation == null) {
      return false;
    }
    Map<DexString, DexAnnotationElement> elementMap = toElementMap(metadataAnnotation.annotation);
    try {
      if (getKind(kotlin, elementMap) == SYNTHETIC_CLASS_KIND) {
        KotlinClassMetadata kMetadata = toKotlinClassMetadata(kotlin, elementMap);
        if (kMetadata instanceof SyntheticClass) {
          return ((SyntheticClass) kMetadata).isLambda();
        }
      }
      assert toKotlinClassMetadata(kotlin, elementMap) instanceof SyntheticClass
              == (getKind(kotlin, elementMap) == SYNTHETIC_CLASS_KIND)
          : "Synthetic class kinds should agree";
      return false;
    } catch (KotlinMetadataException exception) {
      if (reportUnknownMetadata.get()) {
        appView.reporter().warning(KotlinMetadataDiagnostic.unknownMetadataVersion());
      }
      appView
          .reporter()
          .info(
              new StringDiagnostic(
                  "Class "
                      + clazz.type.toSourceString()
                      + " has malformed kotlin.Metadata: "
                      + exception.getMessage()));
      return false;
    }
  }

  public static boolean hasKotlinClassMetadataAnnotation(
      DexClass clazz, DexDefinitionSupplier definitionSupplier) {
    return clazz
        .annotations()
        .hasAnnotation(definitionSupplier.dexItemFactory().kotlinMetadataType);
  }

  public static KotlinClassMetadata toKotlinClassMetadata(
      Kotlin kotlin, DexEncodedAnnotation metadataAnnotation) throws KotlinMetadataException {
    return toKotlinClassMetadata(kotlin, toElementMap(metadataAnnotation));
  }

  private static Map<DexString, DexAnnotationElement> toElementMap(
      DexEncodedAnnotation metadataAnnotation) {
    Map<DexString, DexAnnotationElement> elementMap = new IdentityHashMap<>();
    for (DexAnnotationElement element : metadataAnnotation.elements) {
      elementMap.put(element.name, element);
    }
    return elementMap;
  }

  private static KotlinClassMetadata toKotlinClassMetadata(
      Kotlin kotlin, Map<DexString, DexAnnotationElement> elementMap)
      throws KotlinMetadataException {
    int k = getKind(kotlin, elementMap);
    DexAnnotationElement metadataVersion = elementMap.get(kotlin.metadata.metadataVersion);
    int[] mv = metadataVersion == null ? null : getUnboxedIntArray(metadataVersion.value, "mv");
    DexAnnotationElement data1 = elementMap.get(kotlin.metadata.data1);
    String[] d1 = data1 == null ? null : getUnboxedStringArray(data1.value, "d1");
    DexAnnotationElement data2 = elementMap.get(kotlin.metadata.data2);
    String[] d2 = data2 == null ? null : getUnboxedStringArray(data2.value, "d2");
    DexAnnotationElement extraString = elementMap.get(kotlin.metadata.extraString);
    String xs = extraString == null ? null : getUnboxedString(extraString.value, "xs");
    DexAnnotationElement packageName = elementMap.get(kotlin.metadata.packageName);
    String pn = packageName == null ? null : getUnboxedString(packageName.value, "pn");
    DexAnnotationElement extraInt = elementMap.get(kotlin.metadata.extraInt);
    Integer xi = extraInt == null ? null : (Integer) extraInt.value.getBoxedValue();

    try {
      return KotlinClassMetadata.read(
          new KotlinMetadataAnnotationWrapper(k, mv, d1, d2, xs, pn, xi));
    } catch (ClassCastException | InconsistentKotlinMetadataException | MetadataError e) {
      throw new KotlinMetadataException(e);
    }
  }

  private static int getKind(Kotlin kotlin, Map<DexString, DexAnnotationElement> elementMap) {
    DexAnnotationElement kind = elementMap.get(kotlin.metadata.kind);
    if (kind == null) {
      throw new MetadataError("element 'k' is missing.");
    }
    return (Integer) kind.value.getBoxedValue();
  }

  public static KotlinClassLevelInfo createKotlinInfo(
      Kotlin kotlin,
      DexClass clazz,
      KotlinClassMetadata kMetadata,
      AppView<?> appView,
      Consumer<DexEncodedMethod> keepByteCode) {
    Metadata metadata = kMetadata.getAnnotationData$kotlinx_metadata_jvm();
    String packageName = metadata.pn();
    int[] metadataVersion = metadata.mv();
    if (kMetadata instanceof KotlinClassMetadata.Class) {
      return KotlinClassInfo.create(
          (KotlinClassMetadata.Class) kMetadata,
          packageName,
          metadataVersion,
          clazz,
          appView,
          keepByteCode);
    } else if (kMetadata instanceof KotlinClassMetadata.FileFacade) {
      // e.g., B.kt becomes class `BKt`
      return KotlinFileFacadeInfo.create(
          (FileFacade) kMetadata, packageName, metadataVersion, clazz, appView, keepByteCode);
    } else if (kMetadata instanceof KotlinClassMetadata.MultiFileClassFacade) {
      // multi-file class with the same @JvmName.
      return KotlinMultiFileClassFacadeInfo.create(
          (MultiFileClassFacade) kMetadata, packageName, metadataVersion, appView.dexItemFactory());
    } else if (kMetadata instanceof KotlinClassMetadata.MultiFileClassPart) {
      // A single file, which is part of multi-file class.
      return KotlinMultiFileClassPartInfo.create(
          (MultiFileClassPart) kMetadata,
          packageName,
          metadataVersion,
          clazz,
          appView,
          keepByteCode);
    } else if (kMetadata instanceof KotlinClassMetadata.SyntheticClass) {
      return KotlinSyntheticClassInfo.create(
          (KotlinClassMetadata.SyntheticClass) kMetadata,
          packageName,
          metadataVersion,
          clazz,
          kotlin,
          appView);
    } else {
      throw new MetadataError("unsupported 'k' value: " + metadata.k());
    }
  }

  private static int[] getUnboxedIntArray(DexValue v, String elementName) {
    if (!v.isDexValueArray()) {
      throw new MetadataError("invalid '" + elementName + "' value: " + v.toSourceString());
    }
    DexValueArray intArrayValue = v.asDexValueArray();
    DexValue[] values = intArrayValue.getValues();
    int[] result = new int [values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = (Integer) values[i].getBoxedValue();
    }
    return result;
  }

  private static String[] getUnboxedStringArray(DexValue v, String elementName) {
    if (!v.isDexValueArray()) {
      throw new MetadataError("invalid '" + elementName + "' value: " + v.toSourceString());
    }
    DexValueArray stringArrayValue = v.asDexValueArray();
    DexValue[] values = stringArrayValue.getValues();
    String[] result = new String [values.length];
    for (int i = 0; i < values.length; i++) {
      result[i] = getUnboxedString(values[i], elementName + "[" + i + "]");
    }
    return result;
  }

  private static String getUnboxedString(DexValue v, String elementName) {
    if (!v.isDexValueString()) {
      throw new MetadataError("invalid '" + elementName + "' value: " + v.toSourceString());
    }
    return v.asDexValueString().getValue().toString();
  }

  public static class MetadataError extends RuntimeException {
    private MetadataError(String cause) {
      super(cause);
    }
  }
}
