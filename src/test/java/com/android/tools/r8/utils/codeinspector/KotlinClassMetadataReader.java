// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexEncodedAnnotation;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueArray;
import com.android.tools.r8.kotlin.Kotlin;
import java.util.IdentityHashMap;
import java.util.Map;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

// TODO(b/145824437): This is a dup of the same class in source to avoid the error while building
//  keep rules for r8lib. Should be able to avoid this redundancy at build configuration level or
//  change -printusage to apply mappings regarding relocated deps.
class KotlinClassMetadataReader {
  static KotlinClassMetadata toKotlinClassMetadata(
      Kotlin kotlin, DexEncodedAnnotation metadataAnnotation) {
    Map<DexString, DexAnnotationElement> elementMap = new IdentityHashMap<>();
    for (DexAnnotationElement element : metadataAnnotation.elements) {
      elementMap.put(element.name, element);
    }

    DexAnnotationElement kind = elementMap.get(kotlin.metadata.kind);
    if (kind == null) {
      throw new MetadataError("element 'k' is missing.");
    }
    Integer k = (Integer) kind.value.getBoxedValue();
    DexAnnotationElement metadataVersion = elementMap.get(kotlin.metadata.metadataVersion);
    int[] mv = metadataVersion == null ? null : getUnboxedIntArray(metadataVersion.value, "mv");
    DexAnnotationElement bytecodeVersion = elementMap.get(kotlin.metadata.bytecodeVersion);
    int[] bv = bytecodeVersion == null ? null : getUnboxedIntArray(bytecodeVersion.value, "bv");
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

    KotlinClassHeader header = new KotlinClassHeader(k, mv, bv, d1, d2, xs, pn, xi);
    return KotlinClassMetadata.read(header);
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

  private static class MetadataError extends RuntimeException {
    MetadataError(String cause) {
      super(cause);
    }
  }
}
