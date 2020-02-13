// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.fieldvalueanalysis;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.ClassInitializerDefaultsOptimization.ClassInitializerDefaultsResult;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.field.EmptyInstanceFieldInitializationInfoCollection;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoCollection;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfoFactory;
import com.android.tools.r8.shaking.AppInfoWithLiveness;

public class InstanceFieldValueAnalysis extends FieldValueAnalysis {

  // Information about how this instance constructor initializes the fields on the newly created
  // instance.
  private final InstanceFieldInitializationInfoCollection.Builder builder =
      InstanceFieldInitializationInfoCollection.builder();

  private final InstanceFieldInitializationInfoFactory factory;

  private InstanceFieldValueAnalysis(
      AppView<AppInfoWithLiveness> appView,
      IRCode code,
      OptimizationFeedback feedback,
      DexProgramClass clazz,
      DexEncodedMethod method) {
    super(appView, code, feedback, clazz, method);
    factory = appView.instanceFieldInitializationInfoFactory();
  }

  /**
   * Returns information about how this instance constructor initializes the fields on the newly
   * created instance.
   */
  public static InstanceFieldInitializationInfoCollection run(
      AppView<?> appView,
      IRCode code,
      ClassInitializerDefaultsResult classInitializerDefaultsResult,
      OptimizationFeedback feedback,
      DexEncodedMethod method) {
    assert appView.appInfo().hasLiveness();
    assert appView.enableWholeProgramOptimizations();
    assert method.isInstanceInitializer();
    DexProgramClass clazz = appView.definitionFor(method.method.holder).asProgramClass();
    if (!appView.options().enableValuePropagationForInstanceFields) {
      return EmptyInstanceFieldInitializationInfoCollection.getInstance();
    }
    DexEncodedMethod otherInstanceInitializer =
        clazz.lookupDirectMethod(other -> other.isInstanceInitializer() && other != method);
    if (otherInstanceInitializer != null) {
      // Conservatively bail out.
      // TODO(b/125282093): Handle multiple instance initializers on the same class.
      return EmptyInstanceFieldInitializationInfoCollection.getInstance();
    }
    InstanceFieldValueAnalysis analysis =
        new InstanceFieldValueAnalysis(appView.withLiveness(), code, feedback, clazz, method);
    analysis.computeFieldOptimizationInfo(classInitializerDefaultsResult);
    return analysis.builder.build();
  }

  @Override
  void updateFieldOptimizationInfo(DexEncodedField field, Value value) {
    super.updateFieldOptimizationInfo(field, value);

    // If this instance field is initialized with an argument, then record this in the instance
    // field initialization info.
    Value root = value.getAliasedValue();
    if (root.isDefinedByInstructionSatisfying(Instruction::isArgument)) {
      Argument argument = root.definition.asArgument();
      builder.recordInitializationInfo(
          field, factory.createArgumentInitializationInfo(argument.getArgumentIndex()));
    }
  }
}
