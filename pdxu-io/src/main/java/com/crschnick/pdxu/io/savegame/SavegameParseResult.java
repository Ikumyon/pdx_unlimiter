package com.crschnick.pdxu.io.savegame;

import com.crschnick.pdxu.io.node.Node;

import java.util.Optional;

public abstract class SavegameParseResult {

    public abstract void visit(Visitor visitor);

    public Optional<Success> success() {
        return Optional.empty();
    }

    public static class Success extends SavegameParseResult {

        public SavegameContent content;

        public Success(SavegameContent content) {
            this.content = content;
        }

        public Node combinedNode() {
            return content.combinedNode();
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.success(this);
        }

        @Override
        public Optional<Success> success() {
            return Optional.of(this);
        }
    }

    public static class Error extends SavegameParseResult {

        public Throwable error;

        public Error(Throwable error) {
            this.error = error;
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.error(this);
        }
    }

    public static class Invalid extends SavegameParseResult {

        public String message;

        public Invalid(String message) {
            this.message = message;
        }

        @Override
        public void visit(Visitor visitor) {
            visitor.invalid(this);
        }
    }

    public static abstract class Visitor {

        public void success(Success s) {
        }

        public void error(Error e) {
        }

        public void invalid(Invalid iv) {
        }
    }
}
