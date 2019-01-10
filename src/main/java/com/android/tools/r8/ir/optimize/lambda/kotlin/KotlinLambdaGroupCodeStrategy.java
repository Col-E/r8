// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.lambda.kotlin;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.analysis.type.Nullability.maybeNull;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.CheckCast;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.InstanceGet;
import com.android.tools.r8.ir.code.InstancePut;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.lambda.CaptureSignature;
import com.android.tools.r8.ir.optimize.lambda.CodeProcessor;
import com.android.tools.r8.ir.optimize.lambda.CodeProcessor.Strategy;
import com.android.tools.r8.ir.optimize.lambda.LambdaGroup;
import java.util.ArrayList;
import java.util.List;

// Defines the code processing strategy for kotlin lambdas.
final class KotlinLambdaGroupCodeStrategy implements Strategy {
  private final KotlinLambdaGroup group;

  KotlinLambdaGroupCodeStrategy(KotlinLambdaGroup group) {
    this.group = group;
  }

  @Override
  public LambdaGroup group() {
    return group;
  }

  @Override
  public boolean isValidStaticFieldWrite(CodeProcessor context, DexField field) {
    DexType lambda = field.clazz;
    assert group.containsLambda(lambda);
    // Only support writes to singleton static field named 'INSTANCE' from lambda
    // static class initializer.
    return field.name == context.kotlin.functional.kotlinStyleLambdaInstanceName &&
        lambda == field.type &&
        context.factory.isClassConstructor(context.method.method) &&
        context.method.method.holder == lambda;
  }

  @Override
  public boolean isValidStaticFieldRead(CodeProcessor context, DexField field) {
    DexType lambda = field.clazz;
    assert group.containsLambda(lambda);
    // Support all reads of singleton static field named 'INSTANCE'.
    return field.name == context.kotlin.functional.kotlinStyleLambdaInstanceName &&
        lambda == field.type;
  }

  @Override
  public boolean isValidInstanceFieldWrite(CodeProcessor context, DexField field) {
    DexType lambda = field.clazz;
    DexMethod method = context.method.method;
    assert group.containsLambda(lambda);
    // Support writes to capture instance fields inside lambda constructor only.
    return method.holder == lambda && context.factory.isConstructor(method);
  }

  @Override
  public boolean isValidInstanceFieldRead(CodeProcessor context, DexField field) {
    assert group.containsLambda(field.clazz);
    // Support all reads from capture instance fields.
    return true;
  }

  @Override
  public boolean isValidNewInstance(CodeProcessor context, NewInstance invoke) {
    // Only valid for stateful lambdas.
    return !(group.isStateless() && group.isSingletonLambda(invoke.clazz));
  }

  @Override
  public boolean isValidInvoke(CodeProcessor context, InvokeMethod invoke) {
    return isValidInitializerCall(context, invoke) || isValidVirtualCall(invoke);
  }

  private boolean isValidInitializerCall(CodeProcessor context, InvokeMethod invoke) {
    DexMethod method = invoke.getInvokedMethod();
    DexType lambda = method.holder;
    assert group.containsLambda(lambda);
    // Allow calls to a constructor from other classes if the lambda is singleton,
    // otherwise allow such a call only from the same class static initializer.
    boolean isSingletonLambda = group.isStateless() && group.isSingletonLambda(lambda);
    return (isSingletonLambda == (context.method.method.holder == lambda)) &&
        invoke.isInvokeDirect() &&
        context.factory.isConstructor(method) &&
        CaptureSignature.getCaptureSignature(method.proto.parameters).equals(group.id().capture);
  }

  private boolean isValidVirtualCall(InvokeMethod invoke) {
    assert group.containsLambda(invoke.getInvokedMethod().holder);
    // Allow all virtual calls.
    return invoke.isInvokeVirtual();
  }

  @Override
  public void patch(CodeProcessor context, NewInstance newInstance) {
    NewInstance patchedNewInstance = new NewInstance(
        group.getGroupClassType(),
        context.code.createValue(
            TypeLatticeElement.fromDexType(
                newInstance.clazz, definitelyNotNull(), context.appInfo)));
    context.instructions().replaceCurrentInstruction(patchedNewInstance);
  }

  @Override
  public void patch(CodeProcessor context, InvokeMethod invoke) {
    assert group.containsLambda(invoke.getInvokedMethod().holder);
    if (isValidInitializerCall(context, invoke)) {
      patchInitializer(context, invoke.asInvokeDirect());
    } else {
      // Regular calls to virtual methods only need target method be replaced.
      assert isValidVirtualCall(invoke);
      DexMethod method = invoke.getInvokedMethod();
      context.instructions().replaceCurrentInstruction(
          new InvokeVirtual(mapVirtualMethod(context.factory, method),
              createValueForType(context, method.proto.returnType), invoke.arguments()));
    }
  }

  @Override
  public void patch(CodeProcessor context, InstancePut instancePut) {
    // Instance put should only appear in lambda class instance constructor,
    // we should never get here since we never rewrite them.
    throw new Unreachable();
  }

