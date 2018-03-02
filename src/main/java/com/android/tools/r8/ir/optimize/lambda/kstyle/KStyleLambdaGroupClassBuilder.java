// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.lambda.kstyle;

import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.DexAnnotation;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexAnnotationSetRefList;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexValue.DexValueNull;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.ir.optimize.lambda.LambdaGroup.LambdaInfo;
import com.android.tools.r8.ir.optimize.lambda.LambdaGroupClassBuilder;
import com.android.tools.r8.ir.synthetic.SynthesizedCode;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

// Builds components of k-style lambda group class.
final class KStyleLambdaGroupClassBuilder
    extends LambdaGroupClassBuilder<KStyleLambdaGroup> implements KStyleConstants {

  private final KStyleLambdaGroupId id;

  KStyleLambdaGroupClassBuilder(DexItemFactory factory, KStyleLambdaGroup group, String origin) {
    super(group, factory, origin);
    this.id = group.id();
  }

  @Override
  protected DexType getSuperClassType() {
    return factory.kotlin.functional.lambdaType;
  }

  // Always generate public final classes.
  @Override
  protected ClassAccessFlags buildAccessFlags() {
    return PUBLIC_LAMBDA_CLASS_FLAGS;
  }

  // Take the attribute from the group, if exists.
  @Override
  protected EnclosingMethodAttribute buildEnclosingMethodAttribute() {
    return id.enclosing;
  }

  // Take the attribute from the group, if exists.
  @Override
  protected List<InnerClassAttribute> buildInnerClasses() {
    return id.innerClassAccess == KStyleLambdaGroupId.MISSING_INNER_CLASS_ATTRIBUTE
        ? Collections.emptyList()
        : Lists.newArrayList(new InnerClassAttribute(
            id.innerClassAccess, group.getGroupClassType(), null, null));
  }

  @Override
  protected DexAnnotationSet buildAnnotations() {
    // Kotlin-style lambdas supported by the merged may only contain optional signature and
    // kotlin metadata annotations. We remove the latter, but keep the signature if present.
    String signature = id.signature;
    return signature == null ? DexAnnotationSet.empty()
        : new DexAnnotationSet(new DexAnnotation[]{
            DexAnnotation.createSignatureAnnotation(signature, factory)});
  }

  @Override
  protected DexEncodedMethod[] buildVirtualMethods() {
    // All virtual method are dispatched on $id$ field.
    //
    // For each of the virtual method name/signatures seen in the group
    // we generate a correspondent method in lambda group class with same
    // name/signatures dispatching the call to appropriate code taken
    // from the lambda class.

    Map<DexString, Map<DexProto, List<DexEncodedMethod>>> methods = collectVirtualMethods();
    List<DexEncodedMethod> result = new ArrayList<>();

    for (Entry<DexString, Map<DexProto, List<DexEncodedMethod>>> upper : methods.entrySet()) {
      DexString methodName = upper.getKey();
      for (Entry<DexProto, List<DexEncodedMethod>> inner : upper.getValue().entrySet()) {
        // Methods for unique name/signature pair.
        DexProto methodProto = inner.getKey();
        List<DexEncodedMethod> implMethods = inner.getValue();

        boolean isMainMethod =
            id.mainMethodName == methodName && id.mainMethodProto == methodProto;

        // For bridge methods we still use same PUBLIC FINAL as for the main method,
        // since inlining removes BRIDGE & SYNTHETIC attributes from the bridge methods
        // anyways and our new method is a product of inlining.
        MethodAccessFlags accessFlags = MAIN_METHOD_FLAGS.copy();

        // Mark all the impl methods for force inlining
        // LambdaGroupVirtualMethodSourceCode relies on.
        for (DexEncodedMethod implMethod : implMethods) {
          if (implMethod != null) {
            implMethod.markForceInline();
          }
        }

        result.add(new DexEncodedMethod(
            factory.createMethod(group.getGroupClassType(), methodProto, methodName),
            accessFlags,
            isMainMethod ? id.mainMethodAnnotations : DexAnnotationSet.empty(),
            isMainMethod ? id.mainMethodParamAnnotations : DexAnnotationSetRefList.empty(),
            new SynthesizedCode(
                new VirtualMethodSourceCode(factory, group.getGroupClassType(),
                    methodProto, group.getLambdaIdField(factory), implMethods))));
      }
    }

    return result.toArray(new DexEncodedMethod[result.size()]);
  }

  // Build a map of virtual methods with unique name/proto pointing to a list of methods
  // from lambda classes implementing appropriate logic. The indices in the list correspond
  // to lambda ids. Note that some of the slots in the lists may be empty, indicating the
  // fact that corresponding lambda does not have a virtual method with this signature.
  private Map<DexString, Map<DexProto, List<DexEncodedMethod>>> collectVirtualMethods() {
    Map<DexString, Map<DexProto, List<DexEncodedMethod>>> methods = new LinkedHashMap<>();
    assert lambdaIdsOrdered();
    for (LambdaInfo lambda : lambdas) {
      for (DexEncodedMethod method : lambda.clazz.virtualMethods()) {
        List<DexEncodedMethod> list = methods
            .computeIfAbsent(method.method.name,
                k -> new LinkedHashMap<>())
            .computeIfAbsent(method.method.proto,
                k -> Lists.newArrayList(Collections.nCopies(lambdas.size(), null)));
        assert list.get(lambda.id) == null;
        list.set(lambda.id, method);
      }
    }
    return methods;
  }

  private boolean lambdaIdsOrdered() {
    for (int i = 0; i < lambdas.size(); i++) {
      assert lambdas.get(i).id == i;
    }
    return true;
  }

  @Override
  protected DexEncodedMethod[] buildDirectMethods() {
    // We only build an instance initializer and optional class
    // initializer for stateless lambdas.

    boolean statelessLambda = group.isStateless();
    DexType groupClassType = group.getGroupClassType();

    DexEncodedMethod[] result = new DexEncodedMethod[statelessLambda ? 2 : 1];
    // Instance initializer mapping parameters into capture fields.
    DexProto initializerProto = group.createConstructorProto(factory);
    result[0] = new DexEncodedMethod(
        factory.createMethod(groupClassType, initializerProto, factory.constructorMethodName),
        CONSTRUCTOR_FLAGS_RELAXED,  // always create access-relaxed constructor.
        DexAnnotationSet.empty(),
        DexAnnotationSetRefList.empty(),
        new SynthesizedCode(
            new InstanceInitializerSourceCode(factory, groupClassType,
                group.getLambdaIdField(factory), id -> group.getCaptureField(factory, id),
                initializerProto, id.mainMethodProto.parameters.size())));

    // Static class initializer for stateless lambdas.
    if (statelessLambda) {
      result[1] = new DexEncodedMethod(
          factory.createMethod(groupClassType,
              factory.createProto(factory.voidType),
              factory.classConstructorMethodName),
          CLASS_INITIALIZER_FLAGS,
          DexAnnotationSet.empty(),
          DexAnnotationSetRefList.empty(),
          new SynthesizedCode(
              new ClassInitializerSourceCode(
                  factory, groupClassType, lambdas.size(),
                  id -> group.getSingletonInstanceField(factory, id))));
    }

    return result;
  }

  @Override
  protected DexEncodedField[] buildInstanceFields() {
    // Lambda id field plus other fields defined by the capture signature.
    String capture = id.capture;
    int size = capture.length();
    DexEncodedField[] result = new DexEncodedField[1 + size];

    result[0] = new DexEncodedField(group.getLambdaIdField(factory),
        CAPTURE_FIELD_FLAGS_RELAXED, DexAnnotationSet.empty(), null);

    for (int id = 0; id < size; id++) {
      result[id + 1] = new DexEncodedField(group.getCaptureField(factory, id),
          CAPTURE_FIELD_FLAGS_RELAXED, DexAnnotationSet.empty(), null);
    }

    return result;
  }

  @Override
  protected DexEncodedField[] buildStaticFields() {
    if (!group.isStateless()) {
      return DexEncodedField.EMPTY_ARRAY;
    }

    // One field for each stateless lambda in the group.
    int size = lambdas.size();
    DexEncodedField[] result = new DexEncodedField[size];
    for (int id = 0; id < size; id++) {
      result[id] = new DexEncodedField(group.getSingletonInstanceField(factory, id),
          SINGLETON_FIELD_FLAGS, DexAnnotationSet.empty(), DexValueNull.NULL);
    }
    return result;
  }

  @Override
  protected DexTypeList buildInterfaces() {
    return new DexTypeList(new DexType[]{id.iface});
  }
}
