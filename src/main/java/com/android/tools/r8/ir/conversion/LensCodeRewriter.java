// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.conversion;

import static com.android.tools.r8.graph.UseRegistry.MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;
import static com.android.tools.r8.ir.code.Invoke.Type.STATIC;
import static com.android.tools.r8.ir.code.Invoke.Type.VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.CHECK_CAST;
import static com.android.tools.r8.ir.code.Opcodes.CONST_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.CONST_METHOD_HANDLE;
import static com.android.tools.r8.ir.code.Opcodes.INIT_CLASS;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_OF;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_CUSTOM;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_MULTI_NEW_ARRAY;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_NEW_ARRAY;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_POLYMORPHIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_SUPER;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.MOVE_EXCEPTION;
import static com.android.tools.r8.ir.code.Opcodes.NEW_ARRAY_EMPTY;
import static com.android.tools.r8.ir.code.Opcodes.NEW_INSTANCE;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_GET;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_PUT;
import static com.android.tools.r8.utils.ObjectUtils.getBooleanOrElse;

import com.android.tools.r8.errors.CompilationError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AccessControl;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexClassAndField;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLens;
import com.android.tools.r8.graph.GraphLens.FieldLookupResult;
import com.android.tools.r8.graph.GraphLens.MethodLookupResult;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.RewrittenPrototypeDescription;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfo;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.ArgumentInfoCollection;
import com.android.tools.r8.graph.RewrittenPrototypeDescription.RewrittenTypeInfo;
import com.android.tools.r8.graph.classmerging.VerticallyMergedClasses;
import com.android.tools.r8.ir.analysis.type.DestructivePhiTypeUpdater;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.analysis.value.SingleNumberValue;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.CatchHandlers;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstInstruction;
import com.android.tools.r8.ir.code.ConstMethodHandle;
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
import com.android.tools.r8.ir.code.InvokeNewArray;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.MoveException;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Return;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.TypeAndLocalInfoSupplier;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.optimize.enums.EnumUnboxer;
import com.android.tools.r8.logging.Log;
import com.android.tools.r8.optimize.MemberRebindingAnalysis;
import com.google.common.collect.Sets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;

public class LensCodeRewriter {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;

  private final EnumUnboxer enumUnboxer;
  private final LensCodeRewriterUtils helper;

  LensCodeRewriter(AppView<? extends AppInfoWithClassHierarchy> appView, EnumUnboxer enumUnboxer) {
    this.appView = appView;
    this.enumUnboxer = enumUnboxer;
    this.helper = new LensCodeRewriterUtils(appView);
  }

  private Value makeOutValue(Instruction insn, IRCode code) {
    if (insn.outValue() != null) {
      TypeElement oldType = insn.getOutType();
      TypeElement newType =
          oldType.fixupClassTypeReferences(appView.graphLens()::lookupType, appView);
      return code.createValue(newType, insn.getLocalInfo());
    }
    return null;
  }

