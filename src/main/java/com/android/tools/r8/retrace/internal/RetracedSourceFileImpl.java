// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.retrace.RetracedClassReference;
import com.android.tools.r8.retrace.RetracedSourceFile;

public class RetracedSourceFileImpl implements RetracedSourceFile {

  private final RetracedClassReference classReference;
  private final String filename;

  RetracedSourceFileImpl(RetracedClassReference classReference, String filename) {
    assert classReference != null;
    this.classReference = classReference;
    this.filename = filename;
  }

  @Override
  public boolean hasRetraceResult() {
    return filename != null;
  }

  @Override
  public String getSourceFile() {
    return filename;
  }

  @Override
  public String getOrInferSourceFile() {
    return getOrInferSourceFile(null);
  }

  @Override
  public String getOrInferSourceFile(String original) {
    String sourceFile = filename;
    return sourceFile != null
        ? sourceFile
        : RetraceUtils.inferSourceFile(
            classReference.getTypeName(),
            original == null ? "" : original,
            classReference.isKnown());
  }
}
