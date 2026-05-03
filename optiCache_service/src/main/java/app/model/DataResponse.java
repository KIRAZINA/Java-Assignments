package app.model;

public final class DataResponse {
    private final String query;
    private final String data;
    private final boolean cached;
    private final long timestamp;

    public DataResponse(String query, String data, boolean cached, long timestamp) {
        this.query = query;
        this.data = data;
        this.cached = cached;
        this.timestamp = timestamp;
    }

    public String getQuery() {
        return query;
    }

    public String getData() {
        return data;
    }

    public boolean isCached() {
        return cached;
    }

    public long getTimestamp() {
        return timestamp;
    }
}
