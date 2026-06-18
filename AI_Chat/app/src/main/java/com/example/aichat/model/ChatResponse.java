package com.example.aichat.model;

public class ChatResponse {
    private String id;
    private String object;
    private long created;
    private String model;
    private Usage usage;
    private Choice[] choices;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getObject() {
        return object;
    }

    public void setObject(String object) {
        this.object = object;
    }

    public long getCreated() {
        return created;
    }

    public void setCreated(long created) {
        this.created = created;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public Usage getUsage() {
        return usage;
    }

    public void setUsage(Usage usage) {
        this.usage = usage;
    }

    public Choice[] getChoices() {
        return choices;
    }

    public void setChoices(Choice[] choices) {
        this.choices = choices;
    }

    public static class Usage {
        private int prompt_tokens;
        private int completion_tokens;
        private int total_tokens;

        public int getPromptTokens() {
            return prompt_tokens;
        }

        public void setPromptTokens(int prompt_tokens) {
            this.prompt_tokens = prompt_tokens;
        }

        public int getCompletionTokens() {
            return completion_tokens;
        }

        public void setCompletionTokens(int completion_tokens) {
            this.completion_tokens = completion_tokens;
        }

        public int getTotalTokens() {
            return total_tokens;
        }

        public void setTotalTokens(int total_tokens) {
            this.total_tokens = total_tokens;
        }
    }

    public static class Choice {
        private Message message;
        private String finish_reason;
        private int index;

        public Message getMessage() {
            return message;
        }

        public void setMessage(Message message) {
            this.message = message;
        }

        public String getFinishReason() {
            return finish_reason;
        }

        public void setFinishReason(String finish_reason) {
            this.finish_reason = finish_reason;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }
    }

    public static class StreamResponse {
        private String id;
        private String object;
        private long created;
        private String model;
        private StreamChoice[] choices;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getObject() {
            return object;
        }

        public void setObject(String object) {
            this.object = object;
        }

        public long getCreated() {
            return created;
        }

        public void setCreated(long created) {
            this.created = created;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public StreamChoice[] getChoices() {
            return choices;
        }

        public void setChoices(StreamChoice[] choices) {
            this.choices = choices;
        }
    }

    public static class StreamChoice {
        private StreamDelta delta;
        private String finish_reason;
        private int index;

        public StreamDelta getDelta() {
            return delta;
        }

        public void setDelta(StreamDelta delta) {
            this.delta = delta;
        }

        public String getFinishReason() {
            return finish_reason;
        }

        public void setFinishReason(String finish_reason) {
            this.finish_reason = finish_reason;
        }

        public int getIndex() {
            return index;
        }

        public void setIndex(int index) {
            this.index = index;
        }
    }

    public static class StreamDelta {
        private String role;
        private String content;

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}
