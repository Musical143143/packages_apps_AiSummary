package com.example.aisummary;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;

public class MainActivity extends Activity {

    // ================================
    // 1) CONFIGURE YOUR AI SERVER HERE
    // ================================

    // Example: OpenAI official endpoint
    private static final String API_URL = "https://api.openai.com/v1/chat/completions";

    // ⚠️ Put your own key here. Don't publish it to public GitHub.
    private static final String API_KEY = "YOUR_API_KEY_HERE";

    // Example model: gpt-4.1-mini or gpt-4o-mini or any supported model
    private static final String MODEL_NAME = "gpt-4.1-mini";

    private EditText inputText;
    private Button summarizeBtn;
    private TextView outputText;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        inputText = findViewById(R.id.inputText);
        summarizeBtn = findViewById(R.id.summarizeBtn);
        outputText = findViewById(R.id.outputText);
        statusText = findViewById(R.id.statusText);

        summarizeBtn.setOnClickListener(v -> {
            String text = inputText.getText().toString().trim();
            if (text.isEmpty()) {
                outputText.setText("Please enter some text to summarize.");
                return;
            }

            // UI updates
            summarizeBtn.setEnabled(false);
            statusText.setText("Contacting AI server...");
            outputText.setText("");

            // Run network call in background thread
            new Thread(() -> {
                try {
                    String summary = callAiSummary(text);
                    runOnUiThread(() -> {
                        statusText.setText("Done.");
                        outputText.setText(summary);
                        summarizeBtn.setEnabled(true);
                    });
                } catch (Exception e) {
                    e.printStackTrace();
                    runOnUiThread(() -> {
                        statusText.setText("Error while calling AI.");
                        outputText.setText("Error: " + e.getMessage());
                        summarizeBtn.setEnabled(true);
                    });
                }
            }).start();
        });
    }

    /**
     * Calls an OpenAI-compatible chat completion endpoint to summarize the given text.
     */
    private String callAiSummary(String input) throws Exception {
        // Build JSON body:
        // {
        //   "model": "gpt-4.1-mini",
        //   "messages": [
        //     {"role":"system","content":"You are a summarization engine ..."},
        //     {"role":"user","content":"<input text>"}
        //   ]
        // }

        JSONObject body = new JSONObject();

        body.put("model", MODEL_NAME);

        JSONArray messages = new JSONArray();

        JSONObject systemMsg = new JSONObject();
        systemMsg.put("role", "system");
        systemMsg.put("content",
                "You are a summarization engine. Summarize the user's text in 3–5 concise sentences. " +
                "Do not add extra commentary, just the summary.");
        messages.put(systemMsg);

        JSONObject userMsg = new JSONObject();
        userMsg.put("role", "user");
        userMsg.put("content", input);
        messages.put(userMsg);

        body.put("messages", messages);

        // Open connection
        URL url = new URL(API_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        try {
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(60000);

            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);

            // Send JSON request
            OutputStream os = conn.getOutputStream();
            os.write(body.toString().getBytes("UTF-8"));
            os.flush();
            os.close();

            int code = conn.getResponseCode();
            BufferedReader br;
            if (code >= 200 && code < 300) {
                br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            } else {
                br = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            }

            StringBuilder respBuilder = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                respBuilder.append(line);
            }
            br.close();

            if (code < 200 || code >= 300) {
                throw new Exception("HTTP " + code + ": " + respBuilder.toString());
            }

            String resp = respBuilder.toString();
            return parseSummaryFromResponse(resp);

        } finally {
            conn.disconnect();
        }
    }

    /**
     * Parses the first message content from OpenAI-style response:
     * {
     *   "choices": [
     *     {
     *       "message": {
     *          "role": "assistant",
     *          "content": "summary text here"
     *       }
     *     }
     *   ]
     * }
     */
    private String parseSummaryFromResponse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONArray choices = root.getJSONArray("choices");
        if (choices.length() == 0) {
            throw new Exception("No choices in AI response.");
        }

        JSONObject first = choices.getJSONObject(0);
        JSONObject message = first.getJSONObject("message");
        String content = message.getString("content");

        if (content == null || content.trim().isEmpty()) {
            throw new Exception("Empty summary from AI.");
        }

        return content.trim();
    }
}
