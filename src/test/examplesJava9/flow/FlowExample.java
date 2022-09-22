// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package flow;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Flow.Publisher;
import java.util.concurrent.Flow.Subscriber;
import java.util.concurrent.Flow.Subscription;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public class FlowExample {
  public static void main(String[] args) throws Exception {
    OneShotPublisher oneShotPublisher = new OneShotPublisher();
    SampleSubscriber<Object> subscriber = new SampleSubscriber<>(50L, System.out::println);
    oneShotPublisher.subscribe(subscriber);
    oneShotPublisher.awaitPublishing();
  }

  public static class OneShotPublisher implements Publisher<Boolean> {

    private final ForkJoinPool executor = new ForkJoinPool(); // daemon-based

    private boolean subscribed; // true after first subscribe

    public synchronized void subscribe(Subscriber<? super Boolean> subscriber) {
      if (subscribed) {
        subscriber.onError(new IllegalStateException()); // only one allowed
      } else {
        subscribed = true;
        subscriber.onSubscribe(new OneShotSubscription(subscriber, executor));
      }
    }

    void awaitPublishing() throws InterruptedException {
      // On Android 7 and higher, calling System.exit() terminates all threads without waiting for
      // tasks on the fork join pool to be finished. For the test to be completed we need such
      // tasks to finish.
      int seconds = 60;
      executor.awaitTermination(seconds, TimeUnit.SECONDS);
    }

    static class OneShotSubscription implements Subscription {

      private final Subscriber<? super Boolean> subscriber;
      private final ExecutorService executor;
      private Future<?> future; // to allow cancellation
      private boolean completed;

      OneShotSubscription(Subscriber<? super Boolean> subscriber, ExecutorService executor) {
        this.subscriber = subscriber;
        this.executor = executor;
      }

      public synchronized void request(long n) {
        if (n != 0 && !completed) {
          completed = true;
          if (n < 0) {
            IllegalArgumentException ex = new IllegalArgumentException();
            executor.execute(() -> subscriber.onError(ex));
          } else {
            future =
                executor.submit(
                    () -> {
                      subscriber.onNext(Boolean.TRUE);
                      subscriber.onComplete();
                    });
          }
        }
      }

      public synchronized void cancel() {
        completed = true;
        if (future != null) {
          future.cancel(false);
        }
      }
    }
  }

  static class SampleSubscriber<T> implements Subscriber<T> {

    final Consumer<? super T> consumer;
    Subscription subscription;
    final long bufferSize;
    long count;

    SampleSubscriber(long bufferSize, Consumer<? super T> consumer) {
      this.bufferSize = bufferSize;
      this.consumer = consumer;
    }

    public void onSubscribe(Subscription subscription) {
      long initialRequestSize = bufferSize;
      count = bufferSize - bufferSize / 2; // re-request when half consumed
      (this.subscription = subscription).request(initialRequestSize);
    }

    public void onNext(T item) {
      if (--count <= 0) {
        subscription.request(count = bufferSize - bufferSize / 2);
      }
      consumer.accept(item);
    }

    public void onError(Throwable ex) {
      ex.printStackTrace();
    }

    public void onComplete() {}
  }
}
