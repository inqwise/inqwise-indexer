package com.inqwise.indexer;

import java.util.List;
import java.util.stream.Collectors;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

public class PreloadIndexer extends Indexer {
  public static final String LAST_HEADER = "is_last";

  private MessageConsumer<JsonArray> loaderConsumer;
  private boolean completed;

  public PreloadIndexer(
    Vertx vertx,
    IndexerModel model,
    Indexer nextIndexer,
    IndexerDocumentStore documentStore,
    IndexerOptions options
  ) {
    super(vertx, model, nextIndexer, documentStore, options);
  }

  @Override
  protected Future<Void> startListeners() {
    return switch (model.getStatus()) {
      case STARTED -> startPreloadListener();
      case COMPLETED -> preloadComplete();
      case NON_ACTIVE, DELETED -> Future.succeededFuture();
    };
  }

  private Future<Void> startPreloadListener() {
    if (loaderConsumer != null) {
      return Future.succeededFuture();
    }

    loaderConsumer = vertx.eventBus()
      .<JsonArray>consumer(model.getUid())
      .handler(this::onPreloadIndexAction)
      .exceptionHandler(Throwable::printStackTrace);

    return Future.succeededFuture();
  }

  protected void onPreloadIndexAction(Message<JsonArray> message) {
    Future<Void> result = Future.succeededFuture();

    if (nextIndexer == null) {
      result = Future.failedFuture("preload indexer has no replacement indexer");
    } else if (!message.body().isEmpty()) {
      List<IndexerActionItem> requests = message.body().stream()
        .map(JsonObject.class::cast)
        .map(IndexerActionItem::fromJson)
        .collect(Collectors.toList());

      result = result.compose(ignored -> nextIndexer.index(requests));
    }

    if (message.headers().contains(LAST_HEADER)) {
      result = result.compose(ignored -> preloadComplete());
    }

    result
      .onSuccess(ignored -> message.reply(new JsonObject()))
      .onFailure(error -> message.fail(1, error.getMessage()));
  }

  private Future<Void> preloadComplete() {
    if (completed) {
      return Future.succeededFuture();
    }

    completed = true;
    Future<Void> close = loaderConsumer == null ? Future.succeededFuture() : loaderConsumer.unregister();
    loaderConsumer = null;

    return close;
  }

  public boolean isCompleted() {
    return completed;
  }
}
