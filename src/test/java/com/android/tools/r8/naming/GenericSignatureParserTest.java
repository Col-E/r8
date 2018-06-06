// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.naming;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.naming.signature.GenericSignatureAction;
import com.android.tools.r8.naming.signature.GenericSignatureParser;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.lang.reflect.GenericSignatureFormatError;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.Test;

public class GenericSignatureParserTest extends TestBase {
  private static class ReGenerateGenericSignatureRewriter
      implements GenericSignatureAction<String> {

    private StringBuilder renamedSignature;

    public String getRenamedSignature() {
      return renamedSignature.toString();
    }

    @Override
    public void parsedSymbol(char symbol) {
      renamedSignature.append(symbol);
    }

    @Override
    public void parsedIdentifier(String identifier) {
      renamedSignature.append(identifier);
    }

    @Override
    public String parsedTypeName(String name) {
      renamedSignature.append(name);
      return name;
    }

    @Override
    public String parsedInnerTypeName(String enclosingType, String name) {
      renamedSignature.append(name);
      return name;
    }

    @Override
    public void start() {
      renamedSignature = new StringBuilder();
    }

    @Override
    public void stop() {
      // nothing to do
    }
  }

  public void parseSimpleError(BiConsumer<GenericSignatureParser<String>, String> parse,
      Consumer<GenericSignatureFormatError> errorChecker) {
    try {
      String signature = "X";
      GenericSignatureParser<String> parser =
          new GenericSignatureParser<>(new ReGenerateGenericSignatureRewriter());
      parse.accept(parser, signature);
      fail("Succesfully parsed " + signature);
    } catch (GenericSignatureFormatError e) {
      errorChecker.accept(e);
    }
  }

  @Test
  public void simpleParseError() {
    parseSimpleError(
        GenericSignatureParser::parseClassSignature,
        e -> assertTrue(e.getMessage().startsWith("Expected L at position 1")));
    // TODO(sgjesse): The position 2 reported here is onr off.
    parseSimpleError(
        GenericSignatureParser::parseFieldSignature,
        e -> assertTrue(e.getMessage().startsWith("Expected L, [ or T at position 2")));
    parseSimpleError(GenericSignatureParser::parseMethodSignature,
        e -> assertTrue(e.getMessage().startsWith("Expected ( at position 1")));
  }

  private void parseSignature(String signature, Set<Integer> validPrefixes,
      Consumer<String> parser, ReGenerateGenericSignatureRewriter rewriter) {
    for (int i = 0; i < 2; i++) {
      parser.accept(signature);
      assertEquals(signature, rewriter.getRenamedSignature());
    }

    for (int i = 1; i < signature.length(); i++) {
      try {
        if (validPrefixes == null || !validPrefixes.contains(i)) {
          parser.accept(signature.substring(0, i));
          fail("Succesfully parsed " + signature.substring(0, i) + " (position " + i +")");
        }
      } catch (GenericSignatureFormatError e) {
        assertTrue("" + i + " Was: " + e.getMessage(), e.getMessage().contains("at position " + (i + 1)));
      }
    }
  }

  private void parseClassSignature(String signature, Set<Integer> validPrefixes) {
    ReGenerateGenericSignatureRewriter rewriter = new ReGenerateGenericSignatureRewriter();
    GenericSignatureParser<String> parser = new GenericSignatureParser<>(rewriter);

    parseSignature(signature, validPrefixes, parser::parseClassSignature, rewriter);
  }

  private void parseFieldSignature(String signature, Set<Integer> validPrefixes) {
    ReGenerateGenericSignatureRewriter rewriter = new ReGenerateGenericSignatureRewriter();
    GenericSignatureParser<String> parser = new GenericSignatureParser<>(rewriter);

    parseSignature(signature, validPrefixes, parser::parseFieldSignature, rewriter);
  }

  private void parseMethodSignature(String signature, Set<Integer> validPrefixes) {
    ReGenerateGenericSignatureRewriter rewriter = new ReGenerateGenericSignatureRewriter();
    GenericSignatureParser<String> parser = new GenericSignatureParser<>(rewriter);

    parseSignature(signature, validPrefixes, parser::parseMethodSignature, rewriter);
  }

