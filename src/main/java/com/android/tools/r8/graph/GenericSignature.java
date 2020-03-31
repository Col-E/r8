// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.DescriptorUtils.getClassBinaryNameFromDescriptor;
import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromClassBinaryName;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.utils.DescriptorUtils;
import com.google.common.collect.ImmutableList;
import java.lang.reflect.GenericSignatureFormatError;
import java.nio.CharBuffer;
import java.util.List;
import java.util.function.Function;

/**
 * Internal encoding of the generics signature attribute as defined by JVMS 7 $ 4.3.4.
 * <pre>
 * ClassSignature ::=
 *     FormalTypeParameters? SuperclassSignature SuperinterfaceSignature*
 *
 *
 * FormalTypeParameters ::=
 *     < FormalTypeParameter+ >
 *
 * FormalTypeParameter ::=
 *     Identifier ClassBound InterfaceBound*
 *
 * ClassBound ::=
 *     : FieldTypeSignature?
 *
 * InterfaceBound ::=
 *     : FieldTypeSignature
 *
 * SuperclassSignature ::=
 *     ClassTypeSignature
 *
 * SuperinterfaceSignature ::=
 *     ClassTypeSignature
 *
 *
 * FieldTypeSignature ::=
 *     ClassTypeSignature
 *     ArrayTypeSignature
 *     TypeVariableSignature
 *
 *
 * ClassTypeSignature ::=
 *     L PackageSpecifier? SimpleClassTypeSignature ClassTypeSignatureSuffix* ;
 *
 * PackageSpecifier ::=
 *     Identifier / PackageSpecifier*
 *
 * SimpleClassTypeSignature ::=
 *     Identifier TypeArguments?
 *
 * ClassTypeSignatureSuffix ::=
 *     . SimpleClassTypeSignature
 *
 * TypeVariableSignature ::=
 *     T Identifier ;
 *
 * TypeArguments ::=
 *     < TypeArgument+ >
 *
 * TypeArgument ::=
 *     WildcardIndicator? FieldTypeSignature
 *     *
 *
 * WildcardIndicator ::=
 *     +
 *     -
 *
 * ArrayTypeSignature ::=
 *     [ TypeSignature
 *
 * TypeSignature ::=
 *     FieldTypeSignature
 *     BaseType
 *
 *
 * MethodTypeSignature ::=
 *     FormalTypeParameters? (TypeSignature*) ReturnType ThrowsSignature*
 *
 * ReturnType ::=
 *     TypeSignature
 *     VoidDescriptor
 *
 * ThrowsSignature ::=
 *     ^ ClassTypeSignature
 *     ^ TypeVariableSignature
 * </pre>
 */
public class GenericSignature {

  private static final List<FormalTypeParameter> EMPTY_TYPE_PARAMS = ImmutableList.of();

  interface DexDefinitionSignature<T extends DexDefinition> {
    default boolean isClassSignature() {
      return false;
    }

    default ClassSignature asClassSignature() {
      return null;
    }

    default boolean isFieldTypeSignature() {
      return false;
    }

    default FieldTypeSignature asFieldTypeSignature() {
      return null;
    }

    default boolean isMethodTypeSignature() {
      return false;
    }

    default MethodTypeSignature asMethodTypeSignature() {
      return null;
    }
  }

  public static class FormalTypeParameter {

    final String name;
    final FieldTypeSignature classBound;
    final List<FieldTypeSignature> interfaceBounds;

    FormalTypeParameter(
        String name, FieldTypeSignature classBound, List<FieldTypeSignature> interfaceBounds) {
      this.name = name;
      this.classBound = classBound;
      this.interfaceBounds = interfaceBounds;
    }

    public String getName() {
      return name;
    }

    public FieldTypeSignature getClassBound() {
      return classBound;
    }

    public List<FieldTypeSignature> getInterfaceBounds() {
      return interfaceBounds;
    }
  }

  public static class ClassSignature implements DexDefinitionSignature<DexClass> {
    static final ClassSignature UNKNOWN_CLASS_SIGNATURE =
        new ClassSignature(
            ImmutableList.of(),
            ClassTypeSignature.UNKNOWN_CLASS_TYPE_SIGNATURE,
            ImmutableList.of());

