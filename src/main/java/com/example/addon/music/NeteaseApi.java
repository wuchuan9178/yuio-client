package com.example.addon.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class NeteaseApi {
    private static final String REFERER = "https://music.163.com/";
    private static final String AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120 Safari/537.36";

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build();

    public record Song(long id, String name, String artist, String album, String coverUrl) {
    }

    private NeteaseApi() {
    }

    public static Song find(String title, String artist) {
        if (title == null || title.isBlank()) return null;

        String keywords = artist == null || artist.isBlank() ? title : title + " " + artist;

        try {
            String query = URLEncoder.encode(keywords, StandardCharsets.UTF_8);
            JsonObject result = field(get("https://music.163.com/api/search/get?s=" + query + "&type=1&limit=1"), "result");
            if (result == null) return null;

            JsonArray songs = array(result, "songs");
            if (songs == null || songs.isEmpty()) return null;

            JsonObject song = songs.get(0).getAsJsonObject();
            long id = song.get("id").getAsLong();

            return new Song(id, text(song, "name"), firstArtist(song), albumName(song), cover(id));
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String cover(long id) {
        try {
            JsonObject root = get("https://music.163.com/api/song/detail?ids=%5B" + id + "%5D");
            JsonArray songs = array(root, "songs");
            if (songs == null || songs.isEmpty()) return null;

            JsonObject album = field(songs.get(0).getAsJsonObject(), "album");
            String url = text(album, "picUrl");
            return url == null || url.isBlank() ? null : url;
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String lyrics(long id) {
        try {
            JsonObject lrc = field(get("https://music.163.com/api/song/lyric?id=" + id + "&lv=-1&tv=-1"), "lrc");
            String text = text(lrc, "lyric");
            return text == null || text.isBlank() ? null : text;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static String firstArtist(JsonObject song) {
        JsonArray artists = array(song, "artists");
        if (artists == null || artists.isEmpty()) return "";

        StringBuilder builder = new StringBuilder();
        for (JsonElement element : artists) {
            String name = text(element.getAsJsonObject(), "name");
            if (name == null || name.isBlank()) continue;
            if (!builder.isEmpty()) builder.append('/');
            builder.append(name);
        }

        return builder.toString();
    }

    private static String albumName(JsonObject song) {
        JsonObject album = field(song, "album");
        String name = text(album, "name");
        return name == null ? "" : name;
    }

    private static JsonObject get(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .header("Referer", REFERER)
            .header("User-Agent", AGENT)
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) return null;

        JsonElement root = JsonParser.parseString(response.body());
        return root.isJsonObject() ? root.getAsJsonObject() : null;
    }

    private static JsonObject field(JsonObject object, String key) {
        if (object == null) return null;
        JsonElement element = object.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray array(JsonObject object, String key) {
        if (object == null) return null;
        JsonElement element = object.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String text(JsonObject object, String key) {
        if (object == null) return null;
        JsonElement element = object.get(key);
        return element == null || element.isJsonNull() ? null : element.getAsString();
    }
}