  public void forClassSignatures(BiConsumer<String, Set<Integer>> consumer) {
    consumer.accept("Ljava/lang/Object;", null);
    consumer.accept("LOuter$InnerInterface<TU;>;", null);

    consumer.accept("La;", null);
    consumer.accept("La.i;", null);
    consumer.accept("La.i.j;", null);
    consumer.accept("La/b;", null);
    consumer.accept("La/b.i;", null);
    consumer.accept("La/b.i.j;", null);
    consumer.accept("La/b/c;", null);
    consumer.accept("La/b/c.i;", null);
    consumer.accept("La/b/c.i.j;", null);
    consumer.accept("La$b;", null);
    consumer.accept("La$b.i;", null);
    consumer.accept("La$b.i.j;", null);
    consumer.accept("La$b$c;", null);
    consumer.accept("La$b$c.i;", null);
    consumer.accept("La$b$c.i.j;", null);

    StringBuilder builder = new StringBuilder();
    for (int i = 0; i < 3; i++) {
      builder.append("TT;");
      consumer.accept("La<" + builder.toString() + ">;", null);
      consumer.accept("La<" + builder.toString() + ">.i;", null);
      consumer.accept("La<" + builder.toString() + ">.i.j;", null);
      consumer.accept("La/b<" + builder.toString() + ">;", null);
      consumer.accept("La/b<" + builder.toString() + ">.i;", null);
      consumer.accept("La/b<" + builder.toString() + ">.i.j;", null);
      consumer.accept("La/b/c<" + builder.toString() + ">;", null);
      consumer.accept("La/b/c<" + builder.toString() + ">.i;", null);
      consumer.accept("La/b/c<" + builder.toString() + ">.i.j;", null);
      consumer.accept("La$b<" + builder.toString() + ">;", null);
      consumer.accept("La$b<" + builder.toString() + ">.i;", null);
      consumer.accept("La$b<" + builder.toString() + ">.i.j;", null);
      consumer.accept("La$b$c<" + builder.toString() + ">;", null);
      consumer.accept("La$b$c<" + builder.toString() + ">.i;", null);
      consumer.accept("La$b$c<" + builder.toString() + ">.i.j;", null);
    }
  }


  public void forBasicTypes(Consumer<String> consumer) {
    for (char c : "BCDFIJSZ".toCharArray()) {
      consumer.accept(new String(new char[]{c}));
    }
  }

  public void forBasicTypesAndVoid(Consumer<String> consumer) {
    forBasicTypes(consumer);
    consumer.accept("V");
  }

  public void forTypeVariableSignatures(BiConsumer<String, Set<Integer>> consumer) {
    consumer.accept("TT;", null);

    consumer.accept("Ta;", null);
    consumer.accept("Tab;", null);
    consumer.accept("Tabc;", null);
    consumer.accept("Ta-b;", null);
    consumer.accept("Ta-b-c;", null);
  }

  public void forArrayTypeSignatures(BiConsumer<String, Set<Integer>> consumer) {
    StringBuilder arrayPrefix = new StringBuilder();
    for (int i = 0; i < 3; i++) {
      arrayPrefix.append("[");
      forBasicTypes(t -> consumer.accept(arrayPrefix.toString() + t, null));
      forClassSignatures((x, y) -> {
        consumer.accept(arrayPrefix.toString() + x, y);
      });
      forTypeVariableSignatures((x, y) -> {
        consumer.accept(arrayPrefix.toString() + x, y);
      });
      //consumer.accept();
    }
  }

