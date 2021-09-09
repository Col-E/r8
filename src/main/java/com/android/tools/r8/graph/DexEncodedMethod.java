// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.DexEncodedMethod.CompilationState.PROCESSED_INLINING_CANDIDATE_ANY;
import static com.android.tools.r8.graph.DexEncodedMethod.CompilationState.PROCESSED_INLINING_CANDIDATE_SAME_CLASS;
import static com.android.tools.r8.graph.DexEncodedMethod.CompilationState.PROCESSED_INLINING_CANDIDATE_SAME_NEST;
import static com.android.tools.r8.graph.DexEncodedMethod.CompilationState.PROCESSED_INLINING_CANDIDATE_SAME_PACKAGE;
import static com.android.tools.r8.graph.DexEncodedMethod.CompilationState.PROCESSED_INLINING_CANDIDATE_SUBCLASS;
import static com.android.tools.r8.graph.DexEncodedMethod.CompilationState.PROCESSED_NOT_INLINING_CANDIDATE;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.getNoKotlinInfo;
import static com.android.tools.r8.utils.AndroidApiLevel.NOT_SET;
import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;
import static java.util.Objects.requireNonNull;

import com.android.tools.r8.cf.CfVersion;
import com.android.tools.r8.cf.code.CfConstNull;
import com.android.tools.r8.cf.code.CfConstNumber;
import com.android.tools.r8.cf.code.CfConstString;
import com.android.tools.r8.cf.code.CfInstanceOf;
import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.cf.code.CfLoad;
import com.android.tools.r8.cf.code.CfLogicalBinop;
import com.android.tools.r8.cf.code.CfNew;
import com.android.tools.r8.cf.code.CfReturn;
import com.android.tools.r8.cf.code.CfStackInstruction;
import com.android.tools.r8.cf.code.CfStackInstruction.Opcode;
import com.android.tools.r8.cf.code.CfStore;
import com.android.tools.r8.cf.code.CfThrow;
import com.android.tools.r8.code.Const4;
import com.android.tools.r8.code.ConstString;
import com.android.tools.r8.code.InstanceOf;
import com.android.tools.r8.code.Instruction;
import com.android.tools.r8.code.InvokeDirect;
import com.android.tools.r8.code.InvokeStatic;
import com.android.tools.r8.code.NewInstance;
import com.android.tools.r8.code.Return;
import com.android.tools.r8.code.Throw;
import com.android.tools.r8.code.XorIntLit8;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.JumboStringRewriter;
import com.android.tools.r8.dex.MethodToCodeObjectMapping;
import com.android.tools.r8.dex.MixedSectionCollection;
import com.android.tools.r8.errors.InternalCompilerError;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.DexAnnotation.AnnotatedKind;
import com.android.tools.r8.graph.GenericSignature.MethodTypeSignature;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.NumericType;
import com.android.tools.r8.ir.code.ValueType;
import com.android.tools.r8.ir.conversion.DexBuilder;
import com.android.tools.r8.ir.optimize.Inliner.ConstraintWithTarget;
import com.android.tools.r8.ir.optimize.Inliner.Reason;
import com.android.tools.r8.ir.optimize.NestUtils;
import com.android.tools.r8.ir.optimize.info.CallSiteOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.ConcreteCallSiteOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.DefaultMethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.MutableMethodOptimizationInfo;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackSimple;
import com.android.tools.r8.ir.optimize.inliner.WhyAreYouNotInliningReporter;
import com.android.tools.r8.ir.regalloc.RegisterAllocator;
import com.android.tools.r8.ir.synthetic.ForwardMethodBuilder;
import com.android.tools.r8.kotlin.KotlinMethodLevelInfo;
import com.android.tools.r8.naming.ClassNameMapper;
import com.android.tools.r8.naming.MemberNaming.MethodSignature;
import com.android.tools.r8.naming.MemberNaming.Signature;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.position.MethodPosition;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ConsumerUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.structural.CompareToVisitor;
import com.android.tools.r8.utils.structural.HashingVisitor;
import com.android.tools.r8.utils.structural.Ordered;
import com.android.tools.r8.utils.structural.StructuralItem;
import com.android.tools.r8.utils.structural.StructuralMapping;
import com.android.tools.r8.utils.structural.StructuralSpecification;
import com.google.common.collect.ImmutableList;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.IntPredicate;
import org.objectweb.asm.Opcodes;

