package com.noenv.markus;

import io.reactivex.Completable;
import io.reactivex.Observable;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.VertxOptions;
import io.vertx.core.http.HttpClientOptions;
import io.vertx.core.http.RequestOptions;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.reactivex.CompletableHelper;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.core.http.HttpClient;
import io.vertx.reactivex.core.http.HttpClientRequest;
import io.vertx.reactivex.core.http.HttpClientResponse;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.noenv.markus.MainVerticle.*;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest {

  @Test
  public void shouldGetCreate(final TestContext context) {
    Vertx.rxClusteredVertx(new VertxOptions())
      .repeat(2)
      .toList(2)
      .flatMapCompletable(
        vertx -> vertx.get(0).rxDeployVerticle(MainVerticle.class.getName(), new DeploymentOptions())
          .ignoreElement()
          .andThen(
            Completable.defer(
              () -> httpRequest(vertx.get(0).createHttpClient(), PATH_CREATE, response -> context.assertEquals(200, response.statusCode()))
            )
          )
          .doOnComplete(() -> vertx.get(0).close())
          .doOnComplete(() -> vertx.get(1).close())
      )
      .subscribe(CompletableHelper.toObserver(context.asyncAssertSuccess()));
  }

  @Test
  public void shouldGetRemove(final TestContext context) {
    Vertx.rxClusteredVertx(new VertxOptions())
      .repeat(2)
      .toList(2)
      .flatMapCompletable(
        vertx -> vertx.get(0).rxDeployVerticle(MainVerticle.class.getName(), new DeploymentOptions())
          .ignoreElement()
          .andThen(
            Completable.defer(
              () -> httpRequest(vertx.get(0).createHttpClient(), PATH_REMOVE, response -> context.assertEquals(200, response.statusCode()))
            )
          )
          .doOnComplete(() -> vertx.get(0).close())
          .doOnComplete(() -> vertx.get(1).close())
      )
      .subscribe(CompletableHelper.toObserver(context.asyncAssertSuccess()));
  }

  @Test
  @Ignore
  public void load(final TestContext context) {
    final var requestCounter = new AtomicLong(0);
    final var successCounter = new AtomicLong(0);

    final var client = Vertx.vertx().createHttpClient(new HttpClientOptions().setDefaultPort(PORT_HTTP));

    final var requestsPerSecond = 1000;

    Observable.interval(0, 1, TimeUnit.SECONDS)
      .doOnNext(
        t -> {
          if (requestsPerSecond <= (requestCounter.get() - successCounter.get())) {
            return; // no need to request even more if target cant keep up
          }

          Observable.range(0, requestsPerSecond)
            .flatMapCompletable(
              c -> httpRequest(client, PATH_CREATE, response -> {if (200 == response.statusCode()) successCounter.incrementAndGet();})
                  .doOnSubscribe(dis -> requestCounter.incrementAndGet())
            )
            .subscribe(() -> {}, Throwable::printStackTrace);
        }
      )
      .ignoreElements()
      .subscribe(() -> {}, Throwable::printStackTrace);

    Observable.interval(1, 1, TimeUnit.SECONDS)
      .map(cnt -> cnt + 1)
      .doOnNext(cnt -> System.err.println("requestCounter " + requestCounter.get() + " rps " + requestCounter.get() / cnt + " success " + successCounter.get() + " sps " + successCounter.get() / cnt))
      .subscribe();

    context.async(); // never stop
  }

  private static Completable httpRequest(final HttpClient client, final String path, final Consumer<HttpClientResponse> consumer) {
    // VertX 4
    return client.rxRequest(new RequestOptions().setPort(PORT_HTTP).setURI(path))
      .flatMap(HttpClientRequest::rxSend)
      .doOnSuccess(consumer::accept)
      .ignoreElement();

    // VertX 3
    // final var subject = CompletableSubject.create();
    // final var request = client.request(HttpMethod.GET, new RequestOptions().setPort(PORT_HTTP).setURI(path).setSsl(false));
    // request.exceptionHandler(subject::onError);
    // request.endHandler(nothing -> subject.onComplete());
    // request.handler(consumer::accept);
    // request.end();
    // return subject;
  }
}
