// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.IOExceptionDiagnostic;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

/** Interface for receiving String resource. */
public interface StringConsumer {

  /**
   * Callback to receive a String resource.
   *
   * <p>The consumer is expected not to throw, but instead report any errors via the diagnostics
   * {@param handler}. If an error is reported via {@param handler} and no exceptions are thrown,
   * then the compiler guaranties to exit with an error.
   * @param string String resource.
   * @param handler Diagnostics handler for reporting.
   */
  void accept(String string, DiagnosticsHandler handler);

  static EmptyConsumer emptyConsumer() {
    return EmptyConsumer.EMPTY_CONSUMER;
  }

  /** Empty consumer to request the production of the resource but ignore its value. */
  class EmptyConsumer implements StringConsumer {

    private static EmptyConsumer EMPTY_CONSUMER = new EmptyConsumer();

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      // Ignore content.
    }
  }

  /** Forwarding consumer to delegate to an optional existing consumer. */
  class ForwardingConsumer implements StringConsumer {

    private final StringConsumer consumer;

    /** @param consumer Consumer to forward to, if null, nothing will be forwarded. */
    public ForwardingConsumer(StringConsumer consumer) {
      this.consumer = consumer;
    }

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.accept(string, handler);
      }
    }
  }

  /** File consumer to write contents to a file-system file. */
  class FileConsumer extends ForwardingConsumer {

    private final Path outputPath;
    private Charset encoding = StandardCharsets.UTF_8;

    /** Consumer that writes to {@param outputPath}. */
    public FileConsumer(Path outputPath) {
      this(outputPath, null);
    }

    /** Consumer that forwards to {@param consumer} and also writes to {@param outputPath}. */
    public FileConsumer(Path outputPath, StringConsumer consumer) {
      super(consumer);
      this.outputPath = outputPath;
    }

    /** Set the encoding. Defaults to UTF8. */
    public void setEncoding(Charset encoding) {
      assert encoding != null;
      this.encoding = encoding;
    }

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      super.accept(string, handler);
      try {
        Files.write(outputPath, Collections.singletonList(string), encoding);
      } catch (IOException e) {
        handler.error(new IOExceptionDiagnostic(e, new PathOrigin(outputPath)));
      }
    }
  }

  /**
   * Stream consumer to write contents to an output stream.
   *
   * <p>Note: No close events are given to this stream so it should either be a permanent stream or
   * the closing needs to happen outside of the compilation itself. If the stream is not one of the
   * standard streams, i.e., System.out or System.err, you should likely implement yor own consumer.
   */
  class StreamConsumer extends ForwardingConsumer {

    private final Origin origin;
    private final OutputStream outputStream;
    private Charset encoding = StandardCharsets.UTF_8;

    /** Consumer that writes to {@param outputStream}. */
    public StreamConsumer(Origin origin, OutputStream outputStream) {
      this(origin, outputStream, null);
    }

    /** Consumer that forwards to {@param consumer} and also writes to {@param outputStream}. */
    public StreamConsumer(Origin origin, OutputStream outputStream, StringConsumer consumer) {
      super(consumer);
      this.origin = origin;
      this.outputStream = outputStream;
    }

    /** Set the encoding. Defaults to UTF8. */
    public void setEncoding(Charset encoding) {
      assert encoding != null;
      this.encoding = encoding;
    }

    @Override
    public void accept(String string, DiagnosticsHandler handler) {
      super.accept(string, handler);
      try (BufferedWriter writer =
          new BufferedWriter(new OutputStreamWriter(outputStream, encoding.newEncoder()))) {
        writer.write(string);
      } catch (IOException e) {
        handler.error(new IOExceptionDiagnostic(e, origin));
      }
    }
  }
}