  public void forClassTypeSignatures(BiConsumer<String, Set<Integer>> consumer) {
    // In https://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.3.4 (Java 7) it
    // says: "If the class bound does not specify a type, it is taken to be Object.". That sentence
    // is not present for Java 8 or Java 9. We do test it here, and javac from OpenJDK 8 will also
    // produce that for a class that extends Object and implements at least one interface.

    // class C<T> { ... }.
    consumer.accept("<T:>Ljava/lang/Object;", null);
    consumer.accept("<T:Ljava/lang/Object;>Ljava/lang/Object;", null);

    // class C<T,U> { ... }.
    consumer.accept("<T:U:>Ljava/lang/Object;", null);
    consumer.accept("<T:Ljava/lang/Object;U:Ljava/lang/Object;>Ljava/lang/Object;", null);

    // class C<T extends AbstractList> { ... }.
    consumer.accept("<T:Ljava/util/AbstractList;>Ljava/lang/Object;", null);
    // class C<T extends AbstractList<T>> { ... }.
    consumer.accept("<T:Ljava/util/AbstractList<TT;>;>Ljava/lang/Object;", null);

    // class C<T extends List> { ... }.
    consumer.accept("<T::Ljava/util/List;>Ljava/lang/Object;", null);
    // class C<T extends List<T>> { ... }.
    consumer.accept("<T::Ljava/util/List<TT;>;>Ljava/lang/Object;", null);
    // class C<T extends List<T[]>> { ... }.
    consumer.accept("<T::Ljava/util/List<[TT;>;>Ljava/lang/Object;", null);
    // class C<T extends List<T[][]>> { ... }.
    consumer.accept("<T::Ljava/util/List<[[TT;>;>Ljava/lang/Object;", null);

    // class C<T extends AbstractList & List> { ... }.
    consumer.accept("<T:Ljava/util/AbstractList;:Ljava/util/List;>Ljava/lang/Object;", null);
    // class C<T extends AbstractList<T> & List<T>> { ... }.
    consumer.accept(
        "<T:Ljava/util/AbstractList<TT;>;:Ljava/util/List<TT;>;>Ljava/lang/Object;", null);
    // class C<T extends AbstractList<T[]> & List<T[]>> { ... }.
    consumer.accept(
        "<T:Ljava/util/AbstractList<[TT;>;:Ljava/util/List<[TT;>;>Ljava/lang/Object;", null);
    // class C<T extends AbstractList<T[][]> & List<T[][]>> { ... }.
    consumer.accept(
        "<T:Ljava/util/AbstractList<[[TT;>;:Ljava/util/List<[[TT;>;>Ljava/lang/Object;", null);

    // class C<T extends AbstractList & List & Iterator> { ... }.
    consumer.accept(
        "<T:Ljava/util/AbstractList;:Ljava/util/List;:Ljava/util/Iterator;>Ljava/lang/Object;",
        null);
    // class C<T extends AbstractList<T> & List<T> & Iterator<T>> { ... }.
    consumer.accept(
        "<T:Ljava/util/AbstractList<TT;>;:Ljava/util/List<TT;>;:Ljava/util/Iterator<TT;>;>"
            + "Ljava/lang/Object;", null);

    // class C<T,U> { ... }.
    consumer.accept("<T:U:>Ljava/lang/Object;", null);
    consumer.accept("<T:Ljava/lang/Object;U:Ljava/lang/Object;>Ljava/lang/Object;", null);

    // class C extends java.util.AbstractList<String>
    consumer.accept("Ljava/util/AbstractList<Ljava/lang/String;>;", null);
    // class C extends java.util.AbstractList<String> implements List<String>
    consumer.accept(
        "Ljava/util/AbstractList<Ljava/lang/String;>;Ljava/util/List<Ljava/lang/String;>;",
        ImmutableSet.of(44));
    // class C extends java.util.AbstractList<String> implements List<String>, Iterator<String>
    consumer.accept(
        "Ljava/util/AbstractList<Ljava/lang/String;>;"
            + "Ljava/util/List<Ljava/lang/String;>;Ljava/util/Iterator<Ljava/lang/String;>;",
        ImmutableSet.of(44, 80));

    // class C<T> extends java.util.AbstractList<T>
    consumer.accept("<T:Ljava/lang/Object;>Ljava/util/AbstractList<TT;>;", null);
    // class C<T> extends java.util.AbstractList<T> implements List<T>
    consumer.accept("<T:Ljava/lang/Object;>Ljava/util/AbstractList<TT;>;Ljava/util/List<TT;>;",
        ImmutableSet.of(51));
    // class C<T> extends java.util.AbstractList<T> implements List<T>, Iterator<T>
    consumer.accept("<T:Ljava/lang/Object;>Ljava/util/AbstractList<TT;>"
            + ";Ljava/util/List<TT;>;Ljava/util/Iterator<TT;>;",
        ImmutableSet.of(51, 72));

    // class Outer<T> {
    //   class Inner {
    //     class InnerInner {
    //     }
    //     class ExtendsInnerInner extends InnerInner {
    //     }
    //   }
    //   class ExtendsInner extends Inner {
    //   }
    // }
    consumer.accept("<T:Ljava/lang/Object;>Ljava/lang/Object;", null);  // Outer signature.
    // Inner has no signature.
    // InnerInner has no signature.
    consumer.accept("LOuter<TT;>.Inner.InnerInner;", null);  // ExtendsInnerInner signature.
    consumer.accept("LOuter<TT;>.Inner;", null);  // ExtendsInner signature.

    // class Outer<T> {
    //   class Inner<T> {
    //   }
    //   interface InnerInterface<T> {
    //   }
    //   abstract class ExtendsInner<U> extends Inner implements InnerInterface<U> {
    //   }
    // }
    consumer.accept(
        "<U:Ljava/lang/Object;>LOuter<TT;>.Inner<TT;>;LOuter$InnerInterface<TU;>;",
        ImmutableSet.of(45));
  }

