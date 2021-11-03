// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import org.objectweb.asm.MethodVisitor;

public interface CfWritableCode {

  enum CfWritableCodeKind {
    DEFAULT,
    DEFAULT_INSTANCE_INITIALIZER,
    THROW_NULL
  }

  default int acceptCompareTo(CfWritableCode code, CompareToVisitor visitor) {
    CfWritableCodeKind kind = getCfWritableCodeKind();
    CfWritableCodeKind otherKind = code.getCfWritableCodeKind();
    if (kind != otherKind) {
      return kind.compareTo(otherKind);
    }
    switch (kind) {
      case DEFAULT:
        return asCfCode().acceptCompareTo(code.asCfCode(), visitor);
      case DEFAULT_INSTANCE_INITIALIZER:
        return 0;
      case THROW_NULL:
        return 0;
      default:
        throw new Unreachable();
    }
  }

  void acceptHashing(HashingVisitor visitor);

  CfWritableCodeKind getCfWritableCodeKind();

  default boolean isCfCode() {
    return false;
  }

  default CfCode asCfCode() {
    return null;
  }

  void writeCf(
      ProgramMethod method,
      CfVersion classFileVersion,
      AppView<?> appView,
      NamingLens namingLens,
      LensCodeRewriterUtils rewriter,
      MethodVisitor visitor);
}
