// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.cf.code;

import static com.android.tools.r8.utils.BiPredicateUtils.or;

import com.android.tools.r8.cf.code.CfFrame.FrameType;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCodeStackMapValidatingException;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.MapUtils;
import com.android.tools.r8.utils.collections.ImmutableDeque;
import com.android.tools.r8.utils.collections.ImmutableInt2ReferenceSortedMap;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceAVLTreeMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceSortedMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

public class CfFrameVerificationHelper {

  private static final CfFrame NO_FRAME =
      new CfFrame(
          ImmutableInt2ReferenceSortedMap.<FrameType>builder().build(), ImmutableDeque.of());

  private final AppView<?> appView;
  private final DexItemFactory factory;

  private CfFrame currentFrame = NO_FRAME;
  private final DexType context;
  private final Map<CfLabel, CfFrame> stateMap;
  private final List<CfTryCatch> tryCatchRanges;
  private final int maxStackHeight;

  private final Deque<CfTryCatch> currentCatchRanges = new ArrayDeque<>();
  private final Set<CfLabel> tryCatchRangeLabels;

  public CfFrameVerificationHelper(
      AppView<?> appView,
      DexType context,
      Map<CfLabel, CfFrame> stateMap,
      List<CfTryCatch> tryCatchRanges,
      int maxStackHeight) {
    this.appView = appView;
    this.context = context;
    this.stateMap = stateMap;
    this.tryCatchRanges = tryCatchRanges;
    this.factory = appView.dexItemFactory();
    this.maxStackHeight = maxStackHeight;
    // Compute all labels that marks a start or end to catch ranges.
    tryCatchRangeLabels = Sets.newIdentityHashSet();
    for (CfTryCatch tryCatchRange : tryCatchRanges) {
      tryCatchRangeLabels.add(tryCatchRange.start);
      tryCatchRangeLabels.add(tryCatchRange.end);
    }
  }

  public FrameType readLocal(int index, DexType expectedType) {
    checkFrameIsSet();
    FrameType frameType = currentFrame.getLocals().get(index);
    if (frameType == null) {
      throw CfCodeStackMapValidatingException.error("No local at index " + index);
    }
    checkIsAssignable(
        frameType,
        expectedType,
        or(
            this::isUninitializedThisAndTarget,
            this::isUninitializedNewAndTarget,
            this::isAssignableAndInitialized));
    return frameType;
  }

  public void storeLocal(int index, FrameType frameType) {
    checkFrameIsSet();
    currentFrame.getLocals().put(index, frameType);
  }

  public FrameType pop() {
    checkFrameIsSet();
    if (currentFrame.getStack().isEmpty()) {
      throw CfCodeStackMapValidatingException.error("Cannot pop() from an empty stack");
    }
    return currentFrame.getStack().removeLast();
  }

  public FrameType popInitialized(DexType expectedType) {
    return pop(expectedType, this::isAssignableAndInitialized);
  }

  public FrameType pop(DexType expectedType, BiPredicate<FrameType, DexType> isAssignable) {
    FrameType frameType = pop();
    checkIsAssignable(frameType, expectedType, isAssignable);
    return frameType;
  }

  public CfFrameVerificationHelper popAndDiscardInitialized(DexType expectedType) {
    checkFrameIsSet();
    popInitialized(expectedType);
    return this;
  }

  public CfFrameVerificationHelper popAndDiscardInitialized(DexType... expectedTypes) {
    checkFrameIsSet();
    for (int i = expectedTypes.length - 1; i >= 0; i--) {
      popInitialized(expectedTypes[i]);
    }
    return this;
  }

  public FrameType pop(FrameType expectedType) {
    FrameType frameType = pop();
    checkIsAssignable(frameType, expectedType);
    return frameType;
  }

  public CfFrameVerificationHelper popAndDiscard(FrameType... expectedTypes) {
    checkFrameIsSet();
    for (int i = expectedTypes.length - 1; i >= 0; i--) {
      pop(expectedTypes[i]);
    }
    return this;
  }

