// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.ProguardConfiguration;
import java.util.Set;

public class LogMethodOptimizer implements LibraryMethodModelCollection {

  private static final int VERBOSE = 2;
  private static final int DEBUG = 3;
  private static final int INFO = 4;
  private static final int WARN = 5;
  private static final int ERROR = 6;
  private static final int ASSERT = 7;

  private final AppView<?> appView;

  private final DexType logType;

  private final DexMethod isLoggableMethod;
  private final DexMethod vMethod;
  private final DexMethod dMethod;
  private final DexMethod iMethod;
  private final DexMethod wMethod;
  private final DexMethod eMethod;
  private final DexMethod wtfMethod;

  LogMethodOptimizer(AppView<?> appView) {
    this.appView = appView;

    DexItemFactory dexItemFactory = appView.dexItemFactory();
    DexType logType = dexItemFactory.androidUtilLogType;
    this.logType = logType;
    this.isLoggableMethod =
        dexItemFactory.createMethod(
            logType,
            dexItemFactory.createProto(
                dexItemFactory.booleanType, dexItemFactory.stringType, dexItemFactory.intType),
            "isLoggable");
    this.vMethod =
        dexItemFactory.createMethod(
            logType,
            dexItemFactory.createProto(
                dexItemFactory.intType, dexItemFactory.stringType, dexItemFactory.stringType),
            "v");
    this.dMethod =
        dexItemFactory.createMethod(
            logType,
            dexItemFactory.createProto(
                dexItemFactory.intType, dexItemFactory.stringType, dexItemFactory.stringType),
            "d");
    this.iMethod =
        dexItemFactory.createMethod(
            logType,
            dexItemFactory.createProto(
                dexItemFactory.intType, dexItemFactory.stringType, dexItemFactory.stringType),
            "i");
    this.wMethod =
        dexItemFactory.createMethod(
            logType,
            dexItemFactory.createProto(
                dexItemFactory.intType, dexItemFactory.stringType, dexItemFactory.stringType),
            "w");
    this.eMethod =
        dexItemFactory.createMethod(
            logType,
            dexItemFactory.createProto(
                dexItemFactory.intType, dexItemFactory.stringType, dexItemFactory.stringType),
            "e");
    this.wtfMethod =
        dexItemFactory.createMethod(
            logType,
            dexItemFactory.createProto(
                dexItemFactory.intType, dexItemFactory.stringType, dexItemFactory.stringType),
            "wtf");
  }

  public static boolean isEnabled(AppView<?> appView) {
    ProguardConfiguration proguardConfiguration = appView.options().getProguardConfiguration();
    return proguardConfiguration != null
        && proguardConfiguration.getMaxRemovedAndroidLogLevel() >= VERBOSE;
  }

  @Override
  public DexType getType() {
    return logType;
  }

  @Override
  public void optimize(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      Set<Value> affectedValues) {
    int maxRemovedAndroidLogLevel =
        appView.options().getProguardConfiguration().getMaxRemovedAndroidLogLevel();
    if (singleTarget.getReference() == isLoggableMethod) {
      Value logLevelValue = invoke.arguments().get(1).getAliasedValue();
      if (!logLevelValue.isPhi() && !logLevelValue.hasLocalInfo()) {
        Instruction definition = logLevelValue.definition;
        if (definition.isConstNumber()) {
          int logLevel = definition.asConstNumber().getIntValue();
          replaceInvokeWithConstNumber(
              code, instructionIterator, invoke, maxRemovedAndroidLogLevel >= logLevel ? 0 : 1);
        }
      }
    } else if (singleTarget.getReference() == vMethod) {
      if (maxRemovedAndroidLogLevel >= VERBOSE) {
        replaceInvokeWithConstNumber(code, instructionIterator, invoke, 0);
      }
    } else if (singleTarget.getReference() == dMethod) {
      if (maxRemovedAndroidLogLevel >= DEBUG) {
        replaceInvokeWithConstNumber(code, instructionIterator, invoke, 0);
      }
    } else if (singleTarget.getReference() == iMethod) {
      if (maxRemovedAndroidLogLevel >= INFO) {
        replaceInvokeWithConstNumber(code, instructionIterator, invoke, 0);
      }
    } else if (singleTarget.getReference() == wMethod) {
      if (maxRemovedAndroidLogLevel >= WARN) {
        replaceInvokeWithConstNumber(code, instructionIterator, invoke, 0);
      }
    } else if (singleTarget.getReference() == eMethod) {
      if (maxRemovedAndroidLogLevel >= ERROR) {
        replaceInvokeWithConstNumber(code, instructionIterator, invoke, 0);
      }
    } else if (singleTarget.getReference() == wtfMethod) {
      if (maxRemovedAndroidLogLevel >= ASSERT) {
        replaceInvokeWithConstNumber(code, instructionIterator, invoke, 0);
      }
    }
  }

  private void replaceInvokeWithConstNumber(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethod invoke, int value) {
    if (invoke.hasOutValue() && invoke.outValue().hasAnyUsers()) {
      instructionIterator.replaceCurrentInstructionWithConstInt(code, value);
    } else {
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }
}