    final List<FormalTypeParameter> formalTypeParameters;
    final ClassTypeSignature superClassSignature;
    final List<ClassTypeSignature> superInterfaceSignatures;

    ClassSignature(
        List<FormalTypeParameter> formalTypeParameters,
        ClassTypeSignature superClassSignature,
        List<ClassTypeSignature> superInterfaceSignatures) {
      assert formalTypeParameters != null;
      assert superClassSignature != null;
      assert superInterfaceSignatures != null;
      this.formalTypeParameters = formalTypeParameters;
      this.superClassSignature = superClassSignature;
      this.superInterfaceSignatures = superInterfaceSignatures;
    }

    public ClassTypeSignature superClassSignature() {
      return superClassSignature;
    }

    public List<ClassTypeSignature> superInterfaceSignatures() {
      return superInterfaceSignatures;
    }

    @Override
    public boolean isClassSignature() {
      return true;
    }

    @Override
    public ClassSignature asClassSignature() {
      return this;
    }
  }

  public abstract static class TypeSignature {
    public boolean isFieldTypeSignature() {
      return false;
    }

    public FieldTypeSignature asFieldTypeSignature() {
      return null;
    }

    public boolean isBaseTypeSignature() {
      return false;
    }

    public BaseTypeSignature asBaseTypeSignature() {
      return null;
    }

    public TypeSignature toArrayTypeSignature(AppView<?> appView) {
      return null;
    }

    public TypeSignature toArrayElementTypeSignature(AppView<?> appView) {
      return null;
    }
  }

  public enum WildcardIndicator {
    NOT_AN_ARGUMENT,
    NONE,
    NEGATIVE,
    POSITIVE
  }

  public abstract static class FieldTypeSignature
      extends TypeSignature implements DexDefinitionSignature<DexEncodedField> {

    private final WildcardIndicator wildcardIndicator;

    private FieldTypeSignature(WildcardIndicator wildcardIndicator) {
      this.wildcardIndicator = wildcardIndicator;
    }

    public final boolean isArgument() {
      return wildcardIndicator != WildcardIndicator.NOT_AN_ARGUMENT;
    }

    public WildcardIndicator getWildcardIndicator() {
      return wildcardIndicator;
    }

    @Override
    public boolean isFieldTypeSignature() {
      return true;
    }

    @Override
    public FieldTypeSignature asFieldTypeSignature() {
      return this;
    }

    public boolean isClassTypeSignature() {
      return false;
    }

    public ClassTypeSignature asClassTypeSignature() {
      return null;
    }

    public boolean isArrayTypeSignature() {
      return false;
    }

    public ArrayTypeSignature asArrayTypeSignature() {
      return null;
    }

    public boolean isTypeVariableSignature() {
      return false;
    }

    public TypeVariableSignature asTypeVariableSignature() {
      return null;
    }

    public boolean isUnknown() {
      return this == ClassTypeSignature.UNKNOWN_CLASS_TYPE_SIGNATURE;
    }

    public abstract FieldTypeSignature asArgument(WildcardIndicator indicator);

    public boolean isStar() {
      return false;
    }
  }

  private static final class StarFieldTypeSignature extends FieldTypeSignature {

    private static final StarFieldTypeSignature STAR_FIELD_TYPE_SIGNATURE =
        new StarFieldTypeSignature();

    private StarFieldTypeSignature() {
      super(WildcardIndicator.NONE);
    }

    @Override
    public FieldTypeSignature asArgument(WildcardIndicator indicator) {
      throw new Unreachable("Should not be called");
    }

    @Override
    public boolean isStar() {
      return true;
    }
  }

  public static class ClassTypeSignature extends FieldTypeSignature {
    static final ClassTypeSignature UNKNOWN_CLASS_TYPE_SIGNATURE =
        new ClassTypeSignature(DexItemFactory.nullValueType, ImmutableList.of());

    final DexType type;
    // E.g., for Map<K, V>, a signature will indicate what types are for K and V.
    // Note that this could be nested, e.g., Map<K, Consumer<V>>.
    final List<FieldTypeSignature> typeArguments;

    // TODO(b/129925954): towards immutable structure?
    // Double-linked enclosing-inner relations.
    ClassTypeSignature enclosingTypeSignature;
    ClassTypeSignature innerTypeSignature;

