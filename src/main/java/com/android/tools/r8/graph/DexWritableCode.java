// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.dex.CodeToKeep;
import com.android.tools.r8.dex.IndexedItemCollection;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import java.nio.ShortBuffer;

public interface DexWritableCode {

  enum DexWritableCodeKind {
    DEFAULT,
    DEFAULT_INSTANCE_INITIALIZER,
    THROW_NULL,
    THROW_EXCEPTION
  }

  boolean isThrowExceptionCode();

  ThrowExceptionCode asThrowExceptionCode();

  default int acceptCompareTo(DexWritableCode code, CompareToVisitor visitor) {
    DexWritableCodeKind kind = getDexWritableCodeKind();
    DexWritableCodeKind otherKind = code.getDexWritableCodeKind();
    if (kind != otherKind) {
      return kind.compareTo(otherKind);
    }
    switch (kind) {
      case DEFAULT:
        return asDexCode().acceptCompareTo(code.asDexCode(), visitor);
      case DEFAULT_INSTANCE_INITIALIZER:
        return 0;
      case THROW_NULL:
        return 0;
      case THROW_EXCEPTION:
        assert isThrowExceptionCode();
        return asThrowExceptionCode().acceptCompareTo(code.asThrowExceptionCode(), visitor);
      default:
        throw new Unreachable();
    }
  }

  void acceptHashing(HashingVisitor visitor);

  int codeSizeInBytes();

  void collectIndexedItems(
      AppView<?> appView,
      IndexedItemCollection indexedItems,
      ProgramMethod context,
      LensCodeRewriterUtils rewriter);

  void collectMixedSectionItems(MixedSectionCollection mixedItems);

  void writeKeepRulesForDesugaredLibrary(CodeToKeep codeToKeep);

  GraphLens getCodeLens(AppView<?> appView);

  DexDebugInfoForWriting getDebugInfoForWriting();

  DexWritableCodeKind getDexWritableCodeKind();

  DexString getHighestSortingString();

  TryHandler[] getHandlers();

  Try[] getTries();

  int getRegisterSize(ProgramMethod method);

  int getIncomingRegisterSize(ProgramMethod method);

  int getOutgoingRegisterSize();

  Code asCode();

  default boolean isDexCode() {
    return false;
  }

  default DexCode asDexCode() {
    return null;
  }

  /** Rewrites the code to have JumboString bytecode if required by mapping. */
  DexWritableCode rewriteCodeWithJumboStrings(
      ProgramMethod method, ObjectToOffsetMapping mapping, DexItemFactory factory, boolean force);

  void setCallSiteContexts(ProgramMethod method);

  void writeDex(
      ShortBuffer shortBuffer,
      ProgramMethod context,
      GraphLens graphLens,
      GraphLens codeLens,
      LensCodeRewriterUtils lensCodeRewriter,
      ObjectToOffsetMapping mapping);
}
