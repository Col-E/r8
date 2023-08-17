// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.ListIterator;
import java.util.Set;
import java.util.function.Function;

public class DestructivePhiTypeUpdater {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final Function<DexType, DexType> mapping;

  public DestructivePhiTypeUpdater(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      GraphLens graphLens,
      GraphLens codeLens) {
    this(appView, type -> graphLens.lookupType(type, codeLens));
  }

  public DestructivePhiTypeUpdater(
      AppView<? extends AppInfoWithClassHierarchy> appView, Function<DexType, DexType> mapping) {
    this.appView = appView;
    this.mapping = mapping;
  }

  public void recomputeAndPropagateTypes(IRCode code, Set<Phi> affectedPhis) {
    // We have updated at least one type lattice element which can cause phi's to narrow to a more
    // precise type. Because cycles in phi's can occur, we have to reset all phi's before
    // computing the new values.
    Deque<Phi> worklist = new ArrayDeque<>(affectedPhis);
    while (!worklist.isEmpty()) {
      Phi phi = worklist.poll();
      phi.setType(TypeElement.getBottom());
      for (Phi affectedPhi : phi.uniquePhiUsers()) {
        if (affectedPhis.add(affectedPhi)) {
          worklist.add(affectedPhi);
        }
      }
    }
    assert verifyAllChangedPhisAreScheduled(code, affectedPhis);
    // Assuming all values have been rewritten correctly above, the non-phi operands to phi's are
    // replaced with correct types and all other phi operands are BOTTOM.
    assert verifyAllPhiOperandsAreBottom(affectedPhis);

    AffectedValues affectedValues = new AffectedValues();
    worklist.addAll(affectedPhis);
    while (!worklist.isEmpty()) {
      Phi phi = worklist.poll();
      TypeElement newType = phi.computePhiType(appView);
      if (!phi.getType().equals(newType)) {
        assert !newType.isBottom();
        phi.setType(newType);
        worklist.addAll(phi.uniquePhiUsers());
        affectedValues.addAll(phi.affectedValues());
      }
    }

    assert TypeAnalysis.verifyValuesUpToDate(appView, code, affectedPhis);

    // Now that the types of all transitively type affected phis have been reset, we can
    // perform a narrowing, starting from the values that are affected by those phis.
    affectedValues.narrowingWithAssumeRemoval(appView, code);
  }

  private boolean verifyAllPhiOperandsAreBottom(Set<Phi> affectedPhis) {
    for (Phi phi : affectedPhis) {
      for (Value operand : phi.getOperands()) {
        if (operand.isPhi()) {
          Phi operandPhi = operand.asPhi();
          TypeElement operandType = operandPhi.getType();
          assert !affectedPhis.contains(operandPhi) || operandType.isBottom();
          assert affectedPhis.contains(operandPhi)
              || operandType.isPrimitiveType()
              || operandType.isNullType()
              || (operandType.isReferenceType()
                  && operandType.fixupClassTypeReferences(appView, mapping) == operandType);
        }
      }
    }
    return true;
  }

  private boolean verifyAllChangedPhisAreScheduled(IRCode code, Set<Phi> affectedPhis) {
    ListIterator<BasicBlock> blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      for (Phi phi : block.getPhis()) {
        TypeElement phiType = phi.getType();
        TypeElement substituted = phiType.fixupClassTypeReferences(appView, mapping);
        assert substituted == phiType || affectedPhis.contains(phi);
      }
    }
    return true;
  }
}