    ClassTypeSignature(DexType type, List<FieldTypeSignature> typeArguments) {
      this(type, typeArguments, WildcardIndicator.NOT_AN_ARGUMENT);
    }

    private ClassTypeSignature(
        DexType type, List<FieldTypeSignature> typeArguments, WildcardIndicator indicator) {
      super(indicator);
      assert type != null;
      assert typeArguments != null;
      this.type = type;
      this.typeArguments = typeArguments;
      assert typeArguments.stream().allMatch(FieldTypeSignature::isArgument);
    }

    public DexType type() {
      return type;
    }

    public List<FieldTypeSignature> typeArguments() {
      return typeArguments;
    }

    @Override
    public boolean isClassTypeSignature() {
      return true;
    }

    @Override
    public ClassTypeSignature asClassTypeSignature() {
      return this;
    }

    @Override
    public ClassTypeSignature asArgument(WildcardIndicator indicator) {
      assert indicator != WildcardIndicator.NOT_AN_ARGUMENT;
      ClassTypeSignature argument = new ClassTypeSignature(type, typeArguments, indicator);
      argument.innerTypeSignature = this.innerTypeSignature;
      argument.enclosingTypeSignature = this.enclosingTypeSignature;
      return argument;
    }

    @Override
    public ArrayTypeSignature toArrayTypeSignature(AppView<?> appView) {
      return new ArrayTypeSignature(this);
    }

    static void link(ClassTypeSignature outer, ClassTypeSignature inner) {
      assert outer.innerTypeSignature == null && inner.enclosingTypeSignature == null;
      outer.innerTypeSignature = inner;
      inner.enclosingTypeSignature = outer;
    }
  }

  public static class ArrayTypeSignature extends FieldTypeSignature {

    final TypeSignature elementSignature;

    ArrayTypeSignature(TypeSignature elementSignature) {
      this(elementSignature, WildcardIndicator.NOT_AN_ARGUMENT);
    }

    private ArrayTypeSignature(TypeSignature elementSignature, WildcardIndicator indicator) {
      super(indicator);
      assert elementSignature != null;
      this.elementSignature = elementSignature;
    }

    public TypeSignature elementSignature() {
      return elementSignature;
    }

    @Override
    public boolean isArrayTypeSignature() {
      return true;
    }

    @Override
    public ArrayTypeSignature asArrayTypeSignature() {
      return this;
    }

    @Override
    public FieldTypeSignature asArgument(WildcardIndicator indicator) {
      assert indicator != WildcardIndicator.NOT_AN_ARGUMENT;
      return new ArrayTypeSignature(elementSignature, indicator);
    }

    @Override
    public TypeSignature toArrayTypeSignature(AppView<?> appView) {
      return new ArrayTypeSignature(this);
    }

    @Override
    public TypeSignature toArrayElementTypeSignature(AppView<?> appView) {
      return elementSignature;
    }
  }

  public static class TypeVariableSignature extends FieldTypeSignature {

    final String typeVariable;

    private TypeVariableSignature(String typeVariable) {
      this(typeVariable, WildcardIndicator.NOT_AN_ARGUMENT);
    }

    private TypeVariableSignature(String typeVariable, WildcardIndicator indicator) {
      super(indicator);
      assert typeVariable != null;
      this.typeVariable = typeVariable;
    }

    @Override
    public boolean isTypeVariableSignature() {
      return true;
    }

    @Override
    public TypeVariableSignature asTypeVariableSignature() {
      return this;
    }

    @Override
    public FieldTypeSignature asArgument(WildcardIndicator indicator) {
      assert indicator != WildcardIndicator.NOT_AN_ARGUMENT;
      return new TypeVariableSignature(typeVariable, indicator);
    }

    @Override
    public ArrayTypeSignature toArrayTypeSignature(AppView<?> appView) {
      return new ArrayTypeSignature(this);
    }

    public String getTypeVariable() {
      return typeVariable;
    }
  }

  // TODO(b/129925954): Canonicalization?
  public static class BaseTypeSignature extends TypeSignature {
    final DexType type;

    BaseTypeSignature(DexType type) {
      assert type != null;
      assert type.isPrimitiveType() : type.toDescriptorString();
      this.type = type;
    }

    @Override
    public boolean isBaseTypeSignature() {
      return true;
    }

