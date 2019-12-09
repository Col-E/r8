// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfLabel;
import com.android.tools.r8.cf.code.CfPosition;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.CfCode.LocalVariableInfo;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser.Result;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Multimaps;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.function.Predicate;

public class SourceDebugExtensionRewriter {

  private static final String SYNTHETIC_INLINE_FUNCTION_NAME_PREFIX = "$i$f$";

  private final AppView<?> appView;
  private final DexItemFactory factory;

  public SourceDebugExtensionRewriter(AppView<?> appView) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
  }

  public SourceDebugExtensionRewriter analyze(Predicate<DexProgramClass> shouldProcess) {
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (!shouldProcess.test(clazz)) {
        continue;
      }
      DexAnnotation sourceDebug =
          clazz.annotations.getFirstMatching(factory.annotationSourceDebugExtension);
      if (sourceDebug == null || sourceDebug.annotation.elements.length != 1) {
        continue;
      }
      DexValueString dexValueString = sourceDebug.annotation.elements[0].value.asDexValueString();
      if (dexValueString == null) {
        continue;
      }
      Result parsedData = KotlinSourceDebugExtensionParser.parse(dexValueString.value.toString());
      if (parsedData == null) {
        continue;
      }
      for (DexEncodedMethod method : clazz.methods()) {
        if (method.getCode().isCfCode()) {
          processMethod(method, parsedData);
        }
      }
    }
    return this;
  }

  private static class Context {

    private Position currentPosition = null;
    private LocalVariableInfo localVariableInliningInfoEntry = null;
    private final Stack<CfLabel> endRangeLabels = new Stack<>();
    private final List<CfInstruction> resultingList;
    private final ImmutableListMultimap<CfLabel, LocalVariableInfo> localVariableInfoStartMap;
    private int lastPosition = -1;

    Context(
        int initialSize,
        ImmutableListMultimap<CfLabel, LocalVariableInfo> localVariableInfoStartMap) {
      this.resultingList = new ArrayList<>(initialSize);
      this.localVariableInfoStartMap = localVariableInfoStartMap;
    }

    String getInlinedFunctionName() {
      return localVariableInliningInfoEntry
          .getLocal()
          .name
          .toString()
          .substring(SYNTHETIC_INLINE_FUNCTION_NAME_PREFIX.length());
    }
  }

  private void processMethod(DexEncodedMethod method, Result parsedData) {
    CfCode cfCode = method.getCode().asCfCode();
    Context context =
        new Context(
            cfCode.getInstructions().size() + parsedData.getPositions().size(),
            Multimaps.index(cfCode.getLocalVariables(), LocalVariableInfo::getStart));
    for (CfInstruction instruction : cfCode.getInstructions()) {
      if (instruction.isLabel()) {
        handleLabel(context, instruction.asLabel());
      } else if (instruction.isPosition()
          && (context.currentPosition != null || context.localVariableInliningInfoEntry != null)) {
        handlePosition(context, instruction.asPosition(), parsedData);
      } else {
        context.resultingList.add(instruction);
      }
    }
    cfCode.instructions = context.resultingList;
  }

  private void handleLabel(Context context, CfLabel label) {
    ImmutableList<LocalVariableInfo> localVariableInfos =
        context.localVariableInfoStartMap.get(label);
    if (localVariableInfos != null) {
      LocalVariableInfo newLocalVariableInliningInfo = null;
      for (LocalVariableInfo localVariableInfo : localVariableInfos) {
        String localVariableName = localVariableInfo.getLocal().name.toString();
        if (!localVariableName.startsWith(SYNTHETIC_INLINE_FUNCTION_NAME_PREFIX)) {
          continue;
        }
        // Only one synthetic inlining label for a position should exist.
        assert newLocalVariableInliningInfo == null;
        newLocalVariableInliningInfo = localVariableInfo;
      }
      context.localVariableInliningInfoEntry = newLocalVariableInliningInfo;
    }
    while (!context.endRangeLabels.empty() && context.endRangeLabels.peek() == label) {
      // The inlined range is ending here. Multiple inline ranges can end at the same label.
      assert context.currentPosition != null;
      context.currentPosition = context.currentPosition.callerPosition;
      context.endRangeLabels.pop();
    }
    // Ensure endRangeLabels are in sync with the current position.
    assert !context.endRangeLabels.empty() || context.currentPosition == null;
    context.resultingList.add(label);
  }

  private void handlePosition(Context context, CfPosition position, Result parsedData) {
    if (context.localVariableInliningInfoEntry != null) {
      // This is potentially a new inlining frame.
      KotlinSourceDebugExtensionParser.Position parsedInlinePosition =
          parsedData.getPositions().get(position.getPosition().line);
      if (parsedInlinePosition != null) {
        String descriptor = "L" + parsedInlinePosition.getSource().getPath() + ";";
        if (DescriptorUtils.isClassDescriptor(descriptor)) {
          // This is a new inline function. Build up the inlining information from the parsed data
          // and the local variable table.
          DexType sourceHolder = factory.createType(descriptor);
          final String inlinee = context.getInlinedFunctionName();
          // TODO(b/145904809): See if we can find the inline function.
          DexMethod syntheticExistingMethod =
              factory.createMethod(
                  sourceHolder,
                  factory.createProto(factory.voidType),
                  factory.createString(inlinee));
          context.currentPosition =
              new Position(
                  parsedInlinePosition.getRange().from,
                  null,
                  syntheticExistingMethod,
                  context.currentPosition);
          context.endRangeLabels.push(context.localVariableInliningInfoEntry.getEnd());
          context.lastPosition = position.getPosition().line;
        }
      }
      context.localVariableInliningInfoEntry = null;
    }
    if (context.currentPosition != null) {
      // We have a line-entry in a mapped range. Make sure to increment the index according to
      // the delta in the inlined source.
      Position currentPosition = context.currentPosition;
      assert context.lastPosition > -1;
      int delta = position.getPosition().line - context.lastPosition;
      context.currentPosition =
          new Position(
              context.currentPosition.line + delta,
              null,
              currentPosition.method,
              currentPosition.callerPosition);
      // Append the original line index as the current caller context.
      context.resultingList.add(
          new CfPosition(
              position.getLabel(),
              appendAsOuterMostCaller(context.currentPosition, position.getPosition())));
    } else {
      context.resultingList.add(position);
    }
  }

  private Position appendAsOuterMostCaller(Position position, Position callerPosition) {
    if (position == null) {
      return callerPosition;
    } else {
      return new Position(
          position.line,
          position.file,
          position.method,
          appendAsOuterMostCaller(position.callerPosition, callerPosition));
    }
  }
}
