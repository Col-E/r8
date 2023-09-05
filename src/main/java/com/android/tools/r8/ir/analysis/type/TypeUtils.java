// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.analysis.type;

import static com.android.tools.r8.ir.code.Opcodes.ASSUME;
import static com.android.tools.r8.ir.code.Opcodes.CHECK_CAST;
import static com.android.tools.r8.ir.code.Opcodes.IF;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_GET;
import static com.android.tools.r8.ir.code.Opcodes.INSTANCE_PUT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_DIRECT;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_INTERFACE;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_STATIC;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_SUPER;
import static com.android.tools.r8.ir.code.Opcodes.INVOKE_VIRTUAL;
import static com.android.tools.r8.ir.code.Opcodes.RETURN;
import static com.android.tools.r8.ir.code.Opcodes.STATIC_PUT;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.Assume;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionOrPhi;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.Phi;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.WorkList;
import java.util.Objects;

public class TypeUtils {

  private static class UserAndValuePair {

    final InstructionOrPhi user;
    final Value value;

    UserAndValuePair(InstructionOrPhi user, Value value) {
      this.user = user;
      this.value = value;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null || getClass() != obj.getClass()) {
        return false;
      }
      UserAndValuePair pair = (UserAndValuePair) obj;
      return user == pair.user && value == pair.value;
    }

    @Override
    public int hashCode() {
      return Objects.hash(user, value);
    }
  }

  /**
   * Returns the "use type" of a given value {@link Value}, i.e., the weakest static type that this
   * value must have in order for the program to type check.
   */
  public static TypeElement computeUseType(
      AppView<AppInfoWithLiveness> appView, ProgramMethod method, Value value) {
    TypeElement staticType = value.getType();
    TypeElement useType = TypeElement.getBottom();
    WorkList<UserAndValuePair> users = WorkList.newEqualityWorkList();
    enqueueUsers(value, users);
    while (users.hasNext()) {
      UserAndValuePair item = users.next();
      InstructionOrPhi user = item.user;
      if (user.isPhi()) {
        enqueueUsers(user.asPhi(), users);
      } else {
        Instruction instruction = user.asInstruction();
        TypeElement instructionUseType =
            computeUseTypeForInstruction(appView, method, instruction, item.value, users);
        useType = useType.join(instructionUseType, appView);
        if (useType.isTop() || useType.equalUpToNullability(staticType)) {
          // Bail-out.
          return staticType;
        }
      }
    }
    return useType;
  }

  private static void enqueueUsers(Value value, WorkList<UserAndValuePair> users) {
    for (Instruction user : value.uniqueUsers()) {
      users.addIfNotSeen(new UserAndValuePair(user, value));
    }
    for (Phi user : value.uniquePhiUsers()) {
      users.addIfNotSeen(new UserAndValuePair(user, value));
    }
  }

  private static TypeElement computeUseTypeForInstruction(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethod method,
      Instruction instruction,
      Value value,
      WorkList<UserAndValuePair> users) {
    switch (instruction.opcode()) {
      case ASSUME:
        return computeUseTypeForAssume(instruction.asAssume(), users);
      case CHECK_CAST:
      case IF:
        return TypeElement.getBottom();
      case INSTANCE_GET:
        return computeUseTypeForInstanceGet(appView, instruction.asInstanceGet());
      case INSTANCE_PUT:
        return computeUseTypeForInstancePut(appView, instruction.asInstancePut(), value);
      case INVOKE_DIRECT:
      case INVOKE_INTERFACE:
      case INVOKE_STATIC:
      case INVOKE_SUPER:
      case INVOKE_VIRTUAL:
        return computeUseTypeForInvoke(appView, instruction.asInvokeMethod(), value);
      case RETURN:
        return computeUseTypeForReturn(appView, method);
      case STATIC_PUT:
        return computeUseTypeForStaticPut(appView, instruction.asStaticPut());
      default:
        // Bail out for unhandled instructions.
        return TypeElement.getTop();
    }
  }

  private static TypeElement computeUseTypeForAssume(
      Assume assume, WorkList<UserAndValuePair> users) {
    enqueueUsers(assume.outValue(), users);
    return TypeElement.getBottom();
  }

  private static TypeElement computeUseTypeForInstanceGet(
      AppView<AppInfoWithLiveness> appView, InstanceGet instanceGet) {
    return instanceGet.getField().getHolderType().toTypeElement(appView);
  }

  private static TypeElement computeUseTypeForInstancePut(
      AppView<AppInfoWithLiveness> appView, InstancePut instancePut, Value value) {
    DexField field = instancePut.getField();
    TypeElement useType = TypeElement.getBottom();
    if (instancePut.object() == value) {
      useType = useType.join(field.getHolderType().toTypeElement(appView), appView);
    }
    if (instancePut.value() == value) {
      useType = useType.join(field.getType().toTypeElement(appView), appView);
    }
    return useType;
  }

  private static TypeElement computeUseTypeForInvoke(
      AppView<AppInfoWithLiveness> appView, InvokeMethod invoke, Value value) {
    TypeElement useType = TypeElement.getBottom();
    for (int argumentIndex = 0; argumentIndex < invoke.arguments().size(); argumentIndex++) {
      Value argument = invoke.getArgument(argumentIndex);
      if (argument != value) {
        continue;
      }
      TypeElement useTypeForArgument =
          invoke
              .getInvokedMethod()
              .getArgumentType(argumentIndex, invoke.isInvokeStatic())
              .toTypeElement(appView);
      useType = useType.join(useTypeForArgument, appView);
    }
    assert !useType.isBottom();
    return useType;
  }

  private static TypeElement computeUseTypeForReturn(
      AppView<AppInfoWithLiveness> appView, ProgramMethod method) {
    return method.getReturnType().toTypeElement(appView);
  }

  private static TypeElement computeUseTypeForStaticPut(
      AppView<AppInfoWithLiveness> appView, StaticPut staticPut) {
    return staticPut.getField().getType().toTypeElement(appView);
  }

  @SuppressWarnings("ReferenceEquality")
  public static boolean isNullPointerException(TypeElement type, AppView<?> appView) {
    return type.isClassType()
        && type.asClassType().getClassType() == appView.dexItemFactory().npeType;
  }
}