  @Override
  public void patch(CodeProcessor context, InstanceGet instanceGet) {
    DexField field = instanceGet.getField();
    DexType fieldType = field.type;

    // We need to insert remapped values and in case the capture field
    // of type Object optionally cast to expected field.
    InstanceGet newInstanceGet =
        new InstanceGet(
            createValueForType(context, fieldType),
            instanceGet.object(),
            mapCaptureField(context.factory, field.clazz, field));
    context.instructions().replaceCurrentInstruction(newInstanceGet);

    if (fieldType.isPrimitiveType() || fieldType == context.factory.objectType) {
      return;
    }

    // Since all captured values of non-primitive types are stored in fields of type
    // java.lang.Object, we need to cast them to appropriate type to satisfy the verifier.
    TypeLatticeElement castTypeLattice =
        TypeLatticeElement.fromDexType(fieldType, definitelyNotNull(), context.appInfo);
    Value newValue = context.code.createValue(castTypeLattice, newInstanceGet.getLocalInfo());
    newInstanceGet.outValue().replaceUsers(newValue);
    CheckCast cast = new CheckCast(newValue, newInstanceGet.outValue(), fieldType);
    cast.setPosition(newInstanceGet.getPosition());
    context.instructions().add(cast);
    // If the current block has catch handlers split the check cast into its own block.
    // Since new cast is never supposed to fail, we leave catch handlers empty.
    if (cast.getBlock().hasCatchHandlers()) {
      context.instructions().previous();
      context.instructions().split(context.code, 1, context.blocks);
    }
  }

  @Override
  public void patch(CodeProcessor context, StaticPut staticPut) {
    // Static put should only appear in lambda class static initializer,
    // we should never get here since we never rewrite them.
    throw new Unreachable();
  }

  @Override
  public void patch(CodeProcessor context, StaticGet staticGet) {
    context.instructions().replaceCurrentInstruction(
        new StaticGet(
            context.code.createValue(
                TypeLatticeElement.fromDexType(staticGet.getField().type, maybeNull(), context.appInfo)),
            mapSingletonInstanceField(context.factory, staticGet.getField())));
  }

  private void patchInitializer(CodeProcessor context, InvokeDirect invoke) {
    // Patching includes:
    //  - change of methods
    //  - adding lambda id as the first argument
    //  - reshuffling other arguments (representing captured values)
    //    according to capture signature of the group.

    DexMethod method = invoke.getInvokedMethod();
    DexType lambda = method.holder;

    // Create constant with lambda id.
    Value lambdaIdValue = context.code.createValue(TypeLatticeElement.INT);
    ConstNumber lambdaId = new ConstNumber(lambdaIdValue, group.lambdaId(lambda));
    lambdaId.setPosition(invoke.getPosition());
    context.instructions().previous();
    context.instructions().add(lambdaId);

    // Create a new InvokeDirect instruction.
    Instruction next = context.instructions().next();
    assert next == invoke;

    DexMethod newTarget = mapInitializerMethod(context.factory, method);
    List<Value> newArguments = mapInitializerArgs(lambdaIdValue, invoke.arguments(), method.proto);
    context.instructions().replaceCurrentInstruction(
        new InvokeDirect(newTarget, null /* no return value */, newArguments)
    );
  }

  private Value createValueForType(CodeProcessor context, DexType returnType) {
    return returnType == context.factory.voidType ? null :
        context.code.createValue(
            TypeLatticeElement.fromDexType(returnType, maybeNull(), context.appInfo));
  }

  private List<Value> mapInitializerArgs(
      Value lambdaIdValue, List<Value> oldArguments, DexProto proto) {
    assert oldArguments.size() == proto.parameters.size() + 1;
    List<Value> newArguments = new ArrayList<>();
    newArguments.add(oldArguments.get(0)); // receiver
    newArguments.add(lambdaIdValue); // lambda-id
    List<Integer> reverseMapping =
        CaptureSignature.getReverseCaptureMapping(proto.parameters.values);
    for (int index : reverseMapping) {
      // <original-capture-index> = mapping[<normalized-capture-index>]
      newArguments.add(oldArguments.get(index + 1 /* after receiver */));
    }
    return newArguments;
  }

  // Map lambda class initializer into lambda group class initializer.
  private DexMethod mapInitializerMethod(DexItemFactory factory, DexMethod method) {
    assert factory.isConstructor(method);
    assert CaptureSignature.getCaptureSignature(method.proto.parameters).equals(group.id().capture);
    return factory.createMethod(group.getGroupClassType(),
        group.createConstructorProto(factory), method.name);
  }

  // Map lambda class virtual method into lambda group class method.
  private DexMethod mapVirtualMethod(DexItemFactory factory, DexMethod method) {
    return factory.createMethod(group.getGroupClassType(), method.proto, method.name);
  }

  // Map lambda class capture field into lambda group class capture field.
  private DexField mapCaptureField(DexItemFactory factory, DexType lambda, DexField field) {
    return group.getCaptureField(factory, group.mapFieldIntoCaptureIndex(lambda, field));
  }

  // Map lambda class initializer into lambda group class initializer.
  private DexField mapSingletonInstanceField(DexItemFactory factory, DexField field) {
    return group.getSingletonInstanceField(factory, group.lambdaId(field.clazz));
  }
}
