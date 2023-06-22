// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.positions;

import com.android.tools.r8.ResourceException;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser;
import com.android.tools.r8.kotlin.KotlinSourceDebugExtensionParser.Result;
import com.android.tools.r8.utils.CfLineToMethodMapper;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.InternalOptions.LineNumberOptimization;
import com.android.tools.r8.utils.Pair;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Map.Entry;

// PositionRemapper is a stateful function which takes a position (represented by a
// DexDebugPositionState) and returns a remapped Position.
public interface PositionRemapper {

  Pair<Position, Position> createRemappedPosition(Position position);

  static PositionRemapper getPositionRemapper(
      AppView<?> appView, CfLineToMethodMapper cfLineToMethodMapper) {
    boolean identityMapping =
        appView.options().lineNumberOptimization == LineNumberOptimization.OFF;
    PositionRemapper positionRemapper =
        identityMapping
            ? new IdentityPositionRemapper()
            : new OptimizingPositionRemapper(appView.options());

    // Kotlin inline functions and arguments have their inlining information stored in the
    // source debug extension annotation. Instantiate the kotlin remapper on top of the original
    // remapper to allow for remapping original positions to kotlin inline positions.
    return new KotlinInlineFunctionPositionRemapper(
        appView, positionRemapper, cfLineToMethodMapper);
  }

  void setCurrentMethod(DexEncodedMethod definition);

  class IdentityPositionRemapper implements PositionRemapper {

    @Override
    public Pair<Position, Position> createRemappedPosition(Position position) {
      // If we create outline calls we have to map them.
      assert position.getOutlineCallee() == null;
      return new Pair<>(position, position);
    }

    @Override
    public void setCurrentMethod(DexEncodedMethod definition) {
      // This has no effect.
    }
  }

  class OptimizingPositionRemapper implements PositionRemapper {
    private final int maxLineDelta;
    private DexMethod previousMethod = null;
    private int previousSourceLine = -1;
    private int nextOptimizedLineNumber = 1;

    OptimizingPositionRemapper(InternalOptions options) {
      // TODO(113198295): For dex using "Constants.DBG_LINE_RANGE + Constants.DBG_LINE_BASE"
      // instead of 1 creates a ~30% smaller map file but the dex files gets larger due to reduced
      // debug info canonicalization.
      maxLineDelta = options.isGeneratingClassFiles() ? Integer.MAX_VALUE : 1;
    }

    @Override
    public Pair<Position, Position> createRemappedPosition(Position position) {
      assert position.getMethod() != null;
      if (previousMethod == position.getMethod()) {
        assert previousSourceLine >= 0;
        if (position.getLine() > previousSourceLine
            && position.getLine() - previousSourceLine <= maxLineDelta) {
          nextOptimizedLineNumber += (position.getLine() - previousSourceLine) - 1;
        }
      }

      Position newPosition =
          position
              .builderWithCopy()
              .setLine(nextOptimizedLineNumber++)
              .setCallerPosition(null)
              .build();
      previousSourceLine = position.getLine();
      previousMethod = position.getMethod();
      return new Pair<>(position, newPosition);
    }

    @Override
    public void setCurrentMethod(DexEncodedMethod definition) {
      // This has no effect.
    }
  }

  class KotlinInlineFunctionPositionRemapper implements PositionRemapper {

    private final AppView<?> appView;
    private final DexItemFactory factory;
    private final Map<DexType, Result> parsedKotlinSourceDebugExtensions = new IdentityHashMap<>();
    private final CfLineToMethodMapper lineToMethodMapper;
    private final PositionRemapper baseRemapper;

    // Fields for the current context.
    private DexEncodedMethod currentMethod;
    private Result parsedData = null;

    private KotlinInlineFunctionPositionRemapper(
        AppView<?> appView,
        PositionRemapper baseRemapper,
        CfLineToMethodMapper lineToMethodMapper) {
      this.appView = appView;
      this.factory = appView.dexItemFactory();
      this.baseRemapper = baseRemapper;
      this.lineToMethodMapper = lineToMethodMapper;
    }

