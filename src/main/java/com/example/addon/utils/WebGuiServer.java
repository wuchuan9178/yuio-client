package com.example.addon.utils;

import com.google.gson.*;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

public class WebGuiServer {
    private HttpServer server;
    private ScheduledExecutorService ticker;
    private final Set<Client> clients = ConcurrentHashMap.newKeySet();
    private final Gson gson = new Gson();

    private static class Client {
        final BlockingQueue<String> queue = new LinkedBlockingQueue<>();
    }

    public void start(int port) throws IOException {
        // 每次新建，避免上次 stop 后失效
        if (ticker != null && !ticker.isShutdown()) ticker.shutdownNow();
        ticker = Executors.newSingleThreadScheduledExecutor();

        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::handleRoot);
        server.createContext("/events", this::handleEvents);
        server.createContext("/toggle", this::handleToggle);
        server.createContext("/set", this::handleSet);
        server.createContext("/bind", this::handleBind);
        server.createContext("/bg", this::handleBg);
        server.setExecutor(Executors.newCachedThreadPool());
        server.start();
        ticker.scheduleAtFixedRate(this::broadcast, 0, 500, TimeUnit.MILLISECONDS);
    }

    public void stop() {
        if (ticker != null) ticker.shutdownNow();
        if (server != null) server.stop(0);
        for (Client c : clients) c.queue.offer("__close__");
        clients.clear();
    }

    private void handleRoot(HttpExchange ex) throws IOException {
        serveResource(ex, "/assets/yuio/webgui/index.html", "text/html; charset=utf-8");
    }

    private void handleBg(HttpExchange ex) throws IOException {
        String[] names = {"bg.mp4", "bg.png", "bg.jpg"};
        for (String name : names) {
            String path = "/assets/yuio/webgui/" + name;
            InputStream in = getClass().getResourceAsStream(path);
            if (in == null) continue;

            byte[] body;
            try {
                body = in.readAllBytes();
            } finally {
                in.close();
            }

            String mime = name.endsWith(".mp4") ? "video/mp4" :
                name.endsWith(".png") ? "image/png" : "image/jpeg";

            Headers res = ex.getResponseHeaders();
            res.set("Content-Type", mime);
            res.set("Content-Length", String.valueOf(body.length));
            res.set("Cache-Control", "no-cache");
            res.set("Access-Control-Allow-Origin", "*");

            ex.sendResponseHeaders(200, body.length);
            ex.getResponseBody().write(body);
            ex.close();
            System.out.println("[yuio-webgui] served " + name + " (" + body.length + " bytes)");
            return;
        }
        System.out.println("[yuio-webgui] no bg resource found in assets/yuio/webgui/");
        ex.sendResponseHeaders(404, -1);
        ex.close();
    }

    private void serveResource(HttpExchange ex, String path, String mime) throws IOException {
        try (InputStream in = getClass().getResourceAsStream(path)) {
            if (in == null) {
                byte[] m = "not found".getBytes();
                ex.sendResponseHeaders(404, m.length);
                ex.getResponseBody().write(m);
            } else {
                byte[] body = in.readAllBytes();
                ex.getResponseHeaders().set("Content-Type", mime);
                ex.sendResponseHeaders(200, body.length);
                ex.getResponseBody().write(body);
            }
        } finally {
            ex.close();
        }
    }

    private void handleEvents(HttpExchange ex) throws IOException {
        ex.getResponseHeaders().set("Content-Type", "text/event-stream");
        ex.getResponseHeaders().set("Cache-Control", "no-cache");
        ex.sendResponseHeaders(200, 0);

        Client c = new Client();
        clients.add(c);
        c.queue.offer(stateJson());

        try (OutputStream os = ex.getResponseBody()) {
            while (true) {
                String msg = c.queue.poll(30, TimeUnit.SECONDS);
                if (msg == null) {
                    os.write(": ping\n\n".getBytes());
                    os.flush();
                    continue;
                }
                if (msg.equals("__close__")) break;
                os.write(("data: " + msg + "\n\n").getBytes(StandardCharsets.UTF_8));
                os.flush();
            }
        } catch (Exception ignored) {
        } finally {
            clients.remove(c);
        }
    }

    private void handleToggle(HttpExchange ex) throws IOException {
        String name = parseQuery(ex.getRequestURI().getQuery()).get("module");
        if (name != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                Module m = Modules.get().get(name);
                if (m != null) m.toggle();
            });
        }
        respondOk(ex);
    }

    private void handleSet(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
        String mod = q.get("module"), set = q.get("setting"), val = q.get("value");
        if (mod != null && set != null && val != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                Module m = Modules.get().get(mod);
                if (m == null) return;
                outer:
                for (SettingGroup g : m.settings) {
                    for (Setting<?> s : g) {
                        if (s.name.equals(set)) { s.parse(val); break outer; }
                    }
                }
            });
        }
        respondOk(ex);
    }

    private void handleBind(HttpExchange ex) throws IOException {
        Map<String, String> q = parseQuery(ex.getRequestURI().getQuery());
        String mod = q.get("module");
        String keyStr = q.get("key");
        if (mod != null && keyStr != null) {
            MinecraftClient mc = MinecraftClient.getInstance();
            mc.execute(() -> {
                Module m = Modules.get().get(mod);
                if (m == null) return;
                try {
                    int key = Integer.parseInt(keyStr);
                    Object kb = m.keybind;
                    Class<?> c = kb.getClass();

                    if (key < 0) {
                        try { c.getMethod("reset").invoke(kb); }
                        catch (Throwable ignored) {}
                    } else {
                        if (tryInvoke(c, kb, "set", new Class[]{boolean.class, int.class, int.class},
                            new Object[]{true, key, 0})) return;
                        if (tryInvoke(c, kb, "set", new Class[]{boolean.class, int.class},
                            new Object[]{true, key})) return;
                        if (tryInvoke(c, kb, "set", new Class[]{int.class},
                            new Object[]{key})) return;
                        tryInvoke(c, kb, "set", new Class[]{int.class, int.class},
                            new Object[]{key, 0});
                    }
                } catch (Exception ignored) {}
            });
        }
        respondOk(ex);
    }

    private static boolean tryInvoke(Class<?> c, Object target, String name, Class<?>[] types, Object[] args) {
        try {
            Method m = c.getMethod(name, types);
            m.invoke(target, args);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void respondOk(HttpExchange ex) throws IOException {
        byte[] ok = "ok".getBytes();
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.sendResponseHeaders(200, ok.length);
        ex.getResponseBody().write(ok);
        ex.close();
    }

    private void broadcast() {
        if (clients.isEmpty()) return;
        String json = stateJson();
        for (Client c : clients) c.queue.offer(json);
    }

    private static String addonOf(Module m) {
        String pkg = m.getClass().getPackageName();
        if (pkg.startsWith("meteordevelopment.meteorclient")) return "Meteor";
        if (pkg.startsWith("com.example.addon")) return "yuio";
        if (pkg.startsWith("dev.sherii")) return "sherii";
        String[] parts = pkg.split("\\.");
        return parts.length >= 2 ? parts[0] + "." + parts[1] : pkg;
    }

    private static String currentKey(Module m) {
        try {
            Object kb = m.keybind;
            Class<?> c = kb.getClass();

            boolean bound = false;
            Object boundRes = tryCall(c, kb, "isSet");
            if (boundRes == null) boundRes = tryCall(c, kb, "hasKey");
            if (boundRes instanceof Boolean b) bound = b;
            if (!bound) return "";

            Object v = tryCall(c, kb, "getValue");
            if (v == null) v = tryCall(c, kb, "get");
            if (v == null) v = tryCall(c, kb, "getKey");
            if (v instanceof Integer i) return i < 0 ? "" : String.valueOf(i);
        } catch (Throwable ignored) {}
        return "";
    }

    private static Object tryCall(Class<?> c, Object target, String name) {
        try {
            Method m = c.getMethod(name);
            return m.invoke(target);
        } catch (Throwable t) {
            return null;
        }
    }

    private String stateJson() {
        JsonArray arr = new JsonArray();
        for (Module m : Modules.get().getAll()) {
            if (m.category == null) continue;
            JsonObject o = new JsonObject();
            o.addProperty("name", m.name);
            o.addProperty("desc", m.description);
            o.addProperty("cat", m.category.name);
            o.addProperty("addon", addonOf(m));
            o.addProperty("on", m.isActive());
            o.addProperty("key", currentKey(m));

            JsonArray settings = new JsonArray();
            for (SettingGroup group : m.settings) {
                for (Setting<?> s : group) {
                    JsonObject so = new JsonObject();
                    so.addProperty("name", s.name);
                    so.addProperty("type", s.getClass().getSimpleName());
                    so.addProperty("value", String.valueOf(s.get()));
                    settings.add(so);
                }
            }
            o.add("settings", settings);
            arr.add(o);
        }
        JsonObject root = new JsonObject();
        root.add("modules", arr);
        return gson.toJson(root);
    }

    private static Map<String, String> parseQuery(String q) {
        Map<String, String> m = new HashMap<>();
        if (q == null) return m;
        for (String pair : q.split("&")) {
            int i = pair.indexOf('=');
            if (i > 0) {
                try {
                    m.put(URLDecoder.decode(pair.substring(0, i), "UTF-8"),
                        URLDecoder.decode(pair.substring(i + 1), "UTF-8"));
                } catch (Exception ignored) {}
            }
        }
        return m;
    }
}