  /** Replace type appearances, invoke targets and field accesses with actual definitions. */
  public void rewrite(IRCode code, ProgramMethod method) {
    Set<Phi> affectedPhis =
        enumUnboxer != null ? enumUnboxer.rewriteCode(code) : Sets.newIdentityHashSet();
    GraphLens graphLens = appView.graphLens();
    DexItemFactory factory = appView.dexItemFactory();
    // Rewriting types that affects phi can cause us to compute TOP for cyclic phi's. To solve this
    // we track all phi's that needs to be re-computed.
    ListIterator<BasicBlock> blocks = code.listIterator();
    boolean mayHaveUnreachableBlocks = false;
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      if (block.hasCatchHandlers() && appView.options().enableVerticalClassMerging) {
        boolean anyGuardsRenamed = block.renameGuardsInCatchHandlers(graphLens);
        if (anyGuardsRenamed) {
          mayHaveUnreachableBlocks |= unlinkDeadCatchHandlers(block);
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
              DexCallSite newCallSite = helper.rewriteCallSite(callSite, method);
              if (newCallSite != callSite) {
                Value newOutValue = makeOutValue(invokeCustom, code);
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
                  helper.rewriteDexMethodHandle(handle, NOT_ARGUMENT_TO_LAMBDA_METAFACTORY, method);
              if (newHandle != handle) {
                Value newOutValue = makeOutValue(current, code);
                iterator.replaceCurrentInstruction(new ConstMethodHandle(newOutValue, newHandle));
                if (newOutValue != null && newOutValue.getType() != current.getOutType()) {
                  affectedPhis.addAll(newOutValue.uniquePhiUsers());
                }
              }
            }
            break;

          case INIT_CLASS:
            {
              InitClass initClass = current.asInitClass();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      initClass.getClassValue(), (t, v) -> new InitClass(v, t));
            }
            break;

          case INVOKE_DIRECT:
          case INVOKE_INTERFACE:
          case INVOKE_POLYMORPHIC:
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
                        });
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
                  graphLens.lookupMethod(invokedMethod, method.getReference(), invoke.getType());
              DexMethod actualTarget = lensLookup.getReference();
              Invoke.Type actualInvokeType = lensLookup.getType();
              if (actualTarget != invokedMethod || invoke.getType() != actualInvokeType) {
                RewrittenPrototypeDescription prototypeChanges = lensLookup.getPrototypeChanges();

                List<Value> newInValues;
                ArgumentInfoCollection argumentInfoCollection =
                    prototypeChanges.getArgumentInfoCollection();
                if (argumentInfoCollection.isEmpty()) {
                  newInValues = invoke.inValues();
                } else {
                  if (argumentInfoCollection.hasRemovedArguments()) {
                    if (Log.ENABLED) {
                      Log.info(
                          getClass(),
                          "Invoked method "
                              + invokedMethod.toSourceString()
                              + " with "
                              + argumentInfoCollection.numberOfRemovedArguments()
                              + " arguments removed");
                    }
                  }
                  newInValues = new ArrayList<>(actualTarget.proto.parameters.size());
                  for (int i = 0; i < invoke.inValues().size(); i++) {
                    ArgumentInfo argumentInfo = argumentInfoCollection.getArgumentInfo(i);
                    if (argumentInfo.isRewrittenTypeInfo()) {
                      RewrittenTypeInfo argInfo = argumentInfo.asRewrittenTypeInfo();
                      Value rewrittenValue =
                          rewriteValueIfDefault(
                              code,
                              iterator,
                              argInfo.getOldType(),
                              argInfo.getNewType(),
                              invoke.inValues().get(i));
                      newInValues.add(rewrittenValue);
                    } else if (!argumentInfo.isRemovedArgumentInfo()) {
                      newInValues.add(invoke.inValues().get(i));
                    }
                  }
                }

                ConstInstruction constantReturnMaterializingInstruction = null;
                if (prototypeChanges.hasBeenChangedToReturnVoid(appView)
                    && invoke.outValue() != null) {
                  constantReturnMaterializingInstruction =
                      prototypeChanges.getConstantReturn(code, invoke.getPosition());
                  if (invoke.outValue().hasLocalInfo()) {
                    constantReturnMaterializingInstruction
                        .outValue()
                        .setLocalInfo(invoke.outValue().getLocalInfo());
                  }
                  invoke.outValue().replaceUsers(constantReturnMaterializingInstruction.outValue());
                  if (invoke.getOutType() != constantReturnMaterializingInstruction.getOutType()) {
                    affectedPhis.addAll(
                        constantReturnMaterializingInstruction.outValue().uniquePhiUsers());
                  }
                }

                Value newOutValue =
                    prototypeChanges.hasBeenChangedToReturnVoid(appView)
                        ? null
                        : makeOutValue(invoke, code);

                Map<SingleNumberValue, Map<DexType, Value>> parameterMap = new IdentityHashMap<>();

                int parameterIndex = newInValues.size() - (actualInvokeType == STATIC ? 0 : 1);
                for (ExtraParameter parameter : prototypeChanges.getExtraParameters()) {
                  DexType type = actualTarget.proto.getParameter(parameterIndex++);

                  SingleNumberValue numberValue = parameter.getValue(appView);

                  // Try to find an existing constant instruction, otherwise generate a new one.
                  Value value =
                      parameterMap
                          .computeIfAbsent(numberValue, ignore -> new IdentityHashMap<>())
                          .computeIfAbsent(
                              type,
                              ignore -> {
                                iterator.previous();
                                Instruction instruction =
                                    numberValue.createMaterializingInstruction(
                                        appView,
                                        code,
                                        TypeAndLocalInfoSupplier.create(
                                            parameter.getTypeElement(appView, type), null));
                                assert !instruction.instructionTypeCanThrow();
                                instruction.setPosition(
                                    appView.options().debug
                                        ? invoke.getPosition()
                                        : Position.none());
                                iterator.add(instruction);
                                iterator.next();
                                return instruction.outValue();
                              });

                  newInValues.add(value);

                  // TODO(b/164901008): Fix when the number of arguments overflows.
                  if (newInValues.size() > 255) {
                    throw new CompilationError(
                        "The addition of extra unused null parameters in R8 led to the overflow of"
                            + " the number of arguments of the method "
                            + actualTarget);
                  }
                }

                assert newInValues.size()
                    == actualTarget.proto.parameters.size() + (actualInvokeType == STATIC ? 0 : 1);

                // TODO(b/157111832): This bit should be part of the graph lens lookup result.
                boolean isInterface =
                    getBooleanOrElse(
                        appView.definitionFor(actualTarget.holder), DexClass::isInterface, false);
                Invoke newInvoke =
                    Invoke.create(
                        actualInvokeType,
                        actualTarget,
                        null,
                        newOutValue,
                        newInValues,
                        isInterface);
                iterator.replaceCurrentInstruction(newInvoke);
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

                DexType actualReturnType = actualTarget.proto.returnType;
                DexType expectedReturnType = graphLens.lookupType(invokedMethod.proto.returnType);
                if (newInvoke.outValue() != null && actualReturnType != expectedReturnType) {
                  throw new Unreachable(
                      "Unexpected need to insert a cast. Possibly related to resolving"
                          + " b/79143143.\n"
                          + invokedMethod
                          + " type changed from "
                          + expectedReturnType
                          + " to "
                          + actualReturnType);
                }
              }
            }
            break;

          case INSTANCE_GET:
            {
              InstanceGet instanceGet = current.asInstanceGet();
              DexField field = instanceGet.getField();
              DexField actualField = rewriteFieldReference(field, method, graphLens);
              DexMethod replacementMethod =
                  graphLens.lookupGetFieldForMethod(actualField, method.getReference());
              if (replacementMethod != null) {
                Value newOutValue = makeOutValue(current, code);
                iterator.replaceCurrentInstruction(
                    new InvokeStatic(replacementMethod, newOutValue, current.inValues()));
                if (newOutValue != null && newOutValue.getType() != current.getOutType()) {
                  affectedPhis.addAll(current.outValue().uniquePhiUsers());
                }
              } else if (actualField != field) {
                Value newOutValue = makeOutValue(instanceGet, code);
                iterator.replaceCurrentInstruction(
                    new InstanceGet(newOutValue, instanceGet.object(), actualField));
                if (newOutValue != null && newOutValue.getType() != current.getOutType()) {
                  affectedPhis.addAll(newOutValue.uniquePhiUsers());
                }
              }
            }
            break;

          case INSTANCE_PUT:
            {
              InstancePut instancePut = current.asInstancePut();
              DexField field = instancePut.getField();
              DexField actualField = rewriteFieldReference(field, method, graphLens);
              DexMethod replacementMethod =
                  graphLens.lookupPutFieldForMethod(actualField, method.getReference());
              if (replacementMethod != null) {
                iterator.replaceCurrentInstruction(
                    new InvokeStatic(replacementMethod, null, current.inValues()));
              } else if (actualField != field) {
                Value rewrittenValue =
                    rewriteValueIfDefault(
                        code, iterator, field.type, actualField.type, instancePut.value());
                InstancePut newInstancePut =
                    InstancePut.createPotentiallyInvalid(
                        actualField, instancePut.object(), rewrittenValue);
                iterator.replaceCurrentInstruction(newInstancePut);
              }
            }
            break;

          case STATIC_GET:
            {
              StaticGet staticGet = current.asStaticGet();
              DexField field = staticGet.getField();
              DexField actualField = rewriteFieldReference(field, method, graphLens);
              DexMethod replacementMethod =
                  graphLens.lookupGetFieldForMethod(actualField, method.getReference());
              if (replacementMethod != null) {
                Value newOutValue = makeOutValue(current, code);
                iterator.replaceCurrentInstruction(
                    new InvokeStatic(replacementMethod, newOutValue, current.inValues()));
                if (newOutValue != null && newOutValue.getType() != current.getOutType()) {
                  affectedPhis.addAll(newOutValue.uniquePhiUsers());
                }
              } else if (actualField != field) {
                Value newOutValue = makeOutValue(staticGet, code);
                iterator.replaceCurrentInstruction(new StaticGet(newOutValue, actualField));
                if (newOutValue != null && newOutValue.getType() != current.getOutType()) {
                  affectedPhis.addAll(newOutValue.uniquePhiUsers());
                }
              }
            }
            break;

          case STATIC_PUT:
            {
              StaticPut staticPut = current.asStaticPut();
              DexField field = staticPut.getField();
              DexField actualField = rewriteFieldReference(field, method, graphLens);
              DexMethod replacementMethod =
                  graphLens.lookupPutFieldForMethod(actualField, method.getReference());
              if (replacementMethod != null) {
                iterator.replaceCurrentInstruction(
                    new InvokeStatic(replacementMethod, current.outValue(), current.inValues()));
              } else if (actualField != field) {
                Value rewrittenValue =
                    rewriteValueIfDefault(
                        code, iterator, field.type, actualField.type, staticPut.value());
                StaticPut newStaticPut = new StaticPut(rewrittenValue, actualField);
                iterator.replaceCurrentInstruction(newStaticPut);
              }
            }
            break;

          case CHECK_CAST:
            {
              CheckCast checkCast = current.asCheckCast();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      checkCast.getType(), (t, v) -> new CheckCast(v, checkCast.object(), t));
            }
            break;

          case CONST_CLASS:
            {
              ConstClass constClass = current.asConstClass();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      constClass.getValue(), (t, v) -> new ConstClass(v, t));
            }
            break;

          case INSTANCE_OF:
            {
              InstanceOf instanceOf = current.asInstanceOf();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      instanceOf.type(), (t, v) -> new InstanceOf(v, instanceOf.value(), t));
            }
            break;

          case INVOKE_MULTI_NEW_ARRAY:
            {
              InvokeMultiNewArray multiNewArray = current.asInvokeMultiNewArray();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      multiNewArray.getArrayType(),
                      (t, v) -> new InvokeMultiNewArray(t, v, multiNewArray.inValues()));
            }
            break;

          case INVOKE_NEW_ARRAY:
            {
              InvokeNewArray newArray = current.asInvokeNewArray();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      newArray.getArrayType(),
                      (t, v) -> new InvokeNewArray(t, v, newArray.inValues()));
            }
            break;

          case MOVE_EXCEPTION:
            {
              MoveException moveException = current.asMoveException();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      moveException.getExceptionType(),
                      (t, v) -> new MoveException(v, t, appView.options()));
            }
            break;

          case NEW_ARRAY_EMPTY:
            {
              NewArrayEmpty newArrayEmpty = current.asNewArrayEmpty();
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(
                      newArrayEmpty.type, (t, v) -> new NewArrayEmpty(v, newArrayEmpty.size(), t));
            }
            break;

          case NEW_INSTANCE:
            {
              DexType type = current.asNewInstance().clazz;
              new InstructionReplacer(code, current, iterator, affectedPhis)
                  .replaceInstructionIfTypeChanged(type, NewInstance::new);
            }
            break;

          case RETURN:
            {
              Return ret = current.asReturn();
              if (ret.isReturnVoid()) {
                break;
              }
              DexType returnType = code.method().method.proto.returnType;
              Value retValue = ret.returnValue();
              DexType initialType =
                  retValue.getType().isPrimitiveType()
                      ? retValue.getType().asPrimitiveType().toDexType(factory)
                      : factory.objectType; // Place holder, any reference type will do.
              Value rewrittenValue =
                  rewriteValueIfDefault(code, iterator, initialType, returnType, retValue);
              if (retValue != rewrittenValue) {
                Return newReturn = new Return(rewrittenValue);
                iterator.replaceCurrentInstruction(newReturn);
              }
            }
            break;

          default:
            if (current.hasOutValue()) {
              // For all other instructions, substitute any changed type.
              TypeElement type = current.getOutType();
              TypeElement substituted =
                  type.fixupClassTypeReferences(graphLens::lookupType, appView);
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
    if (!affectedPhis.isEmpty()) {
      new DestructivePhiTypeUpdater(appView).recomputeAndPropagateTypes(code, affectedPhis);
    }
    assert code.isConsistentSSABeforeTypesAreCorrect();
    assert code.hasNoVerticallyMergedClasses(appView);
  }

  private DexField rewriteFieldReference(
      DexField reference, ProgramMethod context, GraphLens graphLens) {
    FieldLookupResult lookup = graphLens.lookupFieldResult(reference);
    if (lookup.hasReboundReference()) {
      DexClass holder = appView.definitionFor(lookup.getReboundReference().getHolderType());
      DexEncodedField definition = lookup.getReboundReference().lookupOnClass(holder);
      if (definition != null) {
        DexClassAndField field = DexClassAndField.create(holder, definition);
        if (AccessControl.isMemberAccessible(field, holder, context, appView).isTrue()) {
          return MemberRebindingAnalysis.validMemberRebindingTargetFor(appView, field, reference);
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
    if (initialValue.isConstNumber()
        && initialValue.definition.asConstNumber().isZero()
        && defaultValueHasChanged(oldType, newType)) {
      iterator.previous();
      Value rewrittenDefaultValue =
          iterator.insertConstNumberInstruction(
              code, appView.options(), 0, defaultValueLatticeElement(newType));
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
    if (invokedMethod.name != appView.dexItemFactory().constructorMethodName) {
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
  private boolean unlinkDeadCatchHandlers(BasicBlock block) {
    assert block.hasCatchHandlers();
    CatchHandlers<BasicBlock> catchHandlers = block.getCatchHandlers();
    List<DexType> guards = catchHandlers.getGuards();
    List<BasicBlock> targets = catchHandlers.getAllTargets();

    Set<DexType> previouslySeenGuards = new HashSet<>();
    List<BasicBlock> deadCatchHandlers = new ArrayList<>();
    for (int i = 0; i < guards.size(); i++) {
      // The type may have changed due to class merging.
      DexType guard = appView.graphLens().lookupType(guards.get(i));
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

    void replaceInstructionIfTypeChanged(
        DexType type, BiFunction<DexType, Value, Instruction> constructor) {
      DexType newType = appView.graphLens().lookupType(type);
      if (newType != type) {
        Value newOutValue = makeOutValue(current, code);
        Instruction newInstruction = constructor.apply(newType, newOutValue);
        iterator.replaceCurrentInstruction(newInstruction);
        if (newOutValue != null) {
          if (newOutValue.getType() != current.getOutType()) {
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
      }
    }
  }
}
