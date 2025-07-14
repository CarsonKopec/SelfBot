package com.github.imagineforgee.selfbotlib.dispatch;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public class EventDispatcher {

    private final Sinks.Many<Event> eventSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Flux<Event> eventFlux = eventSink.asFlux();

    private final Map<String, Function<JsonObject, ? extends Event>> eventParsers = new ConcurrentHashMap<>();

    public <T extends Event> void registerParser(String eventType, Function<JsonObject, T> parser) {
        eventParsers.put(eventType, parser);
    }

    public void dispatch(JsonObject gatewayPayload) {
        if (!gatewayPayload.has("t") || !gatewayPayload.has("d")) return;

        String eventType = gatewayPayload.get("t").getAsString();
        JsonElement dataEl = gatewayPayload.get("d");

        if (!dataEl.isJsonObject()) {
            System.out.printf("[Dispatcher] Skipped event '%s' — not a JsonObject%n", eventType);
            return;
        }

        JsonObject data = dataEl.getAsJsonObject();
        Function<JsonObject, ? extends Event> parser = eventParsers.get(eventType);
        if (parser != null) {
            Event event = parser.apply(data);
            eventSink.tryEmitNext(event);
        }
    }


    public Flux<Event> getEventFlux() {
        return eventFlux;
    }

    public <T extends Event> Flux<T> onEvent(Class<T> clazz) {
        return eventFlux
                .filter(event -> clazz.isAssignableFrom(event.getClass()))
                .map(clazz::cast);
    }
}
