// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.verticalclassmerging;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.FieldPut;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Return;

/**
 * Inserts check-cast instructions after vertical class merging when this is needed for the program
 * to type check.
 *
 * <p>Any class type is assignable to any interface type. If an interface I is merged into its
 * unique (non-interface) subtype C, then assignments that used to be valid may no longer be valid
 * due to the stronger type checking imposed by the JVM. Therefore, casts are inserted where
 * necessary for the program to type check after vertical class merging.
 *
 * <p>Example: If the interface I is merged into its unique subclass C, then the invoke-interface
 * instruction will be rewritten by the {@link com.android.tools.r8.ir.conversion.LensCodeRewriter}
 * to an invoke-virtual instruction. After this rewriting, the program no longer type checks, and
 * therefore a cast is inserted before the invoke-virtual instruction: {@code C c = (C) o}.
 *
 * <pre>
 *   Object o = get();
 *   o.m(); // invoke-interface {o}, void I.m()
 * </pre>
 */
public abstract class InterfaceTypeToClassTypeLensCodeRewriterHelper {

  public static InterfaceTypeToClassTypeLensCodeRewriterHelper create(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      IRCode code,
      NonIdentityGraphLens graphLens,
      GraphLens codeLens) {
    NonIdentityGraphLens previousLens =
        graphLens.find(lens -> lens.isVerticalClassMergerLens() || lens == codeLens);
    if (previousLens != null
        && previousLens != codeLens
        && previousLens.isVerticalClassMergerLens()) {
      return new InterfaceTypeToClassTypeLensCodeRewriterHelperImpl(appView, code);
    }
    return new EmptyInterfaceTypeToClassTypeLensCodeRewriterHelper();
  }

  public abstract void insertCastsForOperandsIfNeeded(
      InvokeMethod originalInvoke,
      InvokeMethod rewrittenInvoke,
      MethodLookupResult lookupResult,
      BasicBlockIterator blockIterator,
      BasicBlock block,
      InstructionListIterator instructionIterator);

  public abstract void insertCastsForOperandsIfNeeded(
      Return rewrittenReturn,
      BasicBlockIterator blockIterator,
      BasicBlock block,
      InstructionListIterator instructionIterator);

  public abstract void insertCastsForOperandsIfNeeded(
      FieldPut originalFieldPut,
      InvokeStatic rewrittenFieldPut,
      BasicBlockIterator blockIterator,
      BasicBlock block,
      InstructionListIterator instructionIterator);

  public abstract void insertCastsForOperandsIfNeeded(
      FieldPut originalFieldPut,
      FieldPut rewrittenFieldPut,
      BasicBlockIterator blockIterator,
      BasicBlock block,
      InstructionListIterator instructionIterator);

  public abstract void processWorklist();
}
