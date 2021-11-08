// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.retrace.RetracedSourceFile;

public class RetracedSourceFileImpl implements RetracedSourceFile {

  private final ClassReference classReference;
  private final String filename;

  RetracedSourceFileImpl(ClassReference classReference, String filename) {
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
    String sourceFile = getSourceFile();
    return sourceFile != null
        ? sourceFile
        : RetraceUtils.inferSourceFile(classReference.getTypeName(), "", true);
  }
}
