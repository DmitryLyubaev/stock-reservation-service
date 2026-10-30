package dev.dlyubaev.stockreservation.commands;

public record OperationResult(int status, String body, boolean replayed) {}