  public void popAndInitialize(DexType context, DexType methodHolder) {
    checkFrameIsSet();
    FrameType objectRef =
        pop(
            factory.objectType,
            or(this::isUninitializedThisAndTarget, this::isUninitializedNewAndTarget));
    CfFrame newFrame =
        currentFrame.markInstantiated(
            objectRef, objectRef.isUninitializedNew() ? methodHolder : context);
    setNoFrame();
    checkFrameAndSet(newFrame);
  }

  public CfFrameVerificationHelper push(FrameType type) {
    checkFrameIsSet();
    currentFrame.getStack().addLast(type);
    if (currentFrame.computeStackSize() > maxStackHeight) {
      throw CfCodeStackMapValidatingException.error(
          "The max stack height of "
              + maxStackHeight
              + " is violated when pushing type "
              + type
              + " to existing stack of size "
              + currentFrame.getStack().size());
    }
    return this;
  }

  public CfFrameVerificationHelper push(DexType type) {
    return push(FrameType.initialized(type));
  }

  public CfFrameVerificationHelper seenLabel(CfLabel label) {
    if (tryCatchRangeLabels.contains(label)) {
      for (CfTryCatch tryCatchRange : tryCatchRanges) {
        if (tryCatchRange.start == label) {
          currentCatchRanges.add(tryCatchRange);
        }
      }
      currentCatchRanges.removeIf(currentRange -> currentRange.end == label);
    }
    return this;
  }

  public void checkTryCatchRange(CfTryCatch tryCatchRange) {
    // According to the spec:
    // https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1
    // saying ` and the handler's target (the initial instruction of the handler code) is type
    // safe assuming an incoming type state T. The type state T is derived from ExcStackFrame
    // by replacing the operand stack with a stack whose sole element is the handler's
    // exception class.
    tryCatchRange.targets.forEach(
        target -> {
          CfFrame destinationFrame = stateMap.get(target);
          if (destinationFrame == null) {
            throw CfCodeStackMapValidatingException.error("No frame for target catch range target");
          }
          // From the spec: the handler's exception class is assignable to the class Throwable.
          tryCatchRange.guards.forEach(
              guard -> {
                if (!CfAssignability.isAssignable(guard, factory.throwableType, appView)) {
                  throw CfCodeStackMapValidatingException.error(
                      "Could not assign '" + guard.toSourceString() + "' to throwable.");
                }
                checkStackIsAssignable(
                    ImmutableDeque.of(FrameType.initialized(guard)), destinationFrame.getStack());
              });
        });
  }

  private void checkFrameIsSet() {
    if (currentFrame == NO_FRAME) {
      throw CfCodeStackMapValidatingException.error("Unexpected state change");
    }
  }

  public void checkFrameAndSet(CfFrame newFrame) {
    if (currentFrame != NO_FRAME) {
      checkFrame(newFrame);
    }
    setFrame(newFrame);
  }

  private void setFrame(CfFrame frame) {
    assert frame != NO_FRAME;
    currentFrame =
        new CfFrame(
            new Int2ReferenceAVLTreeMap<>(frame.getLocals()), new ArrayDeque<>(frame.getStack()));
  }

  public void checkExceptionEdges() {
    for (CfTryCatch currentCatchRange : currentCatchRanges) {
      for (CfLabel target : currentCatchRange.targets) {
        CfFrame destinationFrame = stateMap.get(target);
        if (destinationFrame == null) {
          throw CfCodeStackMapValidatingException.error("No frame for target catch range target");
        }
        checkLocalsIsAssignable(currentFrame.getLocals(), destinationFrame.getLocals());
      }
    }
  }

  public CfFrame getFrame() {
    return currentFrame;
  }

  public void checkTarget(CfLabel label) {
    checkFrame(stateMap.get(label));
  }

  public void checkFrame(CfFrame destinationFrame) {
    if (destinationFrame == null) {
      throw CfCodeStackMapValidatingException.error("No destination frame");
    }
    checkFrame(destinationFrame.getLocals(), destinationFrame.getStack());
  }

  public void checkFrame(Int2ReferenceSortedMap<FrameType> locals, Deque<FrameType> stack) {
    checkIsAssignable(currentFrame.getLocals(), currentFrame.getStack(), locals, stack);
  }

  public void setNoFrame() {
    currentFrame = NO_FRAME;
  }

