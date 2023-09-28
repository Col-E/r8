// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.graph.UseRegistry.MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;
import static com.android.tools.r8.ir.code.InvokeType.VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.ARGUMENT;
import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.CHECK_CAST;
import static com.android.tools.r8.ir.code.Opcodes.CONST_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.CONST_METHOD_HANDLE;
import static com.android.tools.r8.ir.code.Opcodes.CONST_METHOD_TYPE;
import static com.android.tools.r8.ir.code.Opcodes.INIT_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_OF;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_CUSTOM;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_MULTI_NEW_ARRAY;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_POLYMORPHIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_SUPER;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.MOVE_EXCEPTION;
import static com.android.tools.r8.ir.code.Opcodes.NEW_ARRAY_EMPTY;
import static com.android.tools.r8.ir.code.Opcodes.NEW_ARRAY_FILLED;
import static com.android.tools.r8.ir.code.Opcodes.NEW_INSTANCE;
import static com.android.tools.r8.ir.code.Opcodes.NEW_UNBOXED_ENUM_INSTANCE;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_GET;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_PUT;
import static com.android.tools.r8.utils.ObjectUtils.getBooleanOrElse;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DebugLocalInfo;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.classmerging.VerticallyMergedClasses;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.lens.NonIdentityGraphLens;
import com.android.tools.r8.graph.proto.ArgumentInfo;
import com.android.tools.r8.graph.proto.ArgumentInfoCollection;
import com.android.tools.r8.graph.proto.RemovedArgumentInfo;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.proto.RewrittenTypeInfo;
import com.android.tools.r8.ir.analysis.type.DestructivePhiTypeUpdater;
import com.android.tools.r8.ir.analysis.type.DynamicType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.SingleConstValue;
import com.android.tools.r8.ir.analysis.value.SingleValue;
import com.android.tools.r8.ir.code.Argument;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.BasicBlockIterator;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstMethodHandle;
import com.android.tools.r8.ir.code.ConstMethodType;
import com.android.tools.r8.ir.code.FieldInstruction;
import com.android.tools.r8.ir.code.FieldPut;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.InitClass;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstanceOf;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeMultiNewArray;
import com.android.tools.r8.ir.code.InvokePolymorphic;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.code.MoveException;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Position.SourcePosition;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.SafeCheckCast;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;
import com.android.tools.r8.ir.code.UnusedArgument;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.passes.TrivialPhiSimplifier;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxer;
import com.android.tools.r8.optimize.MemberRebindingAnalysis;
import com.android.tools.r8.optimize.argumentpropagation.lenscoderewriter.NullCheckInserter;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.LazyBox;
import com.android.tools.r8.verticalclassmerging.InterfaceTypeToClassTypeLensCodeRewriterHelper;
import com.google.common.collect.Sets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public class LensCodeRewriter {

  private static class GraphLensInterval {

    private final NonIdentityGraphLens graphLens;
    private final GraphLens codeLens;
    private final DexMethod method;

    GraphLensInterval(NonIdentityGraphLens graphLens, GraphLens codeLens, DexMethod method) {
      this.graphLens = graphLens;
      this.codeLens = codeLens;
      this.method = method;
    }

    public NonIdentityGraphLens getGraphLens() {
      return graphLens;
    }

    public GraphLens getCodeLens() {
      return codeLens;
    }

    public DexMethod getMethod() {
      return method;
    }
  }

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final DexItemFactory factory;
  private final EnumUnboxer enumUnboxer;
  private final InternalOptions options;

  LensCodeRewriter(AppView<? extends AppInfoWithClassHierarchy> appView, EnumUnboxer enumUnboxer) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.enumUnboxer = enumUnboxer;
    this.options = appView.options();
  }

  private Value makeOutValue(
      Instruction insn, IRCode code, NonIdentityGraphLens graphLens, GraphLens codeLens) {
    if (insn.hasOutValue()) {
      TypeElement oldType = insn.getOutType();
      TypeElement newType = oldType.rewrittenWithLens(appView, graphLens, codeLens);
      return code.createValue(newType, insn.getLocalInfo());
    }
    return null;
  }

  private Value makeOutValue(FieldInstruction insn, IRCode code, DexField rewrittenField) {
    if (insn.hasOutValue()) {
      Nullability nullability = insn.getOutType().nullability();
      TypeElement newType = TypeElement.fromDexType(rewrittenField.getType(), nullability, appView);
      return code.createValue(newType, insn.getLocalInfo());
    }
    return null;
  }

  /** Replace type appearances, invoke targets and field accesses with actual definitions. */
  public void rewrite(IRCode code, ProgramMethod method, MethodProcessor methodProcessor) {
    Deque<GraphLensInterval> unappliedLenses = getUnappliedLenses(method);
    DexMethod originalMethodReference =
        appView.graphLens().getOriginalMethodSignature(method.getReference());
    while (!unappliedLenses.isEmpty()) {
      GraphLensInterval unappliedLens = unappliedLenses.removeLast();
      RewrittenPrototypeDescription prototypeChanges =
          unappliedLens
              .getGraphLens()
              .lookupPrototypeChangesForMethodDefinition(
                  unappliedLens.getMethod(), unappliedLens.getCodeLens());
      rewritePartial(
          code,
          method,
          originalMethodReference,
          methodProcessor,
          unappliedLens.getGraphLens(),
          unappliedLens.getCodeLens(),
          prototypeChanges);
    }
    assert code.hasNoMergedClasses(appView);
  }

  private void rewritePartial(
      IRCode code,
      ProgramMethod method,
      DexMethod originalMethodReference,
      MethodProcessor methodProcessor,
      NonIdentityGraphLens graphLens,
      GraphLens codeLens,
      RewrittenPrototypeDescription prototypeChanges) {
    // Rewriting types that affects phi can cause us to compute TOP for cyclic phi's. To solve this
    // we track all phi's that needs to be re-computed.
    Set<Phi> affectedPhis = Sets.newIdentityHashSet();
    AffectedValues affectedValues = new AffectedValues();
    Set<UnusedArgument> unusedArguments = Sets.newIdentityHashSet();
    rewriteArguments(
        code, originalMethodReference, prototypeChanges, affectedPhis, unusedArguments);
    if (graphLens.hasCustomCodeRewritings()) {
      assert graphLens.isEnumUnboxerLens();
      assert graphLens.getPrevious() == codeLens;
      affectedPhis.addAll(enumUnboxer.rewriteCode(code, methodProcessor, prototypeChanges));
    }
    if (!unusedArguments.isEmpty()) {
      for (UnusedArgument unusedArgument : unusedArguments) {
        if (unusedArgument.outValue().hasPhiUsers()) {
          // See b/240282988: We can end up in situations where the second round of IR processing
          // introduce phis for irreducible control flow, we need to resolve them.
          TrivialPhiSimplifier.replaceUnusedArgumentTrivialPhis(unusedArgument);
        }
      }
    }
    rewritePartialDefault(
        code,
        method,
        graphLens,
        codeLens,
        prototypeChanges,
        affectedPhis,
        affectedValues,
        unusedArguments);
  }

  @SuppressWarnings("ReferenceEquality")
  private void rewritePartialDefault(
      IRCode code,
      ProgramMethod method,
      NonIdentityGraphLens graphLens,
      GraphLens codeLens,
      RewrittenPrototypeDescription prototypeChangesForMethod,
      Set<Phi> affectedPhis,
      AffectedValues affectedValues,
      Set<UnusedArgument> unusedArguments) {
    BasicBlockIterator blocks = code.listIterator();
    LazyBox<LensCodeRewriterUtils> helper =
        new LazyBox<>(() -> new LensCodeRewriterUtils(appView, graphLens, codeLens));
    InterfaceTypeToClassTypeLensCodeRewriterHelper interfaceTypeToClassTypeRewriterHelper =
        InterfaceTypeToClassTypeLensCodeRewriterHelper.create(appView, code, graphLens, codeLens);
    NullCheckInserter nullCheckInserter =
        NullCheckInserter.create(appView, code, graphLens, codeLens);
    boolean mayHaveUnreachableBlocks = false;
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      if (block.hasCatchHandlers() && options.enableVerticalClassMerging) {
        boolean anyGuardsRenamed = block.renameGuardsInCatchHandlers(graphLens, codeLens);
        if (anyGuardsRenamed) {
          mayHaveUnreachableBlocks |= unlinkDeadCatchHandlers(block, graphLens, codeLens);
        }
      }
      InstructionListIterator iterator = block.listIterator(code);
      while (iterator.hasNext()) {
        Instruction current = iterator.next();
        switch (current.opcode()) {
          case INVOKE_CUSTOM:
            {
              InvokeCustom invokeCustom = current.asInvokeCustom();
              DexCallSite callSite = invokeCustom.getCallSite();
              DexCallSite newCallSite = helper.computeIfAbsent().rewriteCallSite(callSite, method);
              if (newCallSite != callSite) {
                Value newOutValue = makeOutValue(invokeCustom, code, graphLens, codeLens);
                InvokeCustom newInvokeCustom =
                    new InvokeCustom(newCallSite, newOutValue, invokeCustom.inValues());
                iterator.replaceCurrentInstruction(newInvokeCustom);
                if (newOutValue != null && newOutValue.getType() != invokeCustom.getOutType()) {
                  affectedPhis.addAll(newOutValue.uniquePhiUsers());
                }
              }
            }
            break;

          case CONST_METHOD_HANDLE:
            {
              DexMethodHandle handle = current.asConstMethodHandle().getValue();
              DexMethodHandle newHandle =
                  helper
                      .computeIfAbsent()
                      .rewriteDexMethodHandle(handle, NOT_ARGUMENT_TO_LAMBDA_METAFACTORY, method);
              if (newHandle != handle) {
                iterator.replaceCurrentInstruction(
                    new ConstMethodHandle(current.outValue(), newHandle));
              }
            }
            break;
          case CONST_METHOD_TYPE:
            {
              ConstMethodType constType = current.asConstMethodType();
              DexProto rewrittenProto = helper.computeIfAbsent().rewriteProto(constType.getValue());
              if (constType.getValue() != rewrittenProto) {
                iterator.replaceCurrentInstruction(
                    new ConstMethodType(constType.outValue(), rewrittenProto));
              }
            }
            break;

          case INIT_CLASS:
            {
              InitClass initClass = current.asInitClass();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      initClass.getClassValue(),
                      (t, v) -> new InitClass(v, t),
                      graphLens,
                      codeLens);
            }
            break;

          case INVOKE_POLYMORPHIC:
            {
              InvokePolymorphic invoke = current.asInvokePolymorphic();
              // The invoked method is on java.lang.invoke.MethodHandle and always remains as is.
              assert factory.polymorphicMethods.isPolymorphicInvoke(invoke.getInvokedMethod());
              // Rewrite the signature of the handles actual target.
              DexProto rewrittenProto = helper.computeIfAbsent().rewriteProto(invoke.getProto());
              if (invoke.getProto() != rewrittenProto) {
                iterator.replaceCurrentInstruction(
                    new InvokePolymorphic(
                        invoke.getInvokedMethod(),
                        rewrittenProto,
                        invoke.outValue(),
                        invoke.arguments()));
              }
            }
            break;
          case INVOKE_DIRECT:
          case INVOKE_INTERFACE:
          case INVOKE_STATIC:
          case INVOKE_SUPER:
          case INVOKE_VIRTUAL:
            {
              InvokeMethod invoke = current.asInvokeMethod();
              DexMethod invokedMethod = invoke.getInvokedMethod();
              DexType invokedHolder = invokedMethod.holder;
              if (invokedHolder.isArrayType()) {
                DexType baseType = invokedHolder.toBaseType(factory);
                new InstructionReplacer(code, current, iterator, affectedPhis)
                    .replaceInstructionIfTypeChanged(
                        baseType,
                        (t, v) -> {
                          DexType mappedHolder = invokedHolder.replaceBaseType(t, factory);
                          // Just reuse proto and name, as no methods on array types cant be renamed
                          // nor change signature.
                          DexMethod actualTarget =
                              factory.createMethod(
                                  mappedHolder, invokedMethod.proto, invokedMethod.name);
                          return Invoke.create(VIRTUAL, actualTarget, null, v, invoke.inValues());
                        },
                        graphLens,
                        codeLens);
                continue;
              }
              if (!invokedHolder.isClassType()) {
                assert false;
                continue;
              }
              if (invoke.isInvokeDirect()) {
                checkInvokeDirect(method.getReference(), invoke.asInvokeDirect());
              }
              MethodLookupResult lensLookup =
                  graphLens.lookupMethod(
                      invokedMethod, method.getReference(), invoke.getType(), codeLens);
              DexMethod actualTarget = lensLookup.getReference();
              InvokeType actualInvokeType = lensLookup.getType();
              int numberOfArguments =
                  actualTarget.getNumberOfArguments(actualInvokeType.isStatic());

              iterator =
                  insertCastsForInvokeArgumentsIfNeeded(code, blocks, iterator, invoke, lensLookup);

              RewrittenPrototypeDescription prototypeChanges = lensLookup.getPrototypeChanges();
              if (prototypeChanges.requiresRewritingAtCallSite()
                  || invoke.getType() != actualInvokeType
                  || actualTarget != invokedMethod) {
                List<Value> newInValues;
                ArgumentInfoCollection argumentInfoCollection =
                    prototypeChanges.getArgumentInfoCollection();
                if (argumentInfoCollection.isEmpty()) {
                  if (prototypeChanges.hasExtraParameters()) {
                    newInValues = new ArrayList<>(numberOfArguments);
                    newInValues.addAll(invoke.arguments());
                    prototypeChanges.getExtraParameters().forEach(ignore -> newInValues.add(null));
                  } else {
                    newInValues = invoke.arguments();
                  }
                } else {
                  newInValues = Arrays.asList(new Value[numberOfArguments]);
                  int numberOfRemovedArguments = 0;
                  for (int argumentIndex = 0;
                      argumentIndex < invoke.arguments().size();
                      argumentIndex++) {
                    ArgumentInfo argumentInfo =
                        argumentInfoCollection.getArgumentInfo(argumentIndex);
                    if (argumentInfo.isRemovedArgumentInfo()) {
                      numberOfRemovedArguments++;
                      continue;
                    }
                    int newArgumentIndex =
                        argumentInfoCollection.getNewArgumentIndex(
                            argumentIndex, numberOfRemovedArguments);
                    Value newArgument;
                    if (argumentInfo.isRewrittenTypeInfo()) {
                      RewrittenTypeInfo argInfo = argumentInfo.asRewrittenTypeInfo();
                      newArgument =
                          rewriteValueIfDefault(
                              code,
                              iterator,
                              argInfo.getOldType(),
                              argInfo.getNewType(),
                              invoke.getArgument(argumentIndex));
                    } else {
                      newArgument = invoke.getArgument(argumentIndex);
                    }
                    newInValues.set(newArgumentIndex, newArgument);
                  }
                }

                Instruction constantReturnMaterializingInstruction = null;
                if (invoke.hasOutValue()) {
                  if (invoke.hasUnusedOutValue()) {
                    invoke.clearOutValue();
                  } else if (prototypeChanges.hasBeenChangedToReturnVoid()) {
                    TypeAndLocalInfoSupplier typeAndLocalInfo =
                        new TypeAndLocalInfoSupplier() {
                          @Override
                          public DebugLocalInfo getLocalInfo() {
                            return invoke.getLocalInfo();
                          }

                          @Override
                          public TypeElement getOutType() {
                            return graphLens
                                .lookupType(invokedMethod.getReturnType(), codeLens)
                                .toTypeElement(appView);
                          }
                        };
                    assert prototypeChanges.verifyConstantReturnAccessibleInContext(
                        appView.withLiveness(), method, graphLens);
                    constantReturnMaterializingInstruction =
                        prototypeChanges.getConstantReturn(
                            appView.withLiveness(), code, invoke.getPosition(), typeAndLocalInfo);
                    if (invoke.outValue().hasLocalInfo()) {
                      constantReturnMaterializingInstruction
                          .outValue()
                          .setLocalInfo(invoke.outValue().getLocalInfo());
                    }
                    invoke
                        .outValue()
                        .replaceUsers(
                            constantReturnMaterializingInstruction.outValue(), affectedValues);
                    if (!invoke
                        .getOutType()
                        .equals(constantReturnMaterializingInstruction.getOutType())) {
                      affectedPhis.addAll(
                          constantReturnMaterializingInstruction.outValue().uniquePhiUsers());
                    }
                  }
                }

                Value newOutValue;
                if (prototypeChanges.hasRewrittenReturnInfo()) {
                  if (invoke.hasOutValue() && !prototypeChanges.hasBeenChangedToReturnVoid()) {
                    TypeElement newReturnType =
                        prototypeChanges
                            .getRewrittenReturnInfo()
                            .getNewType()
                            .toTypeElement(appView);
                    newOutValue = code.createValue(newReturnType, invoke.getLocalInfo());
                    affectedPhis.addAll(invoke.outValue().uniquePhiUsers());
                  } else {
                    newOutValue = null;
                  }
                } else {
                  newOutValue = makeOutValue(invoke, code, graphLens, codeLens);
                }

                Map<SingleConstValue, Map<DexType, Value>> parameterMap = new IdentityHashMap<>();

                int extraArgumentIndex =
                    numberOfArguments - prototypeChanges.getExtraParameters().size();
                for (ExtraParameter parameter : prototypeChanges.getExtraParameters()) {
                  int newExtraArgumentIndex =
                      argumentInfoCollection.getNewArgumentIndex(extraArgumentIndex, 0);
                  DexType extraArgumentType =
                      actualTarget.getArgumentType(
                          newExtraArgumentIndex, actualInvokeType.isStatic());

                  SingleConstValue singleConstValue = parameter.getValue(appView);

                  // Try to find an existing constant instruction, otherwise generate a new one.
                  InstructionListIterator finalIterator = iterator;
                  Value value =
                      parameterMap
                          .computeIfAbsent(singleConstValue, ignore -> new IdentityHashMap<>())
                          .computeIfAbsent(
                              extraArgumentType,
                              ignore -> {
                                finalIterator.previous();
                                Instruction instruction =
                                    singleConstValue.createMaterializingInstruction(
                                        appView,
                                        code,
                                        TypeAndLocalInfoSupplier.create(
                                            parameter.getTypeElement(appView, extraArgumentType),
                                            null));
                                assert !instruction.instructionTypeCanThrow();
                                instruction.setPosition(
                                    options.debug ? invoke.getPosition() : Position.none());
                                finalIterator.add(instruction);
                                finalIterator.next();
                                return instruction.outValue();
                              });
                  newInValues.set(newExtraArgumentIndex, value);

                  // TODO(b/164901008): Fix when the number of arguments overflows.
                  if (newInValues.size() > 255) {
                    throw new CompilationError(
                        "The addition of extra unused null parameters in R8 led to the overflow of"
                            + " the number of arguments of the method "
                            + actualTarget);
                  }

                  extraArgumentIndex++;
                }

                // TODO(b/157111832): This bit should be part of the graph lens lookup result.
                boolean isInterface =
                    getBooleanOrElse(
                        appView.definitionFor(actualTarget.holder), DexClass::isInterface, false);
                InvokeMethod newInvoke =
                    InvokeMethod.create(
                        actualInvokeType, actualTarget, newOutValue, newInValues, isInterface);

                iterator.replaceCurrentInstruction(newInvoke);

                // Insert casts for the program to type check if interfaces has been vertically
                // merged into their unique (non-interface) subclass. See also b/199561570.
                interfaceTypeToClassTypeRewriterHelper.insertCastsForOperandsIfNeeded(
                    invoke, newInvoke, lensLookup, blocks, block, iterator);

                nullCheckInserter.insertNullCheckForInvokeReceiverIfNeeded(
                    invoke, newInvoke, lensLookup);

                if (newOutValue != null && newOutValue.getType() != current.getOutType()) {
                  affectedPhis.addAll(newOutValue.uniquePhiUsers());
                }

                if (constantReturnMaterializingInstruction != null) {
                  if (block.hasCatchHandlers()) {
                    // Split the block to ensure no instructions after throwing instructions.
                    iterator
                        .split(code, blocks)
                        .listIterator(code)
                        .add(constantReturnMaterializingInstruction);
                  } else {
                    iterator.add(constantReturnMaterializingInstruction);
                  }
                }
              }
            }
            break;

          case INSTANCE_GET:
            {
              InstanceGet instanceGet = current.asInstanceGet();
              DexField field = instanceGet.getField();
              FieldLookupResult lookup = graphLens.lookupFieldResult(field, codeLens);
              DexField rewrittenField = rewriteFieldReference(lookup, method);
              Value newOutValue = null;
              if (rewrittenField != field) {
                newOutValue = makeOutValue(instanceGet, code, rewrittenField);
                iterator.replaceCurrentInstruction(
                    new InstanceGet(newOutValue, instanceGet.object(), rewrittenField));
              }
              if (newOutValue != null) {
                if (lookup.hasReadCastType() && newOutValue.hasNonDebugUsers()) {
                  TypeElement castType =
                      TypeElement.fromDexType(
                          lookup.getReadCastType(), newOutValue.getType().nullability(), appView);
                  Value castOutValue = code.createValue(castType);
                  newOutValue.replaceUsers(castOutValue);
                  CheckCast checkCast =
                      SafeCheckCast.builder()
                          .setCastType(lookup.getReadCastType())
                          .setObject(newOutValue)
                          .setOutValue(castOutValue)
                          .setPosition(instanceGet)
                          .build();
                  iterator.addThrowingInstructionToPossiblyThrowingBlock(
                      code, blocks, checkCast, options);
                  affectedPhis.addAll(checkCast.outValue().uniquePhiUsers());
                } else if (newOutValue.getType() != instanceGet.getOutType()) {
                  affectedPhis.addAll(newOutValue.uniquePhiUsers());
                }
              }
            }
            break;

          case INSTANCE_PUT:
            {
              InstancePut instancePut = current.asInstancePut();
              DexField field = instancePut.getField();
              FieldLookupResult lookup = graphLens.lookupFieldResult(field, codeLens);
              iterator =
                  insertCastForFieldAssignmentIfNeeded(code, blocks, iterator, instancePut, lookup);

              DexField rewrittenField = rewriteFieldReference(lookup, method);
              if (rewrittenField != field) {
                Value rewrittenValue =
                    rewriteValueIfDefault(
                        code, iterator, field.type, rewrittenField.type, instancePut.value());
                InstancePut newInstancePut =
                    InstancePut.createPotentiallyInvalid(
                        rewrittenField, instancePut.object(), rewrittenValue);
                iterator.replaceCurrentInstruction(newInstancePut);
                interfaceTypeToClassTypeRewriterHelper.insertCastsForOperandsIfNeeded(
                    instancePut, newInstancePut, blocks, block, iterator);
              }
            }
            break;

          case STATIC_GET:
            {
              StaticGet staticGet = current.asStaticGet();
              DexField field = staticGet.getField();
              FieldLookupResult lookup = graphLens.lookupFieldResult(field, codeLens);
              DexField rewrittenField = rewriteFieldReference(lookup, method);
              Value newOutValue = null;
              if (rewrittenField != field) {
                newOutValue = makeOutValue(staticGet, code, rewrittenField);
                iterator.replaceCurrentInstruction(new StaticGet(newOutValue, rewrittenField));
              }
              if (newOutValue != null) {
                if (lookup.hasReadCastType() && newOutValue.hasNonDebugUsers()) {
                  TypeElement castType =
                      TypeElement.fromDexType(
                          lookup.getReadCastType(), newOutValue.getType().nullability(), appView);
                  Value castOutValue = code.createValue(castType);
                  newOutValue.replaceUsers(castOutValue);
                  CheckCast checkCast =
                      SafeCheckCast.builder()
                          .setCastType(lookup.getReadCastType())
                          .setObject(newOutValue)
                          .setOutValue(castOutValue)
                          .setPosition(staticGet)
                          .build();
                  iterator.addThrowingInstructionToPossiblyThrowingBlock(
                      code, blocks, checkCast, options);
                  affectedPhis.addAll(checkCast.outValue().uniquePhiUsers());
                } else if (newOutValue.getType() != staticGet.getOutType()) {
                  affectedPhis.addAll(newOutValue.uniquePhiUsers());
                }
              }
            }
            break;

          case STATIC_PUT:
            {
              StaticPut staticPut = current.asStaticPut();
              DexField field = staticPut.getField();
              FieldLookupResult lookup = graphLens.lookupFieldResult(field, codeLens);
              iterator =
                  insertCastForFieldAssignmentIfNeeded(code, blocks, iterator, staticPut, lookup);

              DexField actualField = rewriteFieldReference(lookup, method);
              if (actualField != field) {
                Value rewrittenValue =
                    rewriteValueIfDefault(
                        code, iterator, field.type, actualField.type, staticPut.value());
                StaticPut replacement = new StaticPut(rewrittenValue, actualField);
                iterator.replaceCurrentInstruction(replacement);
                interfaceTypeToClassTypeRewriterHelper.insertCastsForOperandsIfNeeded(
                    staticPut, replacement, blocks, block, iterator);
              }
            }
            break;

          case CHECK_CAST:
            {
              CheckCast checkCast = current.asCheckCast();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      checkCast.getType(),
                      (t, v) ->
                          new CheckCast(v, checkCast.object(), t, checkCast.ignoreCompatRules()),
                      graphLens,
                      codeLens);
            }
            break;

          case CONST_CLASS:
            {
              ConstClass constClass = current.asConstClass();
              Instruction replacement =
                  new InstructionReplacer(code, current, iterator, affectedPhis)
                      .replaceInstructionIfTypeChanged(
                          constClass.getValue(),
                          (t, v) ->
                              t.isPrimitiveType() || t.isVoidType()
                                  ? StaticGet.builder()
                                      .setField(
                                          factory
                                              .getBoxedMembersForPrimitiveOrVoidType(t)
                                              .getTypeField())
                                      .setOutValue(v)
                                      .build()
                                  : new ConstClass(v, t),
                          graphLens,
                          codeLens);
              if (replacement != null && replacement.isStaticGet()) {
                Value nonNullableValue = replacement.outValue();
                Value nullableValue =
                    code.createValue(
                        nonNullableValue
                            .getType()
                            .asReferenceType()
                            .getOrCreateVariant(Nullability.maybeNull()),
                        nonNullableValue.getLocalInfo());
                replacement.setOutValue(nullableValue);
                Assume assume =
                    Assume.create(
                        DynamicType.definitelyNotNull(),
                        nonNullableValue,
                        nullableValue,
                        replacement,
                        appView,
                        method);
                assume.setPosition(replacement.getPosition(), options);
                iterator.add(assume);
              }
            }
            break;

          case INSTANCE_OF:
            {
              InstanceOf instanceOf = current.asInstanceOf();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      instanceOf.type(),
                      (t, v) -> new InstanceOf(v, instanceOf.value(), t),
                      graphLens,
                      codeLens);
            }
            break;

          case INVOKE_MULTI_NEW_ARRAY:
            {
              InvokeMultiNewArray multiNewArray = current.asInvokeMultiNewArray();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      multiNewArray.getArrayType(),
                      (t, v) -> new InvokeMultiNewArray(t, v, multiNewArray.inValues()),
                      graphLens,
                      codeLens);
            }
            break;

          case NEW_ARRAY_FILLED:
            {
              NewArrayFilled newArray = current.asNewArrayFilled();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      newArray.getArrayType(),
                      (t, v) -> new NewArrayFilled(t, v, newArray.inValues()),
                      graphLens,
                      codeLens);
            }
            break;

          case MOVE_EXCEPTION:
            {
              MoveException moveException = current.asMoveException();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      moveException.getExceptionType(),
                      (t, v) -> new MoveException(v, t, options),
                      graphLens,
                      codeLens);
            }
            break;

          case NEW_ARRAY_EMPTY:
            {
              NewArrayEmpty newArrayEmpty = current.asNewArrayEmpty();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      newArrayEmpty.type,
                      (t, v) -> new NewArrayEmpty(v, newArrayEmpty.size(), t),
                      graphLens,
                      codeLens);
            }
            break;

          case NEW_INSTANCE:
            {
              DexType type = current.asNewInstance().clazz;
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(type, NewInstance::new, graphLens, codeLens);
            }
            break;

          case NEW_UNBOXED_ENUM_INSTANCE:
            break;

          case RETURN:
            {
              Return ret = current.asReturn();
              if (ret.isReturnVoid()) {
                break;
              }

              insertCastForReturnIfNeeded(code, blocks, iterator, ret, prototypeChangesForMethod);

              DexType returnType = code.context().getReturnType();
              Value retValue = ret.returnValue();
              DexType initialType =
                  retValue.getType().isPrimitiveType()
                      ? retValue.getType().asPrimitiveType().toDexType(factory)
                      : factory.objectType; // Place holder, any reference type will do.
              Value rewrittenValue =
                  rewriteValueIfDefault(code, iterator, initialType, returnType, retValue);
              Return rewrittenReturn;
              if (retValue != rewrittenValue) {
                rewrittenReturn = new Return(rewrittenValue);
                iterator.replaceCurrentInstruction(rewrittenReturn);
              } else {
                rewrittenReturn = ret;
              }

              // Insert casts for the program to type check if interfaces has been vertically
              // merged into their unique (non-interface) subclass. See also b/199561570.
              interfaceTypeToClassTypeRewriterHelper.insertCastsForOperandsIfNeeded(
                  rewrittenReturn, blocks, block, iterator);
            }
            break;

          case ARGUMENT:
            break;

          case ASSUME:
            assert false;
            break;

          default:
            if (current.hasOutValue()) {
              // For all other instructions, substitute any changed type.
              TypeElement type = current.getOutType();
              TypeElement substituted = type.rewrittenWithLens(appView, graphLens, codeLens);
              if (substituted != type) {
                current.outValue().setType(substituted);
                affectedPhis.addAll(current.outValue().uniquePhiUsers());
              }
            }
            break;
        }
      }
    }
    if (mayHaveUnreachableBlocks) {
      code.removeUnreachableBlocks();
    }
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    if (!affectedPhis.isEmpty()) {
      new DestructivePhiTypeUpdater(appView, graphLens, codeLens)
          .recomputeAndPropagateTypes(code, affectedPhis);
    }
    nullCheckInserter.processWorklist();
    code.removeAllDeadAndTrivialPhis();
    code.removeRedundantBlocks();
    removeUnusedArguments(method, code, unusedArguments);

    // Finalize cast and null check insertion.
    interfaceTypeToClassTypeRewriterHelper.processWorklist();

    assert code.isConsistentSSABeforeTypesAreCorrect(appView);
  }

  // Applies the prototype changes of the current method to the argument instructions:
  // - Replaces constant arguments by their constant value and then removes the (now unused)
  //   argument instruction
  // - Removes unused arguments
  // - Updates the type of arguments whose type has been strengthened
  // TODO(b/270398965): Replace LinkedList.
  @SuppressWarnings("JdkObsolete")
  private void rewriteArguments(
      IRCode code,
      DexMethod originalMethodReference,
      RewrittenPrototypeDescription prototypeChanges,
      Set<Phi> affectedPhis,
      Set<UnusedArgument> unusedArguments) {
    AffectedValues affectedValues = new AffectedValues();
    ArgumentInfoCollection argumentInfoCollection = prototypeChanges.getArgumentInfoCollection();
    List<Instruction> argumentPostlude = new LinkedList<>();
    int oldArgumentIndex = 0;
    int nextArgumentIndex = 0;
    int numberOfRemovedArguments = 0;
    InstructionListIterator instructionIterator = code.entryBlock().listIterator(code);
    while (instructionIterator.hasNext()) {
      Instruction instruction = instructionIterator.next();
      if (!instruction.isArgument()) {
        break;
      }

      Argument argument = instruction.asArgument();
      ArgumentInfo argumentInfo = argumentInfoCollection.getArgumentInfo(oldArgumentIndex);
      if (argumentInfo.isRemovedArgumentInfo()) {
        rewriteRemovedArgument(
            code,
            instructionIterator,
            originalMethodReference,
            argument,
            argumentInfo.asRemovedArgumentInfo(),
            affectedPhis,
            affectedValues,
            argumentPostlude,
            unusedArguments);
        numberOfRemovedArguments++;
      } else {
        int newArgumentIndex =
            argumentInfoCollection.getNewArgumentIndex(oldArgumentIndex, numberOfRemovedArguments);
        Argument replacement;
        if (argumentInfo.isRewrittenTypeInfo()) {
          replacement =
              rewriteArgumentType(
                  code,
                  argument,
                  argumentInfo.asRewrittenTypeInfo(),
                  affectedPhis,
                  newArgumentIndex);
          argument.outValue().replaceUsers(replacement.outValue());
        } else if (newArgumentIndex != oldArgumentIndex) {
          replacement =
              Argument.builder()
                  .setIndex(newArgumentIndex)
                  .setFreshOutValue(code, argument.getOutType(), argument.getLocalInfo())
                  .setPosition(argument.getPosition())
                  .build();
          argument.outValue().replaceUsers(replacement.outValue());
        } else {
          replacement = argument;
        }
        if (newArgumentIndex == nextArgumentIndex) {
          // This is the right position for the argument. Insert it into the code at this position.
          if (replacement != argument) {
            instructionIterator.replaceCurrentInstruction(replacement);
          }
          nextArgumentIndex++;
        } else {
          // Due the a permutation of the argument order, this argument needs to be inserted at a
          // later point. Enqueue the argument into the argument postlude.
          instructionIterator.removeInstructionIgnoreOutValue();
          ListIterator<Instruction> argumentPostludeIterator = argumentPostlude.listIterator();
          while (argumentPostludeIterator.hasNext()) {
            Instruction current = argumentPostludeIterator.next();
            if (!current.isArgument()
                || replacement.getIndexRaw() < current.asArgument().getIndexRaw()) {
              argumentPostludeIterator.previous();
              break;
            }
          }
          argumentPostludeIterator.add(replacement);
        }
      }
      oldArgumentIndex++;
    }

    instructionIterator.previous();

    if (!argumentPostlude.isEmpty()) {
      for (Instruction instruction : argumentPostlude) {
        instructionIterator.add(instruction);
      }
    }

    affectedValues.narrowingWithAssumeRemoval(appView, code);
  }

  private void rewriteRemovedArgument(
      IRCode code,
      InstructionListIterator instructionIterator,
      DexMethod originalMethodReference,
      Argument argument,
      RemovedArgumentInfo removedArgumentInfo,
      Set<Phi> affectedPhis,
      AffectedValues affectedValues,
      List<Instruction> argumentPostlude,
      Set<UnusedArgument> unusedArguments) {
    Instruction replacement;
    if (removedArgumentInfo.hasSingleValue()) {
      SingleValue singleValue = removedArgumentInfo.getSingleValue();
      TypeElement type =
          removedArgumentInfo.getType().isReferenceType() && singleValue.isNull()
              ? TypeElement.getNull()
              : removedArgumentInfo.getType().toTypeElement(appView);
      replacement =
          singleValue.createMaterializingInstruction(
              appView, code, TypeAndLocalInfoSupplier.create(type, argument.getLocalInfo()));
      replacement.setPosition(
          SourcePosition.builder().setLine(0).setMethod(originalMethodReference).build());
    } else {
      TypeElement unusedArgumentType = removedArgumentInfo.getType().toTypeElement(appView);
      replacement = new UnusedArgument(code.createValue(unusedArgumentType));
      replacement.setPosition(Position.none());
      unusedArguments.add(replacement.asUnusedArgument());
    }
    argument.outValue().replaceUsers(replacement.outValue(), affectedValues);
    affectedPhis.addAll(replacement.outValue().uniquePhiUsers());
    argumentPostlude.add(replacement);
    instructionIterator.removeOrReplaceByDebugLocalRead();
  }

  private Argument rewriteArgumentType(
      IRCode code,
      Argument argument,
      RewrittenTypeInfo rewrittenTypeInfo,
      Set<Phi> affectedPhis,
      int newArgumentIndex) {
    TypeElement rewrittenType = rewrittenTypeInfo.getNewType().toTypeElement(appView);
    Argument replacement =
        Argument.builder()
            .setIndex(newArgumentIndex)
            .setFreshOutValue(code, rewrittenType, argument.getLocalInfo())
            .setPosition(argument.getPosition())
            .build();
    affectedPhis.addAll(argument.outValue().uniquePhiUsers());
    return replacement;
  }

  private void removeUnusedArguments(
      ProgramMethod method, IRCode code, Set<UnusedArgument> unusedArguments) {
    for (UnusedArgument unusedArgument : unusedArguments) {
      if (unusedArgument.outValue().hasAnyUsers()) {
        throw new Unreachable("Unused argument with users in " + method.toSourceString());
      }
      InstructionListIterator instructionIterator = unusedArgument.getBlock().listIterator(code);
      instructionIterator.nextUntil(instruction -> instruction == unusedArgument);
      instructionIterator.removeOrReplaceByDebugLocalRead();
    }
  }

  private Deque<GraphLensInterval> getUnappliedLenses(ProgramMethod method) {
    Deque<GraphLensInterval> unappliedLenses = new ArrayDeque<>(8);
    GraphLens codeLens = method.getDefinition().getCode().getCodeLens(appView);
    GraphLens currentLens = appView.graphLens();
    DexMethod currentMethod = method.getReference();
    while (currentLens != codeLens) {
      assert currentLens.isNonIdentityLens();
      NonIdentityGraphLens currentNonIdentityLens = currentLens.asNonIdentityLens();
      NonIdentityGraphLens fromInclusiveLens = currentNonIdentityLens;
      if (!currentNonIdentityLens.hasCustomCodeRewritings()) {
        GraphLens fromInclusiveLensPredecessor = fromInclusiveLens.getPrevious();
        while (fromInclusiveLensPredecessor.isNonIdentityLens()
            && !fromInclusiveLensPredecessor.hasCustomCodeRewritings()
            && fromInclusiveLensPredecessor != codeLens) {
          fromInclusiveLens = fromInclusiveLensPredecessor.asNonIdentityLens();
          fromInclusiveLensPredecessor = fromInclusiveLens.getPrevious();
        }
      }
      GraphLensInterval unappliedLens =
          new GraphLensInterval(
              currentNonIdentityLens, fromInclusiveLens.getPrevious(), currentMethod);
      unappliedLenses.addLast(unappliedLens);
      currentLens = unappliedLens.getCodeLens();
      currentMethod = currentNonIdentityLens.getOriginalMethodSignature(currentMethod, currentLens);
    }
    assert unappliedLenses.size() <= 8;
    return unappliedLenses;
  }

  private InstructionListIterator insertCastForFieldAssignmentIfNeeded(
      IRCode code,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      FieldPut fieldPut,
      FieldLookupResult lookup) {
    if (lookup.hasWriteCastType()) {
      iterator.previous();
      CheckCast checkCast =
          SafeCheckCast.builder()
              .setObject(fieldPut.value())
              .setFreshOutValue(
                  code,
                  lookup
                      .getWriteCastType()
                      .toTypeElement(appView, fieldPut.value().getType().nullability()))
              .setCastType(lookup.getWriteCastType())
              .setPosition(fieldPut.getPosition())
              .build();
      iterator.add(checkCast);
      fieldPut.setValue(checkCast.outValue());

      if (checkCast.getBlock().hasCatchHandlers()) {
        // Split the block and reset the block iterator.
        BasicBlock splitBlock = iterator.splitCopyCatchHandlers(code, blocks, appView.options());
        BasicBlock previousBlock = blocks.previousUntil(block -> block == splitBlock);
        assert previousBlock == splitBlock;
        blocks.next();
        iterator = splitBlock.listIterator(code);
      }

      Instruction next = iterator.next();
      assert next == fieldPut;
    }
    return iterator;
  }

  private InstructionListIterator insertCastsForInvokeArgumentsIfNeeded(
      IRCode code,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      InvokeMethod invoke,
      MethodLookupResult lookup) {
    RewrittenPrototypeDescription prototypeChanges = lookup.getPrototypeChanges();
    if (prototypeChanges.isEmpty()) {
      return iterator;
    }
    for (int argumentIndex = 0; argumentIndex < invoke.arguments().size(); argumentIndex++) {
      RewrittenTypeInfo rewrittenTypeInfo =
          prototypeChanges
              .getArgumentInfoCollection()
              .getArgumentInfo(argumentIndex)
              .asRewrittenTypeInfo();
      if (rewrittenTypeInfo != null && rewrittenTypeInfo.hasCastType()) {
        iterator.previous();
        Value object = invoke.getArgument(argumentIndex);
        CheckCast checkCast =
            SafeCheckCast.builder()
                .setObject(object)
                .setFreshOutValue(
                    code,
                    rewrittenTypeInfo
                        .getCastType()
                        .toTypeElement(appView, object.getType().nullability()))
                .setCastType(rewrittenTypeInfo.getCastType())
                .setPosition(invoke.getPosition())
                .build();
        iterator.add(checkCast);
        invoke.replaceValue(argumentIndex, checkCast.outValue());

        if (checkCast.getBlock().hasCatchHandlers()) {
          // Split the block and reset the block iterator.
          BasicBlock splitBlock = iterator.splitCopyCatchHandlers(code, blocks, appView.options());
          BasicBlock previousBlock = blocks.previousUntil(block -> block == splitBlock);
          assert previousBlock == splitBlock;
          blocks.next();
          iterator = splitBlock.listIterator(code);
        }

        Instruction next = iterator.next();
        assert next == invoke;
      }
    }
    return iterator;
  }

  private InstructionListIterator insertCastForReturnIfNeeded(
      IRCode code,
      BasicBlockIterator blocks,
      InstructionListIterator iterator,
      Return ret,
      RewrittenPrototypeDescription prototypeChanges) {
    if (!prototypeChanges.hasRewrittenReturnInfo()
        || !prototypeChanges.getRewrittenReturnInfo().hasCastType()) {
      return iterator;
    }

    iterator.previous();

    // Split the block and reset the block iterator.
    if (ret.getBlock().hasCatchHandlers()) {
      BasicBlock splitBlock = iterator.splitCopyCatchHandlers(code, blocks, options);
      BasicBlock previousBlock = blocks.previousUntil(block -> block == splitBlock);
      assert previousBlock != null;
      blocks.next();
      iterator = splitBlock.listIterator(code);
    }

    DexType castType = prototypeChanges.getRewrittenReturnInfo().getCastType();
    Value returnValue = ret.returnValue();
    CheckCast checkCast =
        SafeCheckCast.builder()
            .setObject(returnValue)
            .setFreshOutValue(
                code, castType.toTypeElement(appView, returnValue.getType().nullability()))
            .setCastType(castType)
            .setPosition(ret.getPosition())
            .build();
    iterator.add(checkCast);
    ret.replaceValue(0, checkCast.outValue());

    Instruction next = iterator.next();
    assert next == ret;
    return iterator;
  }

  private DexField rewriteFieldReference(FieldLookupResult lookup, ProgramMethod context) {
    if (lookup.hasReboundReference()) {
      DexClass holder = appView.definitionFor(lookup.getReboundReference().getHolderType());
      DexEncodedField definition = lookup.getReboundReference().lookupOnClass(holder);
      if (definition != null) {
        DexClassAndField field = DexClassAndField.create(holder, definition);
        if (AccessControl.isMemberAccessible(field, holder, context, appView).isTrue()) {
          return MemberRebindingAnalysis.validMemberRebindingTargetFor(
              appView, field, lookup.getReference());
        }
      }
    }
    return lookup.getReference();
  }

  // If the initialValue is a default value and its type is rewritten from a reference type to a
  // primitive type, then the default value type lattice needs to be changed.
  private Value rewriteValueIfDefault(
      IRCode code,
      InstructionListIterator iterator,
      DexType oldType,
      DexType newType,
      Value initialValue) {
    if (initialValue.getType().isNullType() && defaultValueHasChanged(oldType, newType)) {
      assert newType.isIntType();
      iterator.previous();
      Value rewrittenDefaultValue =
          iterator.insertConstNumberInstruction(
              code, options, 0, defaultValueLatticeElement(newType));
      iterator.next();
      return rewrittenDefaultValue;
    }
    return initialValue;
  }

  private boolean defaultValueHasChanged(DexType oldType, DexType newType) {
    if (newType.isPrimitiveType()) {
      if (oldType.isPrimitiveType()) {
        return ValueType.fromDexType(newType) != ValueType.fromDexType(oldType);
      }
      return true;
    } else if (oldType.isPrimitiveType()) {
      return true;
    }
    // All reference types uses null as default value.
    assert newType.isReferenceType();
    assert oldType.isReferenceType();
    return false;
  }

  private TypeElement defaultValueLatticeElement(DexType type) {
    if (type.isPrimitiveType()) {
      return TypeElement.fromDexType(type, null, appView);
    }
    return TypeElement.getNull();
  }

  @SuppressWarnings("ReferenceEquality")
  // If the given invoke is on the form "invoke-direct A.<init>, v0, ..." and the definition of
  // value v0 is "new-instance v0, B", where B is a subtype of A (see the Art800 and B116282409
  // tests), then fail with a compilation error if A has previously been merged into B.
  //
  // The motivation for this is that the vertical class merger cannot easily recognize the above
  // code pattern, since it runs prior to IR construction. Therefore, we currently allow merging
  // A and B although this will lead to invalid code, because this code pattern does generally
  // not occur in practice (it leads to a verification error on the JVM, but not on Art).
  private void checkInvokeDirect(DexMethod method, InvokeDirect invoke) {
    VerticallyMergedClasses verticallyMergedClasses = appView.verticallyMergedClasses();
    if (verticallyMergedClasses == null) {
      // No need to check the invocation.
      return;
    }
    DexMethod invokedMethod = invoke.getInvokedMethod();
    if (invokedMethod.name != factory.constructorMethodName) {
      // Not a constructor call.
      return;
    }
    if (invoke.arguments().isEmpty()) {
      // The new instance should always be passed to the constructor call, but continue gracefully.
      return;
    }
    Value receiver = invoke.arguments().get(0);
    if (!receiver.isPhi() && receiver.definition.isNewInstance()) {
      NewInstance newInstance = receiver.definition.asNewInstance();
      if (newInstance.clazz != invokedMethod.holder
          && verticallyMergedClasses.hasBeenMergedIntoSubtype(invokedMethod.holder)) {
        // Generated code will not work. Fail with a compilation error.
        throw appView
            .options()
            .reporter
            .fatalError(
                String.format(
                    "Unable to rewrite `invoke-direct %s.<init>(new %s, ...)` in method `%s` after "
                        + "type `%s` was merged into `%s`. Please add the following rule to your "
                        + "Proguard configuration file: `-keep,allowobfuscation class %s`.",
                    invokedMethod.holder.toSourceString(),
                    newInstance.clazz,
                    method.toSourceString(),
                    invokedMethod.holder,
                    verticallyMergedClasses.getTargetFor(invokedMethod.holder),
                    invokedMethod.holder.toSourceString()));
      }
    }
  }

  /**
   * Due to class merging, it is possible that two exception classes have been merged into one. This
   * function removes catch handlers where the guards ended up being the same as a previous one.
   *
   * @return true if any dead catch handlers were removed.
   */
  private boolean unlinkDeadCatchHandlers(
      BasicBlock block, NonIdentityGraphLens graphLens, GraphLens codeLens) {
    assert block.hasCatchHandlers();
    CatchHandlers<BasicBlock> catchHandlers = block.getCatchHandlers();
    List<DexType> guards = catchHandlers.getGuards();
    List<BasicBlock> targets = catchHandlers.getAllTargets();

    Set<DexType> previouslySeenGuards = new HashSet<>();
    List<BasicBlock> deadCatchHandlers = new ArrayList<>();
    for (int i = 0; i < guards.size(); i++) {
      // The type may have changed due to class merging.
      DexType guard = graphLens.lookupType(guards.get(i), codeLens);
      boolean guardSeenBefore = !previouslySeenGuards.add(guard);
      if (guardSeenBefore) {
        deadCatchHandlers.add(targets.get(i));
      }
    }
    // Remove the guards that are guaranteed to be dead.
    for (BasicBlock deadCatchHandler : deadCatchHandlers) {
      deadCatchHandler.unlinkCatchHandler();
    }
    assert block.consistentCatchHandlers();
    return !deadCatchHandlers.isEmpty();
  }

  class InstructionReplacer {

    private final IRCode code;
    private final Instruction current;
    private final InstructionListIterator iterator;
    private final Set<Phi> affectedPhis;

    InstructionReplacer(
        IRCode code, Instruction current, InstructionListIterator iterator, Set<Phi> affectedPhis) {
      this.code = code;
      this.current = current;
      this.iterator = iterator;
      this.affectedPhis = affectedPhis;
    }

    @SuppressWarnings("ReferenceEquality")
    Instruction replaceInstructionIfTypeChanged(
        DexType type,
        BiFunction<DexType, Value, Instruction> constructor,
        NonIdentityGraphLens graphLens,
        GraphLens codeLens) {
      DexType newType = graphLens.lookupType(type, codeLens);
      if (newType != type) {
        Value newOutValue = makeOutValue(current, code, graphLens, codeLens);
        Instruction newInstruction = constructor.apply(newType, newOutValue);
        iterator.replaceCurrentInstruction(newInstruction);
        if (newOutValue != null) {
          if (!newOutValue.getType().equals(current.getOutType())) {
            affectedPhis.addAll(newOutValue.uniquePhiUsers());
          } else {
            assert current.hasInvariantOutType();
            assert current.isConstClass()
                || current.isInitClass()
                || current.isInstanceOf()
                || (current.isInvokeVirtual()
                    && current.asInvokeVirtual().getInvokedMethod().holder.isArrayType());
          }
        }
        return newInstruction;
      }
      return null;
    }
  }
}
