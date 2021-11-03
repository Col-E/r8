// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.android.tools.r8.dex.CodeToKeep;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import java.nio.ShortBuffer;

public interface DexWritableCode {

  int codeSizeInBytes();

  void collectMixedSectionItems(MixedSectionCollection mixedItems);

  void writeKeepRulesForDesugaredLibrary(CodeToKeep codeToKeep);

  DexDebugInfoForWriting getDebugInfoForWriting();

  DexString getHighestSortingString();

  TryHandler[] getHandlers();

  Try[] getTries();

  int getRegisterSize(ProgramMethod method);

  int getIncomingRegisterSize(ProgramMethod method);

  int getOutgoingRegisterSize();

  /** Rewrites the code to have JumboString bytecode if required by mapping. */
  DexWritableCode rewriteCodeWithJumboStrings(
      ProgramMethod method, ObjectToOffsetMapping mapping, DexItemFactory factory, boolean force);

  void setCallSiteContexts(ProgramMethod method);

  void writeDex(
      ShortBuffer shortBuffer,
      ProgramMethod context,
      GraphLens graphLens,
      LensCodeRewriterUtils lensCodeRewriter,
      ObjectToOffsetMapping mapping);
}
