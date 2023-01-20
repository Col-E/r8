// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import kotlinx.metadata.jvm.KotlinClassMetadata;

/***
 * This is a wrapper around kotlin.Metadata needed for tests to access the internal data. The need
 * for the wrapper comes from R8 relocating kotlin.* to com.android.tools.r8.kotlin.* in R8lib but
 * not in tests, so kotlin.Metadata cannot cross the boundary.
 */
public class KotlinMetadataAnnotationWrapper {

  private final kotlin.Metadata metadata;

  private KotlinMetadataAnnotationWrapper(kotlin.Metadata metadata) {
    this.metadata = metadata;
  }

  public static KotlinMetadataAnnotationWrapper wrap(KotlinClassMetadata classMetadata) {
    return new KotlinMetadataAnnotationWrapper(classMetadata.getAnnotationData());
  }

  public int kind() {
    return metadata.k();
  }

  public String[] data1() {
    return metadata.d1();
  }

  public String[] data2() {
    return metadata.d2();
  }

  public String packageName() {
    return metadata.pn();
  }
}
