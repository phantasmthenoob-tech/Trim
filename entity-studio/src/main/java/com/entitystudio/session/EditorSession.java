package com.entitystudio.session;

import java.time.Instant;
import java.util.UUID;

/** One open editing session: who is editing what, since when, on which page. */
public record EditorSession(UUID entityUuid, UUID editorUuid, Instant openedAt, String page) {
    public EditorSession withPage(String page) {
        return new EditorSession(entityUuid, editorUuid, openedAt, page);
    }
}
