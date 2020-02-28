// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.utils.DescriptorUtils.getClassBinaryNameFromDescriptor;
import static com.android.tools.r8.utils.DescriptorUtils.getDescriptorFromClassBinaryName;

import com.android.tools.r8.errors.Unimplemented;
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

  public static class ClassSignature implements DexDefinitionSignature<DexClass> {
    static final ClassSignature UNKNOWN_CLASS_SIGNATURE =
        new ClassSignature(ClassTypeSignature.UNKNOWN_CLASS_TYPE_SIGNATURE, ImmutableList.of());

    // TODO(b/129925954): encoding formal type parameters
    final ClassTypeSignature superClassSignature;
    final List<ClassTypeSignature> superInterfaceSignatures;

    ClassSignature(
        ClassTypeSignature superClassSignature,
        List<ClassTypeSignature> superInterfaceSignatures) {
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

    public abstract TypeSignature toArrayTypeSignature(AppView<?> appView);

    public abstract TypeSignature toArrayElementTypeSignature(AppView<?> appView);
  }

  // TODO(b/129925954): better structures for a circle of
  //  TypeSignature - FieldTypeSignature - ClassTypeSignature - TypeArgument
  public abstract static class FieldTypeSignature
      extends TypeSignature implements DexDefinitionSignature<DexEncodedField> {
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

    public boolean isTypeVariableSignature() {
      return false;
    }

    public TypeVariableSignature asTypeVariableSignature() {
      return null;
    }
  }

  // TODO(b/129925954): separate ArrayTypeSignature or just reuse ClassTypeSignature?
  public static class ClassTypeSignature extends FieldTypeSignature {
    static final ClassTypeSignature UNKNOWN_CLASS_TYPE_SIGNATURE =
        new ClassTypeSignature(null, ImmutableList.of());

    // This covers class type or array type, with or without type arguments.
    final DexType type;
    // E.g., for Map<K, V>, a signature will indicate what types are for K and V.
    // Note that this could be nested, e.g., Map<K, Consumer<V>>.
    // TODO(b/129925954): What about * ?
    final List<FieldTypeSignature> typeArguments;

    // TODO(b/129925954): towards immutable structure?
    // Double-linked enclosing-inner relations.
    ClassTypeSignature enclosingTypeSignature;
    ClassTypeSignature innerTypeSignature;

    ClassTypeSignature(DexType type, List<FieldTypeSignature> typeArguments) {
      this.type = type;
      this.typeArguments = typeArguments;
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
    public ClassTypeSignature toArrayTypeSignature(AppView<?> appView) {
      DexType arrayType = type.toArrayType(1, appView.dexItemFactory());
      ClassTypeSignature result = new ClassTypeSignature(arrayType, typeArguments);
      copyEnclosingRelations(result);
      return result;
    }

    @Override
    public ClassTypeSignature toArrayElementTypeSignature(AppView<?> appView) {
      assert type.isArrayType();
      DexType elementType = type.toArrayElementType( appView.dexItemFactory());
      ClassTypeSignature result = new ClassTypeSignature(elementType, typeArguments);
      copyEnclosingRelations(result);
      return result;
    }

    private void copyEnclosingRelations(ClassTypeSignature cloned) {
      cloned.enclosingTypeSignature = this.enclosingTypeSignature;
      cloned.innerTypeSignature = this.innerTypeSignature;
    }

    static void link(ClassTypeSignature outer, ClassTypeSignature inner) {
      assert outer.innerTypeSignature == null && inner.enclosingTypeSignature == null;
      outer.innerTypeSignature = inner;
      inner.enclosingTypeSignature = outer;
    }

    // TODO(b/129925954): rewrite GenericSignatureRewriter with this pattern?
    public interface Converter<R> {
      R init();
      R visitType(DexType type, R result);
      R visitTypeArgument(FieldTypeSignature typeArgument, R result);
      R visitInnerTypeSignature(ClassTypeSignature innerTypeSignature, R result);
    }

    public <R> R convert(Converter<R> converter) {
      R result = converter.init();
      result = converter.visitType(type, result);
      for (FieldTypeSignature typeArgument : typeArguments) {
        result = converter.visitTypeArgument(typeArgument, result);
      }
      if (innerTypeSignature != null) {
        result = converter.visitInnerTypeSignature(innerTypeSignature, result);
      }
      return result;
    }
  }

  public static class TypeVariableSignature extends FieldTypeSignature {
    final String typeVariable;

    TypeVariableSignature(String typeVariable) {
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
    public TypeSignature toArrayTypeSignature(AppView<?> appView) {
      throw new Unimplemented("TypeVariableSignature::toArrayTypeSignature");
    }

    @Override
    public TypeSignature toArrayElementTypeSignature(AppView<?> appView) {
      throw new Unimplemented("TypeVariableSignature::toArrayElementTypeSignature");
    }
  }

  // TODO(b/129925954): Canonicalization?
  public static class BaseTypeSignature extends TypeSignature {
    final DexType type;

    BaseTypeSignature(DexType type) {
      assert type.isPrimitiveType() || type.isPrimitiveArrayType()
              || type.isVoidType()
          : type.toDescriptorString();
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
    public BaseTypeSignature toArrayTypeSignature(AppView<?> appView) {
      assert !type.isVoidType();
      DexType arrayType = type.toArrayType(1, appView.dexItemFactory());
      return new BaseTypeSignature(arrayType);
    }

    @Override
    public BaseTypeSignature toArrayElementTypeSignature(AppView<?> appView) {
      assert type.isPrimitiveArrayType();
      DexType elementType = type.toArrayElementType(appView.dexItemFactory());
      return new BaseTypeSignature(elementType);
    }
  }

  public static class MethodTypeSignature implements DexDefinitionSignature<DexEncodedMethod> {
    static final MethodTypeSignature UNKNOWN_METHOD_TYPE_SIGNATURE =
        new MethodTypeSignature(
            ImmutableList.of(),
            ClassTypeSignature.UNKNOWN_CLASS_TYPE_SIGNATURE,
            ImmutableList.of());

    // TODO(b/129925954): encoding formal type parameters
    final List<TypeSignature> typeSignatures;
    final TypeSignature returnType;
    final List<TypeSignature> throwsSignatures;

    MethodTypeSignature(
        List<TypeSignature> typeSignatures,
        TypeSignature returnType,
        List<TypeSignature> throwsSignatures) {
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

    public TypeSignature returnType() {
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
      DexClass currentClassContext = appView.definitionFor(field.field.holder);
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
      DexClass currentClassContext = appView.definitionFor(method.method.holder);
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
      } catch (Unimplemented e) {
        // TODO(b/129925954): Should not catch this once fully implemented
        return ClassSignature.UNKNOWN_CLASS_SIGNATURE;
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
      } catch (Unimplemented e) {
        // TODO(b/129925954): Should not catch this once fully implemented
        return MethodTypeSignature.UNKNOWN_METHOD_TYPE_SIGNATURE;
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
      } catch (Unimplemented e) {
        // TODO(b/129925954): Should not catch this once fully implemented
        return ClassTypeSignature.UNKNOWN_CLASS_TYPE_SIGNATURE;
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

      parseOptFormalTypeParameters();

      // SuperclassSignature ::= ClassTypeSignature.
      ClassTypeSignature superClassSignature =
          parseClassTypeSignature(ParserPosition.CLASS_SUPER_OR_INTERFACE_ANNOTATION);

      ImmutableList.Builder<ClassTypeSignature> builder = ImmutableList.builder();
      while (symbol > 0) {
        // SuperinterfaceSignature ::= ClassTypeSignature.
        builder.add(parseClassTypeSignature(ParserPosition.CLASS_SUPER_OR_INTERFACE_ANNOTATION));
      }

      return new ClassSignature(superClassSignature, builder.build());
    }

    private void parseOptFormalTypeParameters() {
      // FormalTypeParameters ::= "<" FormalTypeParameter+ ">".

      if (symbol == '<') {
        scanSymbol();

        updateFormalTypeParameter();

        while ((symbol != '>') && (symbol > 0)) {
          updateFormalTypeParameter();
        }

        expect('>');
      }
    }

    private void updateFormalTypeParameter() {
      // FormalTypeParameter ::= Identifier ClassBound InterfaceBound*.
      scanIdentifier();
      assert identifier != null;

      // ClassBound ::= ":" FieldTypeSignature?.
      expect(':');

      if (symbol == 'L' || symbol == '[' || symbol == 'T') {
        parseFieldTypeSignature(ParserPosition.MEMBER_ANNOTATION);
      }

      while (symbol == ':') {
        // InterfaceBound ::= ":" FieldTypeSignature.
        scanSymbol();
        parseFieldTypeSignature(ParserPosition.MEMBER_ANNOTATION);
      }
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
        throw new Unimplemented("GenericSignature.TypeArgument *");
      } else if (symbol == '+') {
        scanSymbol();
        return parseFieldTypeSignature(ParserPosition.ENCLOSING_INNER_OR_TYPE_ANNOTATION);
      } else if (symbol == '-') {
        scanSymbol();
        return parseFieldTypeSignature(ParserPosition.ENCLOSING_INNER_OR_TYPE_ANNOTATION);
      } else {
        return parseFieldTypeSignature(ParserPosition.ENCLOSING_INNER_OR_TYPE_ANNOTATION);
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
      parseOptFormalTypeParameters();

      expect('(');

      ImmutableList.Builder<TypeSignature> parameterSignatureBuilder = ImmutableList.builder();
      while (symbol != ')' && (symbol > 0)) {
        parameterSignatureBuilder.add(updateTypeSignature(ParserPosition.MEMBER_ANNOTATION));
      }

      expect(')');

      TypeSignature returnType = updateReturnType();

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
          parameterSignatureBuilder.build(), returnType, throwsSignatureBuilder.build());
    }

    private TypeSignature updateReturnType() {
      // ReturnType ::= TypeSignature | "V".
      if (symbol != 'V') {
        return updateTypeSignature(ParserPosition.MEMBER_ANNOTATION);
      } else {
        scanSymbol();
        return new BaseTypeSignature(appView.dexItemFactory().voidType);
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