    @Override
    public BaseTypeSignature asBaseTypeSignature() {
      return this;
    }

    @Override
    public ArrayTypeSignature toArrayTypeSignature(AppView<?> appView) {
      assert !type.isVoidType();
      return new ArrayTypeSignature(this);
    }
  }

  public static class ReturnType {
    static final ReturnType VOID = new ReturnType(null);

    // `null` indicates that it's `void`.
    final TypeSignature typeSignature;

    ReturnType(TypeSignature typeSignature) {
      this.typeSignature = typeSignature;
    }

    public boolean isVoidDescriptor() {
      return typeSignature == null;
    }

    public TypeSignature typeSignature() {
      return typeSignature;
    }
  }

  public static class MethodTypeSignature implements DexDefinitionSignature<DexEncodedMethod> {
    static final MethodTypeSignature UNKNOWN_METHOD_TYPE_SIGNATURE =
        new MethodTypeSignature(
            ImmutableList.of(), ImmutableList.of(), ReturnType.VOID, ImmutableList.of());

    final List<FormalTypeParameter> formalTypeParameters;
    final List<TypeSignature> typeSignatures;
    final ReturnType returnType;
    final List<TypeSignature> throwsSignatures;

    MethodTypeSignature(
        final List<FormalTypeParameter> formalTypeParameters,
        List<TypeSignature> typeSignatures,
        ReturnType returnType,
        List<TypeSignature> throwsSignatures) {
      assert formalTypeParameters != null;
      assert typeSignatures != null;
      assert returnType != null;
      assert throwsSignatures != null;
      this.formalTypeParameters = formalTypeParameters;
      this.typeSignatures = typeSignatures;
      this.returnType = returnType;
      this.throwsSignatures = throwsSignatures;
    }

    public TypeSignature getParameterTypeSignature(int i) {
      if (typeSignatures.isEmpty() || i < 0 || i >= typeSignatures.size()) {
        return null;
      }
      return typeSignatures.get(i);
    }

    public ReturnType returnType() {
      return returnType;
    }

    public List<TypeSignature> throwsSignatures() {
      return throwsSignatures;
    }

    @Override
    public boolean isMethodTypeSignature() {
      return true;
    }

    @Override
    public MethodTypeSignature asMethodTypeSignature() {
      return this;
    }

    public List<FormalTypeParameter> getFormalTypeParameters() {
      return formalTypeParameters;
    }
  }

  enum Kind {
    CLASS, FIELD, METHOD;

    static Kind fromDexDefinition(DexDefinition definition) {
      if (definition.isDexClass()) {
        return CLASS;
      }
      if (definition.isDexEncodedField()) {
        return FIELD;
      }
      if (definition.isDexEncodedMethod()) {
        return METHOD;
      }
      throw new Unreachable("Unexpected kind of DexDefinition: " + definition);
    }

    Function<String, ? extends DexDefinitionSignature<? extends DexDefinition>>
        parserMethod(Parser parser) {
      switch (this) {
        case CLASS:
          return parser::parseClassSignature;
        case FIELD:
          return parser::parseFieldTypeSignature;
        case METHOD:
          return parser::parseMethodTypeSignature;
      }
      throw new Unreachable("Unexpected kind: " + this);
    }
  }

  public static class Parser {
    // TODO(b/129925954): Can we merge variants of to*Signature below and just expose
    //  type-parameterized version of this, like
    //    <T extends DexDefinitionSignature<?>> T toGenericSignature
    //  without unchecked cast?
    private static DexDefinitionSignature<? extends DexDefinition> toGenericSignature(
        DexClass currentClassContext,
        DexDefinition definition,
        AppView<AppInfoWithLiveness> appView) {
      DexAnnotationSet annotations = definition.annotations();
      if (annotations.annotations.length == 0) {
        return null;
      }
      for (int i = 0; i < annotations.annotations.length; i++) {
        DexAnnotation annotation = annotations.annotations[i];
        if (!DexAnnotation.isSignatureAnnotation(annotation, appView.dexItemFactory())) {
          continue;
        }
        Kind kind = Kind.fromDexDefinition(definition);
        Parser parser = new Parser(currentClassContext, appView);
        String signature = DexAnnotation.getSignature(annotation);
        try {
          return kind.parserMethod(parser).apply(signature);
        } catch (GenericSignatureFormatError e) {
          appView.options().warningInvalidSignature(
              definition, currentClassContext.getOrigin(), signature, e);
        }
      }
      return null;
    }