  public void forMethodSignatures(BiConsumer<String, Set<Integer>> consumer) {
    forBasicTypesAndVoid(t -> consumer.accept("()" + t, null));
    forBasicTypesAndVoid(t -> consumer.accept("(BCDFIJSZ)" + t, null));
    forBasicTypesAndVoid(t -> consumer.accept("<T:>(BCDFIJSZ)" + t, null));
    forBasicTypesAndVoid(t -> consumer.accept("<T:Ljava/util/List;>(BCDFIJSZ)" + t, null));
    forBasicTypesAndVoid(t -> consumer.accept("<T:U:>(BCDFIJSZ)" + t, null));
    forBasicTypesAndVoid(
        t -> consumer.accept("<T:Ljava/util/List;U:Ljava/util/List;>(BCDFIJSZ)" + t, null));

    consumer.accept("<T:U:>(Ljava/util/List<TT;>;Ljava/util/Iterator<TT;>;)V", null);
    consumer.accept(
        "<T:U:>(Ljava/util/List<TT;>;Ljava/util/Iterator<TT;>;)Ljava/util/List<TT;>;", null);

    consumer.accept("<T:U:>(La/b/c<TT;>.i<TT;>;)V", null);
    consumer.accept("<T:U:>(La/b/c<TT;>.i<TT;>.j<TU;>;)V", null);

    consumer.accept("<T:>()La/b/c<TT;>;", null);
    consumer.accept("<T:>()La/b/c<TT;>.i<TT;>;", null);
    consumer.accept("<T:>()La/b/c<TT;>.i<TT;>.j<TT;>;", null);
  }

  @Test
  public void testClassSignatureGenericClass() {
    forClassTypeSignatures(this::parseClassSignature);
  }

  @Test
  public void parseFieldSignature() {
    forClassSignatures(this::parseFieldSignature);
    forTypeVariableSignatures(this::parseFieldSignature);
    forArrayTypeSignatures(this::parseFieldSignature);
  }

  @Test
  public void parseMethodSignature() {
    forMethodSignatures(this::parseMethodSignature);
  }

