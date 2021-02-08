package com.noenv.markus;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Promise;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

import java.util.LinkedList;
import java.util.Optional;
import java.util.Queue;

public final class MainVerticle extends AbstractVerticle {

  public static final int PORT_HTTP = 8080;

  public static final String PATH_CREATE = "/create";
  public static final String PATH_REMOVE = "/remove";

  private final Queue<MessageConsumer<?>> consumers = new LinkedList<>();

  @Override
  public void start(final Promise<Void> promise) {
    final var router = Router.router(vertx);

    router.route().failureHandler(this::failure);
    router.route(PATH_CREATE).handler(this::create);
    router.route(PATH_REMOVE).handler(this::remove);

    vertx.createHttpServer()
      .requestHandler(router)
      .listen(PORT_HTTP, ar -> { if (ar.succeeded()) promise.complete(); else promise.fail(ar.cause()); });
  }

  private void failure(final RoutingContext context) {
    final var response = context.response();
    response.putHeader("content-type", "text/plain");
    response.setStatusCode(500);
    response.end(context.failure().getMessage());
  }

  private void create(final RoutingContext context) {
    consumers.offer(vertx.eventBus().<Void>consumer("address").handler(this::consume));
    context.response().end();
  }

  private void consume(final Message<Void> message) {
    // no-op - just setting a handler because this triggers ClusteredEventBus#onLocalRegistration
  }

  private void remove(final RoutingContext context) {
    Optional.ofNullable(consumers.poll()).ifPresent(MessageConsumer::unregister);
    context.response().end();
  }
}