    public static ClassSignature toClassSignature(
        DexClass clazz, AppView<AppInfoWithLiveness> appView) {
      DexDefinitionSignature<?> signature = toGenericSignature(clazz, clazz, appView);
      if (signature != null) {
        assert signature.isClassSignature();
        return signature.asClassSignature();
      }
      return ClassSignature.UNKNOWN_CLASS_SIGNATURE;
    }

    public static FieldTypeSignature toFieldTypeSignature(
        DexEncodedField field, AppView<AppInfoWithLiveness> appView) {
      DexClass currentClassContext = appView.definitionFor(field.holder());
      DexDefinitionSignature<?> signature =
          toGenericSignature(currentClassContext, field, appView);
      if (signature != null) {
        assert signature.isFieldTypeSignature();
        return signature.asFieldTypeSignature();
      }
      return ClassTypeSignature.UNKNOWN_CLASS_TYPE_SIGNATURE;
    }

    public static MethodTypeSignature toMethodTypeSignature(
        DexEncodedMethod method, AppView<AppInfoWithLiveness> appView) {
      DexClass currentClassContext = appView.definitionFor(method.holder());
      DexDefinitionSignature<?> signature =
          toGenericSignature(currentClassContext, method, appView);
      if (signature != null) {
        assert signature.isMethodTypeSignature();
        return signature.asMethodTypeSignature();
      }
      return MethodTypeSignature.UNKNOWN_METHOD_TYPE_SIGNATURE;
    }

    /*
     * Parser:
     */
    private char symbol; // 0: eof; else valid term symbol or first char of identifier.

    private String identifier;

    /*
     * Scanner:
     * eof is private to the scan methods
     * and it's set only when a scan is issued at the end of the buffer.
     */
    private boolean eof;

    private char[] buffer;

    private int pos;

    private Parser(DexClass currentClassContext, AppView<AppInfoWithLiveness> appView) {
      this.currentClassContext = currentClassContext;
      this.appView = appView;
    }

    ClassSignature parseClassSignature(String signature) {
      try {
        setInput(signature);
        return parseClassSignature();
      } catch (GenericSignatureFormatError e) {
        throw e;
      } catch (Throwable t) {
        Error e = new GenericSignatureFormatError(
            "Unknown error parsing class signature: " + t.getMessage());
        e.addSuppressed(t);
        throw e;
      }
    }

    MethodTypeSignature parseMethodTypeSignature(String signature) {
      try {
        setInput(signature);
        return parseMethodTypeSignature();
      } catch (GenericSignatureFormatError e) {
        throw e;
      } catch (Throwable t) {
        Error e = new GenericSignatureFormatError(
            "Unknown error parsing method signature: " + t.getMessage());
        e.addSuppressed(t);
        throw e;
      }
    }

    FieldTypeSignature parseFieldTypeSignature(String signature) {
      try {
        setInput(signature);
        return parseFieldTypeSignature(ParserPosition.MEMBER_ANNOTATION);
      } catch (GenericSignatureFormatError e) {
        throw e;
      } catch (Throwable t) {
        Error e = new GenericSignatureFormatError(
            "Unknown error parsing field signature: " + t.getMessage());
        e.addSuppressed(t);
        throw e;
      }
    }

    private void setInput(String input) {
      this.buffer = input.toCharArray();
      this.eof = false;
      pos = 0;
      symbol = 0;
      identifier = null;
      scanSymbol();
    }

    //
    // Action:
    //

    enum ParserPosition {
      CLASS_SUPER_OR_INTERFACE_ANNOTATION,
      ENCLOSING_INNER_OR_TYPE_ANNOTATION,
      MEMBER_ANNOTATION
    }

    private final AppView<AppInfoWithLiveness> appView;
    private final DexClass currentClassContext;
    private DexType lastWrittenType = null;

