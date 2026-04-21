import java.util.*;

public class TranscriptionHistory {
    private final String[] entries;
    private int head = 0;
    private int size = 0;

    public TranscriptionHistory(int capacity) {
        this.entries = new String[capacity];
    }

    public synchronized void add(String text) {
        entries[head % entries.length] = text;
        head = (head + 1) % entries.length;
        if (size < entries.length) size++;
    }

    public synchronized List<String> recent() {
        List<String> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            int idx = (head - 1 - i + entries.length) % entries.length;
            if (entries[idx] != null) list.add(entries[idx]);
        }
        return list;
    }

    public synchronized String last() {
        if (size == 0) return null;
        return entries[(head - 1 + entries.length) % entries.length];
    }

    public synchronized int size() {
        return size;
    }
}