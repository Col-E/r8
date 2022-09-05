// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compilerapi.artprofiles;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.D8;
import com.android.tools.r8.D8Command;
import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.R8;
import com.android.tools.r8.R8Command;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TextInputStream;
import com.android.tools.r8.TextOutputStream;
import com.android.tools.r8.compilerapi.CompilerApiTest;
import com.android.tools.r8.compilerapi.CompilerApiTestRunner;
import com.android.tools.r8.compilerapi.artprofiles.ArtProfilesForRewritingApiTest.ApiTest.ArtProfileConsumerForTesting;
import com.android.tools.r8.compilerapi.mockdata.MockClass;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileBuilder;
import com.android.tools.r8.profile.art.ArtProfileClassRuleInfo;
import com.android.tools.r8.profile.art.ArtProfileConsumer;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfo;
import com.android.tools.r8.profile.art.ArtProfileProvider;
import com.android.tools.r8.profile.art.ArtProfileRuleConsumer;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.ThrowingBiConsumer;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class ArtProfilesForRewritingApiTest extends CompilerApiTestRunner {

  private static final int SOME_API_LEVEL = 24;

  public ArtProfilesForRewritingApiTest(TestParameters parameters) {
    super(parameters);
  }

  @Override
  public Class<? extends CompilerApiTest> binaryTestClass() {
    return ApiTest.class;
  }

  @Test
  public void testD8() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    runTest(test::runD8);
  }

  @Test
  public void testR8() throws Exception {
    ApiTest test = new ApiTest(ApiTest.PARAMETERS);
    runTest(test::runR8);
  }

  private void runTest(
      ThrowingBiConsumer<ArtProfileConsumerForTesting, ArtProfileConsumerForTesting, Exception>
          testRunner)
      throws Exception {
    ArtProfileConsumerForTesting apiArtProfileConsumer = new ArtProfileConsumerForTesting();
    ArtProfileConsumerForTesting textualArtProfileConsumer = new ArtProfileConsumerForTesting();
    testRunner.accept(apiArtProfileConsumer, textualArtProfileConsumer);
    for (ArtProfileConsumerForTesting artProfileConsumer :
        new ArtProfileConsumerForTesting[] {apiArtProfileConsumer, textualArtProfileConsumer}) {
      assertTrue(artProfileConsumer.isFinished());
      assertTrue(artProfileConsumer.isOutputStreamClosed());
      assertEquals(ApiTest.textualArtProfileLines, artProfileConsumer.getResidualArtProfileRules());
      assertEquals(
          ApiTest.textualArtProfileLines,
          artProfileConsumer.getResidualArtProfileRulesFromOutputStream());
    }
  }

  public static class ApiTest extends CompilerApiTest {

    static ClassReference mockClassReference = Reference.classFromClass(MockClass.class);
    static MethodReference mockInitMethodReference =
        Reference.method(mockClassReference, "<init>", Collections.emptyList(), null);
    static MethodReference mockMainMethodReference =
        Reference.method(
            mockClassReference,
            "main",
            Collections.singletonList(Reference.classFromClass(String[].class)),
            null);
    static List<String> textualArtProfileLines =
        Arrays.asList(
            "Lcom/android/tools/r8/compilerapi/mockdata/MockClass;",
            "PLcom/android/tools/r8/compilerapi/mockdata/MockClass;-><init>()V",
            "HSPLcom/android/tools/r8/compilerapi/mockdata/MockClass;->main([Ljava/lang/String;)V");

    public ApiTest(Object parameters) {
      super(parameters);
    }

    public void runD8(
        ArtProfileConsumerForTesting apiArtProfileConsumer,
        ArtProfileConsumerForTesting textualArtProfileConsumer)
        throws Exception {
      ApiArtProfileProviderForTesting apiArtProfileProvider = new ApiArtProfileProviderForTesting();
      TextualArtProfileProviderForTesting textualArtProfileProvider =
          new TextualArtProfileProviderForTesting();
      D8Command.Builder commandBuilder =
          D8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setMinApiLevel(SOME_API_LEVEL)
              .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
              .addArtProfileForRewriting(apiArtProfileProvider, apiArtProfileConsumer)
              .addArtProfileForRewriting(textualArtProfileProvider, textualArtProfileConsumer);
      D8.run(commandBuilder.build());
      assertTrue(textualArtProfileProvider.inputStream.isClosed());
    }

    public void runR8(
        ArtProfileConsumerForTesting apiArtProfileConsumer,
        ArtProfileConsumerForTesting textualArtProfileConsumer)
        throws Exception {
      ApiArtProfileProviderForTesting apiArtProfileProvider = new ApiArtProfileProviderForTesting();
      TextualArtProfileProviderForTesting textualArtProfileProvider =
          new TextualArtProfileProviderForTesting();
      R8Command.Builder commandBuilder =
          R8Command.builder()
              .addClassProgramData(getBytesForClass(getMockClass()), Origin.unknown())
              .addProguardConfiguration(
                  Collections.singletonList("-keep class * { *; }"), Origin.unknown())
              .addLibraryFiles(getJava8RuntimeJar())
              .setMinApiLevel(SOME_API_LEVEL)
              .setProgramConsumer(DexIndexedConsumer.emptyConsumer())
              .addArtProfileForRewriting(apiArtProfileProvider, apiArtProfileConsumer)
              .addArtProfileForRewriting(textualArtProfileProvider, textualArtProfileConsumer);
      R8.run(commandBuilder.build());
      assertTrue(textualArtProfileProvider.inputStream.isClosed());
    }

    @Test
    public void testD8() throws Exception {
      runD8(null, null);
    }

    @Test
    public void testR8() throws Exception {
      runR8(null, null);
    }

    static class ApiArtProfileProviderForTesting implements ArtProfileProvider {

      @Override
      public void getArtProfile(ArtProfileBuilder profileBuilder) {
        profileBuilder
            .addClassRule(
                classRuleBuilder -> classRuleBuilder.setClassReference(mockClassReference))
            .addMethodRule(
                methodRuleBuilder ->
                    methodRuleBuilder
                        .setMethodReference(mockInitMethodReference)
                        .setMethodRuleInfo(
                            methodRuleInfoBuilder -> methodRuleInfoBuilder.setIsHot(true))
                        .setMethodRuleInfo(
                            methodRuleInfoBuilder -> methodRuleInfoBuilder.setIsPostStartup(true)))
            .addMethodRule(
                methodRuleBuilder ->
                    methodRuleBuilder
                        .setMethodReference(mockMainMethodReference)
                        .setMethodRuleInfo(
                            methodRuleInfoBuilder ->
                                methodRuleInfoBuilder
                                    .setIsHot(true)
                                    .setIsStartup(true)
                                    .setIsPostStartup(true)));
      }

      @Override
      public Origin getOrigin() {
        return Origin.unknown();
      }
    }

    static class TextualArtProfileProviderForTesting implements ArtProfileProvider {

      final ClosableByteArrayInputStream inputStream =
          new ClosableByteArrayInputStream(
              String.join("\n", textualArtProfileLines).concat("\n").getBytes());

      @Override
      public void getArtProfile(ArtProfileBuilder profileBuilder) {
        profileBuilder.addHumanReadableArtProfile(
            new TextInputStream() {
              @Override
              public InputStream getInputStream() {
                return inputStream;
              }

              @Override
              public Charset getCharset() {
                return StandardCharsets.UTF_8;
              }
            },
            parserBuilderConsumer -> {});
      }

      @Override
      public Origin getOrigin() {
        return Origin.unknown();
      }
    }

    static class ArtProfileConsumerForTesting implements ArtProfileConsumer {

      private boolean finished;
      private final List<String> residualArtProfileRules = new ArrayList<>();
      private final ClosableByteArrayOutputStream outputStream =
          new ClosableByteArrayOutputStream();

      @Override
      public TextOutputStream getHumanReadableArtProfileConsumer() {
        return new TextOutputStream() {

          @Override
          public OutputStream getOutputStream() {
            return outputStream;
          }

          @Override
          public Charset getCharset() {
            return StandardCharsets.UTF_8;
          }
        };
      }

      @Override
      public ArtProfileRuleConsumer getRuleConsumer() {
        return new ArtProfileRuleConsumer() {

          @Override
          public void acceptClassRule(
              ClassReference classReference, ArtProfileClassRuleInfo classRuleInfo) {
            residualArtProfileRules.add(classReference.getDescriptor());
          }

          @Override
          public void acceptMethodRule(
              MethodReference methodReference, ArtProfileMethodRuleInfo methodRuleInfo) {
            StringBuilder builder = new StringBuilder();
            if (methodRuleInfo.isHot()) {
              builder.append('H');
            }
            if (methodRuleInfo.isStartup()) {
              builder.append('S');
            }
            if (methodRuleInfo.isPostStartup()) {
              builder.append('P');
            }
            residualArtProfileRules.add(
                builder
                    .append(methodReference.getHolderClass().getDescriptor())
                    .append("->")
                    .append(methodReference.getMethodName())
                    .append(methodReference.getMethodDescriptor())
                    .toString());
          }
        };
      }

      @Override
      public void finished(DiagnosticsHandler handler) {
        finished = true;
      }

      List<String> getResidualArtProfileRulesFromOutputStream()
          throws UnsupportedEncodingException {
        return Arrays.asList(outputStream.toString(StandardCharsets.UTF_8.name()).split("\n"));
      }

      List<String> getResidualArtProfileRules() {
        return residualArtProfileRules;
      }

      boolean isFinished() {
        return finished;
      }

      boolean isOutputStreamClosed() {
        return outputStream.isClosed();
      }
    }

    private static class ClosableByteArrayInputStream extends ByteArrayInputStream {

      private boolean closed;

      public ClosableByteArrayInputStream(byte[] buf) {
        super(buf);
      }

      @Override
      public void close() throws IOException {
        super.close();
        closed = true;
      }

      public boolean isClosed() {
        return closed;
      }
    }

    private static class ClosableByteArrayOutputStream extends ByteArrayOutputStream {

      private boolean closed;

      @Override
      public void close() throws IOException {
        super.close();
        closed = true;
      }

      public boolean isClosed() {
        return closed;
      }
    }
  }
}
