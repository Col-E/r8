// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.records;

import static com.android.tools.r8.ir.desugar.records.RecordRewriterHelper.isInvokeDynamicOnRecord;
import static com.android.tools.r8.ir.desugar.records.RecordRewriterHelper.parseInvokeDynamicOnRecord;

import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.DexValue.DexValueMethodHandle;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.desugar.records.RecordRewriterHelper.RecordInvokeDynamic;
import com.android.tools.r8.naming.NamingLens;
import java.util.ArrayList;

/** Used to shrink records in Cf to Cf compilations */
public class RecordCfToCfRewriter {

  private final AppView<?> appView;

  public static RecordCfToCfRewriter create(AppView<?> appView) {
    if (appView.enableWholeProgramOptimizations() && appView.options().isGeneratingClassFiles()) {
      return new RecordCfToCfRewriter(appView);
    }
    return null;
  }

  private RecordCfToCfRewriter(AppView<?> appView) {
    this.appView = appView;
  }

  // Called after final tree shaking, prune and minify field names and field values.
  public CfInvokeDynamic rewriteRecordInvokeDynamic(
      CfInvokeDynamic invokeDynamic, ProgramMethod context, NamingLens namingLens) {
    if (!isInvokeDynamicOnRecord(invokeDynamic, appView, context)) {
      return invokeDynamic;
    }
    RecordInvokeDynamic recordInvokeDynamic =
        parseInvokeDynamicOnRecord(invokeDynamic, appView, context);
    DexString newFieldNames =
        recordInvokeDynamic
            .computeRecordFieldNamesComputationInfo()
            .internalComputeNameFor(
                recordInvokeDynamic.getRecordType(), appView, appView.graphLens(), namingLens);
    DexField[] newFields = computePresentFields(appView.graphLens(), recordInvokeDynamic);
    return writeRecordInvokeDynamic(
        recordInvokeDynamic.withFieldNamesAndFields(newFieldNames, newFields));
  }

  private DexField[] computePresentFields(
      GraphLens graphLens, RecordInvokeDynamic recordInvokeDynamic) {
    ArrayList<DexField> finalFields = new ArrayList<>();
    for (DexField field : recordInvokeDynamic.getFields()) {
      DexEncodedField dexEncodedField =
          recordInvokeDynamic
              .getRecordClass()
              .lookupInstanceField(graphLens.getRenamedFieldSignature(field));
      if (dexEncodedField != null) {
        finalFields.add(field);
      }
    }
    DexField[] newFields = new DexField[finalFields.size()];
    for (int i = 0; i < finalFields.size(); i++) {
      newFields[i] = finalFields.get(i);
    }
    return newFields;
  }

  private CfInvokeDynamic writeRecordInvokeDynamic(RecordInvokeDynamic recordInvokeDynamic) {
    DexItemFactory factory = appView.dexItemFactory();
    DexMethodHandle bootstrapMethod =
        new DexMethodHandle(
            MethodHandleType.INVOKE_STATIC, factory.objectMethodsMembers.bootstrap, false, null);
    ArrayList<DexValue> bootstrapArgs = new ArrayList<>();
    bootstrapArgs.add(new DexValueType(recordInvokeDynamic.getRecordType()));
    bootstrapArgs.add(new DexValueString(recordInvokeDynamic.getFieldNames()));
    for (DexField field : recordInvokeDynamic.getFields()) {
      bootstrapArgs.add(
          new DexValueMethodHandle(
              new DexMethodHandle(MethodHandleType.INSTANCE_GET, field, false, null)));
    }
    return new CfInvokeDynamic(
        factory.createCallSite(
            recordInvokeDynamic.getMethodName(),
            recordInvokeDynamic.getMethodProto(),
            bootstrapMethod,
            bootstrapArgs));
  }
}