  private void failingParseAction(Consumer<GenericSignatureParser<String>> parse)
      throws Exception {
    class ThrowsInParserActionBase<E extends Error> extends ReGenerateGenericSignatureRewriter {
      protected Supplier<? extends E> exceptionSupplier;

      private ThrowsInParserActionBase(Supplier<? extends E> exceptionSupplier) {
        this.exceptionSupplier = exceptionSupplier;
      }
    }

    class ThrowsInParsedSymbol<E extends Error> extends ThrowsInParserActionBase<E> {
      private ThrowsInParsedSymbol(Supplier<? extends E> exceptionSupplier) {
        super(exceptionSupplier);
      }

      @Override
      public void parsedSymbol(char symbol) {
        throw exceptionSupplier.get();
      }
    }

    class ThrowsInParsedIdentifier<E extends Error> extends ThrowsInParserActionBase<E> {
      private ThrowsInParsedIdentifier(Supplier<? extends E> exceptionSupplier) {
        super(exceptionSupplier);
      }

      @Override
      public void parsedIdentifier(String identifier) {
        throw exceptionSupplier.get();
      }
    }

    class ThrowsInParsedTypeName<E extends Error> extends ThrowsInParserActionBase<E> {
      private ThrowsInParsedTypeName(Supplier<? extends E> exceptionSupplier) {
        super(exceptionSupplier);
      }

      @Override
      public String parsedTypeName(String name) {
        throw exceptionSupplier.get();
      }
    }

    class ThrowsInParsedInnerTypeName<E extends Error> extends ThrowsInParserActionBase<E> {
      private ThrowsInParsedInnerTypeName(Supplier<? extends E> exceptionSupplier) {
        super(exceptionSupplier);
      }

      @Override
      public String parsedInnerTypeName(String enclosingType, String name) {
        throw exceptionSupplier.get();
      }
    }

    class ThrowsInStart<E extends Error> extends ThrowsInParserActionBase<E> {
      private ThrowsInStart(Supplier<? extends E> exceptionSupplier) {
        super(exceptionSupplier);
      }

      @Override
      public void start() {
        throw exceptionSupplier.get();
      }
    }

    class ThrowsInStop<E extends Error> extends ThrowsInParserActionBase<E> {
      private ThrowsInStop(Supplier<? extends E> exceptionSupplier) {
        super(exceptionSupplier);
      }

      @Override
      public void stop() {
        throw exceptionSupplier.get();
      }
    }

    List<GenericSignatureAction<String>> throwingActions = ImmutableList.of(
        new ThrowsInParsedSymbol<>(() -> new GenericSignatureFormatError("ERROR")),
        new ThrowsInParsedSymbol<>(() -> new Error("ERROR")),
        new ThrowsInParsedIdentifier<>(() -> new GenericSignatureFormatError("ERROR")),
        new ThrowsInParsedIdentifier<>(() -> new Error("ERROR")),
        new ThrowsInParsedTypeName<>(() -> new GenericSignatureFormatError("ERROR")),
        new ThrowsInParsedTypeName<>(() -> new Error("ERROR")),
        new ThrowsInParsedInnerTypeName<>(() -> new GenericSignatureFormatError("ERROR")),
        new ThrowsInParsedInnerTypeName<>(() -> new Error("ERROR")),
        new ThrowsInStart<>(() -> new GenericSignatureFormatError("ERROR")),
        new ThrowsInStart<>(() -> new Error("ERROR")),
        new ThrowsInStop<>(() -> new GenericSignatureFormatError("ERROR")),
        new ThrowsInStop<>(() -> new Error("ERROR")));

    int plainErrorCount = 0;
    for (GenericSignatureAction<String> action : throwingActions) {
      GenericSignatureParser<String> parser = new GenericSignatureParser<>(action);
      try {
        // This class signature hits all action callbacks.
        parse.accept(parser);
        fail("Parse succeeded for " + action.getClass().getSimpleName());
      } catch (GenericSignatureFormatError e) {
        if (e.getSuppressed().length == 0) {
          assertEquals("ERROR", e.getMessage());
        } else {
          plainErrorCount++;
          assertEquals("Unknown error parsing generic signature: ERROR", e.getMessage());
        }
      }
    }
    assertEquals(6, plainErrorCount);
  }

  @Test
  public void failingParseAction() throws Exception {
    // These signatures hits all action callbacks.
    failingParseAction(parser -> parser.parseClassSignature(
        "<U:Ljava/lang/Object;>LOuter<TT;>.Inner;Ljava/util/List<TU;>;"));
    failingParseAction(
        parser -> parser.parseFieldSignature("LOuter$InnerInterface<TU;>.Inner;"));
    failingParseAction(
        parser -> parser.parseMethodSignature("(LOuter$InnerInterface<TU;>.Inner;)V"));
  }
}
