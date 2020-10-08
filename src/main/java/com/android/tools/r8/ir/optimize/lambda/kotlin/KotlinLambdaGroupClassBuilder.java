// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.lambda.kotlin;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexAnnotationSet;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexTypeList;
import com.android.tools.r8.graph.DexValue.DexValueNull;
import com.android.tools.r8.graph.EnclosingMethodAttribute;
import com.android.tools.r8.graph.GenericSignature;
import com.android.tools.r8.graph.GenericSignature.ClassSignature;
import com.android.tools.r8.graph.GenericSignature.FieldTypeSignature;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ParameterAnnotationsList;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.code.IntSwitch;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.lambda.LambdaGroupClassBuilder;
import com.android.tools.r8.ir.synthetic.SynthesizedCode;
import com.android.tools.r8.ir.synthetic.SyntheticSourceCode;
import com.android.tools.r8.origin.SynthesizedOrigin;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.IntBox;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.TriConsumer;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

// Builds components of kotlin lambda group class.
abstract class KotlinLambdaGroupClassBuilder<T extends KotlinLambdaGroup>
    extends LambdaGroupClassBuilder<T> implements KotlinLambdaConstants {

  final KotlinLambdaGroupId id;
  final InternalOptions options;

  KotlinLambdaGroupClassBuilder(
      T group, DexItemFactory factory, InternalOptions options, String origin) {
    super(group, factory, origin);
    this.id = group.id();
    this.options = options;
  }

  abstract SyntheticSourceCode createInstanceInitializerSourceCode(
      DexType groupClassType, DexMethod initializerMethod, Position callerPosition);

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
    return !id.hasInnerClassAttribute()
        ? Collections.emptyList()
        : Lists.newArrayList(
            new InnerClassAttribute(id.innerClassAccess, group.getGroupClassType(), null, null));
  }

  @Override
  protected ClassSignature buildClassSignature() {
    // Kotlin-style lambdas supported by the merged may only contain optional signature and
    // kotlin metadata annotations. We remove the latter, but keep the signature if present.
    return GenericSignature.parseClassSignature(
        origin, id.signature, new SynthesizedOrigin(origin, getClass()), factory, options.reporter);
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

        // Merging lambdas can introduce methods with too many instructions for the verifier on
        // ART to give up statically verifying the method. We therefore split up the implementation
        // methods and chain them with fallthrough:
        // function <method>() {
        //   switch(field.id) {
        //     case 1:
        //     case 2:
        //     ...
        //     case n:
        //     default: <method$1>()
        // }
        //
        // function <method$1>() {
        //     case n + 1:
        //     case n + 2:
        //     ...
        //     case n + m:
        //     default: throw null
        // }
        IntBox counter = new IntBox(0);
        Box<DexMethod> currentMethodBox =
            new Box<>(factory.createMethod(group.getGroupClassType(), methodProto, methodName));
        splitIntoGroupsBasedOnInstructionSize(
            implMethods,
            (implMethodsToAdd, methodsSoFar, methodsRemaining) -> {
              assert currentMethodBox.isSet();
              // For bridge methods we still use same PUBLIC FINAL as for the main method,
              // since inlining removes BRIDGE & SYNTHETIC attributes from the bridge methods
              // anyways and our new method is a product of inlining.
              MethodAccessFlags accessFlags = MAIN_METHOD_FLAGS.copy();
              DexMethod method = currentMethodBox.get();
              DexMethod fallthrough =
                  methodsRemaining
                      ? factory.createMethod(
                          group.getGroupClassType(),
                          methodProto,
                          methodName.toString() + "$" + counter.getAndIncrement())
                      : null;
              result.add(
                  new DexEncodedMethod(
                      method,
                      accessFlags,
                      MethodTypeSignature.noSignature(),
                      isMainMethod ? id.mainMethodAnnotations : DexAnnotationSet.empty(),
                      isMainMethod
                          ? id.mainMethodParamAnnotations
                          : ParameterAnnotationsList.empty(),
                      new SynthesizedCode(
                          callerPosition ->
                              new KotlinLambdaVirtualMethodSourceCode(
                                  factory,
                                  group.getGroupClassType(),
                                  method,
                                  group.getLambdaIdField(factory),
                                  implMethodsToAdd,
                                  fallthrough,
                                  methodsSoFar,
                                  callerPosition)),
                      true));
              currentMethodBox.set(fallthrough);
            });
        assert !currentMethodBox.isSet();
      }
    }
    return result.toArray(DexEncodedMethod.EMPTY_ARRAY);
  }

  private void splitIntoGroupsBasedOnInstructionSize(
      List<DexEncodedMethod> implMethods,
      TriConsumer<List<DexEncodedMethod>, Integer, Boolean> consumer) {
    List<DexEncodedMethod> methods = new ArrayList<>();
    // Upper bound in DEX for reading the field for switching on the group id.
    final int fieldLoadInstructionSize = 10;
    int verificationSizeLimitInBytes = options.verificationSizeLimitInBytes();
    int currentInstructionsSize = fieldLoadInstructionSize;
    int implMethodsCommitted = 0;
    for (DexEncodedMethod implMethod : implMethods) {
      int packedSwitchPayloadSize =
          (int)
              (IntSwitch.basePackedSize(options.getInternalOutputMode())
                  + IntSwitch.packedPayloadSize(options.getInternalOutputMode(), methods.size()));
      Code code = implMethod.getCode();
      // We only do lambda merging for DEX. If we started doing lambda merging for CF, we would
      // have to compute a size.
      assert code.isDexCode();
      int codeSize = code.asDexCode().codeSizeInBytes();
      int estimatedMethodSize = currentInstructionsSize + codeSize + packedSwitchPayloadSize;
      if (methods.size() > 0 && estimatedMethodSize > verificationSizeLimitInBytes) {
        consumer.accept(methods, implMethodsCommitted, true);
        currentInstructionsSize = fieldLoadInstructionSize;
        implMethodsCommitted += methods.size();
        methods = new ArrayList<>();
      }
      methods.add(implMethod);
      currentInstructionsSize += codeSize;
    }
    consumer.accept(methods, implMethodsCommitted, false);
  }

  // Build a map of virtual methods with unique name/proto pointing to a list of methods
  // from lambda classes implementing appropriate logic. The indices in the list correspond
  // to lambda ids. Note that some of the slots in the lists may be empty, indicating the
  // fact that corresponding lambda does not have a virtual method with this signature.
  private Map<DexString, Map<DexProto, List<DexEncodedMethod>>> collectVirtualMethods() {
    Map<DexString, Map<DexProto, List<DexEncodedMethod>>> methods = new LinkedHashMap<>();
    int size = group.size();
    group.forEachLambda(info -> {
      for (DexEncodedMethod method : info.clazz.virtualMethods()) {
        List<DexEncodedMethod> list = methods
            .computeIfAbsent(method.method.name,
                k -> new LinkedHashMap<>())
            .computeIfAbsent(method.method.proto,
                k -> Lists.newArrayList(Collections.nCopies(size, null)));
        assert list.get(info.id) == null;
        list.set(info.id, method);
      }
    });
    return methods;
  }

  @Override
  protected DexEncodedMethod[] buildDirectMethods() {
    // We only build an instance initializer and optional class
    // initializer for stateless lambdas.

    boolean needsSingletonInstances = group.isStateless() && group.hasAnySingletons();
    DexType groupClassType = group.getGroupClassType();

    DexEncodedMethod[] result = new DexEncodedMethod[needsSingletonInstances ? 2 : 1];
    // Instance initializer mapping parameters into capture fields.
    DexProto initializerProto = group.createConstructorProto(factory);
    DexMethod initializerMethod =
        factory.createMethod(groupClassType, initializerProto, factory.constructorMethodName);
    result[0] =
        new DexEncodedMethod(
            initializerMethod,
            CONSTRUCTOR_FLAGS_RELAXED, // always create access-relaxed constructor.
            MethodTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            ParameterAnnotationsList.empty(),
            new SynthesizedCode(
                callerPosition ->
                    createInstanceInitializerSourceCode(
                        groupClassType, initializerMethod, callerPosition)),
            true);

    // Static class initializer for stateless lambdas.
    if (needsSingletonInstances) {
      DexMethod method =
          factory.createMethod(
              groupClassType,
              factory.createProto(factory.voidType),
              factory.classConstructorMethodName);
      result[1] =
          new DexEncodedMethod(
              method,
              CLASS_INITIALIZER_FLAGS,
              MethodTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              ParameterAnnotationsList.empty(),
              new SynthesizedCode(
                  callerPosition ->
                      new ClassInitializerSourceCode(method, factory, group, callerPosition)),
              true);
    }

    return result;
  }

  @Override
  protected DexEncodedField[] buildInstanceFields() {
    // Lambda id field plus other fields defined by the capture signature.
    String capture = id.capture;
    int size = capture.length();
    DexEncodedField[] result = new DexEncodedField[1 + size];

    result[0] =
        new DexEncodedField(
            group.getLambdaIdField(factory),
            CAPTURE_FIELD_FLAGS_RELAXED,
            FieldTypeSignature.noSignature(),
            DexAnnotationSet.empty(),
            null);

    for (int id = 0; id < size; id++) {
      result[id + 1] =
          new DexEncodedField(
              group.getCaptureField(factory, id),
              CAPTURE_FIELD_FLAGS_RELAXED,
              FieldTypeSignature.noSignature(),
              DexAnnotationSet.empty(),
              null);
    }

    return result;
  }

  @Override
  protected DexEncodedField[] buildStaticFields(
      AppView<? extends AppInfoWithClassHierarchy> appView, OptimizationFeedback feedback) {
    if (!group.isStateless()) {
      return DexEncodedField.EMPTY_ARRAY;
    }
    // One field for each singleton lambda in the group.
    List<DexEncodedField> result = new ArrayList<>(group.size());
    group.forEachLambda(
        info -> {
          if (group.isSingletonLambda(info.clazz.type)) {
            DexField field = group.getSingletonInstanceField(factory, info.id);
            DexEncodedField encodedField =
                new DexEncodedField(
                    field,
                    SINGLETON_FIELD_FLAGS,
                    FieldTypeSignature.noSignature(),
                    DexAnnotationSet.empty(),
                    DexValueNull.NULL);
            result.add(encodedField);

            // Record that the field is definitely not null. It is guaranteed to be assigned in the
            // class initializer of the enclosing class before it is read.
            ClassTypeElement exactType =
                ClassTypeElement.create(field.type, definitelyNotNull(), appView);
            feedback.markFieldHasDynamicLowerBoundType(encodedField, exactType);
            feedback.markFieldHasDynamicUpperBoundType(encodedField, exactType);
          }
        });
    assert result.isEmpty() == !group.hasAnySingletons();
    return result.toArray(DexEncodedField.EMPTY_ARRAY);
  }

  @Override
  protected DexTypeList buildInterfaces() {
    return new DexTypeList(new DexType[]{id.iface});
  }
}
