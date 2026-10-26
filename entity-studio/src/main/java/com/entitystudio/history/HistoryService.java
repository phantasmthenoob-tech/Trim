package com.entitystudio.history;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Per-player bounded undo history + audit trail. Bukkit-free and unit tested. */
public class HistoryService {

    public enum UndoResult {
        DONE,
        EMPTY,
        IRREVERSIBLE,
        FAILED,
    }

    public record UndoOutcome(UndoResult result, EditRecord record, String detail) {
    }

    private final Map<UUID, Deque<EditRecord>> stacks = new ConcurrentHashMap<>();
    private int maxEntries = 500;

    public void setMaxEntries(int maxEntries) {
        this.maxEntries = Math.max(10, maxEntries);
    }

    public void record(EditRecord record) {
        Deque<EditRecord> stack = stacks.computeIfAbsent(record.actorUuid(), k -> new ArrayDeque<>());
        synchronized (stack) {
            stack.addFirst(record);
            while (stack.size() > maxEntries) {
                stack.removeLast();
            }
        }
    }

    public List<EditRecord> recent(UUID actor, int limit) {
        Deque<EditRecord> stack = stacks.get(actor);
        if (stack == null) {
            return List.of();
        }
        synchronized (stack) {
            return new ArrayList<>(stack).subList(0, Math.min(limit, stack.size()));
        }
    }

    public int size(UUID actor) {
        Deque<EditRecord> stack = stacks.get(actor);
        return stack == null ? 0 : stack.size();
    }

    /** Newest record without popping — lets callers pre-check its target. */
    public EditRecord peekFirst(UUID actor) {
        Deque<EditRecord> stack = stacks.get(actor);
        if (stack == null) {
            return null;
        }
        synchronized (stack) {
            return stack.peekFirst();
        }
    }

    public void clear(UUID actor) {
        stacks.remove(actor);
    }

    /** Pops the newest reversible record and runs its undo. */
    public UndoOutcome undoLast(UUID actor) {
        Deque<EditRecord> stack = stacks.get(actor);
        if (stack == null) {
            return new UndoOutcome(UndoResult.EMPTY, null, "Nothing to undo.");
        }
        EditRecord record;
        synchronized (stack) {
            record = stack.pollFirst();
        }
        if (record == null) {
            return new UndoOutcome(UndoResult.EMPTY, null, "Nothing to undo.");
        }
        if (!record.reversible() || record.undo() == null) {
            return new UndoOutcome(UndoResult.IRREVERSIBLE, record,
                    "Operation '" + record.label() + "' cannot be undone.");
        }
        try {
            record.undo().undo();
            return new UndoOutcome(UndoResult.DONE, record, record.label());
        } catch (Exception e) {
            return new UndoOutcome(UndoResult.FAILED, record,
                    "Undo failed: " + e.getMessage());
        }
    }
}
