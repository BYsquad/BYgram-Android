package org.telegram.messenger;

import android.net.Uri;
import android.text.Html;
import android.util.AtomicFile;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class BygramTranslation {
    public enum Provider {
        TELEGRAM("Telegram"), GOOGLE("Google"), YANDEX("Yandex"), MICROSOFT("Microsoft");

        public final String label;

        Provider(String label) {
            this.label = label;
        }
    }

    private static final ExecutorService executor = Executors.newFixedThreadPool(3);
    private static final Pattern htmlEntity = Pattern.compile("&(?:#[0-9]+|#x[0-9a-fA-F]+|[a-zA-Z]+);");
    private static JSONObject settings;
    private static Provider selectedProvider;

    private BygramTranslation() {}

    private static AtomicFile settingsFile() {
        return new AtomicFile(new File(ApplicationLoader.applicationContext.getNoBackupFilesDir(), "bygram_translation.json"));
    }

    private static JSONObject settings() {
        if (settings == null) {
            try {
                settings = new JSONObject(new String(settingsFile().readFully(), StandardCharsets.UTF_8));
            } catch (FileNotFoundException ignored) {
                settings = new JSONObject();
            } catch (IOException | JSONException e) {
                FileLog.e("Unable to read translation settings");
                settings = new JSONObject();
            }
        }
        return settings;
    }

    public static synchronized Provider provider() {
        if (selectedProvider == null) {
            try {
                selectedProvider = Provider.valueOf(settings().optString("provider"));
            } catch (IllegalArgumentException e) {
                selectedProvider = Provider.TELEGRAM;
            }
        }
        return selectedProvider;
    }

    public static synchronized String credential(Provider provider, boolean apiKey) {
        JSONObject credentials = settings().optJSONObject(provider.name());
        return credentials == null ? "" : credentials.optString(apiKey ? "key" : "location");
    }

    public static synchronized boolean select(Provider provider, String apiKey, String location) {
        if (provider != Provider.TELEGRAM && (apiKey.isEmpty() || provider == Provider.YANDEX && location.isEmpty())) {
            return false;
        }
        AtomicFile file = settingsFile();
        FileOutputStream output = null;
        try {
            JSONObject updated = new JSONObject(settings().toString()).put("provider", provider.name());
            if (provider != Provider.TELEGRAM) {
                updated.put(provider.name(), new JSONObject().put("key", apiKey).put("location", location));
            }
            output = file.startWrite();
            output.write(updated.toString().getBytes(StandardCharsets.UTF_8));
            file.finishWrite(output);
            settings = updated;
            selectedProvider = provider;
            return true;
        } catch (IOException | JSONException e) {
            if (output != null) file.failWrite(output);
            return false;
        }
    }

    public static void translate(Provider provider, String text, String language, BooleanSupplier active, Utilities.Callback2<String, Boolean> callback) {
        final String apiKey = credential(provider, true);
        final String location = credential(provider, false);
        executor.execute(() -> {
            String result = null;
            boolean rateLimited = false;
            try {
                StringBuilder translated = new StringBuilder();
                for (String part : BygramTextUtils.splitTranslationText(text, 5000)) {
                    if (!active.getAsBoolean()) {
                        AndroidUtilities.runOnUIThread(() -> callback.run(null, false));
                        return;
                    }
                    translated.append(part.trim().isEmpty() ? part : request(provider, apiKey, location, part, language));
                }
                result = translated.toString();
            } catch (RateLimitException e) {
                rateLimited = true;
            } catch (IOException | JSONException | RuntimeException ignored) {
                // Errors can contain credentials or message text.
            }
            final String translation = result;
            final boolean limited = rateLimited;
            AndroidUtilities.runOnUIThread(() -> callback.run(translation, limited));
        });
    }

    private static String request(Provider provider, String key, String location, String text, String language) throws IOException, JSONException {
        final String target = targetLanguage(provider, language);
        final String url;
        final Object body;
        switch (provider) {
            case GOOGLE:
                url = "https://translation.googleapis.com/language/translate/v2";
                body = new JSONObject().put("q", new JSONArray().put(text)).put("target", target).put("format", "text");
                break;
            case YANDEX:
                url = "https://translate.api.cloud.yandex.net/translate/v2/translate";
                body = new JSONObject().put("texts", new JSONArray().put(text)).put("targetLanguageCode", target)
                        .put("format", "PLAIN_TEXT").put("folderId", location);
                break;
            case MICROSOFT:
                url = "https://api.cognitive.microsofttranslator.com/translate?api-version=3.0&to=" + Uri.encode(target);
                body = new JSONArray().put(new JSONObject().put("Text", text));
                break;
            default:
                throw new IllegalArgumentException();
        }

        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        try {
            connection.setInstanceFollowRedirects(false);
            connection.setConnectTimeout(15000);
            connection.setReadTimeout(20000);
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            switch (provider) {
                case GOOGLE:
                    connection.setRequestProperty("x-goog-api-key", key);
                    break;
                case YANDEX:
                    connection.setRequestProperty("Authorization", "Api-Key " + key);
                    break;
                case MICROSOFT:
                    connection.setRequestProperty("Ocp-Apim-Subscription-Key", key);
                    if (!location.isEmpty()) connection.setRequestProperty("Ocp-Apim-Subscription-Region", location);
                    break;
            }
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
            }
            int status = connection.getResponseCode();
            if (status == 429) throw new RateLimitException();
            if (status != 200) throw new IOException();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder response = new StringBuilder();
                char[] buffer = new char[4096];
                int count;
                while ((count = reader.read(buffer)) != -1) {
                    response.append(buffer, 0, count);
                }
                return translatedText(provider, response.toString());
            }
        } finally {
            connection.disconnect();
        }
    }

    static String targetLanguage(Provider provider, String language) {
        String tag = TranslateController.normalizeLanguage(language);
        if (provider == Provider.MICROSOFT) {
            if ("zh".equals(tag) || "zh-CN".equals(tag)) return "zh-Hans";
            if ("zh-TW".equals(tag)) return "zh-Hant";
            if ("iw".equals(tag)) return "he";
        }
        return tag;
    }

    static String translatedText(Provider provider, String response) throws JSONException {
        switch (provider) {
            case GOOGLE:
                String text = new JSONObject(response).getJSONObject("data").getJSONArray("translations")
                        .getJSONObject(0).getString("translatedText");
                Matcher entities = htmlEntity.matcher(text);
                StringBuffer decoded = new StringBuffer();
                while (entities.find()) {
                    // Keep whitespace outside entities intact, including line breaks.
                    String wrapped = Html.fromHtml("x" + entities.group() + "x", Html.FROM_HTML_MODE_LEGACY).toString();
                    entities.appendReplacement(decoded, Matcher.quoteReplacement(wrapped.substring(1, wrapped.length() - 1)));
                }
                return entities.appendTail(decoded).toString();
            case YANDEX:
                return new JSONObject(response).getJSONArray("translations").getJSONObject(0).getString("text");
            case MICROSOFT:
                return new JSONArray(response).getJSONObject(0).getJSONArray("translations").getJSONObject(0).getString("text");
            default:
                throw new IllegalArgumentException();
        }
    }

    private static class RateLimitException extends IOException {}
}
