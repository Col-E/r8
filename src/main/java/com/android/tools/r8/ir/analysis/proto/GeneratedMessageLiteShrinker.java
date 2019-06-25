// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.naming.dexitembasedstring.FieldNameComputationInfo;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.StringDiagnostic;
import java.util.ArrayList;
import java.util.List;

public class GeneratedMessageLiteShrinker {

  private final AppView<AppInfoWithLiveness> appView;
  private final ProtoReferences references;
  private final ThrowingInfo throwingInfo;

  public GeneratedMessageLiteShrinker(AppView<AppInfoWithLiveness> appView) {
    this.appView = appView;
    this.references = appView.protoShrinker().references;
    this.throwingInfo = ThrowingInfo.defaultForConstString(appView.options());
  }

  public void run(DexEncodedMethod method, IRCode code) {
    if (appView.options().isMinifying() && references.isDynamicMethod(method.method)) {
      rewriteDynamicMethod(method, code);
    }
  }

  /**
   * Finds all const-string instructions in the code that flows into GeneratedMessageLite.
   * newMessageInfo(), and rewrites them into a dex-item-based-const-string if the string value
   * corresponds to the name of an instance field of the enclosing class.
   *
   * <p>NOTE: This is work in progress. Understanding the full semantics of the arguments passed to
   * newMessageInfo is still pending.
   */
  private void rewriteDynamicMethod(DexEncodedMethod method, IRCode code) {
    DexClass clazz = appView.definitionFor(method.method.holder);
    if (clazz == null || !clazz.isProgramClass()) {
      return;
    }

    List<ConstString> rewritingCandidates = new ArrayList<>();
    for (Instruction instruction : code.instructions()) {
      if (instruction.isInvokeStatic()) {
        InvokeStatic invoke = instruction.asInvokeStatic();
        if (invoke.getInvokedMethod() != references.newMessageInfoMethod) {
          continue;
        }
        Value array = invoke.inValues().get(2);
        for (Instruction user : array.uniqueUsers()) {
          if (!user.isArrayPut()) {
            continue;
          }
          Value rewritingCandidate = user.asArrayPut().value().getAliasedValue();
          if (rewritingCandidate.isPhi() || !rewritingCandidate.definition.isConstString()) {
            continue;
          }
          rewritingCandidates.add(rewritingCandidate.definition.asConstString());
        }
      }

      // Implicitly check that the method newMessageInfo() has not been inlined. In that case,
      // we would need to rewrite the const-string instructions that flow into the constructor
      // of com.google.protobuf.RawMessageInfo.
      assert !instruction.isNewInstance()
          || instruction.asNewInstance().clazz != references.rawMessageInfoType;
    }

    boolean changed = false;
    for (ConstString rewritingCandidate : rewritingCandidates) {
      DexString fieldName = rewritingCandidate.getValue();
      DexField field = uniqueInstanceFieldWithName(clazz, fieldName, code.origin);
      if (field == null) {
        continue;
      }
      Value newValue =
          code.createValue(
              TypeLatticeElement.stringClassType(appView, Nullability.definitelyNotNull()));
      rewritingCandidate.replace(
          new DexItemBasedConstString(
              newValue, field, FieldNameComputationInfo.forFieldName(), throwingInfo));
      changed = true;
    }

    if (changed) {
      method.getMutableOptimizationInfo().markUseIdentifierNameString();
    }
  }

  private DexField uniqueInstanceFieldWithName(DexClass clazz, DexString name, Origin origin) {
    DexField field = null;
    for (DexEncodedField encodedField : clazz.instanceFields()) {
      if (encodedField.field.name == name) {
        if (field != null) {
          Reporter reporter = appView.options().reporter;
          String errorMessage =
              "Expected to find a single instance field named \""
                  + name.toSourceString()
                  + "\" in `"
                  + clazz.type.toSourceString()
                  + "`";
          throw reporter.fatalError(new StringDiagnostic(errorMessage, origin));
        }
        field = encodedField.field;
      }
    }
    return field;
  }
}