    @Override
    public Pair<Position, Position> createRemappedPosition(Position position) {
      assert currentMethod != null;
      int line = position.getLine();
      Result parsedData = getAndParseSourceDebugExtension(position.getMethod().holder);
      if (parsedData == null) {
        return baseRemapper.createRemappedPosition(position);
      }
      Map.Entry<Integer, KotlinSourceDebugExtensionParser.Position> inlinedPosition =
          parsedData.lookupInlinedPosition(line);
      if (inlinedPosition == null) {
        return baseRemapper.createRemappedPosition(position);
      }
      int inlineeLineDelta = line - inlinedPosition.getKey();
      int originalInlineeLine = inlinedPosition.getValue().getRange().from + inlineeLineDelta;
      try {
        String binaryName = inlinedPosition.getValue().getSource().getPath();
        String nameAndDescriptor =
            lineToMethodMapper.lookupNameAndDescriptor(binaryName, originalInlineeLine);
        if (nameAndDescriptor == null) {
          return baseRemapper.createRemappedPosition(position);
        }
        String clazzDescriptor = DescriptorUtils.getDescriptorFromClassBinaryName(binaryName);
        String methodName = CfLineToMethodMapper.getName(nameAndDescriptor);
        String methodDescriptor = CfLineToMethodMapper.getDescriptor(nameAndDescriptor);
        String returnTypeDescriptor = DescriptorUtils.getReturnTypeDescriptor(methodDescriptor);
        String[] argumentDescriptors = DescriptorUtils.getArgumentTypeDescriptors(methodDescriptor);
        DexString[] argumentDexStringDescriptors = new DexString[argumentDescriptors.length];
        for (int i = 0; i < argumentDescriptors.length; i++) {
          argumentDexStringDescriptors[i] = factory.createString(argumentDescriptors[i]);
        }
        DexMethod inlinee =
            factory.createMethod(
                factory.createString(clazzDescriptor),
                factory.createString(methodName),
                factory.createString(returnTypeDescriptor),
                argumentDexStringDescriptors);
        if (!inlinee.equals(position.getMethod())) {
          // We have an inline from a different method than the current position.
          Entry<Integer, KotlinSourceDebugExtensionParser.Position> calleePosition =
              parsedData.lookupCalleePosition(line);
          if (calleePosition != null) {
            // Take the first line as the callee position
            position =
                position
                    .builderWithCopy()
                    .setLine(calleePosition.getValue().getRange().from)
                    .build();
          }
          return baseRemapper.createRemappedPosition(
              SourcePosition.builder()
                  .setLine(originalInlineeLine)
                  .setMethod(inlinee)
                  .setCallerPosition(position)
                  .build());
        }
        // This is the same position, so we should really not mark this as an inline position. Fall
        // through to the default case.
      } catch (ResourceException ignored) {
        // Intentionally left empty. Remapping of kotlin functions utility is a best effort mapping.
      }
      return baseRemapper.createRemappedPosition(position);
    }

    private Result getAndParseSourceDebugExtension(DexType holder) {
      if (parsedData == null) {
        parsedData = parsedKotlinSourceDebugExtensions.get(holder);
      }
      if (parsedData != null || parsedKotlinSourceDebugExtensions.containsKey(holder)) {
        return parsedData;
      }
      DexClass clazz = appView.definitionFor(currentMethod.getHolderType());
      DexValueString dexValueString = appView.getSourceDebugExtensionForType(clazz);
      if (dexValueString != null) {
        parsedData = KotlinSourceDebugExtensionParser.parse(dexValueString.value.toString());
      }
      parsedKotlinSourceDebugExtensions.put(holder, parsedData);
      return parsedData;
    }

    @Override
    public void setCurrentMethod(DexEncodedMethod method) {
      this.currentMethod = method;
      this.parsedData = null;
    }
  }
}