    private DexType parsedTypeName(String name, ParserPosition parserPosition) {
      if (parserPosition == ParserPosition.ENCLOSING_INNER_OR_TYPE_ANNOTATION
          && lastWrittenType == null) {
        // We are writing type-arguments for a merged class.
        return null;
      }
      String originalDescriptor = getDescriptorFromClassBinaryName(name);
      DexType type =
          appView.graphLense().lookupType(appView.dexItemFactory().createType(originalDescriptor));
      if (appView.appInfo().wasPruned(type)) {
        type = appView.dexItemFactory().objectType;
      }
      if (parserPosition == ParserPosition.CLASS_SUPER_OR_INTERFACE_ANNOTATION
          && currentClassContext != null) {
        // We may have merged the type down to the current class type.
        DexString classDescriptor = currentClassContext.type.descriptor;
        if (!originalDescriptor.equals(classDescriptor.toString())
            && type.descriptor.equals(classDescriptor)) {
          lastWrittenType = null;
          return type;
        }
      }
      lastWrittenType = type;
      return type;
    }

    private DexType parsedInnerTypeName(DexType enclosingType, String name) {
      if (enclosingType == null) {
        // We are writing inner type names
        return null;
      }
      assert enclosingType.isClassType();
      String enclosingDescriptor = enclosingType.toDescriptorString();
      DexType type =
          appView
              .dexItemFactory()
              .createType(
                  getDescriptorFromClassBinaryName(
                      getClassBinaryNameFromDescriptor(enclosingDescriptor)
                          + DescriptorUtils.INNER_CLASS_SEPARATOR
                          + name));
      return appView.graphLense().lookupType(type);
    }

    //
    // Parser:
    //

    private ClassSignature parseClassSignature() {
      // ClassSignature ::= FormalTypeParameters? SuperclassSignature SuperinterfaceSignature*.

      List<FormalTypeParameter> formalTypeParameters = parseOptFormalTypeParameters();

      // SuperclassSignature ::= ClassTypeSignature.
      ClassTypeSignature superClassSignature =
          parseClassTypeSignature(ParserPosition.CLASS_SUPER_OR_INTERFACE_ANNOTATION);

      ImmutableList.Builder<ClassTypeSignature> builder = ImmutableList.builder();
      while (symbol > 0) {
        // SuperinterfaceSignature ::= ClassTypeSignature.
        builder.add(parseClassTypeSignature(ParserPosition.CLASS_SUPER_OR_INTERFACE_ANNOTATION));
      }

      return new ClassSignature(formalTypeParameters, superClassSignature, builder.build());
    }

    private List<FormalTypeParameter> parseOptFormalTypeParameters() {
      // FormalTypeParameters ::= "<" FormalTypeParameter+ ">".
      if (symbol != '<') {
        return EMPTY_TYPE_PARAMS;
      }
      scanSymbol();

      ImmutableList.Builder<FormalTypeParameter> builder = ImmutableList.builder();
      while ((symbol != '>') && (symbol > 0)) {
        builder.add(updateFormalTypeParameter());
      }
      expect('>');
      return builder.build();
    }

    private FormalTypeParameter updateFormalTypeParameter() {
      // FormalTypeParameter ::= Identifier ClassBound InterfaceBound*.
      scanIdentifier();
      assert identifier != null;

      String typeParameterIdentifier = identifier;

      // ClassBound ::= ":" FieldTypeSignature?.
      expect(':');

      FieldTypeSignature classBound = ClassTypeSignature.UNKNOWN_CLASS_TYPE_SIGNATURE;
      if (symbol == 'L' || symbol == '[' || symbol == 'T') {
        classBound = parseFieldTypeSignature(ParserPosition.MEMBER_ANNOTATION);
      }

      // Only build the interfacebound builder, which is uncommon, if we actually see an interface.
      ImmutableList.Builder<FieldTypeSignature> builder = null;
      while (symbol == ':') {
        // InterfaceBound ::= ":" FieldTypeSignature.
        if (builder == null) {
          builder = ImmutableList.builder();
        }
        scanSymbol();
        builder.add(parseFieldTypeSignature(ParserPosition.MEMBER_ANNOTATION));
      }
      if (builder == null) {
        return new FormalTypeParameter(typeParameterIdentifier, classBound, null);
      }
      return new FormalTypeParameter(typeParameterIdentifier, classBound, builder.build());
    }