public class DexEncodedMethod extends DexEncodedMember<DexEncodedMethod, DexMethod>
    implements StructuralItem<DexEncodedMethod> {

  public static final String CONFIGURATION_DEBUGGING_PREFIX = "Shaking error: Missing method in ";

  /**
   * Encodes the processing state of a method.
   *
   * <p>We also use this enum to encode under what constraints a method may be inlined.
   */
  // TODO(b/128967328): Need to extend this to a state with the context.
  public enum CompilationState {
    /**
     * Has not been processed, yet.
     */
    NOT_PROCESSED,
    /**
     * Has been processed but cannot be inlined due to instructions that are not supported.
     */
    PROCESSED_NOT_INLINING_CANDIDATE,
    /**
     * Code only contains instructions that access public entities and can this be inlined into any
     * context.
     */
    PROCESSED_INLINING_CANDIDATE_ANY,
    /**
     * Code also contains instructions that access protected entities that reside in a different
     * package and hence require subclass relationship to be visible.
     */
    PROCESSED_INLINING_CANDIDATE_SUBCLASS,
    /**
     * Code contains instructions that reference package private entities or protected entities from
     * the same package.
     */
    PROCESSED_INLINING_CANDIDATE_SAME_PACKAGE,
    /** Code contains instructions that reference private entities. */
    PROCESSED_INLINING_CANDIDATE_SAME_NEST,
    /** Code contains invoke super */
    PROCESSED_INLINING_CANDIDATE_SAME_CLASS
  }

  public static final DexEncodedMethod[] EMPTY_ARRAY = {};
  public static final DexEncodedMethod SENTINEL =
      new DexEncodedMethod(
          null,
          MethodAccessFlags.fromDexAccessFlags(0),
          MethodTypeSignature.noSignature(),
          DexAnnotationSet.empty(),
          ParameterAnnotationsList.empty(),
          null,
          false,
          NOT_SET,
          NOT_SET,
          null,
          CallSiteOptimizationInfo.top(),
          DefaultMethodOptimizationInfo.getInstance(),
          false);
  public static final Int2ReferenceMap<DebugLocalInfo> NO_PARAMETER_INFO =
      new Int2ReferenceArrayMap<>(0);

  public final MethodAccessFlags accessFlags;
  public final boolean deprecated;
  public ParameterAnnotationsList parameterAnnotationsList;
  private Code code;
  // TODO(b/128967328): towards finer-grained inlining constraints,
  //   we need to maintain a set of states with (potentially different) contexts.
  private CompilationState compilationState = CompilationState.NOT_PROCESSED;
  private MethodOptimizationInfo optimizationInfo;
  private CallSiteOptimizationInfo callSiteOptimizationInfo;
  private CfVersion classFileVersion;
  /** The apiLevelForCode describes the api level needed for knowing all references in the code */
  private AndroidApiLevel apiLevelForCode;

  private KotlinMethodLevelInfo kotlinMemberInfo = getNoKotlinInfo();
  /** Generic signature information if the attribute is present in the input */
  private MethodTypeSignature genericSignature;

  private OptionalBool isLibraryMethodOverride = OptionalBool.unknown();

  private Int2ReferenceMap<DebugLocalInfo> parameterInfo = NO_PARAMETER_INFO;

  // This flag indicates the current instance is no longer up-to-date as another instance was
  // created based on this. Any further (public) operations on this instance will raise an error
  // to catch potential bugs due to the inconsistency (e.g., http://b/111893131)
  // Any newly added `public` method should check if `this` instance is obsolete.
  private boolean obsolete = false;

  private void checkIfObsolete() {
    assert !obsolete;
  }

  public boolean isObsolete() {
    // Do not be cheating. This util can be used only if you're going to do appropriate action,
    // e.g., using GraphLens#mapDexEncodedMethod to look up the correct, up-to-date instance.
    return obsolete;
  }

  public void setObsolete() {
    // By assigning an Exception, you can see when/how this instance becomes obsolete v.s.
    // when/how such obsolete instance is used.
    obsolete = true;
  }

  @Override
  public MethodAccessFlags getAccessFlags() {
    return accessFlags;
  }

  public DexType getArgumentType(int argumentIndex) {
    return getReference().getArgumentType(argumentIndex, isStatic());
  }

  public int getFirstNonReceiverArgumentIndex() {
    return isStatic() ? 0 : 1;
  }

  public int getNumberOfArguments() {
    return getReference().getArity() + BooleanUtils.intValue(isInstance());
  }

  public CompilationState getCompilationState() {
    return compilationState;
  }

  /**
   * Flags this method as no longer being obsolete.
   *
   * Example use case: The vertical class merger optimistically merges two classes before it is
   * guaranteed that the two classes can be merged. In this process, methods are moved from the
   * source class to the target class using {@link #toTypeSubstitutedMethod(DexMethod)}, which
   * causes the original methods of the source class to become obsolete. If vertical class merging
   * is aborted, the original methods of the source class needs to be marked as not being obsolete.
   */
  public void unsetObsolete() {
    obsolete = false;
  }

  private DexEncodedMethod(
      DexMethod method,
      MethodAccessFlags accessFlags,
      MethodTypeSignature genericSignature,
      DexAnnotationSet annotations,
      ParameterAnnotationsList parameterAnnotationsList,
      Code code,
      boolean d8R8Synthesized,
      AndroidApiLevel apiLevelForDefinition,
      AndroidApiLevel apiLevelForCode,
      CfVersion classFileVersion,
      CallSiteOptimizationInfo callSiteOptimizationInfo,
      MethodOptimizationInfo optimizationInfo,
      boolean deprecated) {
    super(method, annotations, d8R8Synthesized, apiLevelForDefinition);
    this.accessFlags = accessFlags;
    this.deprecated = deprecated;
    this.genericSignature = genericSignature;
    this.parameterAnnotationsList = parameterAnnotationsList;
    this.code = code;
    this.classFileVersion = classFileVersion;
    this.apiLevelForCode = apiLevelForCode;
    this.callSiteOptimizationInfo = requireNonNull(callSiteOptimizationInfo);
    this.optimizationInfo = requireNonNull(optimizationInfo);
    assert accessFlags != null;
    assert code == null || !shouldNotHaveCode();
    assert parameterAnnotationsList != null;
    assert apiLevelForDefinition != null;
    assert apiLevelForCode != null;
  }

  public static DexEncodedMethod toMethodDefinitionOrNull(DexClassAndMethod method) {
    return method != null ? method.getDefinition() : null;
  }

  public boolean isDeprecated() {
    return deprecated;
  }

  @Override
  public DexEncodedMethod self() {
    return this;
  }

  @Override
  public StructuralMapping<DexEncodedMethod> getStructuralMapping() {
    return DexEncodedMethod::syntheticSpecify;
  }

  // Visitor specifying the structure of the method with respect to its "synthetic" content.
  // TODO(b/171867022): Generalize this so that it determines any method in full.
  private static void syntheticSpecify(StructuralSpecification<DexEncodedMethod, ?> spec) {
    spec.withItem(DexEncodedMethod::getReference)
        .withItem(DexEncodedMethod::getAccessFlags)
        .withItem(DexDefinition::annotations)
        .withItem(m -> m.parameterAnnotationsList)
        .withNullableItem(m -> m.classFileVersion)
        .withBool(DexEncodedMember::isD8R8Synthesized)
        // TODO(b/171867022): Make signatures structural and include it in the definition.
        .withAssert(m -> m.genericSignature.hasNoSignature())
        .withAssert(DexEncodedMethod::hasCode)
        .withCustomItem(
            DexEncodedMethod::getCode,
            DexEncodedMethod::compareCodeObject,
            DexEncodedMethod::hashCodeObject);
  }

  private static int compareCodeObject(Code code1, Code code2, CompareToVisitor visitor) {
    if (code1.isCfCode() && code2.isCfCode()) {
      return code1.asCfCode().acceptCompareTo(code2.asCfCode(), visitor);
    }
    if (code1.isDexCode() && code2.isDexCode()) {
      return code1.asDexCode().acceptCompareTo(code2.asDexCode(), visitor);
    }
    throw new Unreachable(
        "Unexpected attempt to compare incompatible synthetic objects: " + code1 + " and " + code2);
  }

  private static void hashCodeObject(Code code, HashingVisitor visitor) {
    if (code.isCfCode()) {
      code.asCfCode().acceptHashing(visitor);
    } else {
      code.asDexCode().acceptHashing(visitor);
    }
  }

  public DexProto getProto() {
    return getReference().getProto();
  }

  public DexType getParameter(int index) {
    return getReference().getParameter(index);
  }

  public DexTypeList getParameters() {
    return getReference().getParameters();
  }

  public DexType getReturnType() {
    return getReference().getReturnType();
  }

  public DexMethodSignature getSignature() {
    return DexMethodSignature.create(getReference());
  }

  public DexType returnType() {
    return getReference().proto.returnType;
  }

  public OptionalBool isLibraryMethodOverride() {
    return isNonPrivateVirtualMethod() ? isLibraryMethodOverride : OptionalBool.FALSE;
  }

  public void setLibraryMethodOverride(OptionalBool isLibraryMethodOverride) {
    assert isNonPrivateVirtualMethod();
    assert !isLibraryMethodOverride.isUnknown();
    assert isLibraryMethodOverride.isPossiblyFalse()
            || this.isLibraryMethodOverride.isPossiblyTrue()
        : "Method `"
            + getReference().toSourceString()
            + "` went from not overriding a library method to overriding a library method";
    assert isLibraryMethodOverride.isPossiblyTrue()
            || this.isLibraryMethodOverride.isPossiblyFalse()
        : "Method `"
            + getReference().toSourceString()
            + "` went from overriding a library method to not overriding a library method";
    this.isLibraryMethodOverride = isLibraryMethodOverride;
  }

  public boolean isProgramMethod(DexDefinitionSupplier definitions) {
    if (getReference().holder.isClassType()) {
      DexClass clazz = definitions.definitionFor(getReference().holder);
      return clazz != null && clazz.isProgramClass();
    }
    return false;
  }

  @Override
  public ProgramMethod asProgramMember(DexDefinitionSupplier definitions) {
    return asProgramMethod(definitions);
  }

  @Override
  public <T> T apply(
      Function<DexEncodedField, T> fieldConsumer, Function<DexEncodedMethod, T> methodConsumer) {
    return methodConsumer.apply(this);
  }

  public DexClassAndMethod asDexClassAndMethod(DexDefinitionSupplier definitions) {
    assert getReference().holder.isClassType();
    DexClass clazz = definitions.definitionForHolder(getReference());
    if (clazz != null) {
      return DexClassAndMethod.create(clazz, this);
    }
    return null;
  }

  public ProgramMethod asProgramMethod(DexDefinitionSupplier definitions) {
    assert getReference().holder.isClassType();
    DexProgramClass clazz = asProgramClassOrNull(definitions.definitionForHolder(getReference()));
    if (clazz != null) {
      return new ProgramMethod(clazz, this);
    }
    return null;
  }

  public static DexClassAndMethod asDexClassAndMethodOrNull(
      DexEncodedMethod method, DexDefinitionSupplier definitions) {
    return method != null ? method.asDexClassAndMethod(definitions) : null;
  }

  public static ProgramMethod asProgramMethodOrNull(
      DexEncodedMethod method, DexDefinitionSupplier definitions) {
    return method != null ? method.asProgramMethod(definitions) : null;
  }

  public boolean isProcessed() {
    checkIfObsolete();
    return compilationState != CompilationState.NOT_PROCESSED;
  }

  public boolean isAbstract() {
    return accessFlags.isAbstract();
  }

  public boolean isBridge() {
    return accessFlags.isBridge();
  }

  public boolean isFinal() {
    return accessFlags.isFinal();
  }

  public boolean isNative() {
    return accessFlags.isNative();
  }

  public boolean isPublic() {
    return accessFlags.isPublic();
  }

  public boolean isSynchronized() {
    return accessFlags.isSynchronized();
  }

  public boolean isInitializer() {
    checkIfObsolete();
    return isInstanceInitializer() || isClassInitializer();
  }

  public boolean isInstanceInitializer() {
    checkIfObsolete();
    return accessFlags.isConstructor() && !accessFlags.isStatic();
  }

  /**
   * Returns true for (private instance) methods that have been created as a result of class merging
   * and will be force-inlined into an instance initializer on the enclosing class.
   */
  public boolean willBeInlinedIntoInstanceInitializer(DexItemFactory dexItemFactory) {
    checkIfObsolete();
    if (getName().startsWith(dexItemFactory.temporaryConstructorMethodPrefix)) {
      assert isPrivate();
      assert !isStatic();
      return true;
    }
    return false;
  }

  public boolean isOrWillBeInlinedIntoInstanceInitializer(DexItemFactory dexItemFactory) {
    return isInstanceInitializer() || willBeInlinedIntoInstanceInitializer(dexItemFactory);
  }

  public boolean isDefaultInitializer() {
    checkIfObsolete();
    return isInstanceInitializer() && getReference().proto.parameters.isEmpty();
  }

  public boolean isClassInitializer() {
    checkIfObsolete();
    return accessFlags.isConstructor() && accessFlags.isStatic();
  }

  public boolean isDefaultMethod() {
    // Assumes holder is an interface
    return !isStatic() && !isAbstract() && !isPrivateMethod() && !isInstanceInitializer();
  }

  /**
   * Returns true if this method can be invoked via invoke-virtual/interface.
   *
   * <p>Note that also private methods can be the target of a virtual invoke. In such cases, the
   * validity of the invoke depends on the access granted to the call site.
   */
  public boolean isVirtualMethod() {
    checkIfObsolete();
    return !accessFlags.isStatic() && !accessFlags.isConstructor();
  }

  public boolean isNonPrivateVirtualMethod() {
    checkIfObsolete();
    return !isPrivateMethod() && isVirtualMethod();
  }

  /**
   * Returns true if this method can be invoked via invoke-virtual, invoke-super or invoke-interface
   * and is non-abstract.
   */
  public boolean isNonAbstractVirtualMethod() {
    checkIfObsolete();
    return isVirtualMethod() && !accessFlags.isAbstract();
  }

  public boolean isNonAbstractNonNativeMethod() {
    checkIfObsolete();
    return !accessFlags.isAbstract() && !accessFlags.isNative();
  }

  public boolean isPublicized() {
    checkIfObsolete();
    return accessFlags.isPromotedToPublic();
  }

  public boolean isPublicMethod() {
    checkIfObsolete();
    return accessFlags.isPublic();
  }

  public boolean isProtectedMethod() {
    checkIfObsolete();
    return accessFlags.isProtected();
  }

  public boolean isPrivateMethod() {
    checkIfObsolete();
    return accessFlags.isPrivate();
  }

  /**
   * Returns true if this method can be invoked via invoke-direct.
   */
  public boolean isDirectMethod() {
    checkIfObsolete();
    return (accessFlags.isPrivate() || accessFlags.isConstructor()) && !accessFlags.isStatic();
  }

  public boolean isInstance() {
    return !isStatic();
  }

  @Override
  public boolean isStatic() {
    checkIfObsolete();
    return accessFlags.isStatic();
  }

  @Override
  public boolean isStaticMember() {
    checkIfObsolete();
    return isStatic();
  }

  /**
   * Returns true if this method is synthetic.
   */
  public boolean isSyntheticMethod() {
    checkIfObsolete();
    return accessFlags.isSynthetic();
  }

  public boolean belongsToDirectPool() {
    return accessFlags.isStatic() || accessFlags.isPrivate() || accessFlags.isConstructor();
  }

  public boolean belongsToVirtualPool() {
    return !belongsToDirectPool();
  }

  @Override
  public KotlinMethodLevelInfo getKotlinInfo() {
    return kotlinMemberInfo;
  }

  @Override
  public void clearKotlinInfo() {
    kotlinMemberInfo = getNoKotlinInfo();
  }

  public void setKotlinMemberInfo(KotlinMethodLevelInfo kotlinMemberInfo) {
    // Structure-changing optimizations, such as (vertical|horizontal) merger or inliner, that
    // may need to redefine what this method is. Simply, the method merged/inlined by optimization
    // is no longer what it used to be; it's safe to ignore metadata of that method, since it is
    // not asked to be kept. But, the nature of the current one is not changed, hence keeping the
    // original one as-is.
    // E.g., originally the current method is extension function, and new information, say, from
    // an inlinee, is extension property. Being merged here means:
    //   * That inlinee is not an extension property anymore. We can ignore metadata from it.
    //   * This method is still an extension function, just with a bigger body.
    assert this.kotlinMemberInfo == getNoKotlinInfo();
    this.kotlinMemberInfo = kotlinMemberInfo;
  }

  public boolean isKotlinFunction() {
    return kotlinMemberInfo.isFunction();
  }

  public boolean isKotlinExtensionFunction() {
    return kotlinMemberInfo.isFunction() && kotlinMemberInfo.asFunction().isExtensionFunction();
  }

  public boolean isOnlyInlinedIntoNestMembers() {
    return compilationState == PROCESSED_INLINING_CANDIDATE_SAME_NEST;
  }

  public boolean isInliningCandidate(
      ProgramMethod container,
      Reason inliningReason,
      AppInfoWithClassHierarchy appInfo,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    checkIfObsolete();
    return isInliningCandidate(
        container.getHolderType(), inliningReason, appInfo, whyAreYouNotInliningReporter);
  }

  public boolean isInliningCandidate(
      DexType containerType,
      Reason inliningReason,
      AppInfoWithClassHierarchy appInfo,
      WhyAreYouNotInliningReporter whyAreYouNotInliningReporter) {
    checkIfObsolete();

    if (inliningReason == Reason.FORCE) {
      // Make sure we would be able to inline this normally.
      if (!isInliningCandidate(
          containerType, Reason.SIMPLE, appInfo, whyAreYouNotInliningReporter)) {
        // If not, raise a flag, because some optimizations that depend on force inlining would
        // silently produce an invalid code, which is worse than an internal error.
        throw new InternalCompilerError("FORCE inlining on non-inlinable: " + toSourceString());
      }
      return true;
    }

    // TODO(b/128967328): inlining candidate should satisfy all states if multiple states are there.
    switch (compilationState) {
      case PROCESSED_INLINING_CANDIDATE_ANY:
        return true;

      case PROCESSED_INLINING_CANDIDATE_SUBCLASS:
        if (appInfo.isSubtype(containerType, getReference().holder)) {
          return true;
        }
        whyAreYouNotInliningReporter.reportCallerNotSubtype();
        return false;

      case PROCESSED_INLINING_CANDIDATE_SAME_PACKAGE:
        if (containerType.isSamePackage(getReference().holder)) {
          return true;
        }
        whyAreYouNotInliningReporter.reportCallerNotSamePackage();
        return false;

      case PROCESSED_INLINING_CANDIDATE_SAME_NEST:
        if (NestUtils.sameNest(containerType, getReference().holder, appInfo)) {
          return true;
        }
        whyAreYouNotInliningReporter.reportCallerNotSameNest();
        return false;

      case PROCESSED_INLINING_CANDIDATE_SAME_CLASS:
        if (containerType == getReference().holder) {
          return true;
        }
        whyAreYouNotInliningReporter.reportCallerNotSameClass();
        return false;

      case PROCESSED_NOT_INLINING_CANDIDATE:
        whyAreYouNotInliningReporter.reportInlineeNotInliningCandidate();
        return false;

      case NOT_PROCESSED:
        whyAreYouNotInliningReporter.reportInlineeNotProcessed();
        return false;

      default:
        throw new Unreachable("Unexpected compilation state: " + compilationState);
    }
  }

  public boolean markProcessed(ConstraintWithTarget state) {
    checkIfObsolete();
    CompilationState prevCompilationState = compilationState;
    switch (state.constraint) {
      case ALWAYS:
        compilationState = PROCESSED_INLINING_CANDIDATE_ANY;
        break;
      case SUBCLASS:
        compilationState = PROCESSED_INLINING_CANDIDATE_SUBCLASS;
        break;
      case PACKAGE:
        compilationState = PROCESSED_INLINING_CANDIDATE_SAME_PACKAGE;
        break;
      case SAMENEST:
        compilationState = PROCESSED_INLINING_CANDIDATE_SAME_NEST;
        break;
      case SAMECLASS:
        compilationState = PROCESSED_INLINING_CANDIDATE_SAME_CLASS;
        break;
      case NEVER:
        compilationState = PROCESSED_NOT_INLINING_CANDIDATE;
        break;
    }
    return prevCompilationState != compilationState;
  }

  public void markNotProcessed() {
    checkIfObsolete();
    compilationState = CompilationState.NOT_PROCESSED;
  }

  public void setCode(Code newCode, AppView<?> appView) {
    checkIfObsolete();
    // If the locals are not kept, we might still need information to satisfy -keepparameternames.
    // The information needs to be retrieved on the original code object before replacing it.
    if (code != null && code.isCfCode() && !hasParameterInfo() && !keepLocals(appView.options())) {
      setParameterInfo(code.collectParameterInfo(this, appView));
    }
    code = newCode;
  }

  public void setCode(IRCode ir, RegisterAllocator registerAllocator, AppView<?> appView) {
    checkIfObsolete();
    DexBuilder builder = new DexBuilder(ir, registerAllocator);
    setCode(builder.build(), appView);
  }

  public boolean keepLocals(InternalOptions options) {
    if (options.testing.noLocalsTableOnInput) {
      return false;
    }
    return options.debug || getOptimizationInfo().isReachabilitySensitive();
  }

  private void setParameterInfo(Int2ReferenceMap<DebugLocalInfo> parameterInfo) {
    assert this.parameterInfo == NO_PARAMETER_INFO;
    this.parameterInfo = parameterInfo;
  }

  public boolean hasParameterInfo() {
    return parameterInfo != NO_PARAMETER_INFO;
  }

  public Map<Integer, DebugLocalInfo> getParameterInfo() {
    return parameterInfo;
  }

  @Override
  public String toString() {
    checkIfObsolete();
    return "Encoded method " + getReference();
  }

  @Override
  void collectMixedSectionItems(MixedSectionCollection mixedItems) {
    mixedItems.visit(this);
  }

  public void collectMixedSectionItemsWithCodeMapping(
      MixedSectionCollection mixedItems, MethodToCodeObjectMapping mapping) {
    DexCode code = mapping.getCode(this);
    if (code != null) {
      code.collectMixedSectionItems(mixedItems);
    }
    annotations().collectMixedSectionItems(mixedItems);
    parameterAnnotationsList.collectMixedSectionItems(mixedItems);
  }

  public boolean shouldNotHaveCode() {
    return accessFlags.isAbstract() || accessFlags.isNative();
  }

  public boolean hasCode() {
    return code != null;
  }

  public Code getCode() {
    checkIfObsolete();
    return code;
  }

  public void removeCode() {
    checkIfObsolete();
    code = null;
  }

  public CfVersion getClassFileVersion() {
    checkIfObsolete();
    assert classFileVersion != null;
    return classFileVersion;
  }

  public CfVersion getClassFileVersionOrElse(CfVersion defaultValue) {
    return hasClassFileVersion() ? getClassFileVersion() : defaultValue;
  }

  public boolean hasClassFileVersion() {
    checkIfObsolete();
    return classFileVersion != null;
  }

  public void upgradeClassFileVersion(CfVersion version) {
    checkIfObsolete();
    assert version != null;
    classFileVersion = Ordered.maxIgnoreNull(classFileVersion, version);
  }

  public void downgradeClassFileVersion(CfVersion version) {
    checkIfObsolete();
    assert version != null;
    classFileVersion = Ordered.minIgnoreNull(classFileVersion, version);
  }

  public String qualifiedName() {
    checkIfObsolete();
    return getReference().qualifiedName();
  }

  public String descriptor() {
    checkIfObsolete();
    return descriptor(NamingLens.getIdentityLens());
  }

  public String descriptor(NamingLens namingLens) {
    checkIfObsolete();
    StringBuilder builder = new StringBuilder();
    builder.append("(");
    for (DexType type : getReference().proto.parameters.values) {
      builder.append(namingLens.lookupDescriptor(type).toString());
    }
    builder.append(")");
    builder.append(namingLens.lookupDescriptor(getReference().proto.returnType).toString());
    return builder.toString();
  }

  @Override
  public boolean hasAnyAnnotations() {
    return hasAnnotations() || hasParameterAnnotations();
  }

  @Override
  public void clearAllAnnotations() {
    clearAnnotations();
    clearParameterAnnotations();
  }

  @Override
  public void rewriteAllAnnotations(
      BiFunction<DexAnnotation, AnnotatedKind, DexAnnotation> rewriter) {
    setAnnotations(
        annotations().rewrite(annotation -> rewriter.apply(annotation, AnnotatedKind.METHOD)));
    setParameterAnnotations(
        getParameterAnnotations()
            .rewrite(annotation -> rewriter.apply(annotation, AnnotatedKind.PARAMETER)));
  }

  public void clearParameterAnnotations() {
    parameterAnnotationsList = ParameterAnnotationsList.empty();
  }

  public DexAnnotationSet getParameterAnnotation(int index) {
    return getParameterAnnotations().get(index);
  }

  public ParameterAnnotationsList getParameterAnnotations() {
    return parameterAnnotationsList;
  }

  public boolean hasParameterAnnotations() {
    return !getParameterAnnotations().isEmpty();
  }

  public void setParameterAnnotations(ParameterAnnotationsList parameterAnnotations) {
    this.parameterAnnotationsList = parameterAnnotations;
  }

  public String toSmaliString(ClassNameMapper naming) {
    checkIfObsolete();
    StringBuilder builder = new StringBuilder();
    builder.append(".method ");
    builder.append(accessFlags.toSmaliString());
    builder.append(" ");
    builder.append(getReference().name.toSmaliString());
    builder.append(getReference().proto.toSmaliString());
    builder.append("\n");
    if (code != null) {
      DexCode dexCode = code.asDexCode();
      builder.append("    .registers ");
      builder.append(dexCode.registerSize);
      builder.append("\n\n");
      builder.append(dexCode.toSmaliString(naming));
    }
    builder.append(".end method\n");
    return builder.toString();
  }

  @Override
  public String toSourceString() {
    checkIfObsolete();
    return getReference().toSourceString();
  }

  public DexEncodedMethod toAbstractMethod() {
    checkIfObsolete();
    // 'final' wants this to be *not* overridden, while 'abstract' wants this to be implemented in
    // a subtype, i.e., self contradict.
    assert !accessFlags.isFinal();
    // static abstract is an invalid access combination and we should never create that.
    assert !accessFlags.isStatic();
    return builder(this)
        .modifyAccessFlags(MethodAccessFlags::setAbstract)
        .setIsLibraryMethodOverrideIf(
            isNonPrivateVirtualMethod() && !isLibraryMethodOverride().isUnknown(),
            isLibraryMethodOverride())
        .unsetCode()
        .addBuildConsumer(
            method -> OptimizationFeedbackSimple.getInstance().unsetBridgeInfo(method))
        .build();
  }

  /**
   * Generates a {@link DexCode} object for the given instructions.
   */
  private DexCode generateCodeFromTemplate(
      int numberOfRegisters, int outRegisters, Instruction... instructions) {
    int offset = 0;
    for (Instruction instruction : instructions) {
      instruction.setOffset(offset);
      offset += instruction.getSize();
    }
    int requiredArgRegisters = accessFlags.isStatic() ? 0 : 1;
    for (DexType type : getReference().proto.parameters.values) {
      requiredArgRegisters += ValueType.fromDexType(type).requiredRegisters();
    }
    return new DexCode(
        Math.max(numberOfRegisters, requiredArgRegisters),
        requiredArgRegisters,
        outRegisters,
        instructions,
        new DexCode.Try[0],
        new DexCode.TryHandler[0],
        null);
  }

  public DexEncodedMethod toEmptyThrowingMethod(InternalOptions options) {
    return options.isGeneratingClassFiles()
        ? toEmptyThrowingMethodCf()
        : toEmptyThrowingMethodDex(true);
  }

  public DexEncodedMethod toEmptyThrowingMethodDex(boolean setIsLibraryOverride) {
    checkIfObsolete();
    assert !shouldNotHaveCode();
    Builder builder = builder(this);
    builder.setCode(buildEmptyThrowingDexCode());
    if (setIsLibraryOverride && isNonPrivateVirtualMethod()) {
      builder.setIsLibraryMethodOverride(isLibraryMethodOverride());
    }
    DexEncodedMethod result = builder.build();
    setObsolete();
    return result;
  }

  private DexEncodedMethod toEmptyThrowingMethodCf() {
    checkIfObsolete();
    assert !shouldNotHaveCode();
    Builder builder = builder(this);
    builder.setCode(buildEmptyThrowingCfCode());
    if (isNonPrivateVirtualMethod()) {
      builder.setIsLibraryMethodOverride(isLibraryMethodOverride());
    }
    DexEncodedMethod result = builder.build();
    setObsolete();
    return result;
  }

  public Code buildEmptyThrowingCode(InternalOptions options) {
    return options.isGeneratingClassFiles()
        ? buildEmptyThrowingCfCode()
        : buildEmptyThrowingDexCode();
  }

  public CfCode buildEmptyThrowingCfCode() {
    return buildEmptyThrowingCfCode(getReference());
  }

  public static CfCode buildEmptyThrowingCfCode(DexMethod method) {
    CfInstruction insn[] = {new CfConstNull(), new CfThrow()};
    return new CfCode(
        method.holder,
        1,
        method.proto.parameters.size() + 1,
        Arrays.asList(insn),
        Collections.emptyList(),
        Collections.emptyList());
  }

  public DexCode buildEmptyThrowingDexCode() {
    Instruction[] insn = {new Const4(0, 0), new Throw(0)};
    return generateCodeFromTemplate(1, 0, insn);
  }

  public Code buildInstanceOfCode(DexType type, boolean negate, InternalOptions options) {
    return options.isGeneratingClassFiles()
        ? buildInstanceOfCfCode(type, negate)
        : buildInstanceOfDexCode(type, negate);
  }

  public CfCode buildInstanceOfCfCode(DexType type, boolean negate) {
    CfInstruction[] instructions = new CfInstruction[3 + BooleanUtils.intValue(negate) * 2];
    int i = 0;
    instructions[i++] = new CfLoad(ValueType.OBJECT, 0);
    instructions[i++] = new CfInstanceOf(type);
    if (negate) {
      instructions[i++] = new CfConstNumber(1, ValueType.INT);
      instructions[i++] = new CfLogicalBinop(CfLogicalBinop.Opcode.Xor, NumericType.INT);
    }
    instructions[i] = new CfReturn(ValueType.INT);
    return new CfCode(
        getReference().holder,
        1 + BooleanUtils.intValue(negate),
        getReference().getArity() + 1,
        Arrays.asList(instructions),
        Collections.emptyList(),
        Collections.emptyList());
  }

  public DexCode buildInstanceOfDexCode(DexType type, boolean negate) {
    Instruction[] instructions = new Instruction[2 + BooleanUtils.intValue(negate)];
    int i = 0;
    instructions[i++] = new InstanceOf(0, 0, type);
    if (negate) {
      instructions[i++] = new XorIntLit8(0, 0, 1);
    }
    instructions[i] = new Return(0);
    return generateCodeFromTemplate(1, 0, instructions);
  }

  public DexEncodedMethod toMethodThatLogsError(AppView<?> appView) {
    Builder builder =
        builder(this)
            .setCode(
                appView.options().isGeneratingClassFiles()
                    ? toCfCodeThatLogsError(appView.dexItemFactory())
                    : toDexCodeThatLogsError(appView.dexItemFactory()))
            .setIsLibraryMethodOverrideIf(
                belongsToVirtualPool() && !isLibraryMethodOverride().isUnknown(),
                isLibraryMethodOverride());
    setObsolete();
    return builder.build();
  }

  public static void setDebugInfoWithFakeThisParameter(Code code, int arity, AppView<?> appView) {
    if (code.isDexCode()) {
      DexCode dexCode = code.asDexCode();
      dexCode.setDebugInfo(dexCode.debugInfoWithFakeThisParameter(appView.dexItemFactory()));
      assert (dexCode.getDebugInfo() == null)
          || (arity == dexCode.getDebugInfo().parameters.length);
    } else {
      assert code.isCfCode();
      CfCode cfCode = code.asCfCode();
      cfCode.addFakeThisParameter(appView.dexItemFactory());
    }
  }

  private DexCode toDexCodeThatLogsError(DexItemFactory itemFactory) {
    checkIfObsolete();
    Signature signature = MethodSignature.fromDexMethod(getReference());
    DexString message =
        itemFactory.createString(
            CONFIGURATION_DEBUGGING_PREFIX
                + getReference().holder.toSourceString()
                + ": "
                + signature);
    DexString tag = itemFactory.createString("[R8]");
    DexType[] args = {itemFactory.stringType, itemFactory.stringType};
    DexProto proto = itemFactory.createProto(itemFactory.intType, args);
    DexMethod logMethod =
        itemFactory.createMethod(
            itemFactory.androidUtilLogType, proto, itemFactory.createString("e"));
    DexType exceptionType = itemFactory.runtimeExceptionType;
    DexMethod exceptionInitMethod =
        itemFactory.createMethod(
            exceptionType,
            itemFactory.createProto(itemFactory.voidType, itemFactory.stringType),
            itemFactory.constructorMethodName);
    return generateCodeFromTemplate(
        2,
        2,
        new ConstString(0, tag),
        new ConstString(1, message),
        new InvokeStatic(2, logMethod, 0, 1, 0, 0, 0),
        new NewInstance(0, exceptionType),
        new InvokeDirect(2, exceptionInitMethod, 0, 1, 0, 0, 0),
        new Throw(0));
  }

  private CfCode toCfCodeThatLogsError(DexItemFactory itemFactory) {
    checkIfObsolete();
    Signature signature = MethodSignature.fromDexMethod(getReference());
    DexString message =
        itemFactory.createString(
            CONFIGURATION_DEBUGGING_PREFIX
                + getReference().holder.toSourceString()
                + ": "
                + signature);
    DexString tag = itemFactory.createString("[R8]");
    DexType logger = itemFactory.javaUtilLoggingLoggerType;
    DexMethod getLogger =
        itemFactory.createMethod(
            logger,
            itemFactory.createProto(logger, itemFactory.stringType),
            itemFactory.createString("getLogger"));
    DexMethod severe =
        itemFactory.createMethod(
            logger,
            itemFactory.createProto(itemFactory.voidType, itemFactory.stringType),
            itemFactory.createString("severe"));
    DexType exceptionType = itemFactory.runtimeExceptionType;
    DexMethod exceptionInitMethod =
        itemFactory.createMethod(
            exceptionType,
            itemFactory.createProto(itemFactory.voidType, itemFactory.stringType),
            itemFactory.constructorMethodName);
    int locals = getReference().proto.parameters.size() + 1;
    if (!isStaticMember()) {
      // Consider `this` pointer
      locals++;
    }
    ImmutableList.Builder<CfInstruction> instructionBuilder = ImmutableList.builder();
    instructionBuilder
        .add(new CfConstString(tag))
        .add(new CfInvoke(Opcodes.INVOKESTATIC, getLogger, false))
        .add(new CfStore(ValueType.OBJECT, locals - 1))
        .add(new CfLoad(ValueType.OBJECT, locals - 1))
        .add(new CfConstString(message))
        .add(new CfInvoke(Opcodes.INVOKEVIRTUAL, severe, false))
        .add(new CfNew(exceptionType))
        .add(new CfStackInstruction(Opcode.Dup))
        .add(new CfConstString(message))
        .add(new CfInvoke(Opcodes.INVOKESPECIAL, exceptionInitMethod, false))
        .add(new CfThrow());
    return new CfCode(
        getReference().holder,
        3,
        locals,
        instructionBuilder.build(),
        Collections.emptyList(),
        Collections.emptyList());
  }

  public DexEncodedMethod toTypeSubstitutedMethod(DexMethod method) {
    checkIfObsolete();
    return toTypeSubstitutedMethod(method, null);
  }

  public DexEncodedMethod toTypeSubstitutedMethod(DexMethod method, Consumer<Builder> consumer) {
    checkIfObsolete();
    if (this.getReference() == method) {
      return this;
    }
    Builder builder = builder(this);
    if (isNonPrivateVirtualMethod() && isLibraryMethodOverride() != OptionalBool.unknown()) {
      builder.setIsLibraryMethodOverride(isLibraryMethodOverride());
    }
    builder.setMethod(method);
    // TODO(b/112847660): Fix type fixers that use this method: Class staticizer
    // TODO(b/112847660): Fix type fixers that use this method: Uninstantiated type optimization
    // TODO(b/112847660): Fix type fixers that use this method: Unused argument removal
    // TODO(b/112847660): Fix type fixers that use this method: Vertical class merger
    // setObsolete();
    if (consumer != null) {
      consumer.accept(builder);
    }
    return builder.build();
  }

  public DexEncodedMethod toRenamedHolderMethod(DexType newHolderType, DexItemFactory factory) {
    DexEncodedMethod.Builder builder = DexEncodedMethod.builder(this);
    builder.setMethod(getReference().withHolder(newHolderType, factory));
    return builder.build();
  }

  public ProgramMethod toPrivateSyntheticMethod(DexProgramClass holder, DexMethod method) {
    assert !isStatic();
    assert !isPrivate();
    assert getHolderType() == method.getHolderType();
    checkIfObsolete();
    return new ProgramMethod(
        holder,
        syntheticBuilder(this)
            .setMethod(method)
            .modifyAccessFlags(
                accessFlags -> {
                  accessFlags.setSynthetic();
                  accessFlags.unsetProtected();
                  accessFlags.unsetPublic();
                  accessFlags.setPrivate();
                })
            .build());
  }

  public DexEncodedMethod toForwardingMethod(
      DexClass newHolder, DexDefinitionSupplier definitions) {
    DexMethod newMethod = getReference().withHolder(newHolder, definitions.dexItemFactory());
    checkIfObsolete();

    // Clear the final flag, as this method is now overwritten. Do this before creating the builder
    // for the forwarding method, as the forwarding method will copy the access flags from this,
    // and if different forwarding methods are created in different subclasses the first could be
    // final.
    accessFlags.demoteFromFinal();

    return syntheticBuilder(this)
        .setMethod(newMethod)
        .modifyAccessFlags(MethodAccessFlags::setSynthetic)
        // If the forwarding target is abstract, we can just create an abstract method. While it
        // will not actually forward, it will create the same exception when hit at runtime.
        // Otherwise, we need to create code that forwards the call to the target.
        .applyIf(
            !isAbstract(),
            builder ->
                builder
                    .setGenericSignature(MethodTypeSignature.noSignature())
                    .setCode(
                        ForwardMethodBuilder.builder(definitions.dexItemFactory())
                            .setStaticSource(newMethod)
                            .applyIf(
                                isStatic(),
                                codeBuilder ->
                                    codeBuilder
                                        .setStaticSource(newMethod)
                                        .setStaticTarget(
                                            getReference(),
                                            getReference()
                                                .getHolderType()
                                                .isInterface(definitions)),
                                codeBuilder ->
                                    codeBuilder
                                        .setNonStaticSource(newMethod)
                                        .setSuperTarget(
                                            getReference(),
                                            getReference()
                                                .getHolderType()
                                                .isInterface(definitions)))
                            .build())
                    .modifyAccessFlags(MethodAccessFlags::setBridge))
        .setIsLibraryMethodOverrideIf(
            !isStatic() && !isLibraryMethodOverride().isUnknown(), isLibraryMethodOverride())
        .build();
  }

  public static DexEncodedMethod createDesugaringForwardingMethod(
      DexClassAndMethod target, DexClass clazz, DexMethod forwardMethod, DexItemFactory factory) {
    DexMethod method = target.getReference();
    assert forwardMethod != null;
    // New method will have the same name, proto, and also all the flags of the
    // default method, including bridge flag.
    DexMethod newMethod = factory.createMethod(clazz.type, method.proto, method.name);
    MethodAccessFlags newFlags = target.getAccessFlags().copy();
    // Some debuggers (like IntelliJ) automatically skip synthetic methods on single step.
    newFlags.setSynthetic();
    newFlags.unsetAbstract();
    // Holder is companion class, or retarget method, not an interface.
    boolean isInterfaceMethodReference = false;
    return syntheticBuilder()
        .setMethod(newMethod)
        .setAccessFlags(newFlags)
        .setGenericSignature(MethodTypeSignature.noSignature())
        .setAnnotations(DexAnnotationSet.empty())
        .setCode(
            ForwardMethodBuilder.builder(factory)
                .setNonStaticSource(newMethod)
                .setStaticTarget(forwardMethod, isInterfaceMethodReference)
                .build())
        .setApiLevelForDefinition(target.getDefinition().getApiLevelForDefinition())
        .setApiLevelForCode(target.getDefinition().getApiLevelForCode())
        .build();
  }

  public DexEncodedMethod toStaticMethodWithoutThis(AppView<AppInfoWithLiveness> appView) {
    checkIfObsolete();
    assert !accessFlags.isStatic();
    Builder builder =
        builder(this)
            .promoteToStatic()
            .withoutThisParameter()
            .adjustOptimizationInfoAfterRemovingThisParameter(appView)
            .setGenericSignature(MethodTypeSignature.noSignature());
    DexEncodedMethod method = builder.build();
    method.copyMetadata(this);
    setObsolete();
    return method;
  }

  /** Rewrites the code in this method to have JumboString bytecode if required by mapping. */
  public DexCode rewriteCodeWithJumboStrings(
      ObjectToOffsetMapping mapping, DexItemFactory factory, boolean force) {
    checkIfObsolete();
    assert code == null || code.isDexCode();
    if (code == null) {
      return null;
    }
    DexCode code = this.code.asDexCode();
    DexString firstJumboString = null;
    if (force) {
      firstJumboString = mapping.getFirstString();
    } else {
      assert code.highestSortingString != null
          || Arrays.stream(code.instructions).noneMatch(Instruction::isConstString);
      assert Arrays.stream(code.instructions).noneMatch(Instruction::isDexItemBasedConstString);
      if (code.highestSortingString != null
          && mapping.getOffsetFor(code.highestSortingString) > Constants.MAX_NON_JUMBO_INDEX) {
        firstJumboString = mapping.getFirstJumboString();
      }
    }
    if (firstJumboString != null) {
      JumboStringRewriter rewriter = new JumboStringRewriter(this, firstJumboString, factory);
      return rewriter.rewrite();
    }
    return code;
  }

  public String codeToString() {
    checkIfObsolete();
    return code == null ? "<no code>" : code.toString(this, null);
  }

  public MethodPosition getPosition() {
    return new MethodPosition(getReference().asMethodReference());
  }

  @Override
  public boolean isDexEncodedMethod() {
    checkIfObsolete();
    return true;
  }

  @Override
  public DexEncodedMethod asDexEncodedMethod() {
    checkIfObsolete();
    return this;
  }

  public static int slowCompare(DexEncodedMethod m1, DexEncodedMethod m2) {
    return m1.getReference().compareTo(m2.getReference());
  }

  @Override
  public MethodOptimizationInfo getOptimizationInfo() {
    checkIfObsolete();
    return optimizationInfo;
  }

  public AndroidApiLevel getApiLevelForCode() {
    return apiLevelForCode;
  }

  public void setApiLevelForCode(AndroidApiLevel apiLevel) {
    assert apiLevel != null;
    this.apiLevelForCode = apiLevel;
  }

  @Override
  public AndroidApiLevel getApiLevel() {
    return (shouldNotHaveCode() ? AndroidApiLevel.B : getApiLevelForCode())
        .max(getApiLevelForDefinition());
  }

  public synchronized MutableMethodOptimizationInfo getMutableOptimizationInfo() {
    checkIfObsolete();
    MutableMethodOptimizationInfo mutableInfo = optimizationInfo.toMutableOptimizationInfo();
    optimizationInfo = mutableInfo;
    return mutableInfo;
  }

  public void setOptimizationInfo(MutableMethodOptimizationInfo info) {
    checkIfObsolete();
    optimizationInfo = info;
  }

  public synchronized void abandonCallSiteOptimizationInfo() {
    checkIfObsolete();
    callSiteOptimizationInfo = CallSiteOptimizationInfo.abandoned();
  }

  public synchronized CallSiteOptimizationInfo getCallSiteOptimizationInfo() {
    checkIfObsolete();
    return callSiteOptimizationInfo;
  }

  public synchronized void joinCallSiteOptimizationInfo(
      CallSiteOptimizationInfo other, AppView<?> appView) {
    checkIfObsolete();
    callSiteOptimizationInfo = callSiteOptimizationInfo.join(other, appView, this);
  }

  public synchronized void setCallSiteOptimizationInfo(
      CallSiteOptimizationInfo callSiteOptimizationInfo) {
    this.callSiteOptimizationInfo = callSiteOptimizationInfo;
  }

  public void copyMetadata(DexEncodedMethod from) {
    checkIfObsolete();
    if (from.hasClassFileVersion()) {
      upgradeClassFileVersion(from.getClassFileVersion());
    }
    apiLevelForCode = getApiLevelForCode().max(from.getApiLevelForCode());
  }

  public MethodTypeSignature getGenericSignature() {
    return genericSignature;
  }

  public void setGenericSignature(MethodTypeSignature genericSignature) {
    assert genericSignature != null;
    this.genericSignature = genericSignature;
  }

  @Override
  public void clearGenericSignature() {
    this.genericSignature = MethodTypeSignature.noSignature();
  }

  public static Builder syntheticBuilder() {
    return new Builder(true);
  }

  private static Builder syntheticBuilder(DexEncodedMethod from) {
    return new Builder(true, from);
  }

  public static Builder builder() {
    return new Builder(false);
  }

  private static Builder builder(DexEncodedMethod from) {
    return new Builder(from.isD8R8Synthesized(), from);
  }

  public static class Builder {

    private MethodAccessFlags accessFlags;
    private Code code;
    private DexMethod method;
    private MethodTypeSignature genericSignature = MethodTypeSignature.noSignature();
    private DexAnnotationSet annotations = DexAnnotationSet.empty();
    private OptionalBool isLibraryMethodOverride = OptionalBool.UNKNOWN;
    private ParameterAnnotationsList parameterAnnotations = ParameterAnnotationsList.empty();
    private CompilationState compilationState = CompilationState.NOT_PROCESSED;
    // TODO(b/190154391): We should set this to top, but the old call site optimization requires
    //  this to be bottom.
    private CallSiteOptimizationInfo callSiteOptimizationInfo = CallSiteOptimizationInfo.bottom();
    private MethodOptimizationInfo optimizationInfo = DefaultMethodOptimizationInfo.getInstance();
    private KotlinMethodLevelInfo kotlinInfo = getNoKotlinInfo();
    private CfVersion classFileVersion = null;
    private AndroidApiLevel apiLevelForDefinition = NOT_SET;
    private AndroidApiLevel apiLevelForCode = NOT_SET;
    private final boolean d8R8Synthesized;
    private boolean deprecated = false;

    // Checks to impose on the built method. They should always be active to start with and be
    // lowered on the use site.
    private boolean checkMethodNotNull = true;
    private boolean checkParameterAnnotationList = true;
    private boolean checkAndroidApiLevels = true;

    private Consumer<DexEncodedMethod> buildConsumer = ConsumerUtils.emptyConsumer();

    private Builder(boolean d8R8Synthesized) {
      this.d8R8Synthesized = d8R8Synthesized;
    }

    private Builder(boolean d8R8Synthesized, DexEncodedMethod from) {
      // Copy all the mutable state of a DexEncodedMethod here.
      method = from.getReference();
      accessFlags = from.getAccessFlags().copy();
      genericSignature = from.getGenericSignature();
      annotations = from.annotations();
      code = from.getCode();
      apiLevelForDefinition = from.getApiLevelForDefinition();
      apiLevelForCode = from.getApiLevelForCode();
      callSiteOptimizationInfo = from.getCallSiteOptimizationInfo();
      optimizationInfo =
          from.getOptimizationInfo().isMutableOptimizationInfo()
              ? from.getOptimizationInfo().asMutableMethodOptimizationInfo().mutableCopy()
              : from.getOptimizationInfo();
      kotlinInfo = from.getKotlinInfo();
      classFileVersion = from.classFileVersion;
      this.d8R8Synthesized = d8R8Synthesized;
      deprecated = from.isDeprecated();

      if (from.getParameterAnnotations().isEmpty()
          || from.getParameterAnnotations().size() == from.getParameters().size()) {
        parameterAnnotations = from.getParameterAnnotations();
      } else {
        // If the there are missing parameter annotations populate these when creating the builder.
        parameterAnnotations =
            from.getParameterAnnotations().withParameterCount(from.getParameters().size());
      }
    }

    public Builder apply(Consumer<Builder> consumer) {
      consumer.accept(this);
      return this;
    }

    public Builder applyIf(boolean condition, Consumer<Builder> thenConsumer) {
      return applyIf(condition, thenConsumer, emptyConsumer());
    }

    public Builder applyIf(
        boolean condition, Consumer<Builder> thenConsumer, Consumer<Builder> elseConsumer) {
      if (condition) {
        thenConsumer.accept(this);
      } else {
        elseConsumer.accept(this);
      }
      return this;
    }

    public Builder fixupCallSiteOptimizationInfo(
        Function<ConcreteCallSiteOptimizationInfo, ? extends CallSiteOptimizationInfo> fn) {
      if (callSiteOptimizationInfo.isConcreteCallSiteOptimizationInfo()) {
        callSiteOptimizationInfo =
            fn.apply(callSiteOptimizationInfo.asConcreteCallSiteOptimizationInfo());
      }
      return this;
    }

    public Builder setSimpleInliningConstraint(
        DexProgramClass holder, SimpleInliningConstraint simpleInliningConstraint) {
      return addBuildConsumer(
          newMethod ->
              OptimizationFeedbackSimple.getInstance()
                  .setSimpleInliningConstraint(
                      // The method has not yet been installed so we cannot use
                      // asProgramMethod(appView).
                      new ProgramMethod(holder, newMethod), simpleInliningConstraint));
    }

    public Builder addBuildConsumer(Consumer<DexEncodedMethod> consumer) {
      this.buildConsumer = this.buildConsumer.andThen(consumer);
      return this;
    }

    public Builder modifyAccessFlags(Consumer<MethodAccessFlags> consumer) {
      consumer.accept(accessFlags);
      return this;
    }

    public Builder setAccessFlags(MethodAccessFlags accessFlags) {
      this.accessFlags = accessFlags;
      return this;
    }

    public Builder setMethod(DexMethod method) {
      this.method = method;
      return this;
    }

    public Builder setCompilationState(CompilationState compilationState) {
      assert this.compilationState == CompilationState.NOT_PROCESSED;
      this.compilationState = compilationState;
      return this;
    }

    public Builder setIsLibraryMethodOverride(OptionalBool isLibraryMethodOverride) {
      assert !isLibraryMethodOverride.isUnknown();
      this.isLibraryMethodOverride = isLibraryMethodOverride;
      return this;
    }

    public Builder setIsLibraryMethodOverrideIf(
        boolean condition, OptionalBool isLibraryMethodOverride) {
      if (condition) {
        return setIsLibraryMethodOverride(isLibraryMethodOverride);
      }
      return this;
    }

    public Builder setIsLibraryMethodOverrideIfKnown(OptionalBool isLibraryMethodOverride) {
      return setIsLibraryMethodOverrideIf(
          !isLibraryMethodOverride.isUnknown(), isLibraryMethodOverride);
    }

    public Builder unsetIsLibraryMethodOverride() {
      this.isLibraryMethodOverride = OptionalBool.UNKNOWN;
      return this;
    }

    public Builder clearAnnotations() {
      return setAnnotations(DexAnnotationSet.empty());
    }

    public Builder clearParameterAnnotations() {
      return setParameterAnnotations(ParameterAnnotationsList.empty());
    }

    public Builder clearAllAnnotations() {
      return clearAnnotations().clearParameterAnnotations();
    }

    public Builder setAnnotations(DexAnnotationSet annotations) {
      this.annotations = annotations;
      return this;
    }

    public Builder setParameterAnnotations(ParameterAnnotationsList parameterAnnotations) {
      this.parameterAnnotations = parameterAnnotations;
      return this;
    }

    public Builder removeParameterAnnotations(IntPredicate predicate) {
      if (parameterAnnotations.isEmpty()) {
        // Nothing to do.
        return this;
      }

      List<DexAnnotationSet> newParameterAnnotations = new ArrayList<>();
      int newNumberOfMissingParameterAnnotations = 0;

      for (int oldIndex = 0; oldIndex < parameterAnnotations.size(); oldIndex++) {
        if (!predicate.test(oldIndex)) {
          if (parameterAnnotations.isMissing(oldIndex)) {
            newNumberOfMissingParameterAnnotations++;
          } else {
            newParameterAnnotations.add(parameterAnnotations.get(oldIndex));
          }
        }
      }

      if (newParameterAnnotations.isEmpty()) {
        return setParameterAnnotations(ParameterAnnotationsList.empty());
      }

      return setParameterAnnotations(
          ParameterAnnotationsList.create(
              newParameterAnnotations.toArray(DexAnnotationSet.EMPTY_ARRAY),
              newNumberOfMissingParameterAnnotations));
    }

    public Builder promoteToStatic() {
      this.accessFlags.promoteToStatic();
      return this;
    }

    public Builder withoutThisParameter() {
      assert code != null;
      if (code.isDexCode()) {
        code = code.asDexCode().withoutThisParameter();
      } else {
        throw new Unreachable("Code " + code.getClass().getSimpleName() + " is not supported.");
      }
      return this;
    }

    public Builder adjustOptimizationInfoAfterRemovingThisParameter(
        AppView<AppInfoWithLiveness> appView) {
      return fixupCallSiteOptimizationInfo(
              callSiteOptimizationInfo -> callSiteOptimizationInfo.fixupAfterParameterRemoval(0))
          .modifyOptimizationInfo(
              (newMethod, optimizationInfo) ->
                  optimizationInfo.adjustOptimizationInfoAfterRemovingThisParameter(appView));
    }

    public Builder modifyOptimizationInfo(
        BiConsumer<DexEncodedMethod, MutableMethodOptimizationInfo> consumer) {
      return addBuildConsumer(
          newMethod -> {
            if (optimizationInfo.isMutableOptimizationInfo()) {
              consumer.accept(newMethod, optimizationInfo.asMutableMethodOptimizationInfo());
            }
          });
    }

    public Builder setCode(Code code) {
      this.code = code;
      return this;
    }

    public Builder unsetCode() {
      return setCode(null);
    }

    public Builder setGenericSignature(MethodTypeSignature methodSignature) {
      this.genericSignature = methodSignature;
      return this;
    }

    public Builder setApiLevelForDefinition(AndroidApiLevel apiLevelForDefinition) {
      this.apiLevelForDefinition = apiLevelForDefinition;
      return this;
    }

    public Builder setApiLevelForCode(AndroidApiLevel apiLevelForCode) {
      this.apiLevelForCode = apiLevelForCode;
      return this;
    }

    public Builder setDeprecated(boolean deprecated) {
      this.deprecated = deprecated;
      return this;
    }

    public Builder setClassFileVersion(CfVersion version) {
      classFileVersion = version;
      return this;
    }

    public Builder disableMethodNotNullCheck() {
      checkMethodNotNull = false;
      return this;
    }

    public Builder disableParameterAnnotationListCheck() {
      checkParameterAnnotationList = false;
      return this;
    }

    public Builder disableAndroidApiLevelCheck() {
      checkAndroidApiLevels = false;
      return this;
    }

    public DexEncodedMethod build() {
      assert !checkMethodNotNull || method != null;
      assert accessFlags != null;
      assert annotations != null;
      assert parameterAnnotations != null;
      assert !checkParameterAnnotationList
          || parameterAnnotations.isEmpty()
          || parameterAnnotations.size() == method.proto.parameters.size();
      assert !checkAndroidApiLevels || apiLevelForDefinition != null;
      assert !checkAndroidApiLevels || apiLevelForCode != null;
      DexEncodedMethod result =
          new DexEncodedMethod(
              method,
              accessFlags,
              genericSignature,
              annotations,
              parameterAnnotations,
              code,
              d8R8Synthesized,
              apiLevelForDefinition,
              apiLevelForCode,
              classFileVersion,
              callSiteOptimizationInfo,
              optimizationInfo,
              deprecated);
      result.setKotlinMemberInfo(kotlinInfo);
      result.compilationState = compilationState;
      if (!isLibraryMethodOverride.isUnknown()) {
        result.setLibraryMethodOverride(isLibraryMethodOverride);
      }
      buildConsumer.accept(result);
      return result;
    }
  }
}