  public boolean isUninitializedThisAndTarget(FrameType source, DexType target) {
    if (!source.isUninitializedThis()) {
      return false;
    }
    return target == factory.objectType || target == context;
  }

  public boolean isUninitializedNewAndTarget(FrameType source, DexType target) {
    if (!source.isUninitializedNew()) {
      return false;
    }
    return target == factory.objectType || target == context;
  }

  public boolean isAssignableAndInitialized(FrameType source, DexType target) {
    if (!source.isInitialized()) {
      return false;
    }
    return CfAssignability.isAssignable(source.getInitializedType(), target, appView);
  }

  public void checkIsAssignable(
      FrameType source, DexType target, BiPredicate<FrameType, DexType> predicate) {
    if (predicate.test(source, target)) {
      return;
    }
    throw CfCodeStackMapValidatingException.error(
        "The expected type " + source + " is not assignable to " + target.toSourceString());
  }

  public void checkIsAssignable(FrameType source, FrameType target) {
    if (!CfAssignability.isAssignable(source, target, appView)) {
      throw CfCodeStackMapValidatingException.error(
          "The expected type " + source + " is not assignable to " + target);
    }
  }

  // Based on https://docs.oracle.com/javase/specs/jvms/se8/html/jvms-4.html#jvms-4.10.1.4.
  private void checkIsAssignable(
      Int2ReferenceSortedMap<FrameType> sourceLocals,
      Deque<FrameType> sourceStack,
      Int2ReferenceSortedMap<FrameType> destLocals,
      Deque<FrameType> destStack) {
    checkLocalsIsAssignable(sourceLocals, destLocals);
    checkStackIsAssignable(sourceStack, destStack);
  }

  private void checkLocalsIsAssignable(
      Int2ReferenceSortedMap<FrameType> sourceLocals,
      Int2ReferenceSortedMap<FrameType> destLocals) {
    // TODO(b/229826687): The tail of locals could have top(s) at destination but still be valid.
    int localsLastKey = sourceLocals.isEmpty() ? -1 : sourceLocals.lastIntKey();
    int otherLocalsLastKey = destLocals.isEmpty() ? -1 : destLocals.lastIntKey();
    if (localsLastKey < otherLocalsLastKey) {
      throw CfCodeStackMapValidatingException.error(
          "Source locals "
              + MapUtils.toString(sourceLocals)
              + " have different local indices than "
              + MapUtils.toString(destLocals));
    }
    for (int i = 0; i < otherLocalsLastKey; i++) {
      FrameType sourceType = sourceLocals.containsKey(i) ? sourceLocals.get(i) : FrameType.top();
      FrameType destinationType = destLocals.containsKey(i) ? destLocals.get(i) : FrameType.top();
      if (!CfAssignability.isAssignable(sourceType, destinationType, appView)) {
        throw CfCodeStackMapValidatingException.error(
            "Could not assign '"
                + MapUtils.toString(sourceLocals)
                + "' to '"
                + MapUtils.toString(destLocals)
                + "'. The local at index "
                + i
                + " with '"
                + sourceType
                + "' not being assignable to '"
                + destinationType
                + "'");
      }
    }
  }

  private void checkStackIsAssignable(Deque<FrameType> sourceStack, Deque<FrameType> destStack) {
    if (sourceStack.size() != destStack.size()) {
      throw CfCodeStackMapValidatingException.error(
          "Source stack "
              + Arrays.toString(sourceStack.toArray())
              + " and destination stack "
              + Arrays.toString(destStack.toArray())
              + " is not the same size");
    }
    Iterator<FrameType> otherIterator = destStack.iterator();
    int i = 0;
    for (FrameType sourceType : sourceStack) {
      FrameType destinationType = otherIterator.next();
      if (!CfAssignability.isAssignable(sourceType, destinationType, appView)) {
        throw CfCodeStackMapValidatingException.error(
            "Could not assign '"
                + Arrays.toString(sourceStack.toArray())
                + "' to '"
                + Arrays.toString(destStack.toArray())
                + "'. The stack value at index "
                + i
                + " (from bottom) with '"
                + sourceType
                + "' not being assignable to '"
                + destinationType
                + "'");
      }
      i++;
    }
  }
}
