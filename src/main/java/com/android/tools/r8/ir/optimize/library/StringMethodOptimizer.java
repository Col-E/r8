// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.library;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.JavaUtilLocaleMembers;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.ValueUtils;
import com.android.tools.r8.utils.ValueUtils.ArrayValues;
import com.google.common.collect.ImmutableMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class StringMethodOptimizer extends StatelessLibraryMethodModelCollection {
  private static boolean DEBUG =
      System.getProperty("com.android.tools.r8.debug.StringMethodOptimizer") != null;

  private final AppView<?> appView;
  private final DexItemFactory dexItemFactory;
  private final boolean enableStringFormatOptimizations;
  private final ImmutableMap<DexMethod, DexMethod> valueOfToStringAppend;

  StringMethodOptimizer(AppView<?> appView) {
    this.appView = appView;
    this.dexItemFactory = appView.dexItemFactory();
    InternalOptions options = appView.options();
    this.enableStringFormatOptimizations =
        appView.enableWholeProgramOptimizations()
            && !options.debug
            && options.isOptimizing()
            && options.isShrinking();
    this.valueOfToStringAppend =
        ImmutableMap.<DexMethod, DexMethod>builder()
            .put(
                dexItemFactory.integerMembers.valueOf,
                dexItemFactory.stringBuilderMethods.appendInt)
            .put(dexItemFactory.byteMembers.valueOf, dexItemFactory.stringBuilderMethods.appendInt)
            .put(dexItemFactory.shortMembers.valueOf, dexItemFactory.stringBuilderMethods.appendInt)
            .put(dexItemFactory.longMembers.valueOf, dexItemFactory.stringBuilderMethods.appendLong)
            .put(dexItemFactory.charMembers.valueOf, dexItemFactory.stringBuilderMethods.appendChar)
            .put(
                dexItemFactory.booleanMembers.valueOf,
                dexItemFactory.stringBuilderMethods.appendBoolean)
            .put(
                dexItemFactory.floatMembers.valueOf,
                dexItemFactory.stringBuilderMethods.appendFloat)
            .put(
                dexItemFactory.doubleMembers.valueOf,
                dexItemFactory.stringBuilderMethods.appendDouble)
            .build();
  }

  private static void debugLog(IRCode code, String message) {
    System.err.println(message + " method=" + code.context().getReference());
  }

  @Override
  public DexType getType() {
    return dexItemFactory.stringType;
  }

  @Override
  @SuppressWarnings("ReferenceEquality")
  public InstructionListIterator optimize(
      IRCode code,
      BasicBlockIterator blockIterator,
      InstructionListIterator instructionIterator,
      InvokeMethod invoke,
      DexClassAndMethod singleTarget,
      AffectedValues affectedValues,
      Set<BasicBlock> blocksToRemove) {
    DexMethod singleTargetReference = singleTarget.getReference();
    var stringMembers = dexItemFactory.stringMembers;
    if (singleTargetReference == stringMembers.equals) {
      optimizeEquals(code, instructionIterator, invoke.asInvokeMethodWithReceiver());
    } else if (singleTargetReference == stringMembers.valueOf) {
      optimizeValueOf(code, instructionIterator, invoke.asInvokeStatic(), affectedValues);
    } else if (enableStringFormatOptimizations
        && (singleTargetReference == stringMembers.format
            || singleTargetReference == stringMembers.formatWithLocale)) {
      instructionIterator =
          optimizeFormat(
              code, instructionIterator, blockIterator, invoke.asInvokeStatic(), affectedValues);
    }
    return instructionIterator;
  }

  private void optimizeEquals(
      IRCode code, InstructionListIterator instructionIterator, InvokeMethodWithReceiver invoke) {
    if (appView.appInfo().hasLiveness()) {
      ProgramMethod context = code.context();
      Value first = invoke.getReceiver().getAliasedValue();
      Value second = invoke.getArgument(1).getAliasedValue();
      if (isPrunedClassNameComparison(first, second, context)
          || isPrunedClassNameComparison(second, first, context)) {
        instructionIterator.replaceCurrentInstructionWithConstInt(code, 0);
      }
    }
  }

  private static class SimpleStringFormatSpec {
    private static class Part {
      final String value;
      final int placeholderIdx;
      private final char formatChar;

      Part(String value) {
        this.value = value;
        this.placeholderIdx = -1;
        this.formatChar = '\0';
      }

      Part(int placeholderIdx, char formatChar) {
        this.value = null;
        this.placeholderIdx = placeholderIdx;
        this.formatChar = formatChar;
      }

      boolean isPlaceholder() {
        return value == null;
      }

      public boolean isLiteral() {
        return value != null;
      }
    }

    final List<Part> parts;
    final int placeholderCount;

    SimpleStringFormatSpec(List<Part> parts) {
      this.parts = parts;
      placeholderCount = (int) parts.stream().filter(Part::isPlaceholder).count();
      assert placeholderCount >= 1 || parts.size() <= 1;
    }

    static SimpleStringFormatSpec parse(boolean allowNumbers, String spec) {
      ArrayList<Part> parts = new ArrayList<>();
      int startIdx = 0;
      int curPlaceholderIdx = 0;
      int specLen = spec.length();
      String curPartValue = "";
      while (true) {
        int percentIdx = spec.indexOf('%', startIdx);
        if (percentIdx == -1) {
          if (startIdx < specLen) {
            curPartValue = curPartValue.concat(spec.substring(startIdx));
          }
          if (!curPartValue.isEmpty() || parts.isEmpty()) {
            parts.add(new Part(curPartValue));
          }
          return new SimpleStringFormatSpec(parts);
        }
        // Trailing % is invalid.
        if (percentIdx + 1 == specLen) {
          return null;
        }
        curPartValue = curPartValue.concat(spec.substring(startIdx, percentIdx));
        char formatChar = spec.charAt(percentIdx + 1);
        switch (formatChar) {
          case 'd':
            if (!allowNumbers) {
              return null;
            }
            // Intentional fall-through.
          case 'b':
          case 's':
            if (!curPartValue.isEmpty()) {
              parts.add(new Part(curPartValue));
              curPartValue = "";
            }
            parts.add(new Part(curPlaceholderIdx, formatChar));
            curPlaceholderIdx += 1;
            break;
          case '%':
            curPartValue = curPartValue.concat("%");
            break;
          default:
            // Do not handle modifiers or other types, because only simple %s result
            // in smaller code to change to StringBuilder (and are sufficiently common).
            return null;
        }
        startIdx = percentIdx + 2;
      }
    }
  }

  private boolean isDefinitelyNotFormattable(TypeElement type) {
    ClassTypeElement classType = type.asClassType();
    if (classType == null) {
      return false;
    }
    DexClass clazz = appView.definitionFor(classType.getClassType());
    if (clazz == null || !clazz.isFinal()) {
      // TODO(b/244238384): Extend to non-final classes.
      return false;
    }
    TypeElement formattableType = dexItemFactory.javaUtilFormattableType.toTypeElement(appView);
    return !type.lessThanOrEqualUpToNullability(formattableType, appView);
  }

  private boolean isSupportedFormatType(char formatChar, TypeElement type) {
    switch (formatChar) {
      case 'b':
        // String.format() converts null to "false" and non-Boolean objects to "true", which we
        // cannot replicate without inserting extra logic.
        return type.isDefinitelyNotNull() && type.isClassType(dexItemFactory.boxedBooleanType);
      case 'd':
        // %d requires Byte, Short, Integer, or Long, and prints null as "null".
        // TODO(b/244238384): Extend to BigInteger.
        return type.isClassType(dexItemFactory.boxedIntType)
            || type.isClassType(dexItemFactory.boxedLongType)
            || type.isClassType(dexItemFactory.boxedByteType)
            || type.isClassType(dexItemFactory.boxedShortType);
      default:
        assert formatChar == 's';
        // Check for string as an optimization since it's the common case.
        return type.isStringType(dexItemFactory) || isDefinitelyNotFormattable(type);
    }
  }

  private boolean localeIsNullOrRootOrEnglish(Value value) {
    if (value.isAlwaysNull(appView)) {
      return true;
    }
    if (!value.isDefinedByInstructionSatisfying(Instruction::isStaticGet)) {
      return false;
    }
    StaticGet staticGet = value.definition.asStaticGet();
    DexField field = staticGet.getField();
    JavaUtilLocaleMembers localeMembers = dexItemFactory.javaUtilLocaleMembers;
    return field.isIdenticalTo(localeMembers.ENGLISH)
        || field.isIdenticalTo(localeMembers.ROOT)
        || field.isIdenticalTo(localeMembers.US);
  }

  private InstructionListIterator optimizeFormat(
      IRCode code,
      InstructionListIterator instructionIterator,
      BasicBlockIterator blockIterator,
      InvokeStatic formatInvoke,
      AffectedValues affectedValues) {
    boolean hasLocale =
        formatInvoke
            .getInvokedMethod()
            .isIdenticalTo(dexItemFactory.stringMembers.formatWithLocale);
    int specParamIdx = hasLocale ? 1 : 0;
    Value specValue = formatInvoke.getArgument(specParamIdx).getAliasedValue();
    if (!specValue.isConstString()) {
      if (DEBUG) {
        debugLog(code, "optimizeFormat: Non-Const Spec");
      }
      return instructionIterator;
    }
    Instruction specInstruction = specValue.getDefinition();
    String specString = specInstruction.asConstString().getValue().toString();
    boolean allowNumbers =
        hasLocale && localeIsNullOrRootOrEnglish(formatInvoke.getFirstArgument().getAliasedValue());
    SimpleStringFormatSpec parsedSpec = SimpleStringFormatSpec.parse(allowNumbers, specString);
    if (parsedSpec == null) {
      if (DEBUG) {
        debugLog(code, "optimizeFormat: Unsupported format with allowNumbers=" + allowNumbers);
      }
      return instructionIterator;
    }

    Value paramsValue = formatInvoke.getArgument(specParamIdx + 1);
    List<Value> elementValues;
    if (paramsValue.isAlwaysNull(appView)) {
      elementValues = Collections.emptyList();
    } else {
      ArrayValues arrayValues =
          ValueUtils.computeSingleUseArrayValues(paramsValue, formatInvoke, code);
      if (arrayValues == null) {
        return instructionIterator;
      }
      elementValues = arrayValues.getElementValues();
    }

    // Extra args are ignored, while too few throw.
    if (elementValues.size() < parsedSpec.placeholderCount) {
      // TODO(b/244238384): Raise IllegalFormatException.
      return instructionIterator;
    }

    // Optimize no placeholders.
    if (parsedSpec.placeholderCount == 0) {
      instructionIterator.replaceCurrentInstructionWithConstString(
          appView,
          code,
          dexItemFactory.createString(parsedSpec.parts.get(0).value),
          affectedValues);
      if (DEBUG) {
        debugLog(code, "String.format(): Optimized no placeholders");
      }
      return instructionIterator;
    }

    for (SimpleStringFormatSpec.Part part : parsedSpec.parts) {
      if (part.isPlaceholder()) {
        Value paramValue = elementValues.get(part.placeholderIdx);
        if (paramValue == null || paramValue.isAlwaysNull(appView)) {
          // Save having to call isAlwaysNull() again.
          elementValues.set(part.placeholderIdx, null);
          continue;
        }
        if (!isSupportedFormatType(part.formatChar, paramValue.getType())) {
          if (DEBUG) {
            debugLog(
                code,
                String.format(
                    "String.format(): Unsupported param %s type %%%s: %s",
                    part.placeholderIdx, part.formatChar, paramValue.getType()));
          }
          return instructionIterator;
        }
      }
    }

    ArrayList<Instruction> newInstructions = new ArrayList<>();

    // Rely on StringBuilder optimizations to convert this to using the string constructor (plus
    // other StringBuilder / valueOf optimizations that may apply).
    NewInstance newInstance =
        NewInstance.builder()
            .setType(dexItemFactory.stringBuilderType)
            .setPosition(formatInvoke)
            .setFreshOutValue(
                code,
                dexItemFactory.stringBuilderType.toTypeElement(
                    appView, Nullability.definitelyNotNull()))
            .build();
    Value stringBuilderValue = newInstance.outValue();
    newInstructions.add(newInstance);

    newInstructions.add(
        InvokeDirect.builder()
            .setMethod(dexItemFactory.stringBuilderMethods.defaultConstructor)
            .setSingleArgument(stringBuilderValue)
            .setPosition(formatInvoke)
            .build());

    for (SimpleStringFormatSpec.Part part : parsedSpec.parts) {
      Value paramValue;
      DexMethod appendMethod = null;
      if (part.isLiteral()) {
        // Create strings for non-placeholder parts of the spec string.
        ConstString constString =
            ConstString.builder()
                .setValue(dexItemFactory.createString(part.value))
                .setPosition(specInstruction)
                .setFreshOutValue(
                    code, TypeElement.stringClassType(appView, Nullability.definitelyNotNull()))
                .build();
        newInstructions.add(constString);
        paramValue = constString.outValue();
        appendMethod = dexItemFactory.stringBuilderMethods.appendString;
      } else {
        paramValue = elementValues.get(part.placeholderIdx);
        if (paramValue == null) {
          ConstString constString =
              ConstString.builder()
                  .setValue(dexItemFactory.createString(part.formatChar == 'b' ? "false" : "null"))
                  .setPosition(specInstruction)
                  .setFreshOutValue(
                      code, TypeElement.stringClassType(appView, Nullability.definitelyNotNull()))
                  .build();
          newInstructions.add(constString);
          paramValue = constString.outValue();
          appendMethod = dexItemFactory.stringBuilderMethods.appendString;
        } else {
          Value paramValueRoot = paramValue.getAliasedValue();
          InvokeStatic paramInvoke =
              paramValueRoot.isPhi() ? null : paramValueRoot.definition.asInvokeStatic();
          // See if the parameter is a call to Integer.valueOf, Boolean.valueOf, etc.
          if (paramInvoke != null) {
            DexMethod invokedMethod = paramInvoke.getInvokedMethod();
            appendMethod = valueOfToStringAppend.get(invokedMethod);
            if (appendMethod != null) {
              paramValue = paramInvoke.getFirstArgument();
            }
          }
          if (appendMethod == null) {
            appendMethod =
                paramValue.getType().isStringType(dexItemFactory)
                    ? dexItemFactory.stringBuilderMethods.appendString
                    : dexItemFactory.stringBuilderMethods.appendObject;
          }
        }
      }
      InvokeVirtual appendInvoke =
          InvokeVirtual.builder()
              .setMethod(appendMethod)
              .setPosition(formatInvoke)
              .setArguments(stringBuilderValue, paramValue)
              .build();
      newInstructions.add(appendInvoke);
    }
    InvokeVirtual toStringInvoke =
        InvokeVirtual.builder()
            .setMethod(dexItemFactory.stringBuilderMethods.toString)
            .setPosition(formatInvoke)
            .setSingleArgument(stringBuilderValue)
            .setFreshOutValue(code, dexItemFactory.stringType.toTypeElement(appView))
            .build();

    // Replace the String.format(), but for simplicity, leave all other array and valueOf() invokes
    // to be removed by dead code elimination.
    instructionIterator.replaceCurrentInstruction(toStringInvoke, affectedValues);
    instructionIterator.previous();
    instructionIterator =
        instructionIterator.addPossiblyThrowingInstructionsToPossiblyThrowingBlock(
            code, blockIterator, newInstructions, appView.options());
    if (DEBUG) {
      debugLog(code, "String.format(): Optimized.");
    }
    return instructionIterator;
  }

  private void optimizeValueOf(
      IRCode code,
      InstructionListIterator instructionIterator,
      InvokeStatic invoke,
      AffectedValues affectedValues) {
    Value object = invoke.getFirstArgument();
    TypeElement type = object.getType();

    // Optimize String.valueOf(null) into "null".
    if (type.isDefinitelyNull()) {
      DexString nullString = dexItemFactory.createString("null");
      instructionIterator.replaceCurrentInstructionWithConstString(
          appView, code, nullString, affectedValues);
      return;
    }

    // Optimize String.valueOf(nonNullString) into nonNullString.
    if (type.isDefinitelyNotNull() && type.isStringType(dexItemFactory)) {
      if (invoke.hasOutValue()) {
        affectedValues.addAll(invoke.outValue().affectedValues());
        invoke.outValue().replaceUsers(object);
      }
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  /**
   * Returns true if {@param classNameValue} is defined by calling {@link Class#getName()} and
   * {@param constStringValue} is a constant string that is identical to the name of a class that
   * has been pruned by the {@link com.android.tools.r8.shaking.Enqueuer}.
   */
  @SuppressWarnings("ReferenceEquality")
  private boolean isPrunedClassNameComparison(
      Value classNameValue, Value constStringValue, ProgramMethod context) {
    if (classNameValue.isPhi() || constStringValue.isPhi()) {
      return false;
    }

    Instruction classNameDefinition = classNameValue.definition;
    if (!classNameDefinition.isInvokeVirtual()) {
      return false;
    }

    DexClassAndMethod singleTarget =
        classNameDefinition.asInvokeVirtual().lookupSingleTarget(appView, context);
    if (singleTarget == null
        || singleTarget.getReference() != dexItemFactory.classMethods.getName) {
      return false;
    }

    if (!constStringValue.definition.isDexItemBasedConstString()) {
      return false;
    }

    DexItemBasedConstString constString = constStringValue.definition.asDexItemBasedConstString();
    DexReference reference = constString.getItem();
    return reference.isDexType()
        && appView.appInfo().withLiveness().wasPruned(reference.asDexType())
        && !constString.getNameComputationInfo().needsToComputeName();
  }
}
