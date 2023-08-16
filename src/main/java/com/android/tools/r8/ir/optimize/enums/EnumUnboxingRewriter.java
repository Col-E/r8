// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClassAndMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.proto.ArgumentInfo;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.proto.RewrittenTypeInfo;
import com.android.tools.r8.ir.analysis.type.ArrayTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.ArrayAccess;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.If;
import com.android.tools.r8.ir.code.InitClass;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMethodWithReceiver;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.MemberType;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.NewUnboxedEnumInstance;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.enums.EnumInstanceFieldData.EnumInstanceFieldKnownData;
import com.android.tools.r8.ir.optimize.enums.classification.CheckNotNullEnumUnboxerMethodClassification;
import com.android.tools.r8.ir.optimize.enums.classification.EnumUnboxerMethodClassification;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.InternalOptions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EnumUnboxingRewriter {

  private final AppView<AppInfoWithLiveness> appView;
  private final Map<DexMethod, DexMethod> checkNotNullToCheckNotZeroMapping;
  private final DexItemFactory factory;
  private final InternalOptions options;
  private final EnumDataMap unboxedEnumsData;
  private final EnumUnboxingLens enumUnboxingLens;
  private final EnumUnboxingUtilityClasses utilityClasses;

  EnumUnboxingRewriter(
      AppView<AppInfoWithLiveness> appView,
      Map<DexMethod, DexMethod> checkNotNullToCheckNotZeroMapping,
      EnumUnboxingLens enumUnboxingLens,
      EnumDataMap unboxedEnumsInstanceFieldData,
      EnumUnboxingUtilityClasses utilityClasses) {
    this.appView = appView;
    this.checkNotNullToCheckNotZeroMapping = checkNotNullToCheckNotZeroMapping;
    this.factory = appView.dexItemFactory();
    this.options = appView.options();
    this.enumUnboxingLens = enumUnboxingLens;
    this.unboxedEnumsData = unboxedEnumsInstanceFieldData;
    this.utilityClasses = utilityClasses;
  }

  private LocalEnumUnboxingUtilityClass getLocalUtilityClass(DexType enumType) {
    return utilityClasses.getLocalUtilityClass(unboxedEnumsData.representativeType(enumType));
  }

  private SharedEnumUnboxingUtilityClass getSharedUtilityClass() {
    return utilityClasses.getSharedUtilityClass();
  }

  private Map<Instruction, DexType> createInitialConvertedEnums(
      IRCode code, RewrittenPrototypeDescription prototypeChanges, Set<Phi> affectedPhis) {
    Map<Instruction, DexType> convertedEnums = new IdentityHashMap<>();
    List<Instruction> extraConstants = new ArrayList<>();
    InstructionListIterator iterator = code.entryBlock().listIterator(code);
    int originalNumberOfArguments =
        code.getNumberOfArguments()
            + prototypeChanges.getArgumentInfoCollection().numberOfRemovedArguments();
    for (int argumentIndex = 0; argumentIndex < originalNumberOfArguments; argumentIndex++) {
      ArgumentInfo argumentInfo =
          prototypeChanges.getArgumentInfoCollection().getArgumentInfo(argumentIndex);
      if (argumentInfo.isRemovedArgumentInfo()) {
        continue;
      }
      Instruction next = iterator.next();
      assert next.isArgument();
      if (argumentInfo.isRewrittenTypeInfo()) {
        RewrittenTypeInfo rewrittenTypeInfo = argumentInfo.asRewrittenTypeInfo();
        DexType enumType =
            getEnumClassTypeOrNull(rewrittenTypeInfo.getOldType().toBaseType(factory));
        if (rewrittenTypeInfo.hasSingleValue()
            && rewrittenTypeInfo.getSingleValue().isSingleNumberValue()) {
          assert rewrittenTypeInfo
              .getSingleValue()
              .isMaterializableInContext(appView, code.context());
          Instruction materializingInstruction =
              rewrittenTypeInfo
                  .getSingleValue()
                  .createMaterializingInstruction(
                      appView,
                      code,
                      TypeAndLocalInfoSupplier.create(
                          rewrittenTypeInfo.getNewType().toTypeElement(appView),
                          next.getLocalInfo()));
          materializingInstruction.setPosition(next.getPosition());
          extraConstants.add(materializingInstruction);
          affectedPhis.addAll(next.outValue().uniquePhiUsers());
          next.outValue().replaceUsers(materializingInstruction.outValue());
          convertedEnums.put(materializingInstruction, enumType);
        } else if (enumType != null) {
          convertedEnums.put(next, enumType);
        }
      }
    }
    if (!extraConstants.isEmpty()) {
      assert extraConstants.size() == 1; // So far this is used only for unboxed enums "this".
      for (Instruction extraConstant : extraConstants) {
        iterator.add(extraConstant);
      }
    }
    return convertedEnums;
  }

  Set<Phi> rewriteCode(
      IRCode code,
      MethodProcessor methodProcessor,
      RewrittenPrototypeDescription prototypeChanges) {
    // We should not process the enum methods, they will be removed and they may contain invalid
    // rewriting rules.
    if (unboxedEnumsData.isEmpty()) {
      return Sets.newIdentityHashSet();
    }
    assert code.isConsistentSSABeforeTypesAreCorrect(appView);
    EnumUnboxerMethodProcessorEventConsumer eventConsumer = methodProcessor.getEventConsumer();
    Set<Phi> affectedPhis = Sets.newIdentityHashSet();
    Map<Instruction, DexType> convertedEnums =
        createInitialConvertedEnums(code, prototypeChanges, affectedPhis);
    BasicBlockIterator blocks = code.listIterator();
    Set<BasicBlock> seenBlocks = Sets.newIdentityHashSet();
    Set<Instruction> instructionsToRemove = Sets.newIdentityHashSet();
    Value zeroConstValue = null;
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      seenBlocks.add(block);
      zeroConstValue = fixNullsInBlockPhis(code, block, zeroConstValue);
      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        Instruction instruction = iterator.next();
        if (instructionsToRemove.contains(instruction)) {
          iterator.removeOrReplaceByDebugLocalRead();
          continue;
        }

        if (instruction.isInitClass()) {
          InitClass initClass = instruction.asInitClass();
          DexType enumType = getEnumClassTypeOrNull(initClass.getClassValue());
          if (enumType != null) {
            iterator.removeOrReplaceByDebugLocalRead();
          }
        } else if (instruction.isIf()) {
          rewriteIf(code, convertedEnums, iterator, instruction.asIf());
        } else if (instruction.isInvokeMethodWithReceiver()) {
          rewriteInvokeMethodWithReceiver(
              code,
              eventConsumer,
              convertedEnums,
              blocks,
              block,
              iterator,
              instruction.asInvokeMethodWithReceiver());
        } else if (instruction.isNewArrayFilled()) {
          rewriteNewArrayFilled(instruction.asNewArrayFilled(), code, convertedEnums, iterator);
        } else if (instruction.isInvokeStatic()) {
          rewriteInvokeStatic(
              instruction.asInvokeStatic(),
              code,
              convertedEnums,
              iterator,
              affectedPhis,
              eventConsumer);
        } else if (instruction.isStaticGet()) {
          rewriteStaticGet(
              code,
              eventConsumer,
              affectedPhis,
              convertedEnums,
              seenBlocks,
              instructionsToRemove,
              iterator,
              instruction.asStaticGet());
        } else if (instruction.isInstanceGet()) {
          rewriteInstanceGet(
              code, eventConsumer, convertedEnums, iterator, instruction.asInstanceGet());
        } else if (instruction.isArrayAccess()) {
          rewriteArrayAccess(
              code, affectedPhis, convertedEnums, iterator, instruction.asArrayAccess());
        } else if (instruction.isNewUnboxedEnumInstance()) {
          NewUnboxedEnumInstance newUnboxedEnumInstance = instruction.asNewUnboxedEnumInstance();
          assert unboxedEnumsData.isUnboxedEnum(newUnboxedEnumInstance.getType());
          iterator.replaceCurrentInstruction(
              code.createIntConstant(
                  EnumUnboxerImpl.ordinalToUnboxedInt(newUnboxedEnumInstance.getOrdinal())));
        }
      }
    }
    code.removeRedundantBlocks();
    assert code.isConsistentSSABeforeTypesAreCorrect(appView);
    return affectedPhis;
  }

  private void rewriteArrayAccess(
      IRCode code,
      Set<Phi> affectedPhis,
      Map<Instruction, DexType> convertedEnums,
      InstructionListIterator iterator,
      ArrayAccess arrayAccess) {
    // Rewrite array accesses from MyEnum[] (OBJECT) to int[] (INT).
    DexType enumType = getEnumArrayTypeOrNull(arrayAccess, convertedEnums);
    if (enumType != null) {
      if (arrayAccess.hasOutValue()) {
        affectedPhis.addAll(arrayAccess.outValue().uniquePhiUsers());
      }
      arrayAccess = arrayAccess.withMemberType(MemberType.INT);
      iterator.replaceCurrentInstruction(arrayAccess);
      convertedEnums.put(arrayAccess, enumType);
      if (arrayAccess.isArrayPut()) {
        ArrayPut arrayPut = arrayAccess.asArrayPut();
        if (arrayPut.value().getType().isNullType()) {
          iterator.previous();
          arrayPut.replacePutValue(iterator.insertConstIntInstruction(code, options, 0));
          iterator.next();
        }
      }
    }
    assert validateArrayAccess(arrayAccess);
  }

  private void rewriteIf(
      IRCode code,
      Map<Instruction, DexType> convertedEnums,
      InstructionListIterator iterator,
      If ifInstruction) {
    if (!ifInstruction.isZeroTest()) {
      for (int operandIndex = 0; operandIndex < 2; operandIndex++) {
        Value operand = ifInstruction.getOperand(operandIndex);
        DexType enumType = getEnumClassTypeOrNull(operand, convertedEnums);
        if (enumType != null) {
          int otherOperandIndex = 1 - operandIndex;
          Value otherOperand = ifInstruction.getOperand(otherOperandIndex);
          if (otherOperand.getType().isNullType()) {
            iterator.previous();
            ifInstruction.replaceValue(
                otherOperandIndex, iterator.insertConstIntInstruction(code, options, 0));
            iterator.next();
            break;
          }
        }
      }
    }
  }

  private void rewriteInstanceGet(
      IRCode code,
      EnumUnboxerMethodProcessorEventConsumer eventConsumer,
      Map<Instruction, DexType> convertedEnums,
      InstructionListIterator iterator,
      InstanceGet instanceGet) {
    DexType holder = instanceGet.getField().holder;
    if (unboxedEnumsData.isUnboxedEnum(holder)) {
      ProgramMethod fieldMethod =
          ensureInstanceFieldMethod(instanceGet.getField(), code.context(), eventConsumer);
      Value rewrittenOutValue =
          code.createValue(
              TypeElement.fromDexType(fieldMethod.getReturnType(), maybeNull(), appView));
      Value in = instanceGet.object();
      if (in.getType().isNullType()) {
        iterator.previous();
        in = iterator.insertConstIntInstruction(code, options, 0);
        iterator.next();
      }
      InvokeStatic invoke =
          new InvokeStatic(fieldMethod.getReference(), rewrittenOutValue, ImmutableList.of(in));
      iterator.replaceCurrentInstruction(invoke);
      if (unboxedEnumsData.isUnboxedEnum(instanceGet.getField().type)) {
        convertedEnums.put(invoke, instanceGet.getField().type);
      }
    }
  }

  private void rewriteStaticGet(
      IRCode code,
      EnumUnboxerMethodProcessorEventConsumer eventConsumer,
      Set<Phi> affectedPhis,
      Map<Instruction, DexType> convertedEnums,
      Set<BasicBlock> seenBlocks,
      Set<Instruction> instructionsToRemove,
      InstructionListIterator iterator,
      StaticGet staticGet) {
    DexField field = staticGet.getField();
    DexType holder = field.holder;
    if (!unboxedEnumsData.isUnboxedEnum(holder)) {
      return;
    }
    if (staticGet.hasUnusedOutValue()) {
      iterator.removeOrReplaceByDebugLocalRead();
      return;
    }
    affectedPhis.addAll(staticGet.outValue().uniquePhiUsers());
    if (unboxedEnumsData.matchesValuesField(field)) {
      // Load the size of this enum's $VALUES array before the current instruction.
      iterator.previous();
      Value sizeValue =
          iterator.insertConstIntInstruction(code, options, unboxedEnumsData.getValuesSize(holder));
      iterator.next();

      // Replace Enum.$VALUES by a call to: int[] SharedUtilityClass.values(int size).
      InvokeStatic invoke =
          InvokeStatic.builder()
              .setMethod(getSharedUtilityClass().getValuesMethod(code.context(), eventConsumer))
              .setFreshOutValue(appView, code)
              .setSingleArgument(sizeValue)
              .build();
      iterator.replaceCurrentInstruction(invoke);

      convertedEnums.put(invoke, holder);

      // Check if the call to SharedUtilityClass.values(size) is followed by a call to
      // clone(). If so, remove it, since SharedUtilityClass.values(size) returns a fresh
      // array. This is needed because the javac generated implementation of MyEnum.values()
      // is implemented as `return $VALUES.clone()`.
      removeRedundantValuesArrayCloning(invoke, instructionsToRemove, seenBlocks);
    } else if (unboxedEnumsData.hasUnboxedValueFor(field)) {
      // Replace by ordinal + 1 for null check (null is 0).
      ConstNumber intConstant = code.createIntConstant(unboxedEnumsData.getUnboxedValue(field));
      iterator.replaceCurrentInstruction(intConstant);
      convertedEnums.put(intConstant, holder);
    } else {
      // Nothing to do, handled by lens code rewriting.
    }
  }

  // Rewrites specific enum methods, such as ordinal, into their corresponding enum unboxed
  // counterpart. The rewriting (== or match) is based on the following:
  // - name, ordinal and compareTo are final and implemented only on java.lang.Enum,
  // - equals, hashCode are final and implemented in java.lang.Enum and java.lang.Object,
  // - getClass is final and implemented only in java.lang.Object,
  // - toString is non-final, implemented in java.lang.Object, java.lang.Enum and possibly
  //   also in the unboxed enum class.
  private void rewriteInvokeMethodWithReceiver(
      IRCode code,
      EnumUnboxerMethodProcessorEventConsumer eventConsumer,
      Map<Instruction, DexType> convertedEnums,
      BasicBlockIterator blocks,
      BasicBlock block,
      InstructionListIterator iterator,
      InvokeMethodWithReceiver invoke) {
    ProgramMethod context = code.context();
    // If the receiver is null, then the invoke is not rewritten even if the receiver is an
    // unboxed enum, but we end up with null.ordinal() or similar which has the correct behavior.
    DexType enumType = getEnumClassTypeOrNull(invoke.getReceiver(), convertedEnums);
    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (enumType != null) {
      if (invokedMethod == factory.enumMembers.ordinalMethod
          || invokedMethod.match(factory.enumMembers.hashCode)) {
        replaceEnumInvoke(
            iterator,
            invoke,
            getSharedUtilityClass().ensureOrdinalMethod(appView, context, eventConsumer));
      } else if (invokedMethod.match(factory.enumMembers.equals)) {
        replaceEnumInvoke(
            iterator,
            invoke,
            getSharedUtilityClass().ensureEqualsMethod(appView, context, eventConsumer));
      } else if (invokedMethod == factory.enumMembers.compareTo
          || invokedMethod == factory.enumMembers.compareToWithObject) {
        replaceEnumInvoke(
            iterator,
            invoke,
            getSharedUtilityClass().ensureCompareToMethod(appView, context, eventConsumer));
      } else if (invokedMethod == factory.enumMembers.nameMethod) {
        rewriteNameMethod(iterator, invoke, enumType, context, eventConsumer);
      } else if (invokedMethod.match(factory.enumMembers.toString)) {
        DexMethod reboundMethod =
            invokedMethod.withHolder(unboxedEnumsData.representativeType(enumType), factory);
        DexMethod lookupMethod =
            enumUnboxingLens
                .lookupMethod(
                    reboundMethod,
                    context.getReference(),
                    invoke.getType(),
                    enumUnboxingLens.getPrevious())
                .getReference();
        // If the SuperEnum had declared a toString() override, then the unboxer moves it to
        // the local utility class method corresponding to that override.
        // If a SubEnum had declared a toString() override, then the unboxer records a
        // synthetic move from SuperEnum.toString() to the dispatch method on the local
        // utility class.
        // When they are the same, then there are no overrides of toString().
        if (lookupMethod == reboundMethod) {
          rewriteNameMethod(iterator, invoke, enumType, context, eventConsumer);
        } else {
          DexClassAndMethod dexClassAndMethod = appView.definitionFor(lookupMethod);
          assert dexClassAndMethod != null;
          assert dexClassAndMethod.isProgramMethod();
          replaceEnumInvoke(iterator, invoke, dexClassAndMethod.asProgramMethod());
        }
      } else if (invokedMethod == factory.objectMembers.getClass) {
        rewriteNullCheck(iterator, invoke, context, eventConsumer);
      } else if (invoke.isInvokeVirtual() || invoke.isInvokeInterface()) {
        DexMethod refinedDispatchMethodReference =
            enumUnboxingLens.lookupRefinedDispatchMethod(
                invokedMethod,
                context.getReference(),
                invoke.getType(),
                enumUnboxingLens.getPrevious(),
                invoke.getArgument(0).getAbstractValue(appView, context),
                enumType);
        if (refinedDispatchMethodReference != null) {
          DexClassAndMethod refinedDispatchMethod =
              appView.definitionFor(refinedDispatchMethodReference);
          assert refinedDispatchMethod != null;
          assert refinedDispatchMethod.isProgramMethod();
          replaceEnumInvoke(iterator, invoke, refinedDispatchMethod.asProgramMethod());
        }
      }
    } else if (invokedMethod == factory.stringBuilderMethods.appendObject
        || invokedMethod == factory.stringBufferMethods.appendObject) {
      // Rewrites stringBuilder.append(enumInstance) as if it was
      // stringBuilder.append(String.valueOf(unboxedEnumInstance));
      Value enumArg = invoke.getArgument(1);
      DexType enumArgType = getEnumClassTypeOrNull(enumArg, convertedEnums);
      if (enumArgType != null) {
        ProgramMethod stringValueOfMethod =
            getLocalUtilityClass(enumArgType)
                .ensureStringValueOfMethod(appView, context, eventConsumer);
        InvokeStatic toStringInvoke =
            InvokeStatic.builder()
                .setMethod(stringValueOfMethod)
                .setSingleArgument(enumArg)
                .setFreshOutValue(appView, code)
                .setPosition(invoke)
                .build();
        DexMethod newAppendMethod =
            invokedMethod == factory.stringBuilderMethods.appendObject
                ? factory.stringBuilderMethods.appendString
                : factory.stringBufferMethods.appendString;
        List<Value> arguments = ImmutableList.of(invoke.getReceiver(), toStringInvoke.outValue());
        InvokeVirtual invokeAppendString =
            new InvokeVirtual(newAppendMethod, invoke.clearOutValue(), arguments);
        invokeAppendString.setPosition(invoke.getPosition());
        iterator.replaceCurrentInstruction(toStringInvoke);
        if (block.hasCatchHandlers()) {
          iterator
              .splitCopyCatchHandlers(code, blocks, appView.options())
              .listIterator(code)
              .add(invokeAppendString);
        } else {
          iterator.add(invokeAppendString);
        }
      }
    }
  }

  private void rewriteNewArrayFilled(
      NewArrayFilled newArrayFilled,
      IRCode code,
      Map<Instruction, DexType> convertedEnums,
      InstructionListIterator instructionIterator) {
    DexType arrayBaseType = newArrayFilled.getArrayType().toBaseType(factory);
    if (!unboxedEnumsData.isUnboxedEnum(arrayBaseType)) {
      return;
    }
    DexType rewrittenArrayType =
        newArrayFilled.getArrayType().replaceBaseType(factory.intType, factory);
    List<Value> elements = new ArrayList<>(newArrayFilled.inValues().size());
    Value zeroValue = null;
    for (Value element : newArrayFilled.inValues()) {
      if (element.getType().isNullType()) {
        if (zeroValue == null) {
          instructionIterator.previous();
          zeroValue = instructionIterator.insertConstIntInstruction(code, options, 0);
          Instruction next = instructionIterator.next();
          assert next == newArrayFilled;
        }
        elements.add(zeroValue);
      } else {
        elements.add(element);
      }
    }
    NewArrayFilled newArray =
        new NewArrayFilled(
            rewrittenArrayType,
            code.createValue(factory.intArrayType.toTypeElement(appView, definitelyNotNull())),
            elements);
    instructionIterator.replaceCurrentInstruction(newArray);
    convertedEnums.put(newArray, newArrayFilled.getArrayType());
  }

  private void rewriteInvokeStatic(
      InvokeStatic invoke,
      IRCode code,
      Map<Instruction, DexType> convertedEnums,
      InstructionListIterator instructionIterator,
      Set<Phi> affectedPhis,
      EnumUnboxerMethodProcessorEventConsumer eventConsumer) {
    ProgramMethod context = code.context();
    DexClassAndMethod singleTarget = invoke.lookupSingleTarget(appView, context);
    if (singleTarget == null) {
      return;
    }
    DexMethod invokedMethod = singleTarget.getReference();

    // Calls to java.lang.Enum.
    if (invokedMethod.getHolderType() == factory.enumType) {
      if (invokedMethod == factory.enumMembers.valueOf) {
        if (!invoke.getFirstArgument().isConstClass()) {
          return;
        }
        DexType enumType =
            invoke.getFirstArgument().getConstInstruction().asConstClass().getValue();
        if (!unboxedEnumsData.isUnboxedEnum(enumType)) {
          return;
        }
        ProgramMethod valueOfMethod =
            getLocalUtilityClass(enumType).ensureValueOfMethod(appView, context, eventConsumer);
        Value outValue = invoke.outValue();
        Value rewrittenOutValue = null;
        if (outValue != null) {
          rewrittenOutValue = code.createValue(TypeElement.getInt());
          affectedPhis.addAll(outValue.uniquePhiUsers());
        }
        InvokeStatic replacement =
            new InvokeStatic(
                valueOfMethod.getReference(),
                rewrittenOutValue,
                Collections.singletonList(invoke.inValues().get(1)));
        instructionIterator.replaceCurrentInstruction(replacement);
        convertedEnums.put(replacement, enumType);
      }
      return;
    }

    // Calls to java.lang.Objects.
    if (invokedMethod.getHolderType() == factory.objectsType) {
      if (invokedMethod == factory.objectsMethods.requireNonNull) {
        assert invoke.arguments().size() == 1;
        Value argument = invoke.getFirstArgument();
        DexType enumType = getEnumClassTypeOrNull(argument, convertedEnums);
        if (enumType != null) {
          rewriteNullCheck(instructionIterator, invoke, context, eventConsumer);
        }
      } else if (invokedMethod == factory.objectsMethods.requireNonNullWithMessage) {
        assert invoke.arguments().size() == 2;
        Value argument = invoke.getFirstArgument();
        DexType enumType = getEnumClassTypeOrNull(argument, convertedEnums);
        if (enumType != null) {
          replaceEnumInvoke(
              instructionIterator,
              invoke,
              getSharedUtilityClass()
                  .ensureCheckNotZeroWithMessageMethod(appView, context, eventConsumer));
        }
      } else if (invokedMethod == factory.objectsMethods.toStringWithObject) {
        rewriteStringValueOf(invoke, context, convertedEnums, instructionIterator, eventConsumer);
      } else if (invokedMethod == factory.objectsMethods.equals) {
        assert invoke.arguments().size() == 2;
        if (Iterables.any(
            invoke.arguments(), arg -> getEnumClassTypeOrNull(arg, convertedEnums) != null)) {
          // If any of the input is null, replace it by const 0.
          // If both inputs are null, no rewriting happen here.
          List<Value> newArguments = new ArrayList<>(invoke.arguments().size());
          for (Value arg : invoke.arguments()) {
            if (arg.getType().isNullType()) {
              Value constZero = insertConstZero(code);
              newArguments.add(constZero);
            } else {
              assert getEnumClassTypeOrNull(arg, convertedEnums) != null;
              newArguments.add(arg);
            }
          }
          replaceEnumInvoke(
              instructionIterator,
              invoke,
              getSharedUtilityClass().ensureObjectsEqualsMethod(appView, context, eventConsumer),
              newArguments);
        } else {
          assert invoke.getArgument(0).getType().isReferenceType();
          assert invoke.getArgument(1).getType().isReferenceType();
        }
      }
      return;
    }

    // Calls to java.lang.String.
    if (invokedMethod.getHolderType() == factory.stringType) {
      if (invokedMethod == factory.stringMembers.valueOf) {
        rewriteStringValueOf(invoke, context, convertedEnums, instructionIterator, eventConsumer);
      }
      return;
    }

    // Calls to java.lang.System.
    if (invokedMethod.getHolderType() == factory.javaLangSystemType) {
      if (invokedMethod == factory.javaLangSystemMembers.arraycopy) {
        // Intentionally empty.
      } else if (invokedMethod == factory.javaLangSystemMembers.identityHashCode) {
        // Note that System.identityHashCode(null) == 0, so it works even if the input is null
        // and not rewritten.
        assert invoke.arguments().size() == 1;
        Value argument = invoke.getFirstArgument();
        DexType enumType = getEnumClassTypeOrNull(argument, convertedEnums);
        if (enumType != null) {
          invoke.outValue().replaceUsers(argument);
          instructionIterator.removeOrReplaceByDebugLocalRead();
        }
      }
      return;
    }

    if (singleTarget.isProgramMethod()
        && checkNotNullToCheckNotZeroMapping.containsKey(singleTarget.getReference())) {
      DexMethod checkNotZeroMethodReference =
          checkNotNullToCheckNotZeroMapping.get(singleTarget.getReference());
      ProgramMethod checkNotZeroMethod =
          appView
              .appInfo()
              .resolveMethodOnClassHolderLegacy(checkNotZeroMethodReference)
              .getResolvedProgramMethod();
      if (checkNotZeroMethod != null) {
        EnumUnboxerMethodClassification classification =
            checkNotZeroMethod.getOptimizationInfo().getEnumUnboxerMethodClassification();
        if (classification.isCheckNotNullClassification()) {
          CheckNotNullEnumUnboxerMethodClassification checkNotNullClassification =
              classification.asCheckNotNullClassification();
          Value argument = invoke.getArgument(checkNotNullClassification.getArgumentIndex());
          DexType enumType = getEnumClassTypeOrNull(argument, convertedEnums);
          if (enumType != null) {
            InvokeStatic replacement =
                InvokeStatic.builder()
                    .setMethod(checkNotZeroMethod)
                    .setArguments(invoke.arguments())
                    .setPosition(invoke.getPosition())
                    .build();
            instructionIterator.replaceCurrentInstruction(replacement);
            convertedEnums.put(replacement, enumType);
            eventConsumer.acceptEnumUnboxerCheckNotZeroContext(checkNotZeroMethod, context);
          }
        } else {
          assert false;
        }
      } else {
        assert false;
      }
    }
  }

  private void rewriteStringValueOf(
      InvokeStatic invoke,
      ProgramMethod context,
      Map<Instruction, DexType> convertedEnums,
      InstructionListIterator instructionIterator,
      EnumUnboxerMethodProcessorEventConsumer eventConsumer) {
    assert invoke.arguments().size() == 1;
    Value argument = invoke.getFirstArgument();
    DexType enumType = getEnumClassTypeOrNull(argument, convertedEnums);
    if (enumType != null) {
      ProgramMethod stringValueOfMethod =
          getLocalUtilityClass(enumType).ensureStringValueOfMethod(appView, context, eventConsumer);
      instructionIterator.replaceCurrentInstruction(
          new InvokeStatic(
              stringValueOfMethod.getReference(), invoke.outValue(), invoke.arguments()));
    }
  }

  public void rewriteNullCheck(
      InstructionListIterator iterator,
      InvokeMethod invoke,
      ProgramMethod context,
      EnumUnboxerMethodProcessorEventConsumer eventConsumer) {
    assert !invoke.hasOutValue() || !invoke.outValue().hasAnyUsers();
    replaceEnumInvoke(
        iterator,
        invoke,
        getSharedUtilityClass().ensureCheckNotZeroMethod(appView, context, eventConsumer));
  }

  private void removeRedundantValuesArrayCloning(
      InvokeStatic invoke, Set<Instruction> instructionsToRemove, Set<BasicBlock> seenBlocks) {
    for (Instruction user : invoke.outValue().aliasedUsers()) {
      if (user.isInvokeVirtual()) {
        InvokeVirtual cloneCandidate = user.asInvokeVirtual();
        if (cloneCandidate.getInvokedMethod().match(appView.dexItemFactory().objectMembers.clone)) {
          if (cloneCandidate.hasOutValue()) {
            cloneCandidate.outValue().replaceUsers(invoke.outValue());
          }
          BasicBlock cloneBlock = cloneCandidate.getBlock();
          if (cloneBlock == invoke.getBlock() || !seenBlocks.contains(cloneBlock)) {
            instructionsToRemove.add(cloneCandidate);
          } else {
            cloneBlock.removeInstruction(cloneCandidate);
          }
        }
      }
    }
  }

  private void rewriteNameMethod(
      InstructionListIterator iterator,
      InvokeMethodWithReceiver invoke,
      DexType enumType,
      ProgramMethod context,
      EnumUnboxerMethodProcessorEventConsumer eventConsumer) {
    ProgramMethod toStringMethod =
        getLocalUtilityClass(enumType)
            .ensureGetInstanceFieldMethod(
                appView, factory.enumMembers.nameField, context, eventConsumer);
    iterator.replaceCurrentInstruction(
        new InvokeStatic(toStringMethod.getReference(), invoke.outValue(), invoke.arguments()));
  }

  private Value fixNullsInBlockPhis(IRCode code, BasicBlock block, Value zeroConstValue) {
    for (Phi phi : block.getPhis()) {
      if (getEnumClassTypeOrNull(phi.getType()) != null) {
        for (int i = 0; i < phi.getOperands().size(); i++) {
          Value operand = phi.getOperand(i);
          if (operand.getType().isNullType()) {
            if (zeroConstValue == null) {
              zeroConstValue = insertConstZero(code);
            }
            phi.replaceOperandAt(i, zeroConstValue);
          }
        }
      }
    }
    return zeroConstValue;
  }

  private Value insertConstZero(IRCode code) {
    InstructionListIterator iterator = code.entryBlock().listIterator(code);
    while (iterator.hasNext() && iterator.peekNext().isArgument()) {
      iterator.next();
    }
    return iterator.insertConstIntInstruction(code, options, 0);
  }

  private ProgramMethod ensureInstanceFieldMethod(
      DexField field,
      ProgramMethod context,
      EnumUnboxerMethodProcessorEventConsumer eventConsumer) {
    EnumInstanceFieldKnownData enumFieldKnownData =
        unboxedEnumsData.getInstanceFieldData(field.holder, field);
    if (enumFieldKnownData.isOrdinal()) {
      return getSharedUtilityClass().ensureOrdinalMethod(appView, context, eventConsumer);
    }
    return getLocalUtilityClass(field.getHolderType())
        .ensureGetInstanceFieldMethod(appView, field, context, eventConsumer);
  }

  private void replaceEnumInvoke(
      InstructionListIterator iterator, InvokeMethod invoke, ProgramMethod method) {
    replaceEnumInvoke(iterator, invoke, method, invoke.arguments());
  }

  private void replaceEnumInvoke(
      InstructionListIterator iterator,
      InvokeMethod invoke,
      ProgramMethod method,
      List<Value> arguments) {
    InvokeStatic replacement =
        new InvokeStatic(
            method.getReference(),
            invoke.hasUnusedOutValue() ? null : invoke.outValue(),
            arguments);
    assert !replacement.hasOutValue()
        || !replacement.getInvokedMethod().getReturnType().isVoidType();
    iterator.replaceCurrentInstruction(replacement);
  }

  private boolean validateArrayAccess(ArrayAccess arrayAccess) {
    ArrayTypeElement arrayType = arrayAccess.array().getType().asArrayType();
    if (arrayType == null) {
      assert arrayAccess.array().getType().isNullType();
      return true;
    }
    assert arrayAccess.getMemberType() != MemberType.OBJECT
        || arrayType.getNesting() > 1
        || arrayType.getBaseType().isReferenceType();
    return true;
  }

  private DexType getEnumClassTypeOrNull(Value receiver, Map<Instruction, DexType> convertedEnums) {
    TypeElement type = receiver.getType();
    if (type.isInt()) {
      return receiver.isPhi() ? null : convertedEnums.get(receiver.getDefinition());
    }
    return getEnumClassTypeOrNull(type);
  }

  private DexType getEnumClassTypeOrNull(TypeElement type) {
    if (!type.isClassType()) {
      return null;
    }
    return getEnumClassTypeOrNull(type.asClassType().getClassType());
  }

  private DexType getEnumClassTypeOrNull(DexType type) {
    return unboxedEnumsData.isUnboxedEnum(type) ? type : null;
  }

  private DexType getEnumArrayTypeOrNull(
      ArrayAccess arrayAccess, Map<Instruction, DexType> convertedEnums) {
    ArrayTypeElement arrayType = arrayAccess.array().getType().asArrayType();
    if (arrayType == null) {
      assert arrayAccess.array().getType().isNullType();
      return null;
    }
    if (arrayType.getNesting() != 1) {
      return null;
    }
    TypeElement baseType = arrayType.getBaseType();
    if (baseType.isClassType()) {
      return getEnumClassTypeOrNull(baseType.asClassType().getClassType());
    }
    if (arrayType.getBaseType().isInt()) {
      return arrayAccess.array().isPhi()
          ? null
          : convertedEnums.get(arrayAccess.array().getDefinition());
    }
    return null;
  }
}