    private FieldTypeSignature parseFieldTypeSignature(ParserPosition parserPosition) {
      // FieldTypeSignature ::= ClassTypeSignature | ArrayTypeSignature | TypeVariableSignature.
      switch (symbol) {
        case 'L':
          return parseClassTypeSignature(parserPosition);
        case '[':
          // ArrayTypeSignature ::= "[" TypeSignature.
          scanSymbol();
          TypeSignature baseTypeSignature = updateTypeSignature(parserPosition);
          return baseTypeSignature.toArrayTypeSignature(appView).asFieldTypeSignature();
        case 'T':
          return updateTypeVariableSignature();
        default:
          parseError("Expected L, [ or T", pos);
      }
      throw new Unreachable("Either FieldTypeSignature is returned or a parse error is thrown.");
    }

    private ClassTypeSignature parseClassTypeSignature(ParserPosition parserPosition) {
      // ClassTypeSignature ::=
      //   "L" (Identifier "/")* Identifier TypeArguments? ("." Identifier TypeArguments?)* ";".
      expect('L');

      StringBuilder qualIdent = new StringBuilder();
      scanIdentifier();
      assert identifier != null;
      while (symbol == '/') {
        qualIdent.append(identifier).append(symbol);
        scanSymbol();
        scanIdentifier();
        assert identifier != null;
      }

      qualIdent.append(this.identifier);
      DexType parsedEnclosingType = parsedTypeName(qualIdent.toString(), parserPosition);

      List<FieldTypeSignature> typeArguments = updateOptTypeArguments();
      ClassTypeSignature outerMostTypeSignature =
          new ClassTypeSignature(parsedEnclosingType, typeArguments);

      ClassTypeSignature outerTypeSignature = outerMostTypeSignature;
      ClassTypeSignature innerTypeSignature;
      while (symbol == '.') {
        // Deal with Member Classes.
        scanSymbol();
        scanIdentifier();
        assert identifier != null;
        parsedEnclosingType = parsedInnerTypeName(parsedEnclosingType, identifier);
        typeArguments = updateOptTypeArguments();
        innerTypeSignature = new ClassTypeSignature(parsedEnclosingType, typeArguments);
        ClassTypeSignature.link(outerTypeSignature, innerTypeSignature);
        outerTypeSignature = innerTypeSignature;
      }

      expect(';');
      return outerMostTypeSignature;
    }

    private List<FieldTypeSignature> updateOptTypeArguments() {
      ImmutableList.Builder<FieldTypeSignature> builder = ImmutableList.builder();
      // OptTypeArguments ::= "<" TypeArgument+ ">".
      if (symbol == '<') {
        scanSymbol();

        builder.add(updateTypeArgument());
        while ((symbol != '>') && (symbol > 0)) {
          builder.add(updateTypeArgument());
        }

        expect('>');
      }
      return builder.build();
    }

    private FieldTypeSignature updateTypeArgument() {
      // TypeArgument ::= (["+" | "-"] FieldTypeSignature) | "*".
      if (symbol == '*') {
        scanSymbol();
        return StarFieldTypeSignature.STAR_FIELD_TYPE_SIGNATURE;
      } else if (symbol == '+') {
        scanSymbol();
        return parseFieldTypeSignature(ParserPosition.ENCLOSING_INNER_OR_TYPE_ANNOTATION)
            .asArgument(WildcardIndicator.POSITIVE);
      } else if (symbol == '-') {
        scanSymbol();
        return parseFieldTypeSignature(ParserPosition.ENCLOSING_INNER_OR_TYPE_ANNOTATION)
            .asArgument(WildcardIndicator.NEGATIVE);
      } else {
        return parseFieldTypeSignature(ParserPosition.ENCLOSING_INNER_OR_TYPE_ANNOTATION)
            .asArgument(WildcardIndicator.NONE);
      }
    }

    private TypeVariableSignature updateTypeVariableSignature() {
      // TypeVariableSignature ::= "T" Identifier ";".
      expect('T');

      scanIdentifier();
      assert identifier != null;

      expect(';');
      return new TypeVariableSignature(identifier);
    }

    private TypeSignature updateTypeSignature(ParserPosition parserPosition) {
      switch (symbol) {
        case 'B':
        case 'C':
        case 'D':
        case 'F':
        case 'I':
        case 'J':
        case 'S':
        case 'Z':
          DexType type = appView.dexItemFactory().createType(String.valueOf(symbol));
          BaseTypeSignature baseTypeSignature = new BaseTypeSignature(type);
          scanSymbol();
          return baseTypeSignature;
        default:
          // Not an elementary type, but a FieldTypeSignature.
          return parseFieldTypeSignature(parserPosition);
      }
    }

