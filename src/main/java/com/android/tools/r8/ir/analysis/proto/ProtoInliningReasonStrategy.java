// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.proto;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.proto.ProtoReferences.MethodToInvokeMembers;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.DefaultInliningOracle;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.inliner.InliningReasonStrategy;

/**
 * Equivalent to the {@link InliningReasonStrategy} in {@link #parent} except for invocations
 * to @SuppressWarnings("UnusedVariable") dynamicMethod().
 */
public class ProtoInliningReasonStrategy implements InliningReasonStrategy {

  private static final int METHOD_TO_INVOKE_ARGUMENT_POSITION_IN_DYNAMIC_METHOD = 1;

  private final InliningReasonStrategy parent;
  private final ProtoReferences references;

  public ProtoInliningReasonStrategy(AppView<?> appView, InliningReasonStrategy parent) {
    this.parent = parent;
    this.references = appView.protoShrinker().references;
  }

  @Override
  public Reason computeInliningReason(
      InvokeMethod invoke,
      ProgramMethod target,
      ProgramMethod context,
      DefaultInliningOracle oracle,
      MethodProcessor methodProcessor) {
    if (references.isAbstractGeneratedMessageLiteBuilder(context.getHolder())
        && invoke.isInvokeSuper()) {
      // Aggressively inline invoke-super calls inside the GeneratedMessageLite builders. Such
      // instructions prohibit inlining of the enclosing method into other contexts, and therefore
      // block class inlining of proto builders.
      return Reason.ALWAYS;
    }
    return references.isDynamicMethod(target) || references.isDynamicMethodBridge(target)
        ? computeInliningReasonForDynamicMethod(invoke, target, context)
        : parent.computeInliningReason(invoke, target, context, oracle, methodProcessor);
  }

  @SuppressWarnings("ReferenceEquality")
  private Reason computeInliningReasonForDynamicMethod(
      InvokeMethod invoke, ProgramMethod target, ProgramMethod context) {
    // Do not allow inlining of dynamicMethod() into a proto library class. This should only happen
    // if there is exactly one proto message in the program, since we would otherwise not be able
    // to conclude a single target.
    if (references.isDynamicMethod(target) && references.isProtoLibraryClass(context.getHolder())) {
      return Reason.NEVER;
    }

    Value methodToInvokeValue =
        invoke
            .inValues()
            .get(METHOD_TO_INVOKE_ARGUMENT_POSITION_IN_DYNAMIC_METHOD)
            .getAliasedValue();
    if (methodToInvokeValue.isPhi()) {
      return Reason.NEVER;
    }

    Instruction methodToInvokeDefinition = methodToInvokeValue.definition;
    if (!methodToInvokeDefinition.isStaticGet()) {
      return Reason.NEVER;
    }

    DexField field = methodToInvokeDefinition.asStaticGet().getField();
    MethodToInvokeMembers methodToInvokeMembers = references.methodToInvokeMembers;
    if (methodToInvokeMembers.isMethodToInvokeWithSimpleBody(field)) {
      return Reason.ALWAYS;
    }

    assert field.holder != references.methodToInvokeType
        || methodToInvokeMembers.isMethodToInvokeWithNonSimpleBody(field);

    return Reason.NEVER;
  }
}
