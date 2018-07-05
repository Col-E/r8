// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.jar;

import static org.objectweb.asm.Opcodes.ASM6;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.GraphLense;
import com.android.tools.r8.graph.JarApplicationReader;
import com.android.tools.r8.ir.optimize.Inliner.Constraint;
import com.android.tools.r8.ir.optimize.InliningConstraints;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

// This visitor can be used to determine if a piece of jar code has any instructions that the
// inliner would not be willing to inline. This can be used to determine if a method can be force
// inlined although its IR is still not available.
//
// TODO(christofferqa): This class is incomplete. Still need to add support for ConstClass,
// InstanceGet, InstanceOf, InstancePut, Monitor, MoveException, NewArrayEmpty, NewInstance,
// StaticGet, StaticPut, etc.
public class InliningConstraintVisitor extends MethodVisitor {

  private final JarApplicationReader application;
  private final AppInfoWithLiveness appInfo;
  private final InliningConstraints inliningConstraints;
  private final DexMethod method;
  private final DexType invocationContext;

  private Constraint constraint = Constraint.ALWAYS;

  public InliningConstraintVisitor(
      JarApplicationReader application,
      AppInfoWithLiveness appInfo,
      GraphLense graphLense,
      DexMethod method,
      DexType invocationContext) {
    super(ASM6);
    this.application = application;
    this.appInfo = appInfo;
    this.inliningConstraints = new InliningConstraints(appInfo, graphLense);
    this.method = method;
    this.invocationContext = invocationContext;
  }

  public Constraint getConstraint() {
    return constraint;
  }

  private void updateConstraint(Constraint other) {
    constraint = Constraint.min(constraint, other);
  }

  // Used to signal that the result is ready, such that we do not need to visit all instructions of
  // the method, if we can see early on that it cannot be inlined anyway.
  public boolean isFinished() {
    return constraint == Constraint.NEVER;
  }

  @Override
  public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
    DexType ownerType = application.getTypeFromName(owner);
    DexMethod target = application.getMethod(ownerType, name, desc);
    switch (opcode) {
      case Opcodes.INVOKEINTERFACE:
        updateConstraint(inliningConstraints.forInvokeInterface(target, invocationContext));
        break;

      case Opcodes.INVOKESPECIAL:
        if (name.equals(Constants.INSTANCE_INITIALIZER_NAME) || ownerType == invocationContext) {
          updateConstraint(inliningConstraints.forInvokeDirect(target, invocationContext));
        } else {
          updateConstraint(inliningConstraints.forInvokeSuper(target, invocationContext));
        }
        break;

      case Opcodes.INVOKESTATIC:
        updateConstraint(inliningConstraints.forInvokeStatic(target, invocationContext));
        break;

      case Opcodes.INVOKEVIRTUAL:
        // Instructions that target a private method in the same class are translated to
        // invoke-direct.
        if (target.holder == method.holder) {
          DexClass clazz = appInfo.definitionFor(target.holder);
          if (clazz != null && clazz.lookupDirectMethod(target) != null) {
            updateConstraint(inliningConstraints.forInvokeDirect(target, invocationContext));
            break;
          }
        }

        updateConstraint(inliningConstraints.forInvokeVirtual(target, invocationContext));
        break;

      default:
        throw new Unreachable("Unexpected opcode " + opcode);
    }
  }

  @Override
  public void visitTypeInsn(int opcode, String typeName) {
    DexType type = application.getTypeFromName(typeName);
    switch (opcode) {
      case Opcodes.ANEWARRAY:
        break;

      case Opcodes.CHECKCAST:
        updateConstraint(inliningConstraints.forCheckCast(type, invocationContext));
        break;

      case Opcodes.INSTANCEOF:
        break;

      case Opcodes.NEW:
        break;

      default:
        throw new Unreachable("Unexpected opcode " + opcode);
    }
  }
}