    private MethodTypeSignature parseMethodTypeSignature() {
      // MethodTypeSignature ::=
      //     FormalTypeParameters? "(" TypeSignature* ")" ReturnType ThrowsSignature*.
      List<FormalTypeParameter> formalTypeParameters = parseOptFormalTypeParameters();

      expect('(');

      ImmutableList.Builder<TypeSignature> parameterSignatureBuilder = ImmutableList.builder();
      while (symbol != ')' && (symbol > 0)) {
        parameterSignatureBuilder.add(updateTypeSignature(ParserPosition.MEMBER_ANNOTATION));
      }

      expect(')');

      ReturnType returnType = updateReturnType();

      ImmutableList.Builder<TypeSignature> throwsSignatureBuilder = ImmutableList.builder();
      if (symbol == '^') {
        do {
          scanSymbol();

          // ThrowsSignature ::= ("^" ClassTypeSignature) | ("^" TypeVariableSignature).
          if (symbol == 'T') {
            throwsSignatureBuilder.add(updateTypeVariableSignature());
          } else {
            throwsSignatureBuilder.add(parseClassTypeSignature(ParserPosition.MEMBER_ANNOTATION));
          }
        } while (symbol == '^');
      }

      return new MethodTypeSignature(
          formalTypeParameters,
          parameterSignatureBuilder.build(),
          returnType,
          throwsSignatureBuilder.build());
    }

    private ReturnType updateReturnType() {
      // ReturnType ::= TypeSignature | "V".
      if (symbol != 'V') {
        return new ReturnType(updateTypeSignature(ParserPosition.MEMBER_ANNOTATION));
      } else {
        scanSymbol();
        return ReturnType.VOID;
      }
    }

    //
    // Scanner:
    //

    private void scanSymbol() {
      if (!eof) {
        assert buffer != null;
        if (pos < buffer.length) {
          symbol = buffer[pos];
          pos++;
        } else {
          symbol = 0;
          eof = true;
        }
      } else {
        parseError("Unexpected end of signature", pos);
      }
    }

    private void expect(char c) {
      if (eof) {
        parseError("Unexpected end of signature", pos);
      }
      if (symbol == c) {
        scanSymbol();
      } else {
        parseError("Expected " + c, pos - 1);
      }
    }

    private boolean isStopSymbol(char ch) {
      switch (ch) {
        case ':':
        case '/':
        case ';':
        case '<':
        case '.':
          return true;
        default:
          return false;
      }
    }

    // PRE: symbol is the first char of the identifier.
    // POST: symbol = the next symbol AFTER the identifier.
    private void scanIdentifier() {
      if (!eof && pos < buffer.length) {
        StringBuilder identifierBuilder = new StringBuilder(32);
        if (!isStopSymbol(symbol)) {
          identifierBuilder.append(symbol);

          char[] bufferLocal = buffer;
          assert bufferLocal != null;
          do {
            char ch = bufferLocal[pos];
            if (((ch >= 'a') && (ch <= 'z')) || ((ch >= 'A') && (ch <= 'Z'))
                || !isStopSymbol(ch)) {
              identifierBuilder.append(bufferLocal[pos]);
              pos++;
            } else {
              identifier = identifierBuilder.toString();
              scanSymbol();
              return;
            }
          } while (pos != bufferLocal.length);
          identifier = identifierBuilder.toString();
          symbol = 0;
          eof = true;
        } else {
          // Identifier starts with incorrect char.
          symbol = 0;
          eof = true;
          parseError();
        }
      } else {
        parseError("Unexpected end of signature", pos);
      }
    }

    private void parseError() {
      parseError("Unexpected", pos);
    }

    private void parseError(String message, int pos) {
      String arrow = CharBuffer.allocate(pos).toString().replace('\0', ' ') + '^';
      throw new GenericSignatureFormatError(
          message + " at position " + (pos + 1) + System.lineSeparator()
              + String.valueOf(buffer) + System.lineSeparator()
              + arrow);
    }
  }
}
