// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import com.android.tools.r8.errors.Unreachable;
import java.lang.annotation.Annotation;
import kotlin.Metadata;
import kotlinx.metadata.jvm.KotlinClassMetadata;

/***
 * This is a wrapper around kotlin.Metadata needed for tests to access the internal data. The need
 * for the wrapper comes from R8 relocating kotlin.* to com.android.tools.r8.kotlin.* in R8lib but
 * not in tests, so kotlin.Metadata cannot cross the boundary.
 *
 * Additionally, it is also used for passing in an instance of kotlin.Metadata which cannot be
 * instantiated from Java.
 */
public class KotlinMetadataAnnotationWrapper implements kotlin.Metadata {

  private static final String[] NULL_STRING_ARRAY = new String[0];
  private static final int[] NULL_INT_ARRAY = new int[0];

  private final int kind;

  @SuppressWarnings("ImmutableAnnotationChecker")
  private final int[] metadataVersion;

  @SuppressWarnings("ImmutableAnnotationChecker")
  private final String[] data1;

  @SuppressWarnings("ImmutableAnnotationChecker")
  private final String[] data2;

  private final int extraInt;
  private final String extraString;
  private final String packageName;

  public KotlinMetadataAnnotationWrapper(
      Integer kind,
      int[] metadataVersion,
      String[] data1,
      String[] data2,
      String extraString,
      String packageName,
      Integer extraInt) {
    // The default values here are taking from the constructor of KotlinClassHeader.
    this.kind = kind == null ? 1 : kind;
    this.metadataVersion = metadataVersion == null ? NULL_INT_ARRAY : metadataVersion;
    this.data1 = data1 == null ? NULL_STRING_ARRAY : data1;
    this.data2 = data2 == null ? NULL_STRING_ARRAY : data2;
    this.extraString = extraString == null ? "" : extraString;
    this.packageName = packageName == null ? "" : packageName;
    this.extraInt = extraInt == null ? 0 : extraInt;
  }

  public static KotlinMetadataAnnotationWrapper wrap(KotlinClassMetadata classMetadata) {
    Metadata annotationData = classMetadata.getAnnotationData$kotlinx_metadata_jvm();
    return new KotlinMetadataAnnotationWrapper(
        annotationData.k(),
        annotationData.mv(),
        annotationData.d1(),
        annotationData.d2(),
        annotationData.xs(),
        annotationData.pn(),
        annotationData.xi());
  }

  public int kind() {
    return kind;
  }

  public String[] data1() {
    return data1;
  }

  public String[] data2() {
    return data2;
  }

  public String packageName() {
    return pn();
  }

  @Override
  public int[] bv() {
    throw new Unreachable("Field is deprecated and should not be used");
  }

  @Override
  public String[] d1() {
    return data1;
  }

  @Override
  public String[] d2() {
    return data2;
  }

  @Override
  public int xi() {
    return extraInt;
  }

  @Override
  public String xs() {
    return extraString;
  }

  @Override
  public int k() {
    return kind;
  }

  @Override
  public int[] mv() {
    return metadataVersion;
  }

  @Override
  public String pn() {
    return packageName;
  }

  @Override
  public Class<? extends Annotation> annotationType() {
    throw new Unreachable("Should never be called");
  }

  @Override
  public int hashCode() {
    throw new Unreachable();
  }

  @Override
  @SuppressWarnings("ImmutableAnnotationChecker")
  public boolean equals(Object obj) {
    throw new Unreachable();
  }
}
