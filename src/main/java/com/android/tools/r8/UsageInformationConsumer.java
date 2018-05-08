// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.origin.PathOrigin;
import com.android.tools.r8.utils.ExceptionDiagnostic;
import com.android.tools.r8.utils.FileUtils;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;

/**
 * Interface for receiving usage feedback from R8.
 *
 * The data is in the format defined for Proguard's <code>-printusage</code> flag. The information
 * will be produced if a consumer is provided. A consumer is automatically setup when running R8
 * with the Proguard <code>-printusage</code> flag set.
 */
public interface UsageInformationConsumer {

  /**
   * Callback to receive the usage-information data.
   *
   * <p>The consumer is expected not to throw, but instead report any errors via the diagnostics
   * {@param handler}. If an error is reported via {@param handler} and no exceptions are thrown,
   * then the compiler guaranties to exit with an error.
   *
   * @param data UTF-8 encoded usage information.
   * @param handler Diagnostics handler for reporting.
   */
  void acceptUsageInformation(byte[] data, DiagnosticsHandler handler);

  static EmptyConsumer emptyConsumer() {
    return EmptyConsumer.EMPTY_CONSUMER;
  }

  /** Empty consumer to request usage information but ignore the result. */
  class EmptyConsumer implements UsageInformationConsumer {

    private static final EmptyConsumer EMPTY_CONSUMER = new EmptyConsumer();

    private EmptyConsumer() {}

    @Override
    public void acceptUsageInformation(byte[] data, DiagnosticsHandler handler) {
      // Ignore content.
    }
  }

    /** Forwarding consumer to delegate to an optional existing consumer. */
  class ForwardingConsumer implements UsageInformationConsumer {

    private final UsageInformationConsumer consumer;

    /** @param consumer Consumer to forward to, if null, nothing will be forwarded. */
    public ForwardingConsumer(UsageInformationConsumer consumer) {
      this.consumer = consumer;
    }

    @Override
    public void acceptUsageInformation(byte[] data, DiagnosticsHandler handler) {
      if (consumer != null) {
        consumer.acceptUsageInformation(data, handler);
      }
    }
  }

  /** File consumer to write contents to a file-system file. */
  class FileConsumer extends ForwardingConsumer {

    private final Path outputPath;

    /** Consumer that writes to {@param outputPath}. */
    public FileConsumer(Path outputPath) {
      this(outputPath, null);
    }

    /** Consumer that forwards to {@param consumer} and also writes to {@param outputPath}. */
    public FileConsumer(Path outputPath, UsageInformationConsumer consumer) {
      super(consumer);
      this.outputPath = outputPath;
    }

    @Override
    public void acceptUsageInformation(byte[] data, DiagnosticsHandler handler) {
      super.acceptUsageInformation(data, handler);
      try {
        FileUtils.writeToFile(outputPath, null, data);
      } catch (IOException e) {
        Origin origin = new PathOrigin(outputPath);
        handler.error(new ExceptionDiagnostic(e, origin));
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

    /** Consumer that writes to {@param outputStream}. */
    public StreamConsumer(Origin origin, OutputStream outputStream) {
      this(origin, outputStream, null);
    }

    /** Consumer that forwards to {@param consumer} and also writes to {@param outputStream}. */
    public StreamConsumer(
        Origin origin, OutputStream outputStream, UsageInformationConsumer consumer) {
      super(consumer);
      this.origin = origin;
      this.outputStream = outputStream;
    }

    @Override
    public void acceptUsageInformation(byte[] data, DiagnosticsHandler handler) {
      super.acceptUsageInformation(data, handler);
      try {
        outputStream.write(data);
      } catch (IOException e) {
        handler.error(new ExceptionDiagnostic(e, origin));
      }
    }
  }
}
