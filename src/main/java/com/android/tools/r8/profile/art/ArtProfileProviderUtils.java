// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import static com.android.tools.r8.utils.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.TextInputStream;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.Box;
import com.android.tools.r8.utils.ClassReferenceUtils;
import com.android.tools.r8.utils.MethodReferenceUtils;
import com.android.tools.r8.utils.UTF8TextInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.function.Consumer;

public class ArtProfileProviderUtils {

  public static ArtProfileProvider createFromHumanReadableArtProfile(Path artProfile) {
    return new ArtProfileProvider() {
      @Override
      public void getArtProfile(ArtProfileBuilder profileBuilder) {
        try {
          profileBuilder.addHumanReadableArtProfile(
              new UTF8TextInputStream(artProfile), emptyConsumer());
        } catch (IOException e) {
          throw new UncheckedIOException(e);
        }
      }

      @Override
      public Origin getOrigin() {
        return new PathOrigin(artProfile);
      }
    };
  }

  /** Serialize the given {@param artProfileProvider} to a string for writing it to a dump. */
  @SuppressWarnings("DefaultCharset")
  public static String serializeToString(ArtProfileProvider artProfileProvider) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    try (OutputStreamWriter outputStreamWriter =
        new OutputStreamWriter(baos, StandardCharsets.UTF_8)) {
      artProfileProvider.getArtProfile(
          new ArtProfileBuilder() {

            @Override
            public ArtProfileBuilder addClassRule(
                Consumer<ArtProfileClassRuleBuilder> classRuleBuilderConsumer) {
              classRuleBuilderConsumer.accept(
                  new ArtProfileClassRuleBuilder() {

                    @Override
                    public ArtProfileClassRuleBuilder setClassReference(
                        ClassReference classReference) {
                      writeLine(
                          outputStreamWriter, ClassReferenceUtils.toSmaliString(classReference));
                      return this;
                    }
                  });
              return this;
            }

            @Override
            public ArtProfileBuilder addMethodRule(
                Consumer<ArtProfileMethodRuleBuilder> methodRuleBuilderConsumer) {
              Box<MethodReference> methodReferenceBox = new Box<>();
              methodRuleBuilderConsumer.accept(
                  new ArtProfileMethodRuleBuilder() {

                    @Override
                    public ArtProfileMethodRuleBuilder setMethodReference(
                        MethodReference methodReference) {
                      methodReferenceBox.set(methodReference);
                      return this;
                    }

                    @Override
                    public ArtProfileMethodRuleBuilder setMethodRuleInfo(
                        Consumer<ArtProfileMethodRuleInfoBuilder> methodRuleInfoBuilderConsumer) {
                      ArtProfileMethodRuleInfoImpl.Builder artProfileMethodRuleInfoBuilder =
                          ArtProfileMethodRuleInfoImpl.builder();
                      methodRuleInfoBuilderConsumer.accept(artProfileMethodRuleInfoBuilder);
                      ArtProfileMethodRuleInfoImpl artProfileMethodRuleInfo =
                          artProfileMethodRuleInfoBuilder.build();
                      try {
                        artProfileMethodRuleInfo.writeHumanReadableFlags(outputStreamWriter);
                      } catch (IOException e) {
                        throw new UncheckedIOException(e);
                      }
                      return this;
                    }
                  });
              writeLine(
                  outputStreamWriter, MethodReferenceUtils.toSmaliString(methodReferenceBox.get()));
              return this;
            }

            @Override
            @SuppressWarnings("DefaultCharset")
            public ArtProfileBuilder addHumanReadableArtProfile(
                TextInputStream textInputStream,
                Consumer<HumanReadableArtProfileParserBuilder> parserBuilderConsumer) {
              try (InputStreamReader inputStreamReader =
                  new InputStreamReader(
                      textInputStream.getInputStream(), textInputStream.getCharset())) {
                char[] buffer = new char[1024];
                int len = inputStreamReader.read(buffer);
                while (len != -1) {
                  outputStreamWriter.write(buffer, 0, len);
                  len = inputStreamReader.read(buffer);
                }
                writeLine(outputStreamWriter);
              } catch (IOException e) {
                throw new UncheckedIOException(e);
              }
              return this;
            }
          });
    }
    return baos.toString();
  }

  private static void writeLine(OutputStreamWriter outputStreamWriter) {
    writeLine(outputStreamWriter, "");
  }

  private static void writeLine(OutputStreamWriter outputStreamWriter, String string) {
    try {
      outputStreamWriter.write(string);
      outputStreamWriter.write('\n');
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }
}
